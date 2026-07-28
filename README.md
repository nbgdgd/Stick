# Триалы — трекер акций и пробных подписок

Android-приложение, которое находит на устройстве приложения из своего каталога и
показывает для них **пробные подписки (free trial)** и **скидки**. Пробные подписки
всегда приоритетнее обычных акций.

Kotlin · Jetpack Compose · Material 3 · Room · WorkManager · OkHttp + kotlinx.serialization

<p align="center">
  <img src="docs/home.png" width="30%" />
  <img src="docs/trials.png" width="30%" />
  <img src="docs/settings.png" width="30%" />
</p>

## APK

Готовый к установке APK лежит в [`artifacts/trial-tracker-1.0.0.apk`](artifacts/trial-tracker-1.0.0.apk)
(minSdk 26, targetSdk 35). Подписан debug-ключом — для установки нужно разрешить
установку из неизвестных источников. Перед публикацией в Play замените
`signingConfig` на настоящий keystore и включите R8 (`isMinifyEnabled = true`).

## Что внутри

### 1. Сканирование установленных приложений
`data/PackageScanner.kt` — по одному пакету через `getApplicationInfo()` в try/catch.

Разрешение **`QUERY_ALL_PACKAGES` не запрашивается**: Google Play строго проверяет
его обоснование и может отклонить приложение. Вместо этого в манифесте объявлен
блок `<queries>` с конкретными пакетами каталога — сопоставление всё равно идёт
только по ним, так что полный список приложений устройства не нужен. Системные
приложения по умолчанию скрыты, переключатель — в настройках.

### 2. Каталог акций
Единого API, который знает, у каких приложений сейчас есть триал, **не существует** —
поэтому каталог ведётся вручную:

* `catalog/deals.json` — источник, который отдаётся по HTTP и обновляется без релиза
  приложения (GitHub Pages / raw.githubusercontent / Firebase Hosting);
* `app/src/main/assets/deals_catalog.json` — та же копия внутри APK, чтобы первый
  запуск работал офлайн;
* адрес источника — в `BuildConfig.CATALOG_URL`, виден в настройках.

Поля: `package_name`, `app_name`, `title`, `type` (`trial`/`discount`), `duration`,
`description`, `price_after`, `discount_percent`, `deep_link`, `last_verified_date`,
`brand_color`, `glyph`, `popularity`.

**`last_verified_date` показывается в UI везде**, где видно предложение — условия
акций отличаются по регионам и быстро устаревают.

Стартовый набор — 20 популярных сервисов (Spotify, YouTube Premium, Netflix,
Duolingo, Canva, CapCut, Notion, NordVPN, Adobe Lightroom, Picsart, Headspace,
Яндекс Музыка, Кинопоиск, Coursera, Strava, Telegram Premium, Google One,
Microsoft 365, Tinder, Grammarly). Условия внесены вручную и требуют перепроверки
перед публикацией.

Автоматический парсинг сторонних страниц **намеренно не реализован**: это
нестабильно и может нарушать ToS сервисов. Место для него — `CatalogRemoteSource`,
за той же suspend-функцией.

### 3. Матчинг и приоритизация
`DealsRepository` — сначала точное совпадение по `package_name`, затем нечёткое по
названию (нормализованному: без регистра, пробелов и пунктуации).

Порядок: триалы выше скидок; внутри триалов — установленные приложения, затем
свежесть проверки, затем популярность (`ui/Categories.kt`).

### 4. Разделы
* **Главная** — приветствие, карточка анализа, 6 категорий, карусель рекомендаций,
  список пробных подписок.
* **Категории** — Free Trials · Лучшие предложения · Скидки · Персональные
  предложения · Рекомендуемые приложения · Избранное, плюс **Все приложения** с
  пометкой найдено / не установлено.
* **Уведомления** — лента изменений каталога.
* **Настройки** — периодичность проверки, системные приложения, приватность,
  источник каталога.
* **Онбординг** — что сканируется и что не уходит с устройства.

### 5. Уведомления
`work/CatalogSyncWorker.kt` — периодическая проверка каталога через WorkManager.
Локальное уведомление приходит только про предложения для **установленных**
приложений, и только один раз на предложение (таблица `seen_deals`).

## Приватность
Список установленных приложений обрабатывается только на устройстве и никуда не
отправляется. Запрос каталога — обычный GET публичного JSON, без данных о
пользователе; сопоставление происходит уже локально. Это объяснено в онбординге и
в настройках.

## Архитектура

```
ui/            Compose-экраны, тема, ViewModel
 ├ screens/    Home · Categories · DealList · Apps · Search · Notifications · Settings · Onboarding
 └ components/ SurfaceCard · DealCardCompact · DealRow · Pill · IconTile
data/
 ├ model/      Deal · DealCatalog · InstalledApp · DealUi
 ├ local/      Room: deals · installed_apps · favorites · seen_deals
 ├ remote/     CatalogRemoteSource (OkHttp)
 ├ PackageScanner.kt
 ├ DealsRepository.kt   матчинг + кэш
 └ SettingsRepository.kt (DataStore)
work/          CatalogSyncWorker
```

Зависимости собираются вручную в `ServiceLocator` — граф маленький, DI-фреймворк
здесь только добавил бы время сборки.

## Дизайн
Тёмная тема, один акцент (`#8B5CF6`). Фон `#08080B`, карточки `#131317` на тон
светлее, вместо теней — граница 1px `#232329`, скругления 15–18dp. Цветная на
карточке только иконка приложения: у установленного — настоящая иконка из
`PackageManager`, иначе плитка в фирменном цвете из каталога.

## Сборка

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleRelease          # APK -> app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest        # рендерит экраны в app/build/screenshots/
```

Скриншоты в `docs/` сняты не эмулятором, а Robolectric + Roborazzi на JVM
(`app/src/test/.../HomeScreenRenderTest.kt`) — layout можно проверять без устройства.
