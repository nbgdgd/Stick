/**
 * Подпись объекта в 3D. Рисуется через drei/Html — DOM поверх канваса.
 * Так текст остаётся резким на любом зуме и не тратит текстурную память,
 * в отличие от спрайтов с растровым шрифтом.
 */

import { Html } from '@react-three/drei'

interface LabelProps {
  position: [number, number, number]
  text: string
  sub?: string
  small?: boolean
  color?: string
  onClick?: () => void
  /** до какого расстояния камеры подпись видна */
  distanceFactor?: number
}

export function Label({ position, text, sub, small, color, onClick, distanceFactor }: LabelProps) {
  return (
    <Html
      position={position}
      center
      distanceFactor={distanceFactor}
      zIndexRange={[20, 0]}
      style={{ pointerEvents: onClick ? 'auto' : 'none' }}
    >
      <div
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
  )
}
