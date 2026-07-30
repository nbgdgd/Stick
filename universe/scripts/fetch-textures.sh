#!/usr/bin/env bash
# Скачивает текстуры планет с solarsystemscope.com (лицензия CC BY 4.0).
# У сайта включена анти-бот защита, поэтому: браузерный User-Agent,
# Referer, пауза между запросами и проверка, что пришёл настоящий JPEG/PNG.
set -u

OUT="$(cd "$(dirname "$0")/.." && pwd)/public/tex"
mkdir -p "$OUT"

UA='Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
REF='https://www.solarsystemscope.com/textures/'

FILES=(
  2k_earth_daymap.jpg
  2k_earth_nightmap.jpg
  2k_earth_clouds.jpg
  2k_moon.jpg
  2k_sun.jpg
  2k_mercury.jpg
  2k_venus_surface.jpg
  2k_venus_atmosphere.jpg
  2k_mars.jpg
  2k_jupiter.jpg
  2k_saturn.jpg
  2k_saturn_ring_alpha.png
  2k_uranus.jpg
  2k_neptune.jpg
  2k_stars_milky_way.jpg
  2k_ceres_fictional.jpg
  2k_eris_fictional.jpg
  2k_haumea_fictional.jpg
  2k_makemake_fictional.jpg
)

ok=0
fail=0
for f in "${FILES[@]}"; do
  dest="$OUT/$f"
  # текстура кольца Сатурна — узкая полоска, файл законно маленький
  min=20000
  case "$f" in *ring*) min=5000 ;; esac
  # уже скачан и достаточно большой — пропускаем
  if [ -f "$dest" ] && [ "$(stat -c%s "$dest")" -gt "$min" ]; then
    echo "skip  $f ($(stat -c%s "$dest") B)"
    ok=$((ok + 1))
    continue
  fi
  got=0
  for attempt in 1 2 3 4 5; do
    curl -sSL --compressed \
      -H "User-Agent: $UA" \
      -H "Referer: $REF" \
      -H 'Accept: image/avif,image/webp,image/png,image/jpeg,*/*' \
      -o "$dest" \
      "https://www.solarsystemscope.com/textures/download/$f"
    size=$(stat -c%s "$dest" 2>/dev/null || echo 0)
    type=$(file -b --mime-type "$dest" 2>/dev/null || echo unknown)
    if [ "$size" -gt "$min" ] && { [ "$type" = "image/jpeg" ] || [ "$type" = "image/png" ]; }; then
      echo "ok    $f ($size B, $type)"
      got=1
      break
    fi
    echo "retry $f (attempt $attempt: $size B, $type)"
    sleep $((attempt * 3))
  done
  if [ "$got" = 1 ]; then ok=$((ok + 1)); else fail=$((fail + 1)); rm -f "$dest"; fi
  sleep 2
done

echo "---"
echo "готово: $ok успешно, $fail не удалось"
[ "$fail" = 0 ]
