/**
 * Подпись объекта в 3D. Рисуется через drei/Html — DOM поверх канваса,
 * поэтому текст остаётся резким на любом зуме и не тратит текстурную память.
 *
 * Два важных момента:
 *
 * 1. `zIndexRange` держит подписи НИЖЕ интерфейса. Раньше диапазон доходил
 *    до 20 и подписи ложились поверх шапки и панели фактов — а так как они
 *    кликабельны, попадание по крестику панели выбирало другую планету.
 *
 * 2. Подпись регистрируется в системе разведения (`lib/labels.ts`), которая
 *    каждые несколько кадров прячет те, что налезают на соседей или на
 *    интерфейс. Сам компонент видимостью не управляет.
 */

import { useEffect, useRef } from 'react'
import { Html } from '@react-three/drei'
import * as THREE from 'three'
import { registerLabel, unregisterLabel, type LabelEntry } from '../lib/labels'

interface LabelProps {
  position: [number, number, number]
  text: string
  sub?: string
  small?: boolean
  color?: string
  onClick?: () => void
  /**
   * Важность: при нехватке места побеждает большее значение.
   * Ориентир: планета 100, спутник 40, звезда с заметкой 60, фон 10.
   */
  priority?: number
}

export function Label({ position, text, sub, small, color, onClick, priority = 20 }: LabelProps) {
  const anchor = useRef<THREE.Object3D>(null)
  const el = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const entry: LabelEntry = { anchor, el, priority, w: 0, h: 0, shown: true }
    registerLabel(entry)
    return () => unregisterLabel(entry)
  }, [priority])

  // Текст сменился — сбрасываем кэш размера, иначе разведение будет
  // считать по старой ширине
  useEffect(() => {
    if (el.current) {
      el.current.dataset.measured = ''
    }
  }, [text, sub])

  return (
    <object3D ref={anchor} position={position}>
      <Html
        center
        // Верхняя граница 4 — ниже любого слоя интерфейса (минимум 10)
        zIndexRange={[4, 0]}
        style={{ pointerEvents: onClick ? 'auto' : 'none' }}
      >
        <div
          ref={el}
          className={`obj-label${small ? ' obj-label--small' : ''}${onClick ? ' obj-label--clickable' : ''}`}
          style={color ? { color } : undefined}
          onClick={
            onClick
              ? (e) => {
                  e.stopPropagation()
                  onClick()
                }
              : undefined
          }
        >
          <span className="obj-label__name">{text}</span>
          {sub && <span className="obj-label__sub">{sub}</span>}
        </div>
      </Html>
    </object3D>
  )
}
