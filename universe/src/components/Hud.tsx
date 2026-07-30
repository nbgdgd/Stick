/**
 * Верхняя панель: где мы находимся и какой сейчас масштаб.
 *
 * Плотность специально низкая. В первой версии здесь одновременно жили
 * название уровня, индикатор масштаба, переключатель масштаба расстояний и
 * два чипа-тумблера — на телефоне это занимало четверть экрана и мешало
 * смотреть на сцену. Сейчас постоянно видны только название уровня и
 * индикатор масштаба; остальное — за кнопкой настроек.
 */

import { useEffect, useRef, useState } from 'react'
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
  const setSandboxOpen = useStore((s) => s.setSandboxOpen)

  const [settingsOpen, setSettingsOpen] = useState(false)
  const settingsRef = useRef<HTMLDivElement>(null)

  // Закрытие по тапу мимо
  useEffect(() => {
    if (!settingsOpen) return
    const onDown = (e: PointerEvent) => {
      if (settingsRef.current && !settingsRef.current.contains(e.target as Node)) {
        setSettingsOpen(false)
      }
    }
    document.addEventListener('pointerdown', onDown)
    return () => document.removeEventListener('pointerdown', onDown)
  }, [settingsOpen])

  const level = LEVELS[levelIndex]

  // При вертикальном поле зрения 55° и расстоянии d видимая высота равна 2·d·tg(27,5°)
  const viewSpanUnits = 2 * cameraDist * Math.tan((55 / 2) * (Math.PI / 180))
  const viewSpanM = viewSpanUnits * level.metersPerUnit

  // Сжатие расстояний осмысленно только там, где есть что сжимать
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
            <div className="hud__scale">
              <span className="hud__scale-value">{formatDistance(viewSpanM)}</span>
              <span className="hud__scale-power">{powerOfTen(viewSpanM)}</span>
            </div>
          </div>
        </div>

        <div className="hud__actions">
          <button className="icon-btn" onClick={() => setSearchOpen(true)} aria-label="Поиск объекта" title="Поиск (/)">
            <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-4.5-4.5" />
            </svg>
          </button>
          <div className="hud__settings-wrap" ref={settingsRef}>
            <button
              className={`icon-btn${settingsOpen ? ' icon-btn--on' : ''}`}
              onClick={() => setSettingsOpen((o) => !o)}
              aria-label="Настройки отображения"
            >
              <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round">
                <path d="M4 7h10M18 7h2M4 17h4M12 17h8" />
                <circle cx="16" cy="7" r="2.2" />
                <circle cx="10" cy="17" r="2.2" />
              </svg>
            </button>

            {settingsOpen && (
              <div className="hud__settings">
                {scaleToggleAvailable && (
                  <>
                    <div className="hud__settings-title">Масштаб расстояний</div>
                    <div className="seg">
                      <button
                        className={`seg__btn${!realScale ? ' seg__btn--on' : ''}`}
                        onClick={() => setRealScale(false)}
                      >
                        Читаемый
                      </button>
                      <button
                        className={`seg__btn${realScale ? ' seg__btn--on' : ''}`}
                        onClick={() => setRealScale(true)}
                      >
                        Реальный
                      </button>
                    </div>
                    <p className="hud__settings-hint">
                      В реальном масштабе система выглядит пустой. Так и есть на самом деле.
                    </p>
                  </>
                )}

                <div className="hud__settings-title">Показывать</div>
                <label className="hud__check">
                  <input type="checkbox" checked={showOrbits} onChange={toggleOrbits} />
                  Орбиты и опорные линии
                </label>
                <label className="hud__check">
                  <input type="checkbox" checked={showLabels} onChange={toggleLabels} />
                  Подписи объектов
                </label>

                <button
                  className="hud__settings-action"
                  onClick={() => {
                    setSettingsOpen(false)
                    setSandboxOpen(true)
                  }}
                >
                  Гравитационная песочница
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Логарифмическая линейка «Powers of Ten»: от 1 м до 10²⁷ м */}
      <div className="scale-track">
        <div
          className="scale-track__fill"
          style={{ width: `${Math.max(0, Math.min(100, (Math.log10(Math.max(viewSpanM, 1)) / 27) * 100))}%` }}
        />
        {LEVELS.map((l) => (
          <div
            key={l.id}
            className={`scale-track__tick${l.index === levelIndex ? ' scale-track__tick--active' : ''}`}
            style={{ left: `${(Math.log10(l.characteristicSizeM) / 27) * 100}%` }}
            title={l.title}
          />
        ))}
      </div>
    </div>
  )
}
