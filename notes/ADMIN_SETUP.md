# Log-in setup (Firebase console, once)

## How log-in works now
- **Real mode:** riders tap **Create account** (username + password) or **Continue with Google**.
  There are NO built-in accounts and no passwords on screen.
- **Admin (real mode):** the username `admin` (Firebase email `admin@biyahe.app`), with a strong password
  only your group knows (at least 10 characters, letters and numbers). The Firestore rules below are what
  give it access to everyone's data.
- **Demo mode only:** `user / user123` and `admin / admin123`, offline, sample data, never Firebase.
  Chanelly types these during the stage tour.
- Each rider's trips and reports are saved to their own account and come back on any phone.

## 0. Remove the old built-in accounts (one time)
Earlier versions created `admin@biyahe.app` (password admin123) and `user@biyahe.app` (user123) in Firebase.
1. (Optional) Log in as `user` once more and use Settings, "Delete my trips and reports" if you don't want its data kept.
2. Firebase console → Authentication → **Users** → delete **admin@biyahe.app** and **user@biyahe.app**.
3. In the app (real mode): **Create account** → username `admin`, a strong password → you're in the admin dashboard.

## 1. Turn on email log-in
Firebase console → **Authentication** → Sign-in method → enable **Email/Password**.
Keep **Anonymous** on as well (phones that synced before log-in existed are upgraded from it, so
their cloud data is kept).


## 2. Replace the Firestore rules
Firestore Database → **Rules** → paste, then **Publish**:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() { return request.auth != null; }
    // The built-in admin account. To add more admins, add their emails here: "name@biyahe.app".
    function isAdmin() {
      return signedIn() && request.auth.token.email in ["admin@biyahe.app"];
    }

    // Riders: full access to their own profile, trips and hazards. Admins: read everyone's.
    // Admins may also WRITE made-up "sample-..." riders (Add / Remove sample riders), never real ones.
    match /users/{uid} {
      allow read, write: if signedIn() && request.auth.uid == uid;
      allow read: if isAdmin();
      allow write: if isAdmin() && uid.matches('sample-.*');
      match /{document=**} {
        allow read, write: if signedIn() && request.auth.uid == uid;
        allow read: if isAdmin();
        allow write: if isAdmin() && uid.matches('sample-.*');
      }
    }

    // Collection-group reads used by the admin dashboard.
    match /{path=**}/trips/{id}   { allow read: if isAdmin(); }
    match /{path=**}/hazards/{id} { allow read: if isAdmin(); }

    // Log of reports sent to agencies: admins only.
    match /reports/{id} { allow read, write: if isAdmin(); }
  }
}
```

(The `admins` collection from the previous version is no longer used and can be deleted.)

## Security notes
- `admin123` / `user123` exist only in demo mode (offline, sample data) and can't reach Firebase.
- Do step 0 right away: until the old `admin@biyahe.app` is deleted, anyone who knows admin123 could still log in as admin.

## Known limits
- "Forgot password" was removed: the accounts have made-up email addresses, so a reset email can't
  arrive. An admin can reset a password in the Firebase console.
- Switching between rider accounts restarts the app (to open that rider's own data).

## Continue with Google (Gmail log-in)
1. Authentication → Sign-in method → **Google** → Enable → pick a project support email → Save.
2. Project settings (gear icon, top left) → **Your apps** → the Android app `com.biyahe.companion` →
   **Add fingerprint** → paste the debug SHA-1:
   `5E:C4:A5:F8:5B:34:EE:D2:B3:1E:71:6A:3E:75:5A:7B:7C:8B:20:ED`
   (from `gradlew :app:signingReport`; another computer or a release build has a different SHA-1: add that too).
3. On the same page, **download google-services.json** again and replace `app/google-services.json`.
4. Rebuild and reinstall. "Continue with Google" now shows the phone's Google accounts.

Google accounts are riders (username shown as `g.<gmail name>`); their data is saved to that Google
account and comes back on any phone. The admin dashboard stays on admin / admin123.
