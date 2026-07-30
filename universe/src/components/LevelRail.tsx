/**
 * Вертикальная линейка уровней справа: и навигация, и карта «где я
 * в порядках величины».
 *
 * Кнопки «+» и «−» отсюда убраны: рядом с 3D-сценой они читаются как зум,
 * а не как смена масштабного уровня. Зум и переход между уровнями теперь
 * разведены по разным элементам (см. `ZoomPad`).
 */

import { useStore } from '../store'
import { LEVELS, formatDistance } from '../data/levels'

export function LevelRail() {
  const levelIndex = useStore((s) => s.levelIndex)
  const setLevel = useStore((s) => s.setLevel)
  const transitioning = useStore((s) => s.transitioning)

  return (
    <div className="rail" aria-label="Уровни масштаба">
      {LEVELS.map((l) => (
        <button
          key={l.id}
          className={`rail__item${l.index === levelIndex ? ' rail__item--on' : ''}`}
          onClick={() => setLevel(l.index)}
          disabled={transitioning}
          title={`${l.title} — ${formatDistance(l.characteristicSizeM)}`}
          aria-label={l.title}
        >
          <span className="rail__dot" />
        </button>
      ))}
    </div>
  )
}
