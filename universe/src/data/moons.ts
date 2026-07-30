/**
 * Значимые спутники планет. Элементы орбит и физические параметры —
 * NASA Planetary Satellite Physical/Mean Orbital Parameters (JPL SSD).
 *
 * Элементы даны относительно планеты; для крупных спутников газовых гигантов
 * это оскулирующие элементы, усреднённые по эпохе — для визуализации хватает.
 */

export interface Moon {
  id: string
  name: string
  nameEn: string
  planetId: string
  /** большая полуось орбиты, км */
  aKm: number
  /** эксцентриситет */
  e: number
  /** наклонение к экватору планеты, град */
  i: number
  /** сидерический период обращения, суток (отрицательный = обратное движение) */
  periodDays: number
  /** средний радиус, км */
  radiusKm: number
  massKg: number
  /** приливно захвачен (синхронное вращение) */
  tidallyLocked: boolean
  texture?: string
  color: string
  blurb: string
  facts: { label: string; value: string }[]
  source: string
}

export const MOONS: Moon[] = [
  {
    id: 'moon',
    name: 'Луна',
    nameEn: 'Moon',
    planetId: 'earth',
    aKm: 384399,
    e: 0.0549,
    i: 5.145,
    periodDays: 27.321661,
    radiusKm: 1737.4,
    massKg: 7.342e22,
    tidallyLocked: true,
    texture: '/tex/2k_moon.jpg',
    color: '#b8b2a8',
    blurb:
      'Пятый по размеру спутник в Солнечной системе и самый крупный относительно своей планеты. ' +
      'Приливный захват означает, что она всегда обращена к Земле одной стороной. Удаляется на 3,8 см в год.',
    facts: [
      { label: 'Расстояние от Земли', value: '384 400 км (в среднем)' },
      { label: 'Период обращения', value: '27,32 суток (сидерический)' },
      { label: 'Синодический месяц', value: '29,53 суток — цикл фаз' },
      { label: 'Диаметр', value: '3 474 км — 27 % от земного' },
      { label: 'Масса', value: '1/81 массы Земли' },
      { label: 'Наклон орбиты', value: '5,145° к эклиптике' },
      { label: 'Удаление от Земли', value: '3,8 см в год' },
      { label: 'Гравитация', value: '1,62 м/с² (0,166 g)' },
      { label: 'Температура', value: 'от −173 до +127 °C' },
    ],
    source: 'NASA Moon Fact Sheet; текстура — solarsystemscope.com по данным LRO (CC BY 4.0)',
  },
  // --- Марс ---
  {
    id: 'phobos',
    name: 'Фобос',
    nameEn: 'Phobos',
    planetId: 'mars',
    aKm: 9376,
    e: 0.0151,
    i: 1.075,
    periodDays: 0.31891,
    radiusKm: 11.267,
    massKg: 1.0659e16,
    tidallyLocked: true,
    color: '#7a6d63',
    blurb:
      'Обращается быстрее, чем Марс вращается, поэтому восходит на западе и садится на востоке — ' +
      'дважды за марсианские сутки. Приливные силы опускают его на 1,8 см за год: через ~50 млн лет он разрушится.',
    facts: [
      { label: 'Расстояние от Марса', value: '9 376 км' },
      { label: 'Период обращения', value: '7 ч 39 мин' },
      { label: 'Размер', value: '27 × 22 × 18 км' },
      { label: 'Судьба', value: 'разрушится приливами через ≈ 50 млн лет' },
    ],
    source: 'JPL SSD Planetary Satellite Parameters',
  },
  {
    id: 'deimos',
    name: 'Деймос',
    nameEn: 'Deimos',
    planetId: 'mars',
    aKm: 23463,
    e: 0.0002,
    i: 1.788,
    periodDays: 1.26244,
    radiusKm: 6.2,
    massKg: 1.4762e15,
    tidallyLocked: true,
    color: '#8a7d70',
    blurb: 'Меньший и более далёкий спутник Марса, всего 12 км в поперечнике. Медленно удаляется от планеты.',
    facts: [
      { label: 'Расстояние от Марса', value: '23 463 км' },
      { label: 'Период обращения', value: '30,3 часа' },
      { label: 'Размер', value: '15 × 12 × 11 км' },
    ],
    source: 'JPL SSD Planetary Satellite Parameters',
  },
  // --- Юпитер: галилеевы спутники ---
  {
    id: 'io',
    name: 'Ио',
    nameEn: 'Io',
    planetId: 'jupiter',
    aKm: 421800,
    e: 0.0041,
    i: 0.036,
    periodDays: 1.769138,
    radiusKm: 1821.6,
    massKg: 8.931938e22,
    tidallyLocked: true,
    color: '#e8d97a',
    blurb:
      'Самое вулканически активное тело Солнечной системы: более 400 действующих вулканов, ' +
      'выбросы поднимаются на 500 км. Причина — приливный разогрев от резонанса с Европой и Ганимедом.',
    facts: [
      { label: 'Расстояние от Юпитера', value: '421 800 км' },
      { label: 'Период обращения', value: '1,77 суток' },
      { label: 'Диаметр', value: '3 643 км' },
      { label: 'Вулканы', value: '> 400 действующих' },
      { label: 'Резонанс', value: '1:2:4 с Европой и Ганимедом' },
      { label: 'Приливный разогрев', value: 'поверхность деформируется на 100 м' },
    ],
    source: 'NASA Galileo/Juno; JPL SSD. Поверхность — процедурный шейдер',
  },
  {
    id: 'europa',
    name: 'Европа',
    nameEn: 'Europa',
    planetId: 'jupiter',
    aKm: 671100,
    e: 0.0094,
    i: 0.466,
    periodDays: 3.551181,
    radiusKm: 1560.8,
    massKg: 4.799844e22,
    tidallyLocked: true,
    color: '#d8cfc0',
    blurb:
      'Под ледяной корой 15–25 км скрывается океан жидкой воды глубиной до 100 км — воды там ' +
      'вдвое больше, чем во всех океанах Земли. Один из главных кандидатов на поиск жизни.',
    facts: [
      { label: 'Расстояние от Юпитера', value: '671 100 км' },
      { label: 'Период обращения', value: '3,55 суток' },
      { label: 'Диаметр', value: '3 122 км' },
      { label: 'Толщина льда', value: '15–25 км' },
      { label: 'Глубина океана', value: 'до 100 км' },
      { label: 'Объём воды', value: '≈ 2× все океаны Земли' },
      { label: 'Миссия', value: 'Europa Clipper (запуск 2024, прибытие 2030)' },
    ],
    source: 'NASA Europa Clipper science pages; JPL SSD. Поверхность — процедурный шейдер',
  },
  {
    id: 'ganymede',
    name: 'Ганимед',
    nameEn: 'Ganymede',
    planetId: 'jupiter',
    aKm: 1070400,
    e: 0.0013,
    i: 0.177,
    periodDays: 7.154553,
    radiusKm: 2634.1,
    massKg: 1.4819e23,
    tidallyLocked: true,
    color: '#9a9186',
    blurb:
      'Крупнейший спутник Солнечной системы — больше Меркурия. Единственный спутник с собственной ' +
      'магнитосферой, порождённой жидким железным ядром.',
    facts: [
      { label: 'Расстояние от Юпитера', value: '1 070 400 км' },
      { label: 'Период обращения', value: '7,15 суток' },
      { label: 'Диаметр', value: '5 268 км — больше Меркурия' },
      { label: 'Особенность', value: 'единственный спутник с магнитосферой' },
      { label: 'Подповерхностный океан', value: 'солёная вода на глубине ≈ 150 км' },
      { label: 'Миссия', value: 'ESA JUICE (прибытие 2031)' },
    ],
    source: 'NASA/ESA JUICE; JPL SSD. Поверхность — процедурный шейдер',
  },
  {
    id: 'callisto',
    name: 'Каллисто',
    nameEn: 'Callisto',
    planetId: 'jupiter',
    aKm: 1882700,
    e: 0.0074,
    i: 0.192,
    periodDays: 16.689017,
    radiusKm: 2410.3,
    massKg: 1.075938e23,
    tidallyLocked: true,
    color: '#6e6459',
    blurb:
      'Самая старая и сильнее всех покрытая кратерами поверхность в Солнечной системе — ' +
      'геологически мертва около 4 млрд лет. Вне основного радиационного пояса Юпитера.',
    facts: [
      { label: 'Расстояние от Юпитера', value: '1 882 700 км' },
      { label: 'Период обращения', value: '16,69 суток' },
      { label: 'Диаметр', value: '4 821 км' },
      { label: 'Возраст поверхности', value: '≈ 4 млрд лет' },
      { label: 'Радиация', value: 'низкая — вне главного пояса Юпитера' },
    ],
    source: 'JPL SSD Planetary Satellite Parameters. Поверхность — процедурный шейдер',
  },
  // --- Сатурн ---
  {
    id: 'titan',
    name: 'Титан',
    nameEn: 'Titan',
    planetId: 'saturn',
    aKm: 1221870,
    e: 0.0288,
    i: 0.28,
    periodDays: 15.945421,
    radiusKm: 2574.7,
    massKg: 1.3452e23,
    tidallyLocked: true,
    color: '#d9a555',
    blurb:
      'Единственный спутник с плотной атмосферой (1,5 атм, азот) и единственное тело кроме Земли ' +
      'с постоянными жидкими водоёмами на поверхности — озёрами из метана и этана.',
    facts: [
      { label: 'Расстояние от Сатурна', value: '1 221 870 км' },
      { label: 'Период обращения', value: '15,95 суток' },
      { label: 'Диаметр', value: '5 149 км — второй по размеру спутник' },
      { label: 'Атмосфера', value: 'N₂ 94 %, CH₄ 5 %, давление 1,5 атм' },
      { label: 'Температура', value: '−179 °C' },
      { label: 'Жидкость на поверхности', value: 'озёра метана и этана' },
      { label: 'Посадка', value: 'зонд Huygens, 14 января 2005' },
      { label: 'Миссия', value: 'NASA Dragonfly (запуск 2028)' },
    ],
    source: 'NASA Cassini-Huygens; JPL SSD. Поверхность — процедурный шейдер',
  },
  {
    id: 'enceladus',
    name: 'Энцелад',
    nameEn: 'Enceladus',
    planetId: 'saturn',
    aKm: 238040,
    e: 0.0047,
    i: 0.009,
    periodDays: 1.370218,
    radiusKm: 252.1,
    massKg: 1.08022e20,
    tidallyLocked: true,
    color: '#f0f4f5',
    blurb:
      'Из трещин у южного полюса бьют гейзеры водяного пара, питающие кольцо E Сатурна. ' +
      'Cassini пролетел через них и обнаружил соли, кремнезём и органику — признаки гидротермальной активности.',
    facts: [
      { label: 'Расстояние от Сатурна', value: '238 040 км' },
      { label: 'Период обращения', value: '1,37 суток' },
      { label: 'Диаметр', value: '504 км' },
      { label: 'Альбедо', value: '0,81 — самая отражающая поверхность системы' },
      { label: 'Гейзеры', value: '> 100 струй у южного полюса' },
      { label: 'Океан', value: 'глобальный, под 20–25 км льда' },
      { label: 'В струях найдено', value: 'соли, SiO₂, метан, органика' },
    ],
    source: 'NASA Cassini; JPL SSD. Поверхность — процедурный шейдер',
  },
  {
    id: 'mimas',
    name: 'Мимас',
    nameEn: 'Mimas',
    planetId: 'saturn',
    aKm: 185540,
    e: 0.0196,
    i: 1.574,
    periodDays: 0.942422,
    radiusKm: 198.2,
    massKg: 3.7493e19,
    tidallyLocked: true,
    color: '#c8c4bd',
    blurb:
      'Кратер Гершель диаметром 139 км занимает треть поперечника спутника — из-за него Мимас ' +
      'похож на «Звезду смерти». Удар почти расколол его.',
    facts: [
      { label: 'Расстояние от Сатурна', value: '185 540 км' },
      { label: 'Период обращения', value: '22,6 часа' },
      { label: 'Диаметр', value: '396 км' },
      { label: 'Кратер Гершель', value: '139 км — треть диаметра' },
    ],
    source: 'JPL SSD Planetary Satellite Parameters. Поверхность — процедурный шейдер',
  },
  {
    id: 'iapetus',
    name: 'Япет',
    nameEn: 'Iapetus',
    planetId: 'saturn',
    aKm: 3560840,
    e: 0.0283,
    i: 15.47,
    periodDays: 79.3215,
    radiusKm: 734.5,
    massKg: 1.8056e21,
    tidallyLocked: true,
    color: '#8f8577',
    blurb:
      'Двухцветный: ведущая сторона почти чёрная (альбедо 0,05), ведомая — яркая ледяная (0,5). ' +
      'По экватору тянется загадочный хребет высотой до 20 км.',
    facts: [
      { label: 'Расстояние от Сатурна', value: '3 560 840 км' },
      { label: 'Период обращения', value: '79,3 суток' },
      { label: 'Диаметр', value: '1 469 км' },
      { label: 'Контраст сторон', value: 'альбедо 0,05 против 0,5' },
      { label: 'Экваториальный хребет', value: 'высота до 20 км, длина 1 300 км' },
    ],
    source: 'NASA Cassini; JPL SSD. Поверхность — процедурный шейдер',
  },
  // --- Уран ---
  {
    id: 'titania',
    name: 'Титания',
    nameEn: 'Titania',
    planetId: 'uranus',
    aKm: 435910,
    e: 0.0011,
    i: 0.34,
    periodDays: 8.706234,
    radiusKm: 788.4,
    massKg: 3.4e21,
    tidallyLocked: true,
    color: '#9c918a',
    blurb:
      'Крупнейший спутник Урана. Поверхность разбита каньонами — грабенами; ' +
      'Мессина Хазма тянется почти на 1 500 км.',
    facts: [
      { label: 'Расстояние от Урана', value: '435 910 км' },
      { label: 'Период обращения', value: '8,71 суток' },
      { label: 'Диаметр', value: '1 577 км' },
      { label: 'Мессина Хазма', value: 'каньон длиной ≈ 1 500 км' },
    ],
    source: 'JPL SSD; NASA Voyager 2. Поверхность — процедурный шейдер',
  },
  {
    id: 'miranda',
    name: 'Миранда',
    nameEn: 'Miranda',
    planetId: 'uranus',
    aKm: 129390,
    e: 0.0013,
    i: 4.232,
    periodDays: 1.413479,
    radiusKm: 235.8,
    massKg: 6.59e19,
    tidallyLocked: true,
    color: '#a9a29b',
    blurb:
      'Самый геологически странный спутник Урана: обрыв Верона Рупес высотой до 20 км — ' +
      'самый высокий известный уступ в Солнечной системе. Возможно, спутник разрушался и собирался заново.',
    facts: [
      { label: 'Расстояние от Урана', value: '129 390 км' },
      { label: 'Период обращения', value: '1,41 суток' },
      { label: 'Диаметр', value: '472 км' },
      { label: 'Верона Рупес', value: 'уступ высотой до 20 км' },
    ],
    source: 'NASA Voyager 2; JPL SSD. Поверхность — процедурный шейдер',
  },
  // --- Нептун ---
  {
    id: 'triton',
    name: 'Тритон',
    nameEn: 'Triton',
    planetId: 'neptune',
    aKm: 354759,
    e: 0.000016,
    i: 156.865,
    periodDays: -5.876854, // обратное движение
    radiusKm: 1353.4,
    massKg: 2.139e22,
    tidallyLocked: true,
    color: '#c5b8b0',
    blurb:
      'Единственный крупный спутник с обратным движением по орбите — почти наверняка захваченный ' +
      'объект пояса Койпера. Криовулканы выбрасывают азот; орбита медленно снижается.',
    facts: [
      { label: 'Расстояние от Нептуна', value: '354 759 км' },
      { label: 'Период обращения', value: '5,88 суток, обратное' },
      { label: 'Диаметр', value: '2 707 км' },
      { label: 'Наклонение орбиты', value: '157° — движется против вращения Нептуна' },
      { label: 'Температура', value: '−235 °C' },
      { label: 'Происхождение', value: 'вероятно, захваченный объект пояса Койпера' },
      { label: 'Судьба', value: 'разрушится приливами через 10–100 млн лет' },
    ],
    source: 'NASA Voyager 2; JPL SSD. Поверхность — процедурный шейдер',
  },
  // --- Плутон ---
  {
    id: 'charon',
    name: 'Харон',
    nameEn: 'Charon',
    planetId: 'pluto',
    aKm: 19591,
    e: 0.0002,
    i: 0.08,
    periodDays: 6.3872,
    radiusKm: 606,
    massKg: 1.586e21,
    tidallyLocked: true,
    color: '#a89b93',
    blurb:
      'Настолько велик относительно Плутона (половина диаметра), что оба тела обращаются вокруг ' +
      'общего центра масс вне Плутона — это двойная система с взаимным приливным захватом.',
    facts: [
      { label: 'Расстояние от Плутона', value: '19 591 км' },
      { label: 'Период обращения', value: '6,39 суток' },
      { label: 'Диаметр', value: '1 212 км — половина Плутона' },
      { label: 'Особенность', value: 'взаимный приливный захват, барицентр вне Плутона' },
    ],
    source: 'NASA New Horizons; JPL SSD. Поверхность — процедурный шейдер',
  },
]

export function moonsOf(planetId: string): Moon[] {
  return MOONS.filter((m) => m.planetId === planetId)
}
