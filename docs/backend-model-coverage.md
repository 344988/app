# Backend model coverage for Android sync

Direct access to the deployed backend OpenAPI is currently blocked from this environment (`/openapi.json` and `/docs` return `403 Forbidden`). The Android model package below is therefore prepared as a safe integration layer based on the entity names requested for the FastAPI project and existing Android API usage. Exact field-level verification still requires backend OpenAPI/source access.

## Covered backend model families

| Backend model | Android DTO | Android domain model | Mapper |
| --- | --- | --- | --- |
| user | `UserDto` | `User` | `UserDto.toDomain()` |
| trip | `TripDto` | `Trip` | `TripDto.toDomain()` |
| vehicle | `VehicleDto` | `Vehicle` | `VehicleDto.toDomain()` |
| route template | `RouteTemplateDto` | `RouteTemplate` | `RouteTemplateDto.toDomain()` |
| stop point | `StopPointDto` | `StopPoint` | `StopPointDto.toDomain()` |
| driver shift | `DriverShiftDto` | `DriverShift` | `DriverShiftDto.toDomain()` |
| inspection | `InspectionDto` | `Inspection` | `InspectionDto.toDomain()` |
| defect report | `DefectReportDto` | `DefectReport` | `DefectReportDto.toDomain()` |
| tracking event | `TrackingEventDto` | `TrackingEvent` | `TrackingEventDto.toDomain()` |
| live map vehicle | `LiveMapVehicleDto` | `LiveMapVehicle` | `LiveMapVehicleDto.toDomain()` |

## Package created

`app/src/main/java/com/bus/app/data/model/`

- `BackendModels.kt` contains DTOs, domain models, and a small live-map UI model.
- `BackendMappers.kt` contains DTO-to-domain mappers and `LiveMapVehicle.toUiModel()`.

## Existing screens not fully connected to these new domain models yet

- `MainMapScreen` still uses the older `ActiveBus` model from `Network.kt`.
- `DriverSetupScreen` still creates `RouteRequest` directly and sends the literal `start_time = "Теперь"`.
- `PassengerSetupScreen` still reads active buses directly from `AppUiState.activeBuses`.
- `AdminPanelScreen` still uses existing `UserDto`, `Company`, and Wialon DTOs from `Network.kt`.
- There are no dedicated screens yet for route templates, driver shifts, inspections, defect reports, or tracking event history.

## Next sync step

After backend OpenAPI/source is available, compare field names and nullability in `BackendModels.kt` with actual FastAPI response schemas, then update Retrofit endpoint return types gradually screen by screen.
