/**
 * Разведение подписей по экрану.
 *
 * Проблема, которую это решает: подписи объектов — этоDOM-элементы поверх
 * канваса, и они не знают друг о друге. На уровне ближайших звёзд в кадр
 * попадает под сорок названий, часть из них — компоненты кратных систем,
 * стоящие в одной точке. Без разведения получается нечитаемая каша.
 *
 * Алгоритм: каждый кадр (точнее, раз в несколько кадров) проецируем якоря
 * подписей в экранные координаты, сортируем по важности, и жадно оставляем
 * те, чей прямоугольник не пересекается с уже принятыми. Плюс запретные
 * зоны — там, где лежит интерфейс.
 *
 * Жадный алгоритм по приоритету, а не оптимальная укладка: оптимальная —
 * NP-трудная, а жадная даёт устойчивый результат (важное всегда побеждает)
 * и стоит доли миллисекунды.
 */

import * as THREE from 'three'

export interface LabelEntry {
  /** объект сцены, к которому привязана подпись */
  anchor: { current: THREE.Object3D | null }
  /** сам DOM-элемент подписи */
  el: { current: HTMLDivElement | null }
  /** чем больше, тем важнее: важные вытесняют неважные */
  priority: number
  /** кэш размера, чтобы не дёргать вёрстку каждый кадр */
  w: number
  h: number
  /** видима ли сейчас (для сглаживания появления) */
  shown: boolean
}

const entries = new Set<LabelEntry>()

/** Прямоугольники, занятые интерфейсом: подписи туда не попадают. */
let reserved: { x: number; y: number; w: number; h: number }[] = []

export function registerLabel(e: LabelEntry) {
  entries.add(e)
}

export function unregisterLabel(e: LabelEntry) {
  entries.delete(e)
}

export function clearLabels() {
  entries.clear()
}

/**
 * Пересчитать запретные зоны по фактическому положению панелей интерфейса.
 * Дёргается при изменении размера окна и при смене состава интерфейса.
 */
export function refreshReservedAreas() {
  const rects: { x: number; y: number; w: number; h: number }[] = []
  // Селекторы соответствуют панелям, которые реально перекрывают сцену
  for (const sel of ['.hud', '.time', '.rail', '.panel']) {
    const node = document.querySelector(sel) as HTMLElement | null
    if (!node) continue
    const r = node.getBoundingClientRect()
    if (r.width > 0 && r.height > 0) rects.push({ x: r.left, y: r.top, w: r.width, h: r.height })
  }
  reserved = rects
}

function overlaps(
  a: { x: number; y: number; w: number; h: number },
  b: { x: number; y: number; w: number; h: number },
  pad: number,
): boolean {
  return (
    a.x - pad < b.x + b.w &&
    a.x + a.w + pad > b.x &&
    a.y - pad < b.y + b.h &&
    a.y + a.h + pad > b.y
  )
}

const tmpV = new THREE.Vector3()

interface Candidate {
  e: LabelEntry
  x: number
  y: number
  depth: number
  rect: { x: number; y: number; w: number; h: number }
}

/**
 * Один проход разведения. Вызывается из useFrame с прореживанием.
 * width/height — размер канваса в CSS-пикселях.
 */
export function declutter(camera: THREE.Camera, width: number, height: number): void {
  const cands: Candidate[] = []

  for (const e of entries) {
    const anchor = e.anchor.current
    const el = e.el.current
    if (!anchor || !el) continue

    anchor.getWorldPosition(tmpV)
    tmpV.project(camera)

    // За камерой или за краем кадра — сразу прячем
    if (tmpV.z > 1 || tmpV.x < -1 || tmpV.x > 1 || tmpV.y < -1 || tmpV.y > 1) {
      if (e.shown) {
        el.style.visibility = 'hidden'
        e.shown = false
      }
      continue
    }

    // Размер меряем один раз: offsetWidth заставляет браузер пересчитать
    // вёрстку, и делать это для сорока элементов каждый кадр — заметно дорого
    if (e.w === 0 && el.offsetWidth > 0) {
      e.w = el.offsetWidth
      e.h = el.offsetHeight
    }
    const w = e.w || 90
    const h = e.h || 26

    const x = (tmpV.x * 0.5 + 0.5) * width
    const y = (-tmpV.y * 0.5 + 0.5) * height
    // Подпись отрисована со сдвигом вверх на свою высоту (translateY(-100%))
    cands.push({ e, x, y, depth: tmpV.z, rect: { x: x - w / 2, y: y - h, w, h } })
  }

  // Важное вперёд; при равной важности — то, что ближе к камере
  cands.sort((a, b) => b.e.priority - a.e.priority || a.depth - b.depth)

  // Подпись, не помещающаяся в кадр целиком, читается как обрывок слова —
  // хуже, чем её отсутствие. Отбрасываем такие до укладки.
  const MARGIN = 4

  const placed: { x: number; y: number; w: number; h: number }[] = reserved.slice()

  for (const c of cands) {
    const r = c.rect
    let ok =
      r.x >= MARGIN && r.y >= MARGIN && r.x + r.w <= width - MARGIN && r.y + r.h <= height - MARGIN
    for (const p of placed) {
      if (!ok) break
      if (overlaps(c.rect, p, 3)) {
        ok = false
        break
      }
    }
    const el = c.e.el.current!
    if (ok) {
      placed.push(c.rect)
      if (!c.e.shown) {
        el.style.visibility = 'visible'
        c.e.shown = true
      }
    } else if (c.e.shown) {
      el.style.visibility = 'hidden'
      c.e.shown = false
    }
  }
}
