/**
 * Млечный Путь, Местная группа и крупные структуры за её пределами.
 *
 * Координаты RA/Dec — J2000, расстояния — по публикациям с цефеидами,
 * TRGB (вершина ветви красных гигантов) и сверхновыми Ia; сводка —
 * NASA/IPAC Extragalactic Database (NED) и каталог Местной группы
 * McConnachie (2012), Astronomical Journal 144, 4.
 */

import { hms, dms } from '../lib/astro'

export interface Galaxy {
  id: string
  name: string
  nameEn: string
  /** обозначение в каталогах */
  catalog?: string
  ra: number
  dec: number
  /** расстояние, световые годы */
  distLy: number
  /** морфологический тип */
  type: string
  /** диаметр, световые годы */
  diameterLy: number
  /** звёздная масса, в массах Солнца */
  massSun?: number
  /** абсолютная величина */
  absMag?: number
  /** гравитационно связана с Млечным Путём или M31 */
  satelliteOf?: 'milky-way' | 'm31' | 'm33'
  note?: string
}

/** Параметры Млечного Пути — для процедурной модели диска. */
export const MILKY_WAY = {
  id: 'milky-way',
  name: 'Млечный Путь',
  nameEn: 'Milky Way',
  /** диаметр звёздного диска, св. лет (оценка по данным Gaia) */
  diskDiameterLy: 100000,
  /** толщина тонкого диска, св. лет */
  thinDiskThicknessLy: 1000,
  /** толщина толстого диска, св. лет */
  thickDiskThicknessLy: 3500,
  /** радиус балджа, св. лет */
  bulgeRadiusLy: 10000,
  /** расстояние Солнца от центра, св. лет (GRAVITY 2019: 8,178 кпк) */
  sunDistanceLy: 26670,
  /** число звёзд, оценка */
  starCount: '100–400 млрд',
  /** масса, включая тёмную материю */
  totalMassSun: 1.5e12,
  /** число спиральных рукавов в принятой модели */
  arms: 4,
  facts: [
    { label: 'Тип', value: 'SBbc — спираль с барной перемычкой' },
    { label: 'Диаметр звёздного диска', value: '≈ 100 000 св. лет' },
    { label: 'Толщина тонкого диска', value: '≈ 1 000 св. лет' },
    { label: 'Звёзд', value: '100–400 млрд' },
    { label: 'Масса с тёмной материей', value: '≈ 1,5 × 10¹² M☉' },
    { label: 'Солнце от центра', value: '26 670 св. лет (8,178 кпк)' },
    { label: 'Галактический год', value: '225–250 млн лет' },
    { label: 'Скорость Солнца по орбите', value: '≈ 230 км/с' },
    { label: 'Возраст', value: '≈ 13,6 млрд лет (старейшие звёзды)' },
  ],
  source:
    'Gaia DR3; GRAVITY Collaboration (2019) — расстояние до Sgr A*; ' +
    'модель рукавов по Reid et al. (2019), VLBA-параллаксы областей звездообразования',
}

/** Спиральные рукава Млечного Пути: логарифмические спирали по VLBA-параллаксам (Reid et al. 2019). */
export interface SpiralArm {
  id: string
  name: string
  /** начальный галактоцентрический радиус, св. лет */
  rStartLy: number
  /** конечный радиус, св. лет */
  rEndLy: number
  /** галактическая азимутальная координата начала, град */
  thetaStart: number
  /** угол закрутки, град */
  pitchDeg: number
  /** относительная плотность звёзд/газа */
  weight: number
}

export const SPIRAL_ARMS: SpiralArm[] = [
  { id: 'norma', name: 'Рукав Наугольника (3 кпк)', rStartLy: 9800, rEndLy: 42000, thetaStart: 18, pitchDeg: 8.5, weight: 0.8 },
  { id: 'scutum', name: 'Рукав Щита — Кентавра', rStartLy: 11700, rEndLy: 52000, thetaStart: 110, pitchDeg: 12.1, weight: 1.0 },
  { id: 'sagittarius', name: 'Рукав Стрельца — Киля', rStartLy: 14300, rEndLy: 55000, thetaStart: 200, pitchDeg: 12.5, weight: 0.95 },
  { id: 'local', name: 'Местный рукав (Орион)', rStartLy: 26000, rEndLy: 36000, thetaStart: 305, pitchDeg: 11.4, weight: 0.55 },
  { id: 'perseus', name: 'Рукав Персея', rStartLy: 26000, rEndLy: 62000, thetaStart: 335, pitchDeg: 9.9, weight: 1.0 },
  { id: 'outer', name: 'Внешний рукав', rStartLy: 39000, rEndLy: 72000, thetaStart: 45, pitchDeg: 6.0, weight: 0.7 },
]

/** Ключевые объекты внутри Млечного Пути. */
export interface GalacticObject {
  id: string
  name: string
  nameEn: string
  catalog?: string
  /** галактические координаты: долгота, широта, град */
  l: number
  b: number
  /** расстояние от Солнца, св. лет */
  distLy: number
  kind: 'center' | 'cluster' | 'nebula' | 'remnant'
  note: string
  facts: { label: string; value: string }[]
}

export const GALACTIC_OBJECTS: GalacticObject[] = [
  {
    id: 'sgr-a-star',
    name: 'Стрелец A*',
    nameEn: 'Sagittarius A*',
    catalog: 'Sgr A*',
    l: 359.944, b: -0.046, distLy: 26670,
    kind: 'center',
    note:
      'Сверхмассивная чёрная дыра в центре Галактики. В 2022 году коллаборация Event Horizon ' +
      'Telescope получила её изображение; в 2020 году за доказательство её существования дали Нобелевскую премию.',
    facts: [
      { label: 'Масса', value: '4,297 × 10⁶ M☉' },
      { label: 'Радиус Шварцшильда', value: '≈ 12,7 млн км (0,085 а.е.)' },
      { label: 'Расстояние', value: '26 670 св. лет' },
      { label: 'Диаметр тени', value: '≈ 51,8 микросекунды дуги' },
      { label: 'Изображение', value: 'Event Horizon Telescope, 12 мая 2022' },
    ],
  },
  {
    id: 'omega-cen',
    name: 'Омега Центавра',
    nameEn: 'Omega Centauri',
    catalog: 'NGC 5139',
    l: 309.1, b: 14.97, distLy: 17090,
    kind: 'cluster',
    note:
      'Самое массивное шаровое скопление Галактики — около 10 млн звёзд. Разброс по составу ' +
      'и возрасту звёзд намекает, что это ядро поглощённой карликовой галактики.',
    facts: [
      { label: 'Звёзд', value: '≈ 10 млн' },
      { label: 'Масса', value: '4 × 10⁶ M☉' },
      { label: 'Диаметр', value: '150 св. лет' },
      { label: 'Возраст', value: '≈ 11,5 млрд лет' },
      { label: 'Природа', value: 'вероятно, ядро поглощённой галактики' },
    ],
  },
  {
    id: 'm13',
    name: 'Скопление в Геркулесе',
    nameEn: 'Great Globular Cluster in Hercules',
    catalog: 'M13 / NGC 6205',
    l: 59.01, b: 40.91, distLy: 22200,
    kind: 'cluster',
    note:
      'Самое известное шаровое скопление северного неба. В 1974 году на него направили ' +
      '«послание Аресибо» — первую целенаправленную межзвёздную радиопередачу.',
    facts: [
      { label: 'Звёзд', value: '≈ 300 тысяч' },
      { label: 'Диаметр', value: '145 св. лет' },
      { label: 'Возраст', value: '≈ 11,65 млрд лет' },
      { label: 'Послание Аресибо', value: '16 ноября 1974' },
    ],
  },
  {
    id: 'm4',
    name: 'M4',
    nameEn: 'Messier 4',
    catalog: 'M4 / NGC 6121',
    l: 350.97, b: 15.97, distLy: 7200,
    kind: 'cluster',
    note: 'Одно из ближайших шаровых скоплений. Содержит белые карлики возрастом около 13 млрд лет.',
    facts: [
      { label: 'Расстояние', value: '7 200 св. лет — одно из ближайших' },
      { label: 'Звёзд', value: '≈ 100 тысяч' },
      { label: 'Возраст', value: '≈ 12,2 млрд лет' },
    ],
  },
  {
    id: 'orion-nebula',
    name: 'Туманность Ориона',
    nameEn: 'Orion Nebula',
    catalog: 'M42 / NGC 1976',
    l: 209.01, b: -19.38, distLy: 1344,
    kind: 'nebula',
    note:
      'Ближайшая к нам массивная область звездообразования. В ней прямо сейчас формируются ' +
      'около 700 звёзд, и видны протопланетные диски вокруг молодых светил.',
    facts: [
      { label: 'Расстояние', value: '1 344 св. года' },
      { label: 'Диаметр', value: '24 св. года' },
      { label: 'Формирующихся звёзд', value: '≈ 700' },
      { label: 'Возраст', value: '≈ 2 млн лет' },
    ],
  },
  {
    id: 'eagle-nebula',
    name: 'Туманность Орла',
    nameEn: 'Eagle Nebula',
    catalog: 'M16 / NGC 6611',
    l: 16.95, b: 0.79, distLy: 5700,
    kind: 'nebula',
    note:
      'Здесь находятся «Столпы Творения» — колонны газа и пыли высотой в несколько световых лет, ' +
      'снятые Хабблом в 1995 году и переснятые JWST в 2022-м.',
    facts: [
      { label: 'Расстояние', value: '5 700 св. лет' },
      { label: 'Столпы Творения', value: 'высота до 4–5 св. лет' },
      { label: 'Снимки', value: 'Hubble 1995, JWST 2022' },
    ],
  },
  {
    id: 'crab-nebula',
    name: 'Крабовидная туманность',
    nameEn: 'Crab Nebula',
    catalog: 'M1 / NGC 1952',
    l: 184.56, b: -5.78, distLy: 6500,
    kind: 'remnant',
    note:
      'Остаток сверхновой, которую наблюдали китайские и арабские астрономы в 1054 году. ' +
      'В центре — пульсар, вращающийся 30 раз в секунду.',
    facts: [
      { label: 'Вспышка сверхновой', value: '1054 год, записана в летописях' },
      { label: 'Расстояние', value: '6 500 св. лет' },
      { label: 'Диаметр', value: '11 св. лет, расширяется на 1 500 км/с' },
      { label: 'Пульсар', value: '30,2 оборота в секунду' },
    ],
  },
]

/** Местная группа: около 80 известных галактик; здесь — все значимые. */
export const LOCAL_GROUP: Galaxy[] = [
  {
    id: 'milky-way', name: 'Млечный Путь', nameEn: 'Milky Way',
    ra: 0, dec: 0, distLy: 0, type: 'SBbc', diameterLy: 100000, massSun: 6.43e10, absMag: -20.9,
    note: 'Наша Галактика — одна из двух доминирующих в Местной группе.',
  },
  {
    id: 'canis-major', name: 'Карликовая галактика Большого Пса', nameEn: 'Canis Major Dwarf',
    ra: hms(7, 34, 0), dec: dms(-32, 0, 0), distLy: 25000, type: 'Irr', diameterLy: 5000,
    satelliteOf: 'milky-way',
    note: 'Спорный объект: возможно, не галактика, а искажение диска Млечного Пути. Если галактика — самая близкая к нам.',
  },
  {
    id: 'sagittarius-dsph', name: 'Карликовая галактика в Стрельце', nameEn: 'Sagittarius Dwarf Spheroidal',
    ra: hms(18, 55, 19.5), dec: dms(-30, 32, 43), distLy: 65000, type: 'dSph', diameterLy: 10000,
    massSun: 2.1e7, absMag: -13.5, satelliteOf: 'milky-way',
    note: 'Прямо сейчас разрывается Млечным Путём: её звёздный поток обвивает нашу Галактику. Её проходы через диск могли запускать волны звездообразования.',
  },
  {
    id: 'lmc', name: 'Большое Магелланово Облако', nameEn: 'Large Magellanic Cloud', catalog: 'LMC',
    ra: hms(5, 23, 34.5), dec: dms(-69, 45, 22), distLy: 163000, type: 'SBm', diameterLy: 32200,
    massSun: 2.7e9, absMag: -18.1, satelliteOf: 'milky-way',
    note: 'Крупнейший спутник Млечного Пути. В 1987 году в нём вспыхнула SN 1987A — ближайшая сверхновая за 400 лет. Содержит туманность Тарантул, самую активную область звездообразования Местной группы.',
  },
  {
    id: 'smc', name: 'Малое Магелланово Облако', nameEn: 'Small Magellanic Cloud', catalog: 'SMC',
    ra: hms(0, 52, 44.8), dec: dms(-72, 49, 43), distLy: 206000, type: 'SB(s)m', diameterLy: 18900,
    massSun: 3.1e8, absMag: -16.8, satelliteOf: 'milky-way',
    note: 'По наблюдениям цефеид в нём Генриетта Ливитт в 1912 году открыла зависимость «период — светимость» — ключ ко всей шкале космических расстояний.',
  },
  {
    id: 'ursa-minor', name: 'Карликовая галактика Малой Медведицы', nameEn: 'Ursa Minor Dwarf',
    ra: hms(15, 9, 8.5), dec: dms(67, 13, 21), distLy: 200000, type: 'dSph', diameterLy: 2000,
    massSun: 2.9e5, absMag: -8.8, satelliteOf: 'milky-way',
  },
  {
    id: 'draco', name: 'Карликовая галактика Дракона', nameEn: 'Draco Dwarf',
    ra: hms(17, 20, 12.4), dec: dms(57, 54, 55), distLy: 260000, type: 'dSph', diameterLy: 2700,
    massSun: 2.9e5, absMag: -8.8, satelliteOf: 'milky-way',
    note: 'Одна из самых «тёмных» галактик: отношение массы к светимости около 440 — почти вся масса тёмная.',
  },
  {
    id: 'sculptor', name: 'Карликовая галактика Скульптора', nameEn: 'Sculptor Dwarf',
    ra: hms(1, 0, 9.4), dec: dms(-33, 42, 33), distLy: 290000, type: 'dSph', diameterLy: 2600,
    massSun: 2.3e6, absMag: -11.1, satelliteOf: 'milky-way',
  },
  {
    id: 'sextans', name: 'Карликовая галактика Секстанта', nameEn: 'Sextans Dwarf',
    ra: hms(10, 13, 3), dec: dms(-1, 36, 53), distLy: 320000, type: 'dSph', diameterLy: 8400,
    massSun: 4.4e5, absMag: -9.3, satelliteOf: 'milky-way',
  },
  {
    id: 'carina', name: 'Карликовая галактика Киля', nameEn: 'Carina Dwarf',
    ra: hms(6, 41, 36.7), dec: dms(-50, 57, 58), distLy: 330000, type: 'dSph', diameterLy: 1600,
    massSun: 3.8e5, absMag: -9.1, satelliteOf: 'milky-way',
  },
  {
    id: 'fornax', name: 'Карликовая галактика Печи', nameEn: 'Fornax Dwarf',
    ra: hms(2, 39, 59.3), dec: dms(-34, 26, 57), distLy: 460000, type: 'dSph', diameterLy: 3400,
    massSun: 2e7, absMag: -13.4, satelliteOf: 'milky-way',
    note: 'Содержит шесть собственных шаровых скоплений — необычно много для карликовой галактики.',
  },
  {
    id: 'leo-ii', name: 'Лев II', nameEn: 'Leo II',
    ra: hms(11, 13, 28.8), dec: dms(22, 9, 6), distLy: 690000, type: 'dSph', diameterLy: 4100,
    massSun: 7.4e5, absMag: -9.8, satelliteOf: 'milky-way',
  },
  {
    id: 'leo-i', name: 'Лев I', nameEn: 'Leo I',
    ra: hms(10, 8, 28.1), dec: dms(12, 18, 23), distLy: 820000, type: 'dSph', diameterLy: 2200,
    massSun: 5.5e6, absMag: -12, satelliteOf: 'milky-way',
    note: 'В центре, возможно, есть чёрная дыра массой 3 млн M☉ — почти как Sgr A*, при том что вся галактика в тысячи раз легче Млечного Пути.',
  },
  {
    id: 'leo-t', name: 'Лев T', nameEn: 'Leo T',
    ra: hms(9, 34, 53.4), dec: dms(17, 3, 5), distLy: 1370000, type: 'dIrr/dSph', diameterLy: 1000,
    massSun: 1.4e5, absMag: -8,
  },
  {
    id: 'phoenix', name: 'Карликовая галактика Феникса', nameEn: 'Phoenix Dwarf',
    ra: hms(1, 51, 6.3), dec: dms(-44, 26, 41), distLy: 1440000, type: 'dIrr/dSph', diameterLy: 1900,
    massSun: 3.3e5, absMag: -9.8,
  },
  {
    id: 'ngc6822', name: 'Галактика Барнарда', nameEn: "Barnard's Galaxy", catalog: 'NGC 6822',
    ra: hms(19, 44, 56.6), dec: dms(-14, 47, 21), distLy: 1630000, type: 'IB(s)m', diameterLy: 7000,
    massSun: 1e8, absMag: -15.2,
    note: 'Хаббл использовал её цефеиды в 1925 году как одно из первых доказательств, что галактики лежат далеко за пределами Млечного Пути.',
  },
  {
    id: 'ngc185', name: 'NGC 185', nameEn: 'NGC 185',
    ra: hms(0, 38, 58), dec: dms(48, 20, 15), distLy: 2050000, type: 'dE3', diameterLy: 8000,
    massSun: 1.3e8, absMag: -14.8, satelliteOf: 'm31',
  },
  {
    id: 'ic10', name: 'IC 10', nameEn: 'IC 10',
    ra: hms(0, 20, 17.3), dec: dms(59, 18, 14), distLy: 2200000, type: 'dIrr', diameterLy: 5000,
    massSun: 1.6e8, absMag: -15.1, satelliteOf: 'm31',
    note: 'Единственная известная галактика со вспышкой звездообразования в Местной группе: рекордная плотность звёзд Вольфа — Райе.',
  },
  {
    id: 'ic1613', name: 'IC 1613', nameEn: 'IC 1613',
    ra: hms(1, 4, 47.8), dec: dms(2, 7, 4), distLy: 2380000, type: 'IAB(s)m', diameterLy: 10000,
    massSun: 1e8, absMag: -15.2,
  },
  {
    id: 'ngc147', name: 'NGC 147', nameEn: 'NGC 147',
    ra: hms(0, 33, 12.1), dec: dms(48, 30, 32), distLy: 2530000, type: 'dE5', diameterLy: 9000,
    massSun: 1e8, absMag: -14.6, satelliteOf: 'm31',
  },
  {
    id: 'm31', name: 'Галактика Андромеды', nameEn: 'Andromeda Galaxy', catalog: 'M31 / NGC 224',
    ra: hms(0, 42, 44.3), dec: dms(41, 16, 9), distLy: 2537000, type: 'SA(s)b', diameterLy: 152000,
    massSun: 1.5e11, absMag: -21.5,
    note: 'Самая массивная галактика Местной группы и самый далёкий объект, видимый невооружённым глазом. Приближается к нам со скоростью 110 км/с; столкновение — примерно через 4,5 млрд лет.',
  },
  {
    id: 'm32', name: 'M32', nameEn: 'Messier 32', catalog: 'M32 / NGC 221',
    ra: hms(0, 42, 41.8), dec: dms(40, 51, 55), distLy: 2650000, type: 'cE2', diameterLy: 6500,
    massSun: 3e8, absMag: -16.4, satelliteOf: 'm31',
    note: 'Компактная эллиптическая галактика — вероятно, ободранное ядро крупной галактики, которую поглотила Андромеда.',
  },
  {
    id: 'leo-a', name: 'Лев A', nameEn: 'Leo A',
    ra: hms(9, 59, 26.5), dec: dms(30, 44, 47), distLy: 2600000, type: 'IBm', diameterLy: 4000,
    massSun: 6e6, absMag: -12.1,
  },
  {
    id: 'm110', name: 'M110', nameEn: 'Messier 110', catalog: 'M110 / NGC 205',
    ra: hms(0, 40, 22.1), dec: dms(41, 41, 7), distLy: 2690000, type: 'dE5', diameterLy: 17000,
    massSun: 9.5e9, absMag: -16.5, satelliteOf: 'm31',
  },
  {
    id: 'm33', name: 'Галактика Треугольника', nameEn: 'Triangulum Galaxy', catalog: 'M33 / NGC 598',
    ra: hms(1, 33, 50.9), dec: dms(30, 39, 37), distLy: 2730000, type: 'SA(s)cd', diameterLy: 60000,
    massSun: 4.8e9, absMag: -18.8,
    note: 'Третья по величине галактика Местной группы и единственная спираль без бара. Возможно, спутник Андромеды. В ней находится NGC 604 — одна из крупнейших известных областей звездообразования.',
  },
  {
    id: 'pegasus-dirr', name: 'Карликовая галактика Пегаса', nameEn: 'Pegasus Dwarf Irregular',
    ra: hms(23, 28, 36.3), dec: dms(14, 44, 35), distLy: 3000000, type: 'dIrr', diameterLy: 7000,
    massSun: 5.8e6, absMag: -12.9,
  },
  {
    id: 'wlm', name: 'WLM', nameEn: 'Wolf-Lundmark-Melotte',
    ra: hms(0, 1, 58.2), dec: dms(-15, 27, 39), distLy: 3040000, type: 'IB(s)m', diameterLy: 8000,
    massSun: 1.6e7, absMag: -14.2,
    note: 'Изолированная галактика на краю Местной группы — почти не испытала внешних воздействий, поэтому важна для проверки моделей эволюции карликовых галактик. Одна из первых целей JWST.',
  },
  {
    id: 'aquarius-dwarf', name: 'Карликовая галактика Водолея', nameEn: 'Aquarius Dwarf',
    ra: hms(20, 46, 51.8), dec: dms(-12, 50, 53), distLy: 3200000, type: 'dIrr', diameterLy: 2000,
    massSun: 1.6e6, absMag: -10.6,
  },
  {
    id: 'sag-dig', name: 'Карликовая иррегулярная в Стрельце', nameEn: 'Sagittarius Dwarf Irregular',
    ra: hms(19, 29, 59), dec: dms(-17, 40, 41), distLy: 3390000, type: 'IB(s)m', diameterLy: 3000,
    massSun: 1e6, absMag: -11.5,
  },
  {
    id: 'ngc3109', name: 'NGC 3109', nameEn: 'NGC 3109',
    ra: hms(10, 3, 6.9), dec: dms(-26, 9, 35), distLy: 4300000, type: 'SB(s)m', diameterLy: 25000,
    massSun: 2.3e8, absMag: -15.7,
    note: 'На границе Местной группы; возможно, центр отдельной небольшой группы галактик.',
  },
]

/** Крупные структуры за Местной группой: скопления, сверхскопления, войды. */
export interface LargeStructure {
  id: string
  name: string
  nameEn: string
  catalog?: string
  ra: number
  dec: number
  /** расстояние, млн световых лет */
  distMly: number
  /** размер, млн световых лет */
  sizeMly: number
  kind: 'group' | 'cluster' | 'supercluster' | 'void' | 'wall' | 'attractor'
  /** масса, в массах Солнца */
  massSun?: number
  /** число галактик */
  galaxyCount?: string
  /** входит в Ланиакею */
  inLaniakea?: boolean
  note: string
}

export const LARGE_STRUCTURES: LargeStructure[] = [
  {
    id: 'local-group', name: 'Местная группа', nameEn: 'Local Group',
    ra: 0, dec: 0, distMly: 0, sizeMly: 10, kind: 'group',
    massSun: 2e12, galaxyCount: '≈ 80', inLaniakea: true,
    note: 'Наша группа галактик: две крупные спирали (Млечный Путь и Андромеда) и десятки карликовых.',
  },
  {
    id: 'sculptor-group', name: 'Группа Скульптора', nameEn: 'Sculptor Group',
    ra: hms(0, 47, 33), dec: dms(-25, 17, 18), distMly: 12.7, sizeMly: 5, kind: 'group',
    galaxyCount: '≈ 13', inLaniakea: true,
    note: 'Ближайшая к нам группа галактик, вытянутая цепочка вдоль луча зрения.',
  },
  {
    id: 'm81-group', name: 'Группа M81', nameEn: 'M81 Group',
    ra: hms(9, 55, 33), dec: dms(69, 3, 55), distMly: 11.7, sizeMly: 4, kind: 'group',
    galaxyCount: '≈ 34', inLaniakea: true,
    note: 'M81 и M82 взаимодействуют: сближение запустило в M82 мощнейшую вспышку звездообразования.',
  },
  {
    id: 'cen-a-group', name: 'Группа Центавра A / M83', nameEn: 'Centaurus A / M83 Group',
    ra: hms(13, 25, 27), dec: dms(-43, 1, 9), distMly: 13, sizeMly: 6, kind: 'group',
    galaxyCount: '≈ 30', inLaniakea: true,
    note: 'Центавр A — ближайшая к нам активная галактика с радиоджетами длиной в миллион световых лет.',
  },
  {
    id: 'virgo-cluster', name: 'Скопление Девы', nameEn: 'Virgo Cluster',
    ra: hms(12, 27, 0), dec: dms(12, 43, 0), distMly: 53.8, sizeMly: 15, kind: 'cluster',
    massSun: 1.2e15, galaxyCount: '1 300–2 000', inLaniakea: true,
    note: 'Ядро нашего сверхскопления. В его центре — M87 с чёрной дырой 6,5 млрд M☉, первой в истории сфотографированной (EHT, 2019). Скопление тормозит разбегание Местной группы.',
  },
  {
    id: 'fornax-cluster', name: 'Скопление Печи', nameEn: 'Fornax Cluster',
    ra: hms(3, 38, 29), dec: dms(-35, 27, 0), distMly: 62, sizeMly: 6, kind: 'cluster',
    massSun: 7e13, galaxyCount: '≈ 340', inLaniakea: true,
    note: 'Второе по богатству скопление в пределах 100 млн св. лет после Девы.',
  },
  {
    id: 'eridanus-cluster', name: 'Скопление Эридана', nameEn: 'Eridanus Cluster',
    ra: hms(3, 20, 0), dec: dms(-21, 0, 0), distMly: 75, sizeMly: 8, kind: 'cluster',
    galaxyCount: '≈ 200', inLaniakea: true,
    note: 'Группа групп: несколько скоплений в процессе слияния.',
  },
  {
    id: 'antlia-cluster', name: 'Скопление Насоса', nameEn: 'Antlia Cluster',
    ra: hms(10, 30, 5), dec: dms(-35, 19, 0), distMly: 132, sizeMly: 5, kind: 'cluster',
    galaxyCount: '≈ 234', inLaniakea: true,
    note: 'Третье по близости богатое скопление после Девы и Печи.',
  },
  {
    id: 'hydra-cluster', name: 'Скопление Гидры', nameEn: 'Hydra Cluster', catalog: 'Abell 1060',
    ra: hms(10, 36, 51), dec: dms(-27, 31, 35), distMly: 158, sizeMly: 10, kind: 'cluster',
    massSun: 3e14, galaxyCount: '≈ 157', inLaniakea: true,
    note: 'Вместе со скоплением Центавра образует основу сверхскопления Гидры — Кентавра.',
  },
  {
    id: 'centaurus-cluster', name: 'Скопление Центавра', nameEn: 'Centaurus Cluster', catalog: 'Abell 3526',
    ra: hms(12, 48, 51.8), dec: dms(-41, 18, 21), distMly: 170, sizeMly: 12, kind: 'cluster',
    massSun: 4e14, galaxyCount: '≈ 300', inLaniakea: true,
    note: 'Второе по яркости в рентгене скопление на небе. Горячий газ в нём — 3,5 млн градусов.',
  },
  {
    id: 'perseus-cluster', name: 'Скопление Персея', nameEn: 'Perseus Cluster', catalog: 'Abell 426',
    ra: hms(3, 19, 48), dec: dms(41, 30, 42), distMly: 240, sizeMly: 11, kind: 'cluster',
    massSun: 6.7e14, galaxyCount: '≈ 190',
    note: 'Самый яркий в рентгене объект неба. В его газе «звучит» акустическая волна от чёрной дыры NGC 1275 — нота си-бемоль на 57 октав ниже среднего до.',
  },
  {
    id: 'norma-cluster', name: 'Скопление Наугольника', nameEn: 'Norma Cluster', catalog: 'Abell 3627',
    ra: hms(16, 15, 32.8), dec: dms(-60, 54, 30), distMly: 220, sizeMly: 12, kind: 'cluster',
    massSun: 1e15, galaxyCount: '≈ 600', inLaniakea: true,
    note: 'Лежит почти в плоскости Млечного Пути, поэтому долго было скрыто пылью — «зона избегания». Ближайшее к Великому Аттрактору массивное скопление.',
  },
  {
    id: 'great-attractor', name: 'Великий Аттрактор', nameEn: 'Great Attractor',
    ra: hms(16, 10, 0), dec: dms(-60, 30, 0), distMly: 250, sizeMly: 30, kind: 'attractor',
    massSun: 5e15, inLaniakea: true,
    note:
      'Гравитационная аномалия, к которой Местная группа движется со скоростью около 600 км/с. ' +
      'Это центр притяжения Ланиакеи; наблюдать его напрямую мешает пыль диска нашей Галактики.',
  },
  {
    id: 'coma-cluster', name: 'Скопление Волос Вероники', nameEn: 'Coma Cluster', catalog: 'Abell 1656',
    ra: hms(12, 59, 48.7), dec: dms(27, 58, 50), distMly: 321, sizeMly: 20, kind: 'cluster',
    massSun: 7e14, galaxyCount: '> 1 000',
    note: 'Изучая движение галактик в этом скоплении, Фриц Цвикки в 1933 году впервые заявил о существовании тёмной материи.',
  },
  {
    id: 'shapley-supercluster', name: 'Сверхскопление Шепли', nameEn: 'Shapley Supercluster',
    ra: hms(13, 25, 0), dec: dms(-30, 0, 0), distMly: 650, sizeMly: 130, kind: 'supercluster',
    massSun: 1e16, galaxyCount: '≈ 8 000',
    note: 'Крупнейшая концентрация массы в наблюдаемой близости. Часть движения Местной группы объясняется его притяжением — за Великим Аттрактором.',
  },
  {
    id: 'bootes-void', name: 'Волопас (Великое Ничто)', nameEn: 'Boötes Void',
    ra: hms(14, 50, 0), dec: dms(46, 0, 0), distMly: 700, sizeMly: 330, kind: 'void',
    galaxyCount: '≈ 60 вместо ожидаемых 2 000',
    note:
      'Почти пустая область диаметром 330 млн световых лет. Если бы Млечный Путь оказался в её центре, ' +
      'мы бы не знали о существовании других галактик до 1960-х годов.',
  },
  {
    id: 'sloan-great-wall', name: 'Великая стена Слоуна', nameEn: 'Sloan Great Wall',
    ra: hms(11, 3, 0), dec: dms(7, 0, 0), distMly: 1370, sizeMly: 1380, kind: 'wall',
    note: 'Гигантская стена галактик, найденная в обзоре Sloan Digital Sky Survey в 2003 году. Одна из крупнейших известных структур.',
  },
  {
    id: 'hercules-cb-wall', name: 'Великая стена Геркулеса — Северной Короны', nameEn: 'Hercules-Corona Borealis Great Wall',
    ra: hms(15, 0, 0), dec: dms(35, 0, 0), distMly: 10000, sizeMly: 10000, kind: 'wall',
    note:
      'Выявлена по скучиванию гамма-всплесков. Если структура реальна, её размер около 10 млрд световых лет — ' +
      'это ставит под вопрос космологический принцип однородности. Существование остаётся спорным.',
  },
]

/** Ланиакея — сверхскопление, в которое входит Местная группа (Tully et al. 2014). */
export const LANIAKEA = {
  id: 'laniakea',
  name: 'Ланиакея',
  nameEn: 'Laniakea Supercluster',
  diameterMly: 520,
  massSun: 1e17,
  galaxyCount: '≈ 100 000',
  facts: [
    { label: 'Диаметр', value: '≈ 520 млн световых лет' },
    { label: 'Масса', value: '≈ 10¹⁷ M☉' },
    { label: 'Галактик', value: '≈ 100 000' },
    { label: 'Центр притяжения', value: 'Великий Аттрактор' },
    { label: 'Открыта', value: '2014, Tully, Courtois, Hoffman, Pomarède' },
    { label: 'Название', value: 'с гавайского — «неизмеримые небеса»' },
  ],
  note:
    'Границы Ланиакеи определены не по видимым галактикам, а по полю скоростей: сверхскопление — ' +
    'это область, внутри которой все галактики падают к одному центру. Ланиакея не связана ' +
    'гравитационно как целое: тёмная энергия разорвёт её раньше, чем она успеет сжаться.',
  source: 'Tully R.B. et al., "The Laniakea supercluster of galaxies", Nature 513, 71 (2014)',
}

/** Наблюдаемая Вселенная — сводка параметров. */
export const OBSERVABLE_UNIVERSE = {
  facts: [
    { label: 'Радиус (сопутствующий)', value: '46,5 млрд световых лет' },
    { label: 'Диаметр', value: '93 млрд световых лет' },
    { label: 'Возраст', value: '13,787 ± 0,020 млрд лет' },
    { label: 'Галактик', value: '≈ 2 × 10¹² (оценка по данным Hubble)' },
    { label: 'Постоянная Хаббла', value: '67,4 ± 0,5 км/с/Мпк (Planck 2018)' },
    { label: 'Тёмная энергия', value: '68,3 %' },
    { label: 'Тёмная материя', value: '26,8 %' },
    { label: 'Обычное вещество', value: '4,9 %' },
    { label: 'Температура реликтового излучения', value: '2,72548 ± 0,00057 К' },
    { label: 'Реликтовое излучение испущено', value: 'через 379 000 лет после Большого взрыва (z ≈ 1090)' },
    { label: 'Плоскостность', value: '|Ωk| < 0,001 — пространство плоское' },
  ],
  note:
    'Наблюдаемая Вселенная — не вся Вселенная, а лишь та часть, свет от которой успел до нас дойти. ' +
    'Её радиус 46,5 млрд световых лет при возрасте 13,8 млрд лет: пространство расширялось, пока свет летел.',
  source: 'Planck 2018 results VI: Cosmological parameters (A&A 641, A6)',
}
