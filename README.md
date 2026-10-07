# VTTG Module Registry

Реестр модулей виртуального стола VTTG. Авторы подают заявку на свой модуль
(открытый репозиторий + ссылка на `module.json`), администратор одобряет или
отклоняет её с комментарием, VTTG забирает каталог одобренных модулей.

Spring Boot 4 / Java 21 / PostgreSQL / Liquibase. Авторизация — JWT из
`auth-service` (тот же секрет, что у остальных сервисов), роли из claim `roles`.

## Запуск

```bash
docker compose up -d          # Postgres на localhost:5433
AUTH_SERVICE_JWT_SECRET=... ./mvnw spring-boot:run
```

Swagger: `http://localhost:8080/swagger-ui.html`.

| Переменная | По умолчанию | |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5433/vttg_modules`, `vttg_modules` | |
| `AUTH_SERVICE_JWT_SECRET` | — | обязательна, ≥ 32 байт |
| `MANIFEST_ALLOWED_HOSTS` | github.com, raw.githubusercontent.com, objects.githubusercontent.com, gitlab.com, codeberg.org, gitflic.ru, gitverse.ru | хосты для ссылок на репозиторий, манифест и архив |
| `MANIFEST_MAX_SIZE` / `MANIFEST_TIMEOUT` | `256KB` / `10s` | |
| `CORS_ALLOWED_ORIGINS` | https://new.ttg.club, https://ttg.club, https://dev.ttg.club | сайт с формой заявки и модерацией |

> **Windows и не-ASCII путь к `%TEMP%`.** `HttpClient` JDK создаёт Unix-сокет во
> временной папке и падает с `Unable to establish loopback connection`, если в пути
> есть кириллица. Локально запускайте с `-Djdk.net.unixdomain.tmpdir=D:/tmp`
> (для тестов: `./mvnw test "-DargLine=-Djdk.net.unixdomain.tmpdir=D:/tmp"`).
> В Docker-образе проблемы нет.

## Требования к модулю

`module.json` по контракту VTTG (`vttg/docs/MODULES.md`) плюс поле `download`:

```json
{
  "id": "map-import",
  "name": "Импорт карт",
  "version": "0.1.0",
  "download": "https://github.com/<owner>/<repo>/releases/download/v0.1.0/map-import.zip",
  "compatibleSystems": ["dnd5e-2024"]
}
```

- `id` — `^[a-z0-9][a-z0-9_-]{0,63}$` (он же имя папки модуля в мире);
- `version` — семантическая версия;
- `download` — https-ссылка на архив модуля на разрешённом хосте;
- ссылку на страницу файла GitHub (`…/blob/<ref>/module.json`) сервис сам
  заменяет на `raw.githubusercontent.com`.

Сервис скачивает манифест при подаче, правке и по кнопке «Перечитать манифест».
Переадресации проверяются по тому же списку хостов, адреса внутренних сетей
отклоняются.

## Жизненный цикл заявки

```
PENDING ──approve──▶ APPROVED ──reject (снятие из каталога)──▶ REJECTED
PENDING ──reject───▶ REJECTED ──правка автором──▶ PENDING
любой, кроме WITHDRAWN ──withdraw──▶ WITHDRAWN
```

- На один `id` модуля — одна живая заявка (`PENDING` или `APPROVED`); чужой
  `id` занять нельзя, отклонённые и отозванные его не держат.
- Отклонение требует комментария.
- Одобренную заявку не правят: новая версия модуля подхватывается
  перечитыванием манифеста без повторной модерации (менять `id` нельзя);
  для смены описания или систем — отозвать и подать заново.

## API

| Метод | Путь | Доступ | |
|---|---|---|---|
| GET | `/api/v1/modules?system=<id>` | все | каталог одобренных; с `system` — подходящие миру, включая универсальные |
| GET | `/api/v1/modules/{moduleId}` | все | один модуль каталога |
| GET | `/api/v1/systems` | все | справочник игровых систем для формы |
| POST | `/api/v1/submissions` | вошедший | подать заявку |
| GET | `/api/v1/submissions/my` | вошедший | мои заявки |
| GET | `/api/v1/submissions/{id}` | автор / модератор | заявка |
| PUT | `/api/v1/submissions/{id}` | автор | исправить и отправить заново |
| POST | `/api/v1/submissions/{id}/refresh-manifest` | автор | перечитать `module.json` |
| DELETE | `/api/v1/submissions/{id}` | автор | отозвать |
| GET | `/api/v1/moderation/submissions?status=PENDING` | ADMIN, MODERATOR | очередь (страницы) |
| POST | `/api/v1/moderation/submissions/{id}/approve` | ADMIN, MODERATOR | одобрить, `{comment?}` |
| POST | `/api/v1/moderation/submissions/{id}/reject` | ADMIN, MODERATOR | отклонить / снять, `{comment}` |
| POST/PATCH/DELETE | `/api/v1/admin/systems[/{id}]` | ADMIN | справочник систем |

Ошибки — `application/problem+json`: 422 — манифест или ссылка не прошли
проверку, 502 — манифест не скачался, 409 — `id` занят или действие
недоступно в текущем статусе.

### Ответ каталога (для VTTG)

```json
[{
  "id": "map-import",
  "name": "Импорт карт",
  "version": "0.1.0",
  "description": "Краткое описание из заявки",
  "author": "TTG Club",
  "icon": "tabler:map-plus",
  "systemIds": ["dnd5e-2024"],
  "repositoryUrl": "https://github.com/…",
  "manifestUrl": "https://raw.githubusercontent.com/…/module.json",
  "downloadUrl": "https://github.com/…/map-import.zip",
  "approvedAt": "2026-10-07T10:00:00Z",
  "updatedAt": "2026-10-07T10:00:00Z"
}]
```

`version` и `downloadUrl` — снимок на момент последней синхронизации; при
установке VTTG стоит читать актуальный манифест по `manifestUrl` — так же, как
он уже делает для систем (`remoteSystemInstaller.ts`).
