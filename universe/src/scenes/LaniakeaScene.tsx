/**
 * Уровень 7 — Ланиакея и сверхскопление Девы.
 *
 * Честно о том, что здесь данные, а что реконструкция:
 *
 *  ДАННЫЕ — положения скоплений галактик: RA/Dec из каталогов Abell и NED,
 *  расстояния по красным смещениям и измерениям Tully-Fisher. Это настоящие
 *  узлы: Дева, Печь, Гидра, Центавр, Наугольник, Великий Аттрактор.
 *
 *  РЕКОНСТРУКЦИЯ — филаменты между узлами и рассеянные галактики. Готовых
 *  трёхмерных моделей сверхскоплений не существует; нити достраиваются
 *  процедурно от узла к узлу с гауссовым разбросом. Плотность подобрана
 *  так, чтобы соответствовать наблюдаемой доле галактик в филаментах (≈ 50 %),
 *  в скоплениях (≈ 10 %) и в поле/войдах (остальное).
 *
 *  Границу Ланиакеи задаёт поле скоростей, а не видимые галактики: это
 *  область, откуда всё падает к Великому Аттрактору (Tully et al. 2014).
 */

import { useMemo } from 'react'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'
import { useStore } from '../store'
import { LARGE_STRUCTURES, LANIAKEA, type LargeStructure } from '../data/galaxies'
import { raDecToXyz, LY_M } from '../lib/astro'
import { mulberry32, gaussian, hashString } from '../lib/rng'
import { Label } from '../components/Label'
import { registerFocus } from '../lib/focus'
import { GlowSprite, SoftDisc } from '../components/GlowSprite'

/** 1 единица сцены = 1 млн световых лет. */
function structurePos(s: LargeStructure): THREE.Vector3 {
  if (s.distMly === 0) return new THREE.Vector3(0, 0, 0)
  const [x, y, z] = raDecToXyz(s.ra, s.dec, s.distMly)
  return new THREE.Vector3(x, z, -y)
}

/** Какие узлы соединяем филаментами. Пары выбраны по известным связям в поле скоростей. */
const FILAMENTS: [string, string][] = [
  ['local-group', 'virgo-cluster'],
  ['local-group', 'sculptor-group'],
  ['local-group', 'm81-group'],
  ['local-group', 'cen-a-group'],
  ['cen-a-group', 'virgo-cluster'],
  ['virgo-cluster', 'fornax-cluster'],
  ['virgo-cluster', 'centaurus-cluster'],
  ['fornax-cluster', 'eridanus-cluster'],
  ['cen-a-group', 'centaurus-cluster'],
  ['centaurus-cluster', 'hydra-cluster'],
  ['hydra-cluster', 'antlia-cluster'],
  ['antlia-cluster', 'centaurus-cluster'],
  ['centaurus-cluster', 'norma-cluster'],
  ['norma-cluster', 'great-attractor'],
  ['hydra-cluster', 'great-attractor'],
  ['great-attractor', 'shapley-supercluster'],
  ['virgo-cluster', 'coma-cluster'],
]

export function LaniakeaScene() {
  const showLabels = useStore((s) => s.showLabels)
  const showOrbits = useStore((s) => s.showOrbits)
  const cameraDist = useStore((s) => s.cameraDist)
  const select = useStore((s) => s.select)
  const setFocus = useStore((s) => s.setFocus)

  const nodes = useMemo(
    () => LARGE_STRUCTURES.filter((s) => s.distMly < 800).map((s) => ({ s, pos: structurePos(s) })),
    [],
  )
  const nodeById = useMemo(() => new Map(nodes.map((n) => [n.s.id, n])), [nodes])

  // Галактики в филаментах и в узлах
  const cosmicPoints = useMemo(() => buildFilaments(nodeById), [nodeById])

  // Линии филаментов — тонкая подсветка структуры
  const filamentLines = useMemo(() => {
    const pts: THREE.Vector3[] = []
    for (const [a, b] of FILAMENTS) {
      const na = nodeById.get(a)
      const nb = nodeById.get(b)
      if (!na || !nb) continue
      pts.push(na.pos, nb.pos)
    }
    return new THREE.BufferGeometry().setFromPoints(pts)
  }, [nodeById])

  const pointMat = useMemo(
    () =>
      new THREE.ShaderMaterial({
        transparent: true,
        depthWrite: false,
        vertexColors: true,
        blending: THREE.AdditiveBlending,
        vertexShader: /* glsl */ `
          attribute float aSize;
          varying vec3 vColor;
          void main() {
            vColor = color;
            vec4 mv = modelViewMatrix * vec4(position, 1.0);
            gl_Position = projectionMatrix * mv;
            gl_PointSize = clamp(aSize * 340.0 / -mv.z, 0.7, 5.5);
          }
        `,
        fragmentShader: /* glsl */ `
          varying vec3 vColor;
          void main() {
            vec2 c = gl_PointCoord - 0.5;
            float d = length(c);
            if (d > 0.5) discard;
            gl_FragColor = vec4(vColor, exp(-d * d * 9.0) * 0.85);
          }
        `,
      }),
    [],
  )

  function pickLaniakea() {
    setFocus(null)
    select({
      id: 'laniakea',
      name: LANIAKEA.name,
      kind: 'Сверхскопление',
      blurb: LANIAKEA.note,
      facts: LANIAKEA.facts,
      distanceM: 0,
      source: LANIAKEA.source,
      artistic: true,
    })
  }

  return (
    <group>
      <ambientLight intensity={1} />

      <points geometry={cosmicPoints} material={pointMat} onClick={pickLaniakea} />

      {showOrbits && (
        // @ts-expect-error примитив line из three, типы R3F его не покрывают
        <line geometry={filamentLines}>
          <lineBasicMaterial color="#5577aa" transparent opacity={0.18} />
        </line>
      )}

      {/* Условная граница Ланиакеи: сфера радиусом 260 млн св. лет,
          смещённая к Великому Аттрактору. Настоящая граница неправильной
          формы и определена по расходимости поля скоростей — сфера здесь
          только как масштабный ориентир. */}
      <LaniakeaBoundary nodeById={nodeById} onClick={pickLaniakea} />

      {nodes.map(({ s, pos }) => (
        <StructureNode
          key={s.id}
          s={s}
          pos={pos}
          showLabel={showLabels && (s.kind !== 'group' || cameraDist < 900 || s.id === 'local-group')}
          onPick={() => {
            setFocus(s.id)
            const facts: { label: string; value: string }[] = [
              { label: 'Расстояние', value: s.distMly === 0 ? '—' : `${s.distMly} млн св. лет` },
              { label: 'Размер', value: `${s.sizeMly} млн св. лет` },
            ]
            if (s.massSun) {
              facts.push({ label: 'Масса', value: `${s.massSun.toExponential(1).replace('e+', ' × 10^')} M☉` })
            }
            if (s.galaxyCount) facts.push({ label: 'Галактик', value: s.galaxyCount })
            if (s.catalog) facts.push({ label: 'Каталог', value: s.catalog })
            facts.push({ label: 'Входит в Ланиакею', value: s.inLaniakea ? 'да' : 'нет' })

            select({
              id: s.id,
              name: s.name,
              kind:
                s.kind === 'cluster' ? 'Скопление галактик'
                : s.kind === 'supercluster' ? 'Сверхскопление'
                : s.kind === 'group' ? 'Группа галактик'
                : s.kind === 'void' ? 'Войд'
                : s.kind === 'wall' ? 'Стена галактик'
                : 'Гравитационная аномалия',
              blurb: s.note,
              facts,
              distanceM: s.distMly * 1e6 * LY_M,
              source: 'Каталоги Abell / NED; расстояния по красным смещениям. Нити — процедурная реконструкция',
              artistic: true,
            })
          }}
        />
      ))}
    </group>
  )
}

function LaniakeaBoundary({
  nodeById,
  onClick,
}: {
  nodeById: Map<string, { s: LargeStructure; pos: THREE.Vector3 }>
  onClick: () => void
}) {
  const ga = nodeById.get('great-attractor')
  const center = useMemo(() => (ga ? ga.pos.clone().multiplyScalar(0.45) : new THREE.Vector3()), [ga])
  return (
    <mesh position={center} onClick={onClick}>
      <sphereGeometry args={[LANIAKEA.diameterMly / 2, 28, 20]} />
      <meshBasicMaterial color="#4488cc" wireframe transparent opacity={0.05} />
    </mesh>
  )
}

function StructureNode({
  s,
  pos,
  showLabel,
  onPick,
}: {
  s: LargeStructure
  pos: THREE.Vector3
  showLabel: boolean
  onPick: () => void
}) {
  const color =
    s.kind === 'attractor' ? '#ff7744'
    : s.kind === 'supercluster' ? '#ffcc66'
    : s.kind === 'cluster' ? '#ffe8b0'
    : s.kind === 'void' ? '#5577aa'
    : s.kind === 'wall' ? '#88ccff'
    : '#bbddff'

  // Экранный размер узла: по массе, если известна, иначе по заявленному размеру
  const r = s.massSun ? Math.pow(s.massSun / 1e14, 0.22) * 7 : s.sizeMly * 0.35

  useFrame(() => {
    registerFocus(s.id, pos, r * 2)
  })

  return (
    <group position={pos}>
      {s.kind === 'void' ? (
        // Войд — не источник света, а отсутствие галактик: показываем контуром
        <SoftDisc color={color} size={s.sizeMly * 0.5} opacity={0.5} onClick={onPick as never} />
      ) : (
        <GlowSprite
          color={color}
          size={r * 1.9}
          intensity={s.kind === 'attractor' ? 1 : 0.75}
          hardness={s.kind === 'cluster' ? 7 : 4.5}
          spikes={s.kind === 'attractor'}
          onClick={onPick as never}
        />
      )}
      {showLabel && (
        <Label
          position={[0, r * 1.1 + 4, 0]}
          text={s.name}
          sub={s.distMly > 0 ? `${s.distMly} млн св. лет` : 'мы здесь'}
          small={s.kind === 'group'}
          onClick={onPick}
        />
      )}
    </group>
  )
}

/**
 * Галактики: часть в узлах (скоплениях), часть вдоль филаментов,
 * часть в поле. Пропорции — по наблюдаемой доле населения структур.
 */
function buildFilaments(
  nodeById: Map<string, { s: LargeStructure; pos: THREE.Vector3 }>,
): THREE.BufferGeometry {
  const rnd = mulberry32(hashString('laniakea-web'))
  const pos: number[] = []
  const col: number[] = []
  const size: number[] = []

  const cCluster = new THREE.Color('#ffdca8')
  const cFilament = new THREE.Color('#a8c4ff')
  const cField = new THREE.Color('#7788aa')

  // 1. Скопления: концентрация с профилем ~ r^-2
  for (const { s, pos: p } of nodeById.values()) {
    if (s.kind === 'void') continue
    const n = s.kind === 'cluster' ? 900 : s.kind === 'supercluster' ? 1500 : s.kind === 'attractor' ? 1200 : 250
    const scale = s.sizeMly * 0.5
    for (let i = 0; i < n; i++) {
      const r = scale * Math.pow(rnd(), 1.8)
      const z = rnd() * 2 - 1
      const t = rnd() * Math.PI * 2
      const sr = Math.sqrt(1 - z * z)
      pos.push(p.x + r * sr * Math.cos(t), p.y + r * z, p.z + r * sr * Math.sin(t))
      const b = 0.45 + Math.pow(rnd(), 1.6) * 0.8
      col.push(cCluster.r * b, cCluster.g * b, cCluster.b * b)
      size.push(0.5 + Math.pow(rnd(), 3) * 2.2)
    }
  }

  // 2. Филаменты: галактики вдоль отрезков между узлами, с разбросом поперёк
  for (const [a, b] of FILAMENTS) {
    const na = nodeById.get(a)
    const nb = nodeById.get(b)
    if (!na || !nb) continue
    const len = na.pos.distanceTo(nb.pos)
    const n = Math.round(Math.min(1400, len * 5))
    // радиус нити: наблюдаемые филаменты — цилиндры радиусом порядка
    // 5–10 Мпк, то есть 16–33 млн св. лет
    const tubeR = 12
    for (let i = 0; i < n; i++) {
      const t = rnd()
      const base = na.pos.clone().lerp(nb.pos, t)
      // изгиб нити: реальные филаменты не прямые
      const bend = Math.sin(t * Math.PI) * 0.12 * len
      const perp = new THREE.Vector3(
        gaussian(rnd),
        gaussian(rnd),
        gaussian(rnd),
      ).normalize()
      base.addScaledVector(perp, bend * (rnd() - 0.5))
      // разброс поперёк, гуще к оси
      const off = new THREE.Vector3(gaussian(rnd), gaussian(rnd), gaussian(rnd)).multiplyScalar(
        tubeR * 0.45 * (0.35 + Math.pow(rnd(), 2)),
      )
      base.add(off)
      pos.push(base.x, base.y, base.z)
      const br = 0.3 + Math.pow(rnd(), 2) * 0.7
      col.push(cFilament.r * br, cFilament.g * br, cFilament.b * br)
      size.push(0.4 + Math.pow(rnd(), 3.4) * 1.7)
    }
  }

  // 3. Поле: разряжённые галактики, из войдов исключены
  const voids = [...nodeById.values()].filter((n) => n.s.kind === 'void')
  const R = 700
  for (let i = 0; i < 5000; i++) {
    const r = R * Math.pow(rnd(), 1 / 3)
    const z = rnd() * 2 - 1
    const t = rnd() * Math.PI * 2
    const sr = Math.sqrt(1 - z * z)
    const p = new THREE.Vector3(r * sr * Math.cos(t), r * z, r * sr * Math.sin(t))
    // выкидываем то, что попало в известный войд
    let inVoid = false
    for (const v of voids) {
      if (p.distanceTo(v.pos) < v.s.sizeMly * 0.5) {
        inVoid = true
        break
      }
    }
    if (inVoid && rnd() < 0.94) continue
    pos.push(p.x, p.y, p.z)
    const b = 0.16 + Math.pow(rnd(), 2.4) * 0.4
    col.push(cField.r * b, cField.g * b, cField.b * b)
    size.push(0.35 + Math.pow(rnd(), 4) * 1.1)
  }

  const g = new THREE.BufferGeometry()
  g.setAttribute('position', new THREE.BufferAttribute(new Float32Array(pos), 3))
  g.setAttribute('color', new THREE.BufferAttribute(new Float32Array(col), 3))
  g.setAttribute('aSize', new THREE.BufferAttribute(new Float32Array(size), 1))
  return g
}
