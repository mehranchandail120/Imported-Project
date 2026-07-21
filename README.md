# QuickCode — Flutter Port

Home Screen + Settings Screen converted from `QuickCode_index_with_deploy.html`
into a native Flutter app, built on top of your `Coder-It` project.

## What's inside

- `lib/screens/home_screen.dart` — prompt box, suggestion chips, recent
  projects grid (synced via Firestore when signed in). Matches `#homeScreen`
  in the HTML.
- `lib/screens/settings_screen.dart` — Account (Google + Email/Password via
  Firebase Auth) and AI Provider (OpenRouter / Lovable / Grok) sections.
  Matches `#qcSettingsPage`.
- `lib/services/auth_service.dart` — Firebase Auth + Google Sign-In wrapper.
- `lib/services/settings_service.dart` — on-device storage for AI provider
  settings (SharedPreferences = Flutter's equivalent of the web app's
  localStorage).
- `lib/services/ai_service.dart` — calls OpenRouter / Grok directly; built-in
  "no key needed" providers (OpenRouter default, Lovable/Gemini) are routed
  through a placeholder backend URL — **you must fill this in**, see below.
- `lib/services/projects_service.dart` — Firestore-backed recent projects.
- `lib/theme/app_theme.dart` — matches the HTML's paper/ink + violet→teal
  gradient look.

## Setup steps (Flutlab.io, no PC)

1. **Upload this zip** to Flutlab as a new/replacement project.
2. **Add Firebase to the app** (since we're using `firebase_auth` +
   `cloud_firestore`):
   - In Firebase Console → Project settings → add an Android app with your
     package name (check `android/app/build.gradle` for `applicationId`).
   - Download `google-services.json` and place it at
     `android/app/google-services.json`.
   - If you also build iOS, add an iOS app in Firebase Console and place
     `GoogleService-Info.plist` at `ios/Runner/GoogleService-Info.plist`.
3. **Enable Auth providers** in Firebase Console → Authentication → Sign-in
   method: turn on **Google** and **Email/Password**.
4. **Enable Firestore** in Firebase Console → Firestore Database → Create
   database (start in production or test mode; add rules restricting
   `users/{uid}/projects/**` to the owning user).
5. **Fix the AI backend URL**: open `lib/services/ai_service.dart` and
   replace `_builtInProxyUrl` with your real backend endpoint. Never embed a
   raw Gemini/OpenRouter key directly in the app — route "built-in" calls
   through a small server (Vercel function, like your other projects) that
   holds the real key.
6. Run `flutter pub get` (Flutlab does this automatically on build) and
   build the APK.

## Not yet ported

The Builder workspace (chat column, Files/Code/Preview/Console/Knowledge
tabs, diff modal, deploy modal, floating AI assistant) from the HTML file
is not included in this pass — only Home + Settings, as requested. Ping me
when you're ready for the Builder screen and I'll continue from here using
the same services already wired up (`AiService`, `ProjectsService`).
