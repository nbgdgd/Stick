/**
 * Управление временем симуляции: пауза, скорость, дата, сброс на «сейчас».
 *
 * Ползунок скорости логарифмический — иначе между «реальным временем»
 * и «100 лет в секунду» невозможно попасть в промежуточные значения.
 */

import { useStore, TIME_PRESETS } from '../store'
import { LEVELS } from '../data/levels'
import { sunGeometry, seasonName } from '../lib/sun'

function fmtDate(ms: number): string {
  const d = new Date(ms)
  return d.toLocaleString('ru-RU', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'UTC',
  })
}

function fmtScale(v: number): string {
  if (v === 1) return 'реальное время'
  if (v < 60) return `× ${v}`
  if (v < 3600) return `${Math.round(v / 60)} мин/с`
  if (v < 86400) return `${(v / 3600).toFixed(v / 3600 < 10 ? 1 : 0)} ч/с`
  if (v < 2629800) return `${(v / 86400).toFixed(v / 86400 < 10 ? 1 : 0)} сут/с`
  if (v < 31557600) return `${(v / 2629800).toFixed(1)} мес/с`
  const years = v / 31557600
  if (years < 1000) return `${years.toFixed(years < 10 ? 1 : 0)} лет/с`
  if (years < 1e6) return `${(years / 1000).toFixed(1)} тыс. лет/с`
  return `${(years / 1e6).toFixed(1)} млн лет/с`
}

export function TimeControls() {
  const simTime = useStore((s) => s.simTime)
  const timeScale = useStore((s) => s.timeScale)
  const setTimeScale = useStore((s) => s.setTimeScale)
  const paused = useStore((s) => s.paused)
  const togglePause = useStore((s) => s.togglePause)
  const resetTime = useStore((s) => s.resetTime)
  const setSimTime = useStore((s) => s.setSimTime)
  const levelIndex = useStore((s) => s.levelIndex)
  const level = LEVELS[levelIndex]

  // Время влияет только на уровни с движением; на статических распределениях
  // (Галактика и выше) прокрутка бессмысленна — реальные времена там
  // измеряются в сотнях миллионов лет
  const timeMatters = levelIndex <= 3

  const sun = sunGeometry(simTime)

  // Ползунок: log10 скорости от 0 (×1) до 9,5 (≈ 100 лет/с)
  const sliderValue = Math.log10(Math.max(1, timeScale))

  return (
    <div className={`time${timeMatters ? '' : ' time--muted'}`}>
      <div className="time__top">
        <button className="time__play" onClick={togglePause} aria-label={paused ? 'Продолжить' : 'Пауза'}>
          {paused ? (
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M8 5v14l11-7z" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M7 5h4v14H7zM13 5h4v14h-4z" />
            </svg>
          )}
        </button>

        <div className="time__readout">
          <div className="time__date">{fmtDate(simTime)} UTC</div>
          <div className="time__rate">{paused ? 'пауза' : fmtScale(timeScale)}</div>
        </div>

        <button className="time__now" onClick={resetTime} title="Вернуться к текущему моменту">
          Сейчас
        </button>
      </div>

      <input
        className="time__slider"
        type="range"
        min={0}
        max={9.5}
        step={0.05}
        value={sliderValue}
        onChange={(e) => setTimeScale(Math.pow(10, parseFloat(e.target.value)))}
        aria-label="Скорость времени"
        disabled={!timeMatters}
      />

      <div className="time__presets">
        {TIME_PRESETS.map((p) => (
          <button
            key={p.value}
            className={`time__preset${Math.abs(Math.log10(timeScale / p.value)) < 0.06 ? ' time__preset--on' : ''}`}
            onClick={() => setTimeScale(p.value)}
            disabled={!timeMatters}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* Быстрые прыжки по времени — удобно проверять сезоны и фазы */}
      <div className="time__jumps">
        <span className="time__jumps-label">Сдвинуть:</span>
        {[
          { label: '−1 год', d: -365.25 },
          { label: '−1 мес', d: -30.44 },
          { label: '−1 сут', d: -1 },
          { label: '+1 сут', d: 1 },
          { label: '+1 мес', d: 30.44 },
          { label: '+1 год', d: 365.25 },
        ].map((j) => (
          <button key={j.label} className="time__jump" onClick={() => setSimTime(simTime + j.d * 86400000)}>
            {j.label}
          </button>
        ))}
      </div>

      {level.id === 'earth' && (
        <div className="time__note">
          {seasonName(sun.eclipticLongitude)} · склонение Солнца {sun.dec.toFixed(1)}°
        </div>
      )}
      {!timeMatters && (
        <div className="time__note">
          На этом масштабе заметные изменения занимают сотни миллионов лет — прокрутка времени
          ничего бы не показала.
        </div>
      )}
    </div>
  )
}
