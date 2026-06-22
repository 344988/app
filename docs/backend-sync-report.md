# Backend sync report: Android “Служебный автобус”

## Scope

This document captures the current Android project structure and the minimal network-layer preparation needed before deeper synchronization with the existing FastAPI backend. The backend is treated as read-only for this phase.

## Current Android structure

### Screens and navigation

The app is currently a single-activity Jetpack Compose app. `MainActivity` owns navigation and registers these routes:

- `auth` — login screen.
- `main_map` — map and role menu.
- `driver_setup` — driver route setup.
- `passenger_setup` — active bus selection.
- `admin_panel` — users, companies, and Wialon/GLONASS admin flows.

### Network layer

The active network entry points are:

- `BusApi` in `app/src/main/java/com/bus/app/data/Network.kt`.
- `ApiClient` in the same file.
- `BusRepository` as the app-facing data contract.
- `ApiBusRepository` as the Retrofit-backed implementation.

Implemented endpoint groups from the Android side:

- Health: `GET /health`.
- Auth: `POST /auth/login` with form fields `username`, `password`.
- Routes/location: `GET /routes/active`, `POST /location/update`, `POST /route/start`.
- Admin users: `GET /admin/users`, `POST /admin/users`.
- Admin companies: `GET /admin/companies`, `POST /admin/companies`.
- Wialon/GLONASS: accounts, test, sync units, units list.

### Storage layer

Session persistence uses DataStore via `SessionDataStore`. Runtime auth state is mirrored in `SessionRuntime` so OkHttp interceptors can attach tokens and handle `401` responses.

### Models / DTOs

Current Android DTOs include:

- Auth/session: `LoginResponse`, `UserDto`.
- Routes/location: `RouteRequest`, `RouteResponse`, `LocationUpdate`, `ActiveBus`.
- Admin: `Company`, `UserCreateRequest`.
- Wialon/GLONASS: `WialonAccount`, `WialonAccountCreateRequest`, `WialonUnit`.

### Auth flow

1. `AuthScreen` calls `AppViewModel.login`.
2. `LoginUseCase` calls `BusRepository.login`.
3. On success, `AppViewModel` stores the token in `SessionRuntime` and `SessionDataStore`.
4. `AuthInterceptor` adds `Authorization: Bearer <token>` to requests.
5. `UnauthorizedInterceptor` invokes logout flow on `401`.

### Map flow

- `MainMapScreen` uses osmdroid.
- Tiles are loaded from HTTPS OpenStreetMap tile URLs.
- A preflight HEAD request checks tile availability.
- Active buses and the current user location are rendered as map markers.

### Driver flow

- `DriverSetupScreen` lets a driver choose start/end stop names and coordinates.
- It builds `RouteRequest` and calls `AppViewModel.startRoute`.
- `AppViewModel.refreshActiveRoutes` also sends driver location before fetching active routes.

## Backend endpoint analysis status

Direct inspection of the remote backend OpenAPI schema from this environment was not possible: `GET http://37.200.79.56:8000/openapi.json` returned `403 Forbidden`. Therefore, this phase uses the Android Retrofit contract and README as the source of expected backend endpoints. A follow-up sync should use an exported OpenAPI JSON file or backend source access to verify exact request/response schemas.

## Endpoint coverage matrix

| Android need | Android endpoint currently declared | Android call path | Sync status |
| --- | --- | --- | --- |
| Login | `POST /auth/login` | `AuthScreen → AppViewModel → LoginUseCase → BusRepository` | Covered |
| Health | `GET /health` | `AppViewModel.refreshActiveRoutes → getHealthSnapshot` | Covered, schema loose (`Map<String, Any>`) |
| Active buses | `GET /routes/active` | `SyncActiveRoutesUseCase` | Covered |
| Driver location | `POST /location/update` | `SyncActiveRoutesUseCase` | Covered |
| Start route | `POST /route/start` | `DriverSetupScreen → AppViewModel.startRoute` | Covered, `start_time` currently sends UI text `Теперь` |
| Admin users | `GET/POST /admin/users` | `AdminPanelScreen → AppViewModel` | Covered |
| Admin companies | `GET/POST /admin/companies` | `AdminPanelScreen → AppViewModel` | Covered, but implementation currently uses local Retrofit interface in repository |
| Wialon accounts | `GET/POST /admin/wialon/accounts` | `AdminPanelScreen → AppViewModel` | Covered |
| Wialon test | `POST /admin/wialon/accounts/{id}/test` | `AdminPanelScreen → AppViewModel` | Covered |
| Wialon unit sync | `POST /admin/wialon/accounts/{id}/sync-units` | `AdminPanelScreen → AppViewModel` | Covered |
| Wialon units list | `GET /admin/wialon/units` | `AdminPanelScreen → AppViewModel` | Covered |
| Stop search | External Nominatim, not backend | `SearchableStopField` | Local/external logic, not backend-backed |
| Map tiles | External OpenStreetMap, not backend | `MainMapScreen` | External logic |

## Current local or outdated logic

- `AppConfig` previously hardcoded `BASE_URL` and logging instead of reading Gradle `BuildConfig` flavor values. This has been prepared for backend sync by wiring `AppConfig` to `BuildConfig`.
- Stop search still uses external Nominatim plus a local fallback list, not backend endpoints.
- `DriverSetupScreen` sends `start_time = "Теперь"`; backend may require an ISO datetime.
- `ApiBusRepository` still has a local `CompanyApi` workaround for company endpoints. It works with the same OkHttp client and base URL, but it duplicates endpoint declarations already present in `BusApi`.
- Several Compose screens and state holders are still concentrated in `MainActivity`, which makes endpoint-by-endpoint migration harder.

## Files likely to change in the next sync phases

### `app/src/main/java/com/bus/app/data/Network.kt`

What changes: adjust DTO field names/nullability and Retrofit endpoint signatures to match the real FastAPI OpenAPI schema.

Why: this is the single most important Android/backend contract file.

### `app/src/main/java/com/bus/app/data/repository/ApiBusRepository.kt`

What changes: normalize response handling, remove local endpoint workarounds after `BusApi` is confirmed, and map API errors consistently.

Why: repository is where Retrofit responses become app-facing results.

### `app/src/main/java/com/bus/app/data/repository/BusRepository.kt`

What changes: update repository methods only when backend capabilities are confirmed.

Why: ViewModel and tests depend on this contract.

### `app/src/main/java/com/bus/app/AppViewModel.kt`

What changes: adapt business flow to backend response schemas and validation rules.

Why: it orchestrates auth, route sync, admin, and Wialon flows.

### `app/src/main/java/com/bus/app/MainActivity.kt`

What changes: only shallow UI wiring initially; deeper UI decomposition should be separate.

Why: the file is large and contains many screens, so broad edits are risky.

### `app/src/test/java/...`

What changes: update fake repositories and add backend-contract-focused unit tests.

Why: interface changes must keep tests compiling and protect sync behavior.

## New files recommended for later phases

- `data/model/*.kt` or `data/dto/*.kt` — split DTOs out of `Network.kt` after backend schema stabilizes.
- `data/network/ApiResult.kt` — typed success/error wrapper for consistent error handling.
- `data/network/BackendError.kt` — parse FastAPI validation and error payloads.
- `ui/admin/AdminPanelScreen.kt`, `ui/map/MainMapScreen.kt`, etc. — split UI from `MainActivity` after network sync.
- `docs/backend-openapi.json` — checked-in snapshot of backend OpenAPI for Android contract reviews.

## Minimal safe preparation completed in this phase

`AppConfig` now reads `BASE_URL` and `HTTP_LOGGING_ENABLED` from generated `BuildConfig` values. This makes the already-defined Gradle product flavors actually control the Android network target without deeper UI or repository rewrites.

## Next safe steps

1. Obtain backend OpenAPI JSON or backend source access.
2. Compare every DTO in `Network.kt` against FastAPI schemas.
3. Fix only request/response mismatches first.
4. Add tests around serialization-sensitive DTOs.
5. Remove the local `CompanyApi` workaround once `BusApi` is confirmed by a clean Android Studio/Gradle build.
6. Move screen code out of `MainActivity` only after API sync is stable.
