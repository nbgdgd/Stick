# AI Universe Explorer

Интерактивная 3D-карта Вселенной: от поверхности Земли до крупномасштабной
структуры наблюдаемой Вселенной, восемь уровней масштаба, на реальных
астрономических данных.

## Запуск

```bash
npm install
npm run tex        # скачать текстуры планет (CC BY 4.0), ~12 МБ
npm run dev        # разработка
npm run build      # сборка в dist/
npm run shots      # скриншоты всех уровней в headless-браузере
```

## Сборка APK

```bash
export ANDROID_HOME=/opt/android-sdk
npm run build
npx cap sync android
cd android && ./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

## Структура

| Путь | Что там |
| --- | --- |
| `src/lib/astro.ts` | Кеплер, юлианские даты, системы координат, ΛCDM-расстояния |
| `src/lib/sun.ts` | Положение Солнца, терминатор, орбита МКС |
| `src/lib/rng.ts` | Детерминированный ГПСЧ для процедурной генерации |
| `src/data/` | Каталоги: планеты, спутники, звёзды, галактики, уровни масштаба |
| `src/scenes/` | По одной сцене на уровень масштаба + камера |
| `src/shaders/` | Шейдеры Земли, атмосферы, Солнца, короны |
| `src/components/` | Интерфейс и переиспользуемые 3D-примитивы |

Источники данных и лицензии ассетов — в `../ASSETS.md`.
Журнал работы и известные проблемы — в `../PROGRESS.md`.
