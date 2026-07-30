/**
 * Реестр положений объектов для наведения камеры.
 *
 * Сцены каждый кадр записывают сюда мировые координаты своих объектов,
 * а камера читает их по id. Через React-состояние это гонять нельзя:
 * положения меняются каждый кадр, а перерисовка дерева на 60 Гц убьёт
 * производительность на телефоне.
 */

import * as THREE from 'three'

const positions = new Map<string, THREE.Vector3>()
const radii = new Map<string, number>()

export function registerFocus(id: string, pos: THREE.Vector3 | [number, number, number], radius = 0) {
  const v = positions.get(id) ?? new THREE.Vector3()
  if (Array.isArray(pos)) v.set(pos[0], pos[1], pos[2])
  else v.copy(pos)
  positions.set(id, v)
  radii.set(id, radius)
}

export function getFocusPosition(id: string): THREE.Vector3 | undefined {
  return positions.get(id)
}

export function getFocusRadius(id: string): number {
  return radii.get(id) ?? 0
}

/** Сбрасываем при переходе между уровнями — иначе останутся координаты чужой сцены. */
export function clearFocusRegistry() {
  positions.clear()
  radii.clear()
}
