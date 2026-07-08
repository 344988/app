# Codex project report: Android app “Служебный Автобус”

## 1. Project identity

- Repository path in the current environment: `/workspace/app`.
- Gradle root project name: `Служебный Авирьус`.
- Android module: `:app`.
- Android namespace/application id: `com.bus.app`.
- UI stack: Kotlin + Jetpack Compose + Navigation Compose.
- Main entry point: `app/src/main/java/com/bus/app/MainActivity.kt`.

## 2. Current build setup

### Android configuration

The Android app module is configured with:

- `compileSdk = 35`.
- `minSdk = 24`.
- `targetSdk = 35`.
- `versionCode = 1`.
- `versionName = "1.0"`.
- Java 11 source/target compatibility.
- Compose enabled.
- `BuildConfig` generation enabled.

### Product flavors

There is one flavor dimension: `environment`.

Configured flavors:

- `dev`
  - application id suffix: `.dev`
  - version suffix: `-dev`
  - `BASE_URL = "http://37.200.79.56:8000/"`
  - HTTP logging enabled
  - cleartext enabled through `network_security_config`
- `stage`
  - application id suffix: `.stage`
  - version suffix: `-stage`
  - same current base URL
  - HTTP logging enabled
  - cleartext disabled by manifest placeholder
- `prod`
  - same current base URL
  - HTTP logging disabled
  - cleartext disabled by manifest placeholder

### Dependencies already configured

Important dependencies currently present:

- Jetpack Compose Material 3 / UI / tooling.
- Navigation Compose.
- Lifecycle runtime KTX.
- Retrofit + Gson converter.
- OkHttp + logging interceptor.
- Google Play Services Location.
- AndroidX DataStore Preferences.
- osmdroid.
- JUnit / AndroidX test dependencies.

## 3. Network configuration

### AppConfig

`AppConfig` reads network values from generated `BuildConfig`:

- `BASE_URL`.
- `HTTP_LOGGING_ENABLED`.

This means Gradle flavors now control the backend URL and logging behavior.

### ApiClient

`ApiClient` builds a Retrofit client with:

- `AppConfig.BASE_URL`.
- Gson converter.
- shared OkHttp client.
- connect/read/write timeouts of 30 seconds.
- `AuthInterceptor`.
- `UnauthorizedInterceptor`.
- conditional HTTP body logging when flavor logging is enabled.

### Auth interceptor

`AuthInterceptor` automatically attaches:

```text
Authorization: Bearer <token>
```

It skips injection when:

- no token is available;
- a request already has an `Authorization` header.

### Unauthorized interceptor

`UnauthorizedInterceptor` watches for HTTP `401` and invokes `SessionRuntime.onUnauthorized`.

## 4. Retrofit API contract currently declared

`BusApi` currently declares these backend endpoints.

### Health

- `GET /health`

### Auth

- `POST /auth/login`
  - `application/x-www-form-urlencoded`
  - fields: `username`, `password`

### Routes and location

- `GET /routes/active`
- `POST /location/update`
- `POST /route/start`

### Admin companies

- `GET /admin/companies`
- `POST /admin/companies`

### Admin users

- `GET /admin/users`
- `POST /admin/users`

### Wialon / GLONASS

- `GET /admin/wialon/accounts`
- `POST /admin/wialon/accounts`
- `POST /admin/wialon/accounts/{id}/test`
- `POST /admin/wialon/accounts/{id}/sync-units`
- `GET /admin/wialon/units`

## 5. Data models / DTOs currently present

### Core models

- `BusStop`
- `UserDto`
- `LoginResponse`
- `RouteRequest`
- `RouteResponse`
- `LocationUpdate`
- `ActiveBus`
- `UserCreateRequest`
- `Company`

### Wialon / GLONASS models

- `WialonAccount`
- `WialonAccountCreateRequest`
- `WialonUnit`

## 6. Repository layer

### BusRepository

`BusRepository` is the app-facing data contract. It covers:

- health snapshot;
- login;
- active routes;
- location update;
- route start;
- company list/create;
- user list/create;
- Wialon account list/create/test;
- Wialon unit sync/list.

### ApiBusRepository

`ApiBusRepository` implements `BusRepository` and includes:

- health probing with 3 attempts;
- average ping calculation;
- packet loss percentage calculation;
- retry with exponential backoff for network calls;
- Retrofit-backed implementations for route/user/Wialon flows;
- local `CompanyApi` shim for company endpoints.

Important note: `CompanyApi` duplicates endpoints that also exist in `BusApi`. It was introduced to work around local Android Studio unresolved reference errors for company methods. This is a technical debt item and should be removed once a clean Gradle/Android Studio sync confirms `BusApi.getCompanies` and `BusApi.createCompany` resolve reliably.

## 7. Domain use-cases

Two use-cases currently exist:

- `LoginUseCase`
  - delegates login to `BusRepository.login`.
- `SyncActiveRoutesUseCase`
  - if role is `driver`, sends current location first;
  - loads active routes;
  - filters routes by company for non-admin users;
  - admin receives unfiltered routes.

## 8. AppViewModel

`AppViewModel` is the central UI/business orchestration layer.

### State

`AppUiState` currently holds:

- token;
- user role;
- user login;
- company id/name;
- user location;
- active buses;
- start/end route points;
- route polyline points;
- travel time text;
- current error message;
- API health status;
- server status level;
- ping;
- packet loss.

### Responsibilities

`AppViewModel` currently handles:

- session restore from DataStore;
- login;
- logout;
- error clearing;
- location state update;
- active route refresh;
- server health mapping to green/yellow/red status;
- admin users loading;
- companies loading/create;
- user creation;
- Wialon accounts loading/create/test;
- Wialon units sync/list;
- route start.

## 9. Session/storage layer

### SessionRuntime

Runtime singleton that stores:

- current token;
- unauthorized callback.

### SessionDataStore

DataStore-backed session persistence stores:

- token;
- role;
- user id;
- login.

`AppViewModel` reads this flow on initialization and restores token/role/login into UI state and `SessionRuntime`.

## 10. MainActivity and navigation

`MainActivity` currently owns:

- `AppViewModel` creation via `by viewModels()`;
- Fused Location Provider setup;
- location permission request flow;
- `SessionRuntime.onUnauthorized` binding;
- Compose theme and navigation host;
- periodic active route refresh while token exists;
- top-level route registration.

Registered routes:

- `auth`
- `main_map`
- `driver_setup`
- `passenger_setup`
- `admin_panel`

## 11. Screens currently implemented

### AuthScreen

- Login/password fields.
- Calls `AppViewModel.login`.
- Navigates to `main_map` on success.

### MainMapScreen

- osmdroid map.
- OpenStreetMap HTTPS tile source.
- tile server preflight check.
- active bus markers.
- current user marker.
- optional route polyline.
- server status badge with ping/loss.
- role-aware navigation menu.

### AdminPanelScreen

Includes:

- companies block;
- create company flow;
- company selection;
- user creation block;
- users list;
- Wialon/GLONASS integration block;
- Wialon account save;
- Wialon account select;
- Wialon test action;
- Wialon sync units action;
- Wialon units list.

### DriverSetupScreen

- Select start/end stop through `SearchableStopField`.
- Builds `RouteRequest`.
- Calls `AppViewModel.startRoute`.

### PassengerSetupScreen

- Displays active buses from state.
- Allows selecting a bus and returning to map.

## 12. Map and search flow

### Map

- Uses osmdroid `MapView`.
- Uses HTTPS OpenStreetMap tile source.
- Performs a HEAD request to tile server before rendering error state.

### Stop search

`SearchableStopField` uses:

- Nominatim external search API;
- `User-Agent` and `Accept: application/json` headers;
- debounce delay;
- local fallback stops when network search fails.

This is currently external/local logic, not backend-backed.

## 13. Driver flow

Current driver flow:

1. Driver opens route setup.
2. Chooses start/end stops.
3. App stores selected `GeoPoint`s in `AppUiState`.
4. App builds `RouteRequest`.
5. App calls `POST /route/start`.
6. During periodic refresh, if user role is `driver`, app sends current location before loading active routes.

Known sync risk: `RouteRequest.startTime` currently receives the literal UI text `"Теперь"`. FastAPI backend may require ISO datetime or another strict format.

## 14. Admin/Wialon flow

Current Wialon admin flow:

1. Admin panel loads Wialon accounts and units.
2. User enters name/baseUrl/token.
3. App sends `WialonAccountCreateRequest`.
4. Token field is cleared after successful save so it is not displayed again.
5. User selects a Wialon account.
6. User can test account connectivity.
7. User can sync units.
8. Units list refreshes after successful sync.

## 15. Tests currently present

### UnauthorizedInterceptorTest

Covers that `UnauthorizedInterceptor` invokes the runtime unauthorized callback on `401`.

### LoginUseCaseTest

Covers:

- login response is returned from repository;
- null login response remains null.

### SyncActiveRoutesUseCaseTest

Covers:

- driver flow updates location before route loading;
- driver receives company-filtered routes;
- admin receives unfiltered routes.

Test fake repositories implement the expanded `BusRepository`, including company and Wialon methods.

## 16. Documentation currently present

### README.md

Contains:

- Android Studio run instructions;
- base URL note;
- auth endpoint format;
- auth header behavior;
- role overview;
- tech stack;
- test command.

Note: README still contains outdated wording about company CRUD being disabled. The current code has company CRUD UI and repository calls, so README should be updated in a later documentation cleanup.

### docs/backend-sync-report.md

Contains backend synchronization analysis:

- current Android structure;
- endpoint coverage matrix;
- stale/local logic;
- files likely to change;
- recommended new files;
- next safe steps.

## 17. Current technical debt / risks

1. `MainActivity.kt` is too large and owns many screens and UI components.
2. `Network.kt` contains many DTOs plus Retrofit contract in one file.
3. `CompanyApi` duplicates `BusApi` company endpoints.
4. Backend OpenAPI was not accessible from this environment (`403 Forbidden`), so exact schema matching is not yet verified.
5. `RouteRequest.startTime` may not match backend expected datetime format.
6. Stop search is external/local, not backend-backed.
7. Admin panel keeps substantial local Compose state; eventually it should move into dedicated state holders or ViewModel state.
8. README needs cleanup to match current company/Wialon implementation.

## 18. Recommended next steps for Codex

### Safe backend sync sequence

1. Obtain backend OpenAPI JSON or backend source access.
2. Compare FastAPI schemas against Android DTOs in `Network.kt`.
3. Fix DTO nullability and serialized field names first.
4. Add tests for JSON serialization/deserialization of critical DTOs.
5. Remove `CompanyApi` shim only after clean Gradle/Android Studio sync proves `BusApi` company methods resolve.
6. Normalize API errors into a small result/error model.
7. Split DTOs out of `Network.kt`.
8. Split screens out of `MainActivity.kt` only after network contract stabilizes.

### Files to inspect first in future tasks

- `app/src/main/java/com/bus/app/data/Network.kt`
- `app/src/main/java/com/bus/app/data/repository/BusRepository.kt`
- `app/src/main/java/com/bus/app/data/repository/ApiBusRepository.kt`
- `app/src/main/java/com/bus/app/AppViewModel.kt`
- `app/src/main/java/com/bus/app/MainActivity.kt`
- `app/src/test/java/com/bus/app/domain/usecase/LoginUseCaseTest.kt`
- `app/src/test/java/com/bus/app/domain/usecase/SyncActiveRoutesUseCaseTest.kt`
