# VTTG Module Registry

Реестр модулей виртуального стола VTTG. Авторы подают заявку на свой модуль
(ссылка на `module.json` в открытом репозитории), администратор одобряет или
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
| `MANIFEST_ALLOWED_HOSTS` | github.com, raw.githubusercontent.com, gitlab.com | где могут лежать модули: хосты открытых репозиториев через запятую (свой GitLab добавляется сюда же) |
| `MANIFEST_MAX_SIZE` / `MANIFEST_TIMEOUT` | `256KB` / `10s` | |
| `CORS_ALLOWED_ORIGINS` | https://new.ttg.club, https://ttg.club, https://dev.ttg.club | сайт с формой заявки и модерацией |

> **Windows и не-ASCII путь к `%TEMP%`.** `HttpClient` JDK создаёт Unix-сокет во
> временной папке и падает с `Unable to establish loopback connection`, если в пути
> есть кириллица. Локально запускайте с `-Djdk.net.unixdomain.tmpdir=D:/tmp`
> (для тестов: `./mvnw test "-DargLine=-Djdk.net.unixdomain.tmpdir=D:/tmp"`).
> В Docker-образе проблемы нет.

## Требования к модулю

`module.json` по контракту VTTG (`vttg/docs/MODULES.md`) плюс обязательные для
каталога поля `description` и `download`:

```json
{
  "id": "map-import",
  "name": "Импорт карт",
  "version": "0.1.0",
  "description": "Импорт карт из Dungeondraft",
  "download": "https://github.com/<owner>/<repo>/releases/download/v0.1.0/map-import.zip",
  "compatibleSystems": ["dnd5e-2024"]
}
```

- `id` — `^[a-z0-9][a-z0-9_-]{0,63}$` (он же имя папки модуля в мире);
- `version` — семантическая версия;
- `description` — описание модуля для каталога, до 1000 символов. Автор его
  в заявке отдельно не пишет;
- `download` — https-ссылка на архив модуля в том же репозитории
  (например, на ассет релиза GitHub);
- `compatibleSystems` — игровые системы модуля (до 20 id по 64 символа).
  Автор их в заявке отдельно не указывает: каталог отбирает модули по этому
  полю, как и сам VTTG. Нет поля, `[]` или `["*"]` — модуль универсальный.
  Системы, которой нет в справочнике, это не запрещает: справочник даёт
  только названия для сайта;
- ссылку на страницу файла GitHub (`…/blob/<ref>/module.json`) сервис сам
  заменяет на `raw.githubusercontent.com`.

Репозиторий автор отдельно не указывает: сервис берёт его из ссылки на
`module.json`. У GitHub это первые два сегмента пути (`владелец/репозиторий`,
и для `github.com`, и для `raw.githubusercontent.com`), у GitLab — путь до
служебного `/-/` (так понимаются и вложенные группы, и свой GitLab).

Сервис скачивает манифест при подаче, правке и по кнопке «Перечитать манифест».
Переадресации проверяются по тому же списку хостов, адреса внутренних сетей
отклоняются.

## Жизненный цикл заявки

```
PENDING ──approve──▶ APPROVED ──reject (снятие из каталога)──▶ REJECTED
PENDING ──reject───▶ REJECTED ──правка автором──▶ PENDING
APPROVED ──одобрена заявка-замена того же автора──▶ SUPERSEDED
любой, кроме WITHDRAWN и SUPERSEDED ──withdraw──▶ WITHDRAWN
```

- Модули — только из открытых репозиториев на хостах из `MANIFEST_ALLOWED_HOSTS`
  (по умолчанию GitHub и GitLab). Архив из `download` должен лежать в том же
  репозитории, что и `module.json`; закрытый репозиторий отсекается сам —
  манифест читается без авторизации.
- **Первое одобрение фиксирует ссылку на `module.json`**, а с ней и
  репозиторий — даже если модуль потом сняли из каталога. Чтобы сменить её,
  автор подаёт новую
  заявку на тот же модуль; старая остаётся в каталоге, пока новую не одобрят,
  а после одобрения получает статус `SUPERSEDED`.
- Чужой `id` модуля занять нельзя. У своего модуля — не больше одной
  одобренной заявки и одной на рассмотрении.
- Отклонение требует комментария.
- Новая версия модуля подхватывается перечитыванием манифеста без повторной
  модерации; `id` модуля менять нельзя, архив по-прежнему должен лежать в
  том же репозитории.

## API

| Метод | Путь | Доступ | |
|---|---|---|---|
| GET | `/api/v1/modules?system=<id>` | все | каталог одобренных; с `system` — подходящие миру, включая универсальные |
| GET | `/api/v1/modules/{moduleId}` | все | один модуль каталога |
| GET | `/api/v1/systems` | все | справочник игровых систем: названия для сайта |
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
  "description": "Описание из module.json",
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

## Деплой

`.github/workflows/deploy.yml` — как у остальных сервисов: пуш в `main` или `dev`
собирает образ через `TTG-Club/shared-workflows/standard@v1` и выкатывает его в
Dokploy (`main` → прод, `dev` → стенд). В репозитории нужны секреты
`DOKPLOY_APP_ID_PROD`, `DOKPLOY_APP_ID_DEV`, `DOKPLOY_URL`, `DOKPLOY_API_KEY`,
`REGISTRY_URL`, `REGISTRY_USERNAME`, `REGISTRY_PASSWORD`, `COSIGN_PRIVATE_KEY`.
