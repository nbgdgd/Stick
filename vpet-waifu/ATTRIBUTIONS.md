# Attributions / Лицензии внешних ассетов

Все внешние ассеты проекта используют лицензии, разрешающие коммерческое
распространение (MIT, Apache-2.0, OFL 1.1, CC0). Ассеты с лицензией CC-BY не
используются, поэтому обязательный экран атрибуции не требуется — этот файл
является единым реестром лицензий проекта.

## Иконки

### Tabler Icons — MIT
- Источник: https://github.com/tabler/tabler-icons
- Лицензия: MIT License, Copyright (c) 2020-2026 Paweł Kuna
- Использование: единый набор интерфейсных иконок (целевой набор редизайна)
- Поставка для Jetpack Compose: библиотека compose-icons,
  артефакт `br.com.devsrsouza.compose.icons:tabler-icons:1.1.1` (Maven Central),
  лицензия MIT — https://github.com/DevSrSouza/compose-icons

### Material Icons (androidx material-icons-extended) — Apache-2.0
- Источник: https://github.com/google/material-design-icons
- Лицензия: Apache License 2.0
- Использование: текущий набор иконок до завершения миграции на Tabler Icons

## Шрифты

### Golos Text — SIL Open Font License 1.1
- Источник: https://fonts.google.com/specimen/Golos+Text
  (файлы: https://github.com/google/fonts/tree/main/ofl/golostext)
- Авторы: Alexandra Korolkova, Vitaly Kuzmin (Paratype)
- Лицензия: OFL 1.1; полный набор кириллицы (subsets: cyrillic, cyrillic-ext)
- Использование: единственная гарнитура интерфейса (3 размера, 2 веса)

### Manrope — SIL Open Font License 1.1 (резервная гарнитура)
- Источник: https://fonts.google.com/specimen/Manrope
- Автор: Mikhail Sharanda
- Лицензия: OFL 1.1; кириллица поддерживается (subsets: cyrillic, cyrillic-ext)

## Звуки

### Kenney — Interface Sounds, UI Audio — CC0
- Источники: https://kenney.nl/assets/interface-sounds (100 звуков),
  https://kenney.nl/assets/ui-audio (50 звуков)
- Лицензия: Creative Commons Zero (CC0) — атрибуция не требуется,
  указана добровольно
- Использование: пул для замены и дополнения интерфейсных звуков

### freesound.org — только CC0
- Отбор строго через фильтр лицензии:
  https://freesound.org/search/?license=Creative+Commons+0
- Лицензия каждого звука проверяется на его странице перед добавлением;
  при добавлении звук вносится в этот файл отдельной строкой

## Оригинальные ассеты проекта (внешним лицензиям не подлежат)

- Арт персонажа и комнаты: оригинальный векторный риг, отрисован кодом
  (Jetpack Compose Canvas), все позы и анимации созданы для проекта
- Звуковые эффекты `res/raw/sfx_*.ogg` и музыка `res/raw/music_*.ogg`:
  синтезированы специально для проекта
