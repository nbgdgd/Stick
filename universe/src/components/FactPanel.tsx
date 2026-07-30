/**
 * Панель фактов выбранного объекта. Снизу на телефоне, сбоку на широком экране.
 *
 * Закрыть её можно тремя способами, и это не избыточность — каждый из них
 * пользователь пробует первым в своей ситуации:
 *  — крестик,
 *  — свайп вниз по шапке (полоска-ручка сверху не декоративная),
 *  — системная кнопка «назад» (обрабатывается в `useBackButton`).
 *
 * Свайп начинается только с шапки или когда список фактов прокручен
 * в самое начало: иначе жест «пролистать факты вниз» закрывал бы панель.
 *
 * Источник данных подписан в каждой карточке: это требование к честности,
 * а не украшение. Если изображение — реконструкция, панель говорит об этом прямо.
 */

import { useEffect, useRef, useState } from 'react'
import { useStore } from '../store'
import { formatDistance } from '../data/levels'

/** На сколько нужно утянуть панель вниз, чтобы она закрылась. */
const DISMISS_PX = 96
/** Скорость, при которой достаточно короткого рывка, px/мс. */
const FLICK_VELOCITY = 0.55

export function FactPanel() {
  const selected = useStore((s) => s.selected)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  const sheetRef = useRef<HTMLDivElement>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const drag = useRef<{ id: number; y0: number; t0: number; y: number; active: boolean } | null>(null)
  const [offset, setOffset] = useState(0)
  const [closing, setClosing] = useState(false)

  // Новый объект — сбрасываем сдвиг от предыдущего свайпа
  useEffect(() => {
    setOffset(0)
    setClosing(false)
  }, [selected?.id])

  function close() {
    setClosing(true)
    // Даём анимации уехать вниз, потом снимаем выбор
    setTimeout(() => {
      select(null)
      setFocus(null)
    }, 180)
  }

  if (!selected) return null

  function onPointerDown(e: React.PointerEvent) {
    // Свайп перехватываем, только если список в самом верху: иначе
    // пользователь не сможет прокрутить факты
    const scrolled = scrollRef.current ? scrollRef.current.scrollTop > 2 : false
    const fromHeader = (e.target as HTMLElement).closest('.panel__grip') !== null
    if (scrolled && !fromHeader) return
    drag.current = { id: e.pointerId, y0: e.clientY, t0: performance.now(), y: e.clientY, active: false }
  }

  function onPointerMove(e: React.PointerEvent) {
    const d = drag.current
    if (!d || d.id !== e.pointerId) return
    const dy = e.clientY - d.y0
    d.y = e.clientY
    // Тянем только вниз; вверх панель не растягиваем
    if (dy > 0) {
      if (!d.active && dy > 6) {
        d.active = true
        try {
          ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
        } catch {
          // указателя уже нет — не критично
        }
      }
      if (d.active) setOffset(dy)
    }
  }

  function onPointerUp(e: React.PointerEvent) {
    const d = drag.current
    if (!d || d.id !== e.pointerId) return
    drag.current = null
    const dy = e.clientY - d.y0
    const dt = Math.max(1, performance.now() - d.t0)
    const v = dy / dt
    if (dy > DISMISS_PX || (dy > 24 && v > FLICK_VELOCITY)) close()
    else setOffset(0)
  }

  return (
    <div
      ref={sheetRef}
      className={`panel${closing ? ' panel--closing' : ''}`}
      role="dialog"
      aria-label={`Факты: ${selected.name}`}
      style={{
        transform: offset ? `translateY(${offset}px)` : undefined,
        // Во время перетаскивания анимация перехода мешает — она делает
        // движение «резиновым» и панель отстаёт от пальца
        transition: drag.current?.active ? 'none' : undefined,
      }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      {/* Шапка — зона захвата для свайпа. Увеличена по высоте: попасть
          пальцем в четырёхпиксельную полоску невозможно. */}
      <div className="panel__grip">
        <div className="panel__handle" />
      </div>

      <button className="panel__close" onClick={close} aria-label="Закрыть">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>

      <div className="panel__scroll" ref={scrollRef}>
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
    </div>
  )
}
