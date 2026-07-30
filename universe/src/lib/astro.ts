/**
 * Астрономическая математика: юлианские даты, решение уравнения Кеплера,
 * перевод элементов орбиты в декартовы координаты, перевод RA/Dec/расстояние
 * в галактические/экваториальные декартовы координаты.
 *
 * Формулы: стандартные (Meeus, "Astronomical Algorithms"; JPL Solar System
 * Dynamics "Approximate Positions of the Major Planets").
 */

export const DEG = Math.PI / 180
export const RAD = 180 / Math.PI

/** Астрономическая единица в метрах (IAU 2012, точное определение). */
export const AU_M = 1.495978707e11
/** Световой год в метрах. */
export const LY_M = 9.4607304725808e15
/** Парсек в метрах. */
export const PC_M = 3.0856775814913673e16
/** Радиус Земли (экваториальный), м. */
export const EARTH_R_M = 6378137

/** Юлианская дата из Unix-миллисекунд. */
export function jdFromUnixMs(ms: number): number {
  return ms / 86400000 + 2440587.5
}

/** Юлианские столетия от эпохи J2000.0. */
export function centuriesSinceJ2000(jd: number): number {
  return (jd - 2451545.0) / 36525
}

/** Дни от J2000.0. */
export function daysSinceJ2000(jd: number): number {
  return jd - 2451545.0
}

/**
 * Решение уравнения Кеплера M = E - e·sin E методом Ньютона.
 * Возвращает эксцентрическую аномалию E в радианах.
 */
export function solveKepler(M: number, e: number): number {
  // приводим M к [-pi, pi] для быстрой сходимости
  let m = M % (2 * Math.PI)
  if (m > Math.PI) m -= 2 * Math.PI
  if (m < -Math.PI) m += 2 * Math.PI
  let E = e < 0.8 ? m : Math.PI
  for (let i = 0; i < 12; i++) {
    const f = E - e * Math.sin(E) - m
    const fp = 1 - e * Math.cos(E)
    const dE = f / fp
    E -= dE
    if (Math.abs(dE) < 1e-12) break
  }
  return E
}

export interface OrbitElements {
  /** большая полуось, а.е. (или другие единицы — на выходе те же) */
  a: number
  /** эксцентриситет */
  e: number
  /** наклонение, град */
  i: number
  /** долгота восходящего узла, град */
  om: number
  /** аргумент перицентра, град */
  w: number
  /** средняя аномалия в эпоху, град */
  M0: number
  /** период, суток (для расчёта средней аномалии) */
  periodDays: number
}

/**
 * Гелиоцентрические (или планетоцентрические) декартовы координаты
 * из кеплеровых элементов на момент t (дни от J2000).
 * Оси: X к точке весеннего равноденствия, Z к северному полюсу эклиптики.
 */
export function orbitPosition(el: OrbitElements, tDays: number): [number, number, number] {
  const n = (2 * Math.PI) / el.periodDays // средняя угловая скорость, рад/сут
  const M = el.M0 * DEG + n * tDays
  const E = solveKepler(M, el.e)
  // положение в плоскости орбиты
  const xv = el.a * (Math.cos(E) - el.e)
  const yv = el.a * Math.sqrt(1 - el.e * el.e) * Math.sin(E)
  return rotateToEcliptic(xv, yv, el.i * DEG, el.om * DEG, el.w * DEG)
}

/** Поворот из плоскости орбиты в опорную плоскость. */
export function rotateToEcliptic(
  xv: number,
  yv: number,
  i: number,
  om: number,
  w: number,
): [number, number, number] {
  const cosW = Math.cos(w)
  const sinW = Math.sin(w)
  const cosO = Math.cos(om)
  const sinO = Math.sin(om)
  const cosI = Math.cos(i)
  const sinI = Math.sin(i)
  // сначала поворот на аргумент перицентра
  const x1 = xv * cosW - yv * sinW
  const y1 = xv * sinW + yv * cosW
  // наклонение
  const x2 = x1
  const y2 = y1 * cosI
  const z2 = y1 * sinI
  // долгота узла
  const x = x2 * cosO - y2 * sinO
  const y = x2 * sinO + y2 * cosO
  return [x, y, z2]
}

/**
 * Элементы больших планет по приближённым формулам JPL (Standish),
 * задаются значением в J2000 и линейной скоростью на юлианское столетие.
 * L — средняя долгота, lp — долгота перигелия, om — долгота восх. узла.
 */
export interface JplElements {
  a: number
  aDot: number
  e: number
  eDot: number
  i: number
  iDot: number
  L: number
  LDot: number
  lp: number
  lpDot: number
  om: number
  omDot: number
}

/** Развёрнутые на момент T (юл. столетия от J2000) элементы. */
export function jplAt(el: JplElements, T: number): OrbitElements & { trueAnomaly: number } {
  const a = el.a + el.aDot * T
  const e = el.e + el.eDot * T
  const i = el.i + el.iDot * T
  const L = el.L + el.LDot * T
  const lp = el.lp + el.lpDot * T
  const om = el.om + el.omDot * T
  const w = lp - om
  const M = L - lp
  // период по третьему закону Кеплера (a в а.е. -> годы), затем в сутки
  const periodDays = Math.pow(Math.abs(a), 1.5) * 365.25636
  return { a, e, i, om, w, M0: M, periodDays, trueAnomaly: 0 }
}

/** Положение планеты по элементам JPL в а.е., эклиптические координаты. */
export function jplPosition(el: JplElements, T: number): [number, number, number] {
  const a = el.a + el.aDot * T
  const e = el.e + el.eDot * T
  const i = (el.i + el.iDot * T) * DEG
  const L = (el.L + el.LDot * T) * DEG
  const lp = (el.lp + el.lpDot * T) * DEG
  const om = (el.om + el.omDot * T) * DEG
  const w = lp - om
  const E = solveKepler(L - lp, e)
  const xv = a * (Math.cos(E) - e)
  const yv = a * Math.sqrt(1 - e * e) * Math.sin(E)
  return rotateToEcliptic(xv, yv, i, om, w)
}

/** Наклон эклиптики к экватору на момент T (юл. столетия), в градусах. */
export function obliquity(T: number): number {
  // IAU 2006, разложение по T
  return 23.439279444 - 0.0130102136 * T - 5.086e-8 * T * T + 5.565e-7 * T * T * T
}

/**
 * Среднее звёздное время по Гринвичу, градусы (0..360).
 * Нужно, чтобы правильно повернуть Землю относительно направления на Солнце.
 */
export function gmstDegrees(jd: number): number {
  const T = centuriesSinceJ2000(jd)
  let g =
    280.46061837 +
    360.98564736629 * (jd - 2451545.0) +
    0.000387933 * T * T -
    (T * T * T) / 38710000
  g = g % 360
  return g < 0 ? g + 360 : g
}

/**
 * RA (часы), Dec (градусы), расстояние -> декартовы экваториальные координаты.
 * X к точке весеннего равноденствия, Z к северному полюсу мира.
 */
export function raDecToXyz(raHours: number, decDeg: number, dist: number): [number, number, number] {
  const ra = raHours * 15 * DEG
  const dec = decDeg * DEG
  const cd = Math.cos(dec)
  return [dist * cd * Math.cos(ra), dist * cd * Math.sin(ra), dist * Math.sin(dec)]
}

/** Разбор строки "чч мм сс" в часы. */
export function hms(h: number, m: number, s: number): number {
  return h + m / 60 + s / 3600
}

/** Разбор "±гг мм сс" в градусы (знак берётся из первого аргумента). */
export function dms(d: number, m: number, s: number): number {
  const sign = d < 0 || Object.is(d, -0) ? -1 : 1
  return sign * (Math.abs(d) + m / 60 + s / 3600)
}

/**
 * Экваториальные -> галактические координаты (l, b) в градусах.
 * Полюс галактики J2000: RA 12h51m26.28s, Dec +27°07'41.7".
 */
const NGP_RA = hms(12, 51, 26.28) * 15 * DEG
const NGP_DEC = dms(27, 7, 41.7) * DEG
const L_NCP = 122.932 * DEG

export function equatorialToGalactic(raHours: number, decDeg: number): [number, number] {
  const ra = raHours * 15 * DEG
  const dec = decDeg * DEG
  const sb = Math.sin(dec) * Math.sin(NGP_DEC) + Math.cos(dec) * Math.cos(NGP_DEC) * Math.cos(ra - NGP_RA)
  const b = Math.asin(Math.max(-1, Math.min(1, sb)))
  const y = Math.cos(dec) * Math.sin(ra - NGP_RA)
  const x = Math.sin(dec) * Math.cos(NGP_DEC) - Math.cos(dec) * Math.sin(NGP_DEC) * Math.cos(ra - NGP_RA)
  let l = L_NCP - Math.atan2(y, x)
  l = ((l * RAD) % 360 + 360) % 360
  return [l, b * RAD]
}

/** Галактические (l,b,расстояние) -> декартовы, X к центру Галактики. */
export function galacticToXyz(lDeg: number, bDeg: number, dist: number): [number, number, number] {
  const l = lDeg * DEG
  const b = bDeg * DEG
  const cb = Math.cos(b)
  return [dist * cb * Math.cos(l), dist * cb * Math.sin(l), dist * Math.sin(b)]
}

/**
 * Красное смещение -> сопутствующее расстояние, Мпк.
 * Плоская ΛCDM: H0 = 67.7 км/с/Мпк, Ωm = 0.31, ΩΛ = 0.69 (Planck 2018).
 * Численное интегрирование по z методом Симпсона.
 */
export const H0 = 67.7
export const OMEGA_M = 0.31
export const C_KMS = 299792.458

export function comovingDistanceMpc(z: number, steps = 256): number {
  const hubbleDist = C_KMS / H0 // ~4428 Мпк
  const f = (zz: number) => 1 / Math.sqrt(OMEGA_M * Math.pow(1 + zz, 3) + (1 - OMEGA_M))
  const h = z / steps
  let sum = f(0) + f(z)
  for (let k = 1; k < steps; k++) {
    sum += f(k * h) * (k % 2 === 1 ? 4 : 2)
  }
  return (hubbleDist * (h / 3) * sum)
}

/** Температура звезды по спектральному классу -> цвет RGB (0..1). */
export function blackbodyColor(tempK: number): [number, number, number] {
  // Аппроксимация Танни Хельгарда для цветовой температуры
  const t = Math.max(1000, Math.min(40000, tempK)) / 100
  let r: number
  let g: number
  let b: number
  if (t <= 66) {
    r = 255
  } else {
    r = 329.698727446 * Math.pow(t - 60, -0.1332047592)
  }
  if (t <= 66) {
    g = 99.4708025861 * Math.log(t) - 161.1195681661
  } else {
    g = 288.1221695283 * Math.pow(t - 60, -0.0755148492)
  }
  if (t >= 66) {
    b = 255
  } else if (t <= 19) {
    b = 0
  } else {
    b = 138.5177312231 * Math.log(t - 10) - 305.0447927307
  }
  const cl = (v: number) => Math.max(0, Math.min(255, v)) / 255
  return [cl(r), cl(g), cl(b)]
}

/** Приблизительная эффективная температура по спектральному классу. */
export function spectralTemp(spec: string): number {
  const m = /^([OBAFGKMLTY])(\d(?:\.\d)?)?/.exec(spec.trim().toUpperCase())
  if (!m) return 5000
  const base: Record<string, [number, number]> = {
    O: [45000, 30000],
    B: [30000, 10000],
    A: [10000, 7500],
    F: [7500, 6000],
    G: [6000, 5200],
    K: [5200, 3700],
    M: [3700, 2400],
    L: [2400, 1300],
    T: [1300, 700],
    Y: [700, 300],
  }
  const [hi, lo] = base[m[1]] ?? [5000, 5000]
  const sub = m[2] ? parseFloat(m[2]) : 5
  return hi + ((lo - hi) * sub) / 10
}

/**
 * Галактические координаты -> декартовы экваториальные.
 *
 * Строим ортонормированный базис галактической системы, выраженный в
 * экваториальных координатах J2000:
 *   ẑ — на северный полюс Галактики,
 *   x̂ — на центр Галактики (Sgr A*, 17ʰ45ᵐ37,2ˢ, −28°56′10″),
 *   ŷ = ẑ × x̂.
 * Нужно, чтобы полоса Млечного Пути на фоне неба шла там, где она есть
 * на самом деле, а не под произвольным углом.
 */
const GC_RA = hms(17, 45, 37.2)
const GC_DEC = dms(-28, 56, 10)

function unit(ra: number, dec: number): [number, number, number] {
  return raDecToXyz(ra, dec, 1)
}

const GAL_Z = unit(NGP_RA * RAD / 15, NGP_DEC * RAD)
const GAL_X_RAW = unit(GC_RA, GC_DEC)
// Ортогонализация: центр Галактики лежит почти в плоскости, но не идеально
const GAL_X = (() => {
  const d = GAL_X_RAW[0] * GAL_Z[0] + GAL_X_RAW[1] * GAL_Z[1] + GAL_X_RAW[2] * GAL_Z[2]
  const v: [number, number, number] = [
    GAL_X_RAW[0] - d * GAL_Z[0],
    GAL_X_RAW[1] - d * GAL_Z[1],
    GAL_X_RAW[2] - d * GAL_Z[2],
  ]
  const n = Math.hypot(v[0], v[1], v[2])
  return [v[0] / n, v[1] / n, v[2] / n] as [number, number, number]
})()
const GAL_Y: [number, number, number] = [
  GAL_Z[1] * GAL_X[2] - GAL_Z[2] * GAL_X[1],
  GAL_Z[2] * GAL_X[0] - GAL_Z[0] * GAL_X[2],
  GAL_Z[0] * GAL_X[1] - GAL_Z[1] * GAL_X[0],
]

export function galacticToEquatorialXyz(lDeg: number, bDeg: number, dist: number): [number, number, number] {
  const l = lDeg * DEG
  const b = bDeg * DEG
  const cb = Math.cos(b)
  const gx = cb * Math.cos(l)
  const gy = cb * Math.sin(l)
  const gz = Math.sin(b)
  return [
    dist * (gx * GAL_X[0] + gy * GAL_Y[0] + gz * GAL_Z[0]),
    dist * (gx * GAL_X[1] + gy * GAL_Y[1] + gz * GAL_Z[1]),
    dist * (gx * GAL_X[2] + gy * GAL_Y[2] + gz * GAL_Z[2]),
  ]
}
