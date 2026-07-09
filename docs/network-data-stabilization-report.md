# Network/data stabilization report

## Что стабилизировано

- `ApiBusRepository` теперь использует единый HTTP status handler через `Response<*>.isSuccessfulOrHandled()` для всех repository-вызовов, кроме `login`, где нужна отдельная семантика неверных credentials.
- Единообразно обработаны основные статусы backend:
  - `401` запускает `SessionRuntime.onUnauthorized` и сохраняет существующий flow logout.
  - `403`, `404`, `429`, `5xx` возвращают `null`/`false` вызывающему ViewModel без падения приложения.
- Сетевые исключения (`SocketTimeoutException`, `UnknownHostException`, `IOException`) распознаются как network failures.
- Повторные запросы больше не ретраятся по умолчанию: `retryWithBackoff` теперь имеет `maxAttempts = 1`, что убирает небезопасные retry для POST/PATCH/мутирующих операций. Точечные безопасные retry можно включать позже явным `maxAttempts > 1` для read-only GET.
- Polling уже ограничен на уровне ViewModel для карты (`MAP_REFRESH_THROTTLE_MS`) и задержками экранов, чтобы не спамить backend.

## Endpoint'ы, уже используемые Android

### Auth/session
- `POST /auth/login`
- `GET /auth/me`

### Health/routes/location
- `GET /health`
- `GET /routes/active`
- `POST /location/update`
- `POST /route/start`

### Admin/users/companies
- `GET /admin/companies`
- `POST /admin/companies`
- `GET /admin/users`
- `POST /admin/users`

### Wialon/ГЛОНАСС
- `GET /admin/wialon/accounts`
- `POST /admin/wialon/accounts`
- `POST /admin/wialon/accounts/{id}/test`
- `POST /admin/wialon/accounts/{id}/sync-units`
- `GET /admin/wialon/units`

### Driver
- `GET /driver/shifts/current`
- `POST /driver/shifts/start`
- `POST /driver/shifts/accept-vehicle`
- `POST /driver/inspections`
- `GET /driver/inspections`
- `GET /driver/trips`
- `POST /driver/trips/{trip_id}/start`
- `POST /driver/trips/{trip_id}/complete`
- `POST /driver/shifts/finish`
- `POST /driver/defects`
- `GET /driver/defects`

### Mechanic
- `GET /admin/mechanic/defects`
- `POST /admin/mechanic/defects/{defect_id}/accept`
- `POST /admin/mechanic/defects/{defect_id}/assign-repair`
- `POST /admin/mechanic/defects/{defect_id}/close`
- `GET /admin/mechanic/vehicles/{vehicle_id}/history`

### Dispatcher
- `GET /admin/trips`
- `POST /admin/trips`
- `PATCH /admin/trips/{trip_id}`
- `POST /admin/trips/{trip_id}/assign-driver`
- `POST /admin/trips/{trip_id}/assign-vehicle`
- `POST /admin/trips/{trip_id}/start`
- `POST /admin/trips/{trip_id}/complete`
- `POST /admin/trips/{trip_id}/cancel`
- `GET /admin/requests`
- `POST /admin/requests/{request_id}/approve`
- `POST /admin/requests/{request_id}/reject`
- `GET /admin/tracking/events`
- `GET /admin/notifications/dispatcher`

### Map/stops/routes
- `GET /admin/map/config`
- `GET /admin/map/tiles/{z}/{x}/{y}`
- `GET /map/vehicles/live`
- `GET /admin/map/vehicles`
- `GET /admin/map/vehicle/{vehicle_id}`
- `GET /admin/map/stops`
- `GET /admin/map/icons/{icon_kind}`
- `GET /admin/stops`
- `GET /admin/route-templates`
- `GET /admin/route-templates/{route_template_id}/stops`
- `GET /admin/trips/{trip_id}/route-template`

## Hardcode/local fallback, который ещё остался

- `LOCAL_STOPS_FALLBACK` в `MainActivity.kt` используется только как offline fallback для Nominatim поиска остановок. Его можно заменить серверным поиском, когда появится endpoint поиска остановок/адресов.
- Nominatim всё ещё используется для ручного поиска точки маршрута. Backend endpoint для geocoding/search пока не подключён.
- Android UI пока хранит часть form state локально в Compose screen state; это не mock backend-данных, но при дальнейшем росте стоит вынести сложные формы в отдельные UI state модели.

## Что ещё желательно добавить на backend позже

- `GET /stops/search?query=` или `GET /admin/stops/search?query=` для отказа от Nominatim и локального fallback списка.
- Единый envelope ошибок (`code`, `message`, `details`, `retry_after`) для точного отображения `403/404/429` в UI.
- WebSocket/SSE endpoint для live map vehicles, чтобы заменить polling карты.
- Endpoint для batch-получения справочников водителей/автобусов диспетчера, чтобы назначения делались выбором из списка, а не вводом ID.
- Endpoint для mark-as-read диспетчерских уведомлений.

## Остаточные риски

- Без единого backend error envelope Android сейчас может отличить статус только на repository уровне, но не всегда показывает конкретный текст причины пользователю.
- Tile endpoint `/admin/map/tiles/{z}/{x}/{y}` может требовать auth headers, а osmdroid tile loader работает через URL tile source; при строгой auth-схеме может понадобиться кастомный tile provider с Authorization header.
- Unit/build verification в текущем контейнере ограничена отсутствием Android SDK.
