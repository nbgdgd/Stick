/**
 * Шейдеры Земли: смешивание дневной и ночной текстуры по фактическому
 * направлению на Солнце, рассеяние Рэлея на терминаторе, отдельная
 * оболочка атмосферы с приближением однократного рассеяния.
 */

export const earthVertex = /* glsl */ `
  varying vec2 vUv;
  varying vec3 vNormal;
  varying vec3 vViewDir;

  void main() {
    vUv = uv;
    vNormal = normalize(mat3(modelMatrix) * normal);
    vec4 worldPos = modelMatrix * vec4(position, 1.0);
    vViewDir = normalize(cameraPosition - worldPos.xyz);
    gl_Position = projectionMatrix * viewMatrix * worldPos;
  }
`

export const earthFragment = /* glsl */ `
  uniform sampler2D dayMap;
  uniform sampler2D nightMap;
  uniform sampler2D cloudMap;
  uniform vec3 sunDirection;   // в мировых координатах, нормирован
  uniform float cloudOpacity;
  uniform float cloudOffset;   // сдвиг облаков по долготе — имитация ветра

  varying vec2 vUv;
  varying vec3 vNormal;
  varying vec3 vViewDir;

  void main() {
    vec3 n = normalize(vNormal);
    float sunDot = dot(n, sunDirection);

    // Плавный терминатор. Ширина ~ 0.12 радиана даёт визуально верную
    // полосу сумерек: на Земле она около 100 км.
    float dayAmount = smoothstep(-0.12, 0.18, sunDot);

    vec3 day = texture2D(dayMap, vUv).rgb;
    vec3 night = texture2D(nightMap, vUv).rgb;

    // Облака сдвигаем по долготе, чтобы атмосфера не была статичной
    vec2 cloudUv = vec2(fract(vUv.x + cloudOffset), vUv.y);
    float clouds = texture2D(cloudMap, cloudUv).r * cloudOpacity;

    // Днём облака белые и освещённые, ночью почти не видны
    vec3 dayLit = mix(day, vec3(1.0), clouds);
    // Огни городов гасим под облаками
    vec3 nightLit = night * (1.0 - clouds * 0.85);

    // Подкрашиваем терминатор в тёплые тона — эффект длинного пути
    // солнечного света через атмосферу (рассеяние Рэлея убирает синий).
    // Полоса узкая: на Земле сумерки занимают около 100 км, то есть менее
    // одного градуса широты — раньше здесь стоял слишком широкий спад,
    // и вся освещённая сторона выходила оранжевой.
    float twilight = exp(-abs(sunDot) * 42.0);
    vec3 sunsetTint = vec3(1.0, 0.5, 0.2) * twilight * 0.28;

    // Освещённая сторона идёт почти в полную яркость: подставка 0.15
    // раньше срезала контраст и делала день тусклым
    vec3 color = mix(nightLit * 1.2, dayLit * (0.55 + 0.45 * dayAmount), dayAmount);
    color += sunsetTint * (1.0 - clouds * 0.5);

    // Зеркальный блик на океане: используем отсутствие облаков и синеву
    float ocean = clamp((day.b - day.r) * 3.0, 0.0, 1.0);
    vec3 h = normalize(sunDirection + vViewDir);
    float spec = pow(max(dot(n, h), 0.0), 60.0) * ocean * dayAmount * (1.0 - clouds);
    color += vec3(0.9, 0.95, 1.0) * spec * 0.6;

    // Френель-подсветка края — переход к атмосфере
    float fres = pow(1.0 - max(dot(n, vViewDir), 0.0), 3.0);
    color += vec3(0.25, 0.45, 0.85) * fres * (0.25 + 0.75 * dayAmount) * 0.5;

    gl_FragColor = vec4(color, 1.0);
  }
`

export const atmosphereVertex = /* glsl */ `
  varying vec3 vNormal;
  varying vec3 vWorldPos;

  void main() {
    vNormal = normalize(mat3(modelMatrix) * normal);
    vec4 wp = modelMatrix * vec4(position, 1.0);
    vWorldPos = wp.xyz;
    gl_Position = projectionMatrix * viewMatrix * wp;
  }
`

/**
 * Атмосферная оболочка, рисуется поверх планеты с аддитивным смешиванием
 * и BackSide, чтобы получить лимб. Интенсивность ~ рэлеевская зависимость
 * от угла и толщины слоя вдоль луча зрения.
 */
export const atmosphereFragment = /* glsl */ `
  uniform vec3 sunDirection;
  uniform vec3 glowColor;
  uniform float intensity;
  uniform float power;

  varying vec3 vNormal;
  varying vec3 vWorldPos;

  void main() {
    vec3 n = normalize(vNormal);
    vec3 viewDir = normalize(cameraPosition - vWorldPos);

    // На лимбе луч проходит больше воздуха — там свечение сильнее
    float rim = pow(1.0 - abs(dot(n, viewDir)), power);

    // Освещённая часть лимба ярче; на ночной стороне остаётся слабый след
    float sunAmount = dot(n, sunDirection);
    float lit = smoothstep(-0.45, 0.35, sunAmount);

    // Прямое рассеяние вперёд: когда смотрим почти на Солнце сквозь атмосферу
    float forward = pow(max(dot(viewDir, -sunDirection), 0.0), 4.0);

    float a = rim * intensity * (0.12 + 0.88 * lit);
    vec3 col = glowColor * a + vec3(1.0, 0.75, 0.5) * forward * rim * 0.4;

    gl_FragColor = vec4(col, a);
  }
`

/** Шейдер поверхности Солнца: гранулы + пятна через сумму шумов. */
export const sunVertex = /* glsl */ `
  varying vec2 vUv;
  varying vec3 vNormal;
  varying vec3 vViewDir;
  void main() {
    vUv = uv;
    vNormal = normalize(mat3(modelMatrix) * normal);
    vec4 wp = modelMatrix * vec4(position, 1.0);
    vViewDir = normalize(cameraPosition - wp.xyz);
    gl_Position = projectionMatrix * viewMatrix * wp;
  }
`

export const sunFragment = /* glsl */ `
  uniform sampler2D surfaceMap;
  uniform float time;

  varying vec2 vUv;
  varying vec3 vNormal;
  varying vec3 vViewDir;

  // Простой градиентный шум (value noise) для медленного шевеления гранул
  float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
  }
  float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
  }
  float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
      v += a * noise(p);
      p *= 2.03;
      a *= 0.5;
    }
    return v;
  }

  void main() {
    // Текстура даёт крупные структуры, шум — живую грануляцию
    vec3 base = texture2D(surfaceMap, vUv).rgb;
    float gran = fbm(vUv * 90.0 + vec2(time * 0.02, time * 0.013));
    float cell = fbm(vUv * 24.0 - vec2(time * 0.008, 0.0));

    vec3 hot = vec3(1.0, 0.78, 0.36);
    vec3 cool = vec3(0.86, 0.32, 0.06);
    vec3 col = mix(cool, hot, clamp(base.r * 0.7 + gran * 0.5 + cell * 0.2, 0.0, 1.0));

    // Потемнение к краю диска — реальный эффект, лучи с края идут
    // из более высоких и холодных слоёв фотосферы
    float mu = max(dot(normalize(vNormal), vViewDir), 0.0);
    float limb = 0.35 + 0.65 * pow(mu, 0.55);
    col *= limb;

    // Яркая кромка хромосферы
    col += vec3(1.0, 0.45, 0.15) * pow(1.0 - mu, 4.0) * 0.7;

    gl_FragColor = vec4(col * 1.7, 1.0);
  }
`

/** Корона Солнца — аддитивный ореол с анимированными протуберанцами. */
export const coronaFragment = /* glsl */ `
  uniform float time;
  uniform vec3 coreColor;
  varying vec2 vUv;

  float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
  }

  void main() {
    vec2 p = vUv - 0.5;
    float r = length(p) * 2.0;
    if (r > 1.0) discard;

    float ang = atan(p.y, p.x);
    // Радиальные струи, медленно вращающиеся
    float streaks = noise(vec2(ang * 6.0, r * 3.0 - time * 0.05)) * 0.5
                  + noise(vec2(ang * 14.0, r * 6.0 - time * 0.08)) * 0.3;

    float falloff = pow(max(0.0, 1.0 - r), 2.6);
    float a = falloff * (0.45 + streaks * 0.9);

    vec3 col = mix(vec3(1.0, 0.55, 0.15), coreColor, falloff);
    gl_FragColor = vec4(col, a * 0.75);
  }
`
