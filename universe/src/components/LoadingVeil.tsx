/**
 * Вспышка на стыке уровней и заголовок нового масштаба.
 *
 * Функциональная задача — прикрыть кадр, в котором старая сцена уже
 * размонтирована, а новая ещё грузит текстуры. Эстетическая — дать
 * ощущение непрерывного пролёта, а не переключения экрана.
 */

import { useEffect, useState } from 'react'
import { useStore } from '../store'
import { LEVELS } from '../data/levels'

export function LoadingVeil() {
  const levelIndex = useStore((s) => s.levelIndex)
  const transitioning = useStore((s) => s.transitioning)
  const level = LEVELS[levelIndex]

  const [showTitle, setShowTitle] = useState(true)

  useEffect(() => {
    setShowTitle(true)
    const t = setTimeout(() => setShowTitle(false), 2600)
    return () => clearTimeout(t)
  }, [levelIndex])

  return (
    <>
      <div className={`veil${transitioning ? ' veil--on' : ''}`} />
      <div className={`level-title${showTitle ? ' level-title--on' : ''}`}>
        <div className="level-title__index">Масштаб {level.index + 1}</div>
        <div className="level-title__name">{level.title}</div>
        <div className="level-title__sub">{level.subtitle}</div>
        <div className="level-title__desc">{level.description}</div>
        <div className="level-title__sim">
          <span className="level-title__sim-label">Симуляция</span>
          {level.simulation}
        </div>
      </div>
    </>
  )
}
