/**
 * Поиск по названию объекта. Ищет по всем уровням сразу и переключает
 * масштаб, если найденный объект живёт на другом уровне.
 *
 * Индекс строится один раз из тех же данных, что рисует сцена — так
 * не бывает расхождений между тем, что можно найти, и тем, что есть.
 */

import { useEffect, useMemo, useRef, useState } from 'react'
import { useStore } from '../store'
import { LEVELS, type LevelId } from '../data/levels'
import { SUN, PLANETS, DWARF_PLANETS } from '../data/planets'
import { MOONS } from '../data/moons'
import { STARS, NOTABLE_FAR_STARS } from '../data/stars'
import { LOCAL_GROUP, GALACTIC_OBJECTS, LARGE_STRUCTURES, MILKY_WAY } from '../data/galaxies'

interface Entry {
  id: string
  name: string
  nameEn?: string
  kind: string
  level: LevelId
  /** дополнительные слова для поиска: каталожные обозначения и т. п. */
  aliases?: string[]
}

function buildIndex(): Entry[] {
  const out: Entry[] = []

  out.push({ id: 'earth', name: 'Земля', nameEn: 'Earth', kind: 'Планета', level: 'earth' })
  out.push({ id: 'iss', name: 'Международная космическая станция', nameEn: 'ISS', kind: 'Аппарат', level: 'earth', aliases: ['МКС', 'ISS'] })
  out.push({ id: 'moon', name: 'Луна', nameEn: 'Moon', kind: 'Спутник', level: 'earth-moon' })

  out.push({ id: 'sun', name: SUN.name, nameEn: SUN.nameEn, kind: 'Звезда', level: 'solar-system' })
  for (const p of [...PLANETS, ...DWARF_PLANETS]) {
    out.push({
      id: p.id,
      name: p.name,
      nameEn: p.nameEn,
      kind: p.kind === 'dwarf' ? 'Карликовая планета' : 'Планета',
      level: 'solar-system',
    })
  }
  for (const m of MOONS) {
    if (m.id === 'moon') continue
    out.push({ id: m.id, name: m.name, nameEn: m.nameEn, kind: 'Спутник', level: 'solar-system' })
  }
  out.push({ id: 'main-belt', name: 'Главный пояс астероидов', kind: 'Область', level: 'solar-system' })
  out.push({ id: 'kuiper-belt', name: 'Пояс Койпера', kind: 'Область', level: 'solar-system' })
  out.push({ id: 'oort-cloud', name: 'Облако Оорта', kind: 'Область', level: 'solar-system' })

  for (const s of [...STARS, ...NOTABLE_FAR_STARS]) {
    if (s.id === 'sun') continue
    out.push({ id: s.id, name: s.name, nameEn: s.nameEn, kind: 'Звезда', level: 'nearby-stars' })
  }

  out.push({ id: 'milky-way', name: MILKY_WAY.name, nameEn: MILKY_WAY.nameEn, kind: 'Галактика', level: 'milky-way' })
  out.push({ id: 'sun-in-galaxy', name: 'Положение Солнца в Галактике', kind: 'Ориентир', level: 'milky-way' })
  for (const o of GALACTIC_OBJECTS) {
    out.push({
      id: o.id,
      name: o.name,
      nameEn: o.nameEn,
      kind: o.kind === 'center' ? 'Чёрная дыра' : o.kind === 'cluster' ? 'Скопление' : 'Туманность',
      level: 'milky-way',
      aliases: o.catalog ? [o.catalog] : undefined,
    })
  }

  for (const g of LOCAL_GROUP) {
    if (g.id === 'milky-way') continue
    out.push({
      id: g.id,
      name: g.name,
      nameEn: g.nameEn,
      kind: 'Галактика',
      level: 'local-group',
      aliases: g.catalog ? [g.catalog] : undefined,
    })
  }

  for (const s of LARGE_STRUCTURES) {
    out.push({
      id: s.id,
      name: s.name,
      nameEn: s.nameEn,
      kind: s.kind === 'cluster' ? 'Скопление' : s.kind === 'void' ? 'Войд' : s.kind === 'wall' ? 'Стена' : 'Сверхскопление',
      level: s.distMly >= 600 ? 'observable-universe' : 'laniakea',
      aliases: s.catalog ? [s.catalog] : undefined,
    })
  }
  out.push({ id: 'laniakea', name: 'Ланиакея', nameEn: 'Laniakea', kind: 'Сверхскопление', level: 'laniakea' })
  out.push({ id: 'cmb', name: 'Реликтовое излучение', nameEn: 'CMB', kind: 'Излучение', level: 'observable-universe', aliases: ['CMB', 'реликт'] })
  out.push({
    id: 'observable-universe',
    name: 'Наблюдаемая Вселенная',
    kind: 'Всё видимое',
    level: 'observable-universe',
  })

  return out
}

/** Нормализация: регистр, ё→е, дефисы — чтобы «Альфа Центавра» находилась как «альфа центавра». */
function norm(s: string): string {
  return s.toLowerCase().replace(/ё/g, 'е').replace(/[−–—]/g, '-').trim()
}

export function SearchOverlay() {
  const open = useStore((s) => s.searchOpen)
  const setOpen = useStore((s) => s.setSearchOpen)
  const setLevel = useStore((s) => s.setLevel)
  const setFocus = useStore((s) => s.setFocus)
  const levelIndex = useStore((s) => s.levelIndex)

  const [q, setQ] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const index = useMemo(buildIndex, [])

  useEffect(() => {
    if (open) {
      setQ('')
      // задержка нужна, чтобы фокус не украл анимацию появления
      const t = setTimeout(() => inputRef.current?.focus(), 60)
      return () => clearTimeout(t)
    }
  }, [open])

  const results = useMemo(() => {
    const nq = norm(q)
    if (!nq) {
      // без запроса показываем то, что есть на текущем уровне
      const cur = LEVELS[levelIndex].id
      return index.filter((e) => e.level === cur).slice(0, 24)
    }
    const scored = index
      .map((e) => {
        const haystacks = [e.name, e.nameEn ?? '', ...(e.aliases ?? [])].map(norm)
        let best = -1
        for (const h of haystacks) {
          if (!h) continue
          if (h === nq) best = Math.max(best, 100)
          else if (h.startsWith(nq)) best = Math.max(best, 70)
          else if (h.includes(nq)) best = Math.max(best, 40)
          else {
            // совпадение по началу любого слова: «стрел» найдёт «Стрелец A*»
            const words = h.split(/[\s-]+/)
            if (words.some((w) => w.startsWith(nq))) best = Math.max(best, 55)
          }
        }
        return { e, score: best }
      })
      .filter((r) => r.score > 0)
      .sort((a, b) => b.score - a.score || a.e.name.localeCompare(b.e.name, 'ru'))
    return scored.slice(0, 30).map((r) => r.e)
  }, [q, index, levelIndex])

  function go(e: Entry) {
    const target = LEVELS.findIndex((l) => l.id === e.level)
    setOpen(false)
    if (target >= 0 && target !== levelIndex) {
      setLevel(target)
      // после наезда камеры навести на объект: сцена к этому моменту
      // уже смонтирована и записала свои координаты в реестр
      setTimeout(() => setFocus(e.id), 1800)
    } else {
      setFocus(e.id)
    }
  }

  if (!open) return null

  return (
    <div className="search" onClick={() => setOpen(false)}>
      <div className="search__box" onClick={(ev) => ev.stopPropagation()}>
        <div className="search__field">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-4.5-4.5" />
          </svg>
          <input
            ref={inputRef}
            value={q}
            onChange={(ev) => setQ(ev.target.value)}
            onKeyDown={(ev) => {
              if (ev.key === 'Enter' && results[0]) go(results[0])
              if (ev.key === 'Escape') setOpen(false)
            }}
            placeholder="Планета, звезда, галактика, скопление…"
            aria-label="Поиск объекта"
          />
          {q && (
            <button className="search__clear" onClick={() => setQ('')} aria-label="Очистить">
              ✕
            </button>
          )}
        </div>

        <div className="search__results">
          {results.length === 0 && <div className="search__empty">Ничего не найдено</div>}
          {results.map((e) => {
            const lvl = LEVELS.find((l) => l.id === e.level)!
            return (
              <button key={`${e.level}:${e.id}`} className="search__item" onClick={() => go(e)}>
                <span className="search__item-name">{e.name}</span>
                <span className="search__item-meta">
                  <span className="search__item-kind">{e.kind}</span>
                  <span className="search__item-level">{lvl.title}</span>
                </span>
              </button>
            )
          })}
        </div>

        <div className="search__hint">
          {q ? `Найдено: ${results.length}` : `Объекты уровня «${LEVELS[levelIndex].title}»`} · Enter — перейти · Esc — закрыть
        </div>
      </div>
    </div>
  )
}
