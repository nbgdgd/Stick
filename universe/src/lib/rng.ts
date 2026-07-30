/**
 * Детерминированный ГПСЧ (mulberry32) — нужен, чтобы процедурная генерация
 * крупномасштабной структуры была воспроизводимой между запусками.
 */
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0
  return function () {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Строка -> 32-битный хеш, для сидов, привязанных к имени объекта. */
export function hashString(s: string): number {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/** Нормальное распределение (Box–Muller) на базе переданного uniform-ГПСЧ. */
export function gaussian(rnd: () => number): number {
  let u = 0
  let v = 0
  while (u === 0) u = rnd()
  while (v === 0) v = rnd()
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v)
}

/** Случайная точка на сфере единичного радиуса, равномерно по площади. */
export function randomOnSphere(rnd: () => number): [number, number, number] {
  const z = rnd() * 2 - 1
  const t = rnd() * Math.PI * 2
  const r = Math.sqrt(1 - z * z)
  return [r * Math.cos(t), r * Math.sin(t), z]
}
