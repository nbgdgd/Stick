/**
 * Вспышка на стыке уровней и короткий заголовок нового масштаба.
 *
 * Функциональная задача — прикрыть кадр, в котором старая сцена уже
 * размонтирована, а новая ещё грузит текстуры.
 *
 * Раньше здесь же показывались описание уровня и блок «Симуляция». На
 * телефоне это была стена текста поверх звёзд, которая налезала на подписи
 * объектов. Описание переехало в панель фактов уровня (кнопка в шапке),
 * а здесь осталось только название — его достаточно, чтобы понять,
 * куда переместились.
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
    const t = setTimeout(() => setShowTitle(false), 1700)
    return () => clearTimeout(t)
  }, [levelIndex])

  return (
    <>
      <div className={`veil${transitioning ? ' veil--on' : ''}`} />
      <div className={`level-title${showTitle ? ' level-title--on' : ''}`}>
        <div className="level-title__index">Масштаб {level.index + 1} из {LEVELS.length}</div>
        <div className="level-title__name">{level.title}</div>
        <div className="level-title__sub">{level.subtitle}</div>
      </div>
    </>
  )
}
