/**
 * Запускает разведение подписей. Живёт внутри канваса, потому что ему
 * нужна камера и размер вьюпорта.
 *
 * Прореживание: подписи разводятся не каждый кадр, а раз в ~100 мс.
 * Положения меняются плавно, и глаз разницы не заметит, а проекция сорока
 * якорей с сортировкой каждый кадр — лишняя работа на телефоне.
 */

import { useEffect, useRef } from 'react'
import { useFrame, useThree } from '@react-three/fiber'
import { declutter, refreshReservedAreas } from '../lib/labels'
import { useStore } from '../store'

export function LabelDeclutter() {
  const size = useThree((s) => s.size)
  const acc = useRef(0)

  // Запретные зоны зависят от того, какие панели сейчас на экране
  const selected = useStore((s) => s.selected)
  const levelIndex = useStore((s) => s.levelIndex)

  useEffect(() => {
    // Ждём кадр: панель фактов появляется с анимацией, и её итоговый
    // прямоугольник известен только после первой отрисовки
    const t = setTimeout(refreshReservedAreas, 60)
    return () => clearTimeout(t)
  }, [selected, levelIndex, size.width, size.height])

  useFrame(({ camera }, dt) => {
    acc.current += dt
    if (acc.current < 0.1) return
    acc.current = 0
    declutter(camera, size.width, size.height)
  })

  return null
}
