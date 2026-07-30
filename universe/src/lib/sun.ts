/**
 * Направление на Солнце и связанная с ним геометрия освещения Земли.
 * Нужно, чтобы терминатор и времена года были не декоративными, а
 * соответствовали выбранной дате.
 */

import { DEG, centuriesSinceJ2000, jdFromUnixMs, obliquity, gmstDegrees, daysSinceJ2000 } from './astro'

export interface SunGeometry {
  /** эклиптическая долгота Солнца, град */
  eclipticLongitude: number
  /** прямое восхождение Солнца, град */
  ra: number
  /** склонение Солнца, град — оно же широта подсолнечной точки */
  dec: number
  /** долгота подсолнечной точки, град (восточная положительна) */
  subsolarLon: number
  /** расстояние Земля — Солнце, а.е. */
  distanceAu: number
  /** единичный вектор от Земли к Солнцу в экваториальных координатах */
  direction: [number, number, number]
  /** угол поворота Земли вокруг оси, град (GMST) */
  gmst: number
}

/**
 * Видимое положение Солнца по низкоточной формуле из Astronomical Almanac.
 * Точность около 0,01° — на порядки лучше, чем нужно для картинки.
 */
export function sunGeometry(unixMs: number): SunGeometry {
  const jd = jdFromUnixMs(unixMs)
  const T = centuriesSinceJ2000(jd)
  const d = daysSinceJ2000(jd)

  // средняя долгота и средняя аномалия
  const L = 280.46646 + 0.9856474 * d
  const g = 357.52911 + 0.9856003 * d

  const gRad = g * DEG
  // уравнение центра
  const lambda =
    L + 1.914602 * Math.sin(gRad) + 0.019993 * Math.sin(2 * gRad) + 0.000289 * Math.sin(3 * gRad)
  const lambdaRad = lambda * DEG

  const eps = obliquity(T) * DEG

  // расстояние по формуле орбиты Земли
  const distanceAu =
    1.00014 - 0.01671 * Math.cos(gRad) - 0.00014 * Math.cos(2 * gRad)

  // экваториальные координаты
  const ra = Math.atan2(Math.cos(eps) * Math.sin(lambdaRad), Math.cos(lambdaRad))
  const dec = Math.asin(Math.sin(eps) * Math.sin(lambdaRad))

  const gmst = gmstDegrees(jd)
  // долгота подсолнечной точки: где местное время — истинный полдень
  let subsolarLon = (ra / DEG - gmst) % 360
  if (subsolarLon > 180) subsolarLon -= 360
  if (subsolarLon < -180) subsolarLon += 360

  const raDeg = ((ra / DEG) % 360 + 360) % 360
  const decDeg = dec / DEG

  const cd = Math.cos(dec)
  const direction: [number, number, number] = [cd * Math.cos(ra), cd * Math.sin(ra), Math.sin(dec)]

  return {
    eclipticLongitude: ((lambda % 360) + 360) % 360,
    ra: raDeg,
    dec: decDeg,
    subsolarLon,
    distanceAu,
    direction,
    gmst,
  }
}

/** Название текущего астрономического сезона в северном полушарии. */
export function seasonName(eclipticLongitude: number): string {
  const l = ((eclipticLongitude % 360) + 360) % 360
  if (l < 90) return 'Весна (сев. полушарие)'
  if (l < 180) return 'Лето (сев. полушарие)'
  if (l < 270) return 'Осень (сев. полушарие)'
  return 'Зима (сев. полушарие)'
}

/** Ближайшее астрономическое событие: равноденствие или солнцестояние. */
export function nextSolarEvent(eclipticLongitude: number): { name: string; degreesAway: number } {
  const l = ((eclipticLongitude % 360) + 360) % 360
  const events = [
    { at: 0, name: 'весеннее равноденствие' },
    { at: 90, name: 'летнее солнцестояние' },
    { at: 180, name: 'осеннее равноденствие' },
    { at: 270, name: 'зимнее солнцестояние' },
    { at: 360, name: 'весеннее равноденствие' },
  ]
  for (const e of events) {
    if (e.at > l) return { name: e.name, degreesAway: e.at - l }
  }
  return { name: events[0].name, degreesAway: 360 - l }
}

/**
 * Орбита МКС. Реальные параметры орбиты (высота ≈ 420 км, наклонение 51,6°,
 * период ≈ 92,9 мин). Долгота восходящего узла дрейфует из-за сжатия Земли
 * примерно на −5° в сутки — это учтено, чтобы трасса смещалась как настоящая.
 */
export const ISS_ORBIT = {
  altitudeKm: 420,
  inclinationDeg: 51.64,
  periodMinutes: 92.9,
  /** прецессия узла, град/сутки */
  nodeDriftPerDay: -5.0,
  /** долгота узла в эпоху J2000, град — условная точка отсчёта */
  node0: 120,
}

/**
 * Положение МКС в экваториальных координатах, в радиусах Земли.
 * Круговая орбита — эксцентриситет реальной орбиты около 0,0005, им можно пренебречь.
 */
export function issPosition(unixMs: number, earthRadiusKm = 6371): [number, number, number] {
  const jd = jdFromUnixMs(unixMs)
  const d = daysSinceJ2000(jd)
  const r = (earthRadiusKm + ISS_ORBIT.altitudeKm) / earthRadiusKm

  const revs = (d * 1440) / ISS_ORBIT.periodMinutes
  const u = revs * 2 * Math.PI // аргумент широты
  const node = (ISS_ORBIT.node0 + ISS_ORBIT.nodeDriftPerDay * d) * DEG
  const inc = ISS_ORBIT.inclinationDeg * DEG

  // положение в плоскости орбиты
  const xo = r * Math.cos(u)
  const yo = r * Math.sin(u)
  // наклонение
  const y1 = yo * Math.cos(inc)
  const z1 = yo * Math.sin(inc)
  // долгота узла
  return [xo * Math.cos(node) - y1 * Math.sin(node), xo * Math.sin(node) + y1 * Math.cos(node), z1]
}
