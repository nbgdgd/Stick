/**
 * Верхняя панель: где мы находимся, какой сейчас масштаб, и переключатели.
 *
 * Индикатор масштаба — главный элемент против потери ориентации при зуме:
 * показывает и ширину видимой области в физических единицах, и порядок
 * величины (10ⁿ м), и расстояние от Земли до текущего центра внимания.
 */

import { useStore } from '../store'
import { LEVELS, formatDistance, powerOfTen } from '../data/levels'

export function Hud() {
  const levelIndex = useStore((s) => s.levelIndex)
  const cameraDist = useStore((s) => s.cameraDist)
  const realScale = useStore((s) => s.realScale)
  const setRealScale = useStore((s) => s.setRealScale)
  const showOrbits = useStore((s) => s.showOrbits)
  const toggleOrbits = useStore((s) => s.toggleOrbits)
  const showLabels = useStore((s) => s.showLabels)
  const toggleLabels = useStore((s) => s.toggleLabels)
  const setSearchOpen = useStore((s) => s.setSearchOpen)
  const selected = useStore((s) => s.selected)

  const level = LEVELS[levelIndex]

  // Ширина видимой области: при вертикальном FOV 55° и расстоянии d
  // видимая высота равна 2·d·tan(27,5°)
  const viewSpanUnits = 2 * cameraDist * Math.tan((55 / 2) * (Math.PI / 180))
  const viewSpanM = viewSpanUnits * level.metersPerUnit

  // Уровни, на которых сжатие масштаба вообще имеет смысл
  const scaleToggleAvailable = level.id === 'solar-system' || level.id === 'earth-moon'

  return (
    <div className="hud">
      <div className="hud__row">
        <div className="hud__level">
          <div className="hud__level-index">
            {level.index + 1}
            <span className="hud__level-total">/{LEVELS.length}</span>
          </div>
          <div className="hud__level-text">
            <div className="hud__title">{level.title}</div>
            <div className="hud__subtitle">{level.subtitle}</div>
          </div>
        </div>

        <div className="hud__actions">
          <button className="icon-btn" onClick={() => setSearchOpen(true)} aria-label="Поиск объекта" title="Поиск (/)">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-4.5-4.5" />
            </svg>
          </button>
        </div>
      </div>

      {/* Индикатор масштаба */}
      <div className="scale-bar">
        <div className="scale-bar__main">
          <span className="scale-bar__label">В кадре</span>
          <span className="scale-bar__value">{formatDistance(viewSpanM)}</span>
          <span className="scale-bar__power">{powerOfTen(viewSpanM)}</span>
        </div>
        <div className="scale-bar__track">
          {/* Логарифмическая линейка от 1 м (10⁰) до 10²⁷ м */}
          <div
            className="scale-bar__fill"
            style={{ width: `${Math.max(0, Math.min(100, (Math.log10(Math.max(viewSpanM, 1)) / 27) * 100))}%` }}
          />
          {LEVELS.map((l) => (
            <div
              key={l.id}
              className={`scale-bar__tick${l.index === levelIndex ? ' scale-bar__tick--active' : ''}`}
              style={{ left: `${(Math.log10(l.characteristicSizeM) / 27) * 100}%` }}
              title={l.title}
            />
          ))}
        </div>
        {selected?.distanceM !== undefined && selected.distanceM > 0 && (
          <div className="scale-bar__from-earth">
            До «{selected.name}» от Земли: <strong>{formatDistance(selected.distanceM)}</strong>
          </div>
        )}
      </div>

      <div className="toggles">
        {scaleToggleAvailable ? (
          <div className="seg">
            <button
              className={`seg__btn${!realScale ? ' seg__btn--on' : ''}`}
              onClick={() => setRealScale(false)}
              title="Расстояния сжаты, чтобы всё влезло в кадр"
            >
              Читаемый масштаб
            </button>
            <button
              className={`seg__btn${realScale ? ' seg__btn--on' : ''}`}
              onClick={() => setRealScale(true)}
              title="Настоящие пропорции расстояний"
            >
              Реальный масштаб
            </button>
          </div>
        ) : (
          <div className="seg seg--note">Масштаб этого уровня — реальный</div>
        )}

        <button className={`chip${showOrbits ? ' chip--on' : ''}`} onClick={toggleOrbits}>
          Орбиты
        </button>
        <button className={`chip${showLabels ? ' chip--on' : ''}`} onClick={toggleLabels}>
          Подписи
        </button>
      </div>

      {realScale && scaleToggleAvailable && (
        <div className="hud__warning">
          Реальный масштаб: планеты меньше пикселя, а между орбитами пустота. Так и есть на самом деле —
          Солнечная система почти целиком состоит из ничего.
        </div>
      )}
    </div>
  )
}
