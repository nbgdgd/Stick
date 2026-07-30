/**
 * Вертикальная линейка уровней масштаба справа — навигация и одновременно
 * карта «где я в порядках величины». Подписи расстояний берутся из
 * характерного размера уровня.
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
        >
          <span className="rail__dot" />
          <span className="rail__text">
            <span className="rail__title">{l.title}</span>
            <span className="rail__size">{formatDistance(l.characteristicSizeM)}</span>
          </span>
        </button>
      ))}

      <div className="rail__nav">
        <button
          className="rail__arrow"
          onClick={() => setLevel(levelIndex - 1)}
          disabled={levelIndex === 0 || transitioning}
          aria-label="Уровень внутрь"
          title="Внутрь (←)"
        >
          −
        </button>
        <button
          className="rail__arrow"
          onClick={() => setLevel(levelIndex + 1)}
          disabled={levelIndex === LEVELS.length - 1 || transitioning}
          aria-label="Уровень наружу"
          title="Наружу (→)"
        >
          +
        </button>
      </div>
    </div>
  )
}
