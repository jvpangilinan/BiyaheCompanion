package com.biyahe.companion.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.ui.graphics.toArgb
import com.biyahe.companion.data.model.RoutePoint
import com.biyahe.companion.sensors.BumpLevel
import com.biyahe.companion.tracking.LatLon
import com.biyahe.companion.ui.theme.ComfortFair
import com.biyahe.companion.ui.theme.ComfortRough
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.ComfortUnknown
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline as MapLine
import java.io.File

private var osmReady = false

/** One-time osmdroid setup. Tiles are cached in the app's own cache folder (no storage permission needed). */
private fun initOsmdroid(context: Context) {
    if (osmReady) return
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    config.userAgentValue = context.packageName
    val base = File(context.cacheDir, "osmdroid")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
    osmReady = true
}

/**
 * OpenStreetMap view (no API key) that draws a route.
 *  - follow = true  : keeps the newest point in the centre (live trip)
 *  - follow = false : zooms to fit the whole route (summary)
 *  - interactive = false : pan/zoom off, so it can sit inside a scrolling screen
 * Needs the INTERNET permission to download map tiles.
 */
@Composable
fun RouteMap(
    points: List<LatLon>,
    modifier: Modifier = Modifier,
    /** Same route with roughness per point. When given, the line is coloured by how rough each stretch was. */
    roughRoute: List<RoutePoint> = emptyList(),
    follow: Boolean = false,
    interactive: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        initOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(MANILA_LAT, MANILA_LON))
            if (!interactive) setOnTouchListener { _, _ -> false }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map -> draw(map, points, roughRoute, follow) }
    )
}

private fun draw(map: MapView, points: List<LatLon>, roughRoute: List<RoutePoint>, follow: Boolean) {
    map.overlays.clear()
    if (points.isEmpty()) {
        map.invalidate()
        return
    }
    val geo = points.map { GeoPoint(it.lat, it.lon) }
    val hasRoughness = roughRoute.size >= 2 && roughRoute.any { it.roughness != null }
    if (hasRoughness) {
        drawRoughLine(map, roughRoute)
    } else if (geo.size >= 2) {
        map.overlays.add(MapLine().apply {
            setPoints(geo)
            outlinePaint.color = 0xFF2F6BE8.toInt()
            outlinePaint.strokeWidth = 12f
        })
    }
    map.overlays.add(Marker(map).apply {
        position = geo.first()
        title = "Start"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    })
    if (geo.size >= 2) {
        map.overlays.add(Marker(map).apply {
            position = geo.last()
            title = if (follow) "You are here" else "End"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        })
    }

    if (follow) {
        if (map.zoomLevelDouble < 15.0) map.controller.setZoom(17.0)
        map.controller.setCenter(geo.last())
    } else {
        map.post {
            if (geo.size == 1) {
                map.controller.setZoom(17.0)
                map.controller.setCenter(geo.first())
            } else {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(geo), false, 80)
            }
        }
    }
    map.invalidate()
}

/**
 * Draws the route as green / amber / red stretches (same cut-offs as BumpLevel: under 1.2 m/s² is
 * low, under 2.2 medium, above that high) and a red dot wherever big bumps were felt.
 * Neighbouring stretches of the same colour are joined into one line to keep the map fast.
 */
private fun drawRoughLine(map: MapView, route: List<RoutePoint>) {
    var runColor = roughnessColor(route[1].roughness)
    var run = mutableListOf(GeoPoint(route[0].latitude, route[0].longitude))
    for (i in 1 until route.size) {
        val p = GeoPoint(route[i].latitude, route[i].longitude)
        val color = roughnessColor(route[i].roughness) // stretch from point i-1 to point i
        if (color != runColor) {
            addLine(map, run, runColor)
            run = mutableListOf(run.last())
            runColor = color
        }
        run.add(p)
    }
    addLine(map, run, runColor)

    val density = map.context.resources.displayMetrics.density
    route.filter { it.bumps > 0 }.forEach { p ->
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(p.latitude, p.longitude)
            icon = bumpDot(density, p.bumps)
            title = if (p.bumps == 1) "1 big bump" else "${p.bumps} big bumps"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        })
    }
}

private fun addLine(map: MapView, pts: List<GeoPoint>, color: Int) {
    if (pts.size < 2) return
    map.overlays.add(MapLine().apply {
        setPoints(pts)
        outlinePaint.color = color
        outlinePaint.strokeWidth = 14f
        outlinePaint.strokeCap = Paint.Cap.ROUND
    })
}

/** Small red dot with a white ring; a little bigger where several bumps were felt. */
private fun bumpDot(density: Float, bumps: Int): GradientDrawable {
    val sizePx = ((if (bumps >= 3) 16 else 12) * density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ComfortRough.toArgb())
        setStroke((2 * density).toInt(), AndroidColor.WHITE)
        setSize(sizePx, sizePx)
    }
}

fun roughnessColor(roughness: Double?): Int = when {
    roughness == null -> ComfortUnknown.toArgb()
    else -> when (BumpLevel.from(roughness)) {
        BumpLevel.LOW -> ComfortSmooth.toArgb()
        BumpLevel.MEDIUM -> ComfortFair.toArgb()
        BumpLevel.HIGH -> ComfortRough.toArgb()
    }
}

private const val MANILA_LAT = 14.5995
private const val MANILA_LON = 120.9842
