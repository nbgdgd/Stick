/**
 * Панель фактов выбранного объекта. Выезжает снизу на телефоне,
 * сбоку — на широком экране (см. CSS).
 *
 * Источник данных подписан в каждой карточке: это требование к честности,
 * а не украшение. Если модель или изображение — реконструкция, панель
 * говорит об этом прямо.
 */

import { useStore } from '../store'
import { formatDistance } from '../data/levels'

export function FactPanel() {
  const selected = useStore((s) => s.selected)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  if (!selected) return null

  return (
    <div className="panel" role="dialog" aria-label={`Факты: ${selected.name}`}>
      <div className="panel__handle" />
      <button
        className="panel__close"
        onClick={() => {
          select(null)
          setFocus(null)
        }}
        aria-label="Закрыть"
      >
        ✕
      </button>

      <div className="panel__head">
        {selected.texture && (
          <div className="panel__thumb">
            <img src={selected.texture} alt="" />
          </div>
        )}
        <div className="panel__titles">
          <div className="panel__kind">{selected.kind}</div>
          <h2 className="panel__name">{selected.name}</h2>
          {selected.distanceM !== undefined && selected.distanceM > 0 && (
            <div className="panel__dist">{formatDistance(selected.distanceM)} от Земли</div>
          )}
        </div>
      </div>

      {selected.artistic && (
        <div className="panel__artistic">
          Художественная реконструкция: реального снимка или 3D-модели этого объекта в открытом
          доступе нет. Числовые данные ниже — настоящие.
        </div>
      )}

      {selected.blurb && <p className="panel__blurb">{selected.blurb}</p>}

      <dl className="facts">
        {selected.facts.map((f, i) =>
          f.value === '' ? (
            <div key={i} className="facts__divider">
              {f.label.replace(/—/g, '').trim()}
            </div>
          ) : (
            <div key={i} className="facts__row">
              <dt>{f.label}</dt>
              <dd>{f.value}</dd>
            </div>
          ),
        )}
      </dl>

      {selected.source && (
        <div className="panel__source">
          <span className="panel__source-label">Источник данных</span>
          {selected.source}
        </div>
      )}
    </div>
  )
}
