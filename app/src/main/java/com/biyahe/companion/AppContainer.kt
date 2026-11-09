package com.biyahe.companion

import android.content.Context
import com.biyahe.companion.auth.AccountService
import com.biyahe.companion.auth.AccountDb
import com.biyahe.companion.auth.Role
import android.annotation.SuppressLint
import android.location.LocationManager
import com.biyahe.companion.safety.CrashMonitor
import com.biyahe.companion.safety.EyeWatch
import com.biyahe.companion.safety.SafetyAlerts
import com.biyahe.companion.safety.SafetyGuards
import com.biyahe.companion.safety.TuningStore
import com.biyahe.companion.safety.SafetyStore
import com.biyahe.companion.tracking.TripEvent
import com.biyahe.companion.safety.TripMode
import com.biyahe.companion.data.local.BiyaheDatabase
import com.biyahe.companion.data.repository.HazardRepository
import com.biyahe.companion.data.repository.SavedRouteRepository
import com.biyahe.companion.data.repository.SettingsRepository
import com.biyahe.companion.data.repository.TripRepository
import com.biyahe.companion.permissions.AppPermission
import com.biyahe.companion.security.AuthSession
import com.biyahe.companion.security.BiometricAuthenticator
import com.biyahe.companion.sensors.ScreenCareController
import com.biyahe.companion.sensors.SensorHub
import com.biyahe.companion.sensors.StepLog
import com.biyahe.companion.tracking.LocationTracker
import com.biyahe.companion.tracking.NoiseMeter
import com.biyahe.companion.tracking.TripRecorder
import com.biyahe.companion.sync.CloudSync
import com.biyahe.companion.trip.RoomTripSink
import com.biyahe.companion.trip.TripController
import com.biyahe.companion.trip.TripSink
import com.biyahe.companion.ui.tour.GuideTour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container. Each section adds its pieces here:
 *
 *  SECTION 2 -> database + repositories
 *  SECTION 3 -> BiometricAuthenticator, AuthSession
 *  SECTION 4 -> SensorHub, StepLog, ScreenCareController (this file)
 *  SECTION 5 -> LocationTracker, NoiseMeter, TripRecorder (this file)
 *  SECTION 6 -> TripSink, TripController (this file); scoring objects need no wiring
 *  SECTION 8 -> CloudSync (Firebase)
 */
class AppContainer(val appContext: Context) {

    /** Long-lived scope for work that must outlive any one screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fixed for the life of the process; switching restarts the app (see DemoMode). */
    val isDemo: Boolean = DemoMode.isOn(appContext)

    /** The database file for whoever is signed in (fixed for this process; switching accounts restarts the app). */
    val databaseName: String = AccountDb.prepare(appContext, isDemo)

    val database: BiyaheDatabase by lazy { BiyaheDatabase.build(appContext, databaseName) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(database.userDao()) }
    val tripRepository: TripRepository by lazy { TripRepository(database.tripDao()) }
    val hazardRepository: HazardRepository by lazy { HazardRepository(database.hazardDao()) }
    val savedRouteRepository: SavedRouteRepository by lazy { SavedRouteRepository(database.savedRouteDao()) }

    // Security
    val authenticator: BiometricAuthenticator by lazy { BiometricAuthenticator(appContext) }
    val authSession: AuthSession = AuthSession()

    /** Log in / sign up; decides rider vs. admin (see AccountService). */
    val accounts: AccountService by lazy { AccountService(appContext, isDemo) }

    // Chanelly's guided tour (demo mode)
    val guideTour: GuideTour = GuideTour()

    // Sensors
    val sensorHub: SensorHub by lazy { SensorHub(appContext) }
    val screenCareController: ScreenCareController by lazy { ScreenCareController(sensorHub) }
    val stepLog: StepLog = StepLog()

    // Tracking (Section 5)
    val locationTracker: LocationTracker by lazy { LocationTracker(appContext) }
    val noiseMeter: NoiseMeter by lazy { NoiseMeter(appContext) }
    val tripRecorder: TripRecorder by lazy {
        TripRecorder(
            sensorHub, locationTracker, noiseMeter,
            speedLimit = { safetyGuards.effectiveLimit() },   // includes rain mode
            crashDetectionOn = { safetyStore.settings.value.crashDetectionOn },
            weavingWarningOn = { safetyStore.settings.value.weavingWarningOn },
            tuning = { tuningStore.tuning.value }
        )
    }

    // Safety: speed limit warning, sleep check, crash detection
    val safetyStore: SafetyStore by lazy { SafetyStore(appContext) }
    val tuningStore: TuningStore by lazy { TuningStore(appContext) }
    val safetyAlerts: SafetyAlerts by lazy { SafetyAlerts(appContext) { tuningStore.tuning.value } }
    val crashMonitor: CrashMonitor by lazy {
        CrashMonitor(
            context = appContext,
            store = safetyStore,
            alerts = safetyAlerts,
            scope = appScope,
            isDemo = isDemo,
            accelerometer = { sensorHub.accelerometer() },
            lastLocation = ::lastKnownLocation
        )
    }

    val safetyGuards: SafetyGuards by lazy {
        SafetyGuards(safetyStore, safetyAlerts, tripRecorder, sensorHub, hazardRepository, stepLog, appScope) { tuningStore.tuning.value }
    }

    /** Eye watch (front camera): drowsy-driving warnings while driving. */
    val eyeWatch: EyeWatch by lazy {
        EyeWatch(safetyStore, safetyAlerts, safetyGuards, tripRecorder, crashMonitor, appScope)
    }

    init {
        safetyGuards.start()
        eyeWatch.start()
        // React to the recorder: speak the speed warning, start the crash countdown.
        appScope.launch {
            tripRecorder.events.collect { e ->
                when (e) {
                    is TripEvent.Speeding ->
                        if (tripRecorder.mode == TripMode.DRIVING) safetyAlerts.speedWarning(e.limitKmh)
                        else safetyGuards.logQuietly(
                            "Vehicle over the limit",
                            "The vehicle went ${e.speedKmh.toInt()} km/h in a ${e.limitKmh} km/h limit. Logged for reports."
                        )
                    TripEvent.CrashSuspected -> crashMonitor.trigger("Impact or sudden stop while moving")
                    TripEvent.Weaving -> safetyAlerts.weavingWarning()
                }
            }
        }
    }

    /** Newest position: the live trip if one is running, otherwise the phone's last known fix. */
    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Pair<Double, Double>? {
        val s = tripRecorder.state.value
        if (s.lastLat != null && s.lastLon != null) return s.lastLat to s.lastLon
        if (!locationTracker.hasPermission()) return null
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }

    // Trip logic (Section 6)
    // Trips are stored in SQLite (Room) through the repository.
    val tripSink: TripSink by lazy { RoomTripSink(tripRepository) }
    val tripController: TripController by lazy {
        TripController(appContext, tripRecorder, locationTracker, stepLog, tripSink, appScope)
    }

    // Section 8: uploads trips and hazard reports (with photos) to Firebase
    val cloudSync: CloudSync by lazy {
        CloudSync(
            appContext, settingsRepository, tripRepository, hazardRepository, appScope, demoMode = isDemo,
            canSync = { accounts.account.value?.let { !it.demo && it.role == Role.USER } == true },
            ensureSession = { accounts.ensureFirebaseSession() }
        )
    }

    private var stepJob: Job? = null

    /**
     * Starts feeding the step counter into [stepLog] while the app process is
     * alive, so "steps before the ride" can be answered when a trip starts.
     * Safe to call repeatedly; does nothing until the permission is granted.
     */
    @Synchronized
    fun startStepLogging() {
        if (stepJob?.isActive == true) return
        if (!AppPermission.STEPS.isGranted(appContext)) return
        stepJob = appScope.launch {
            sensorHub.stepCounter().collect { total ->
                stepLog.record(System.currentTimeMillis(), total)
            }
        }
    }
}
