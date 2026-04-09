# Bus App (Android)

## Запуск проекта
1. Откройте проект в Android Studio.
2. Выполните **Sync Project with Gradle Files**.
3. Запустите variant (например `devDebug`) на эмуляторе/устройстве.

## Где менять BASE_URL
- Текущее значение задается в `app/src/main/java/com/bus/app/config/AppConfig.kt`.
- Сейчас используется прямой IP сервера: `http://37.200.79.56:8000/`.
- Swagger доступен по адресу: `<BASE_URL>/docs`.

## Авторизация
- Endpoint: `POST /auth/login`
- Content-Type: `application/x-www-form-urlencoded`
- Поля: `username`, `password`
- После успешного входа в DataStore сохраняются:
  - `access_token`
  - `role`
  - `user_id` (если доступен)
  - `login`

### Заголовок Authorization
- Во все запросы JWT подставляется через `AuthInterceptor`:
  - `Authorization: Bearer <token>`
- При `401` выполняется автоматический logout (очистка сессии).

## Роли и экраны
- `admin`: админ-панель, карта, настройка рейса, выбор автобуса.
- `driver`: карта, настройка рейса.
- `passenger`: карта, выбор автобуса.
- `customer`: базовый fallback-экран (если роль расширится API, UI не должен падать).

## Важно про API-синхронизацию
- В Android отключен CRUD компаний, так как в текущем backend API нет стабильного `/admin/companies`.
- Для admin UI оставлены только сценарии, соответствующие реально используемым endpoint’ам (`users/roles/logs/requests`).

## Технологии
- Kotlin
- MVVM + ViewModel + StateFlow
- Jetpack Compose + Navigation Compose
- Retrofit + OkHttp
- Coroutines + Flow
- DataStore (session storage)

## Запуск тестов
```bash
./gradlew test
```

## Форматы API
- Login: `application/x-www-form-urlencoded`
- Остальные `POST/PATCH`: JSON

## Timezone / даты
- Все даты в UI должны отображаться в локальном часовом поясе устройства.
