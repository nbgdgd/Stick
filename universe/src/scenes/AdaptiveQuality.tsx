/**
 * Автоматическое снижение качества на слабом устройстве.
 *
 * Зачем: жесты ощущаются вязкими не из-за обработки ввода, а из-за
 * частоты кадров. При 15 кадрах в секунду камера догоняет палец с
 * задержкой в 70 мс, и это читается как «плохое управление». Дешевле
 * убрать то, что стоит дорого и даёт мало, чем оптимизировать ввод.
 *
 * Что снимается по очереди, от самого дорогого к самому дешёвому:
 *  1. масштаб отрисовки (dpr) — квадратичная экономия по пикселям;
 *  2. bloom — полноэкранный проход с несколькими mip-уровнями.
 *
 * Решение принимается по медиане времени кадра, а не по среднему:
 * одиночная просадка при загрузке текстуры не должна сбрасывать качество.
 */

import { useEffect, useRef } from 'react'
import { useFrame, useThree } from '@react-three/fiber'
import { useStore } from '../store'

/** Сколько кадров копим перед решением. */
const WINDOW = 90
/** Медиана выше этого (мс) — устройство не тянет текущее качество. */
const SLOW_MS = 26
/** Медиана ниже этого — есть запас, можно вернуть качество. */
const FAST_MS = 13

export function AdaptiveQuality() {
  const setDpr = useThree((s) => s.setDpr)
  const quality = useStore((s) => s.quality)
  const setQuality = useStore((s) => s.setQuality)

  const times = useRef<number[]>([])
  const cooldown = useRef(0)

  useEffect(() => {
    // Базовый масштаб отрисовки. Выше 1,75 смысла нет даже на хорошем
    // телефоне: сцена состоит из точек и градиентов, а не из мелкого текста.
    setDpr(quality === 'low' ? 1 : quality === 'medium' ? 1.35 : 1.75)
  }, [quality, setDpr])

  useFrame((_, dt) => {
    if (cooldown.current > 0) {
      cooldown.current -= dt
      return
    }
    times.current.push(dt * 1000)
    if (times.current.length < WINDOW) return

    const sorted = times.current.slice().sort((a, b) => a - b)
    const median = sorted[Math.floor(sorted.length / 2)]
    times.current.length = 0

    if (median > SLOW_MS) {
      if (quality === 'high') setQuality('medium')
      else if (quality === 'medium') setQuality('low')
      // После смены качества даём кадрам устаканиться, иначе замер
      // поймает всплеск от пересоздания рендер-таргетов
      cooldown.current = 1.5
    } else if (median < FAST_MS && quality !== 'high') {
      // Возвращаем осторожно и только на ступень вверх
      setQuality(quality === 'low' ? 'medium' : 'high')
      cooldown.current = 3
    }
  })

  return null
}
