class_name Simulation
extends RefCounted

## Оркестратор симуляции: владеет миром, часами и всеми подсистемами.
## Порядок дневных фаз задан здесь и только здесь — от него зависит
## воспроизводимость forward-replay'а после загрузки снепшота.

signal day_advanced(day: int)
signal rewound(day: int)
signal ended(ending: Dictionary)

var world: World
var clock: SimClock

var tier2: Tier2Sim
var trade: Trade
var relations: Relations
var war: WarTheater
var loyalty: Loyalty
var story: Story
var snapshots: SnapshotStore
var interventions: Interventions

var replaying: bool = false

var _significant_event: bool = false


func _init(world_seed: int = 0) -> void:
	world = World.new(world_seed)
	clock = SimClock.new()

	tier2 = Tier2Sim.new(world)
	trade = Trade.new(world)
	relations = Relations.new(world)
	war = WarTheater.new(world)
	loyalty = Loyalty.new(world)
	story = Story.new(world, relations)
	snapshots = SnapshotStore.new(world, clock)
	interventions = Interventions.new(world, relations, loyalty)

	clock.day_started.connect(_on_day_started)
	world.chronicle.entry_added.connect(_on_chronicle_entry)
	story.ending_reached.connect(func(e): ended.emit(e))


# ------------------------------------------------------------------ старт

func new_game() -> void:
	world.generate_map()

	var council := Faction.create_ai(0, "Совет", Color(0.35, 0.85, 0.95), world.world_seed, 0)
	council.is_council = true
	world.add_faction(council)
	world.council_id = council.id

	var humans := Faction.create_human(1, world.world_seed)
	world.add_faction(humans)
	world.human_id = humans.id

	var count := Balance.int_at("world/start_settlements", 3)
	for _i in count:
		var tile := world.claim_site()
		if tile.x < 0:
			break
		var s := Settlement.create(world.next_settlement_id, world.chronicle.next_cell_name(),
			council.id, world.world_pos(tile), tile, world.world_seed, 0)
		world.add_settlement(s)
		world.chronicle.post("founding", 0,
			{ "a": s.name, "b": "южном берегу", "n": str(int(s.population)) }, Chronicle.NORMAL)

	world.chronicle.post_raw(
		"Партия списанных систем выгружена на остров. Расчёт материка: они сгниют здесь.",
		0, Chronicle.CRITICAL, "milestone")
	world.recompute_aggregates()
	snapshots.capture(0)


# ------------------------------------------------------------------ ход времени

## Вызывается из _process ноды Game. Возвращает количество выполненных тиков.
func process(delta: float) -> int:
	if not world.ending_id.is_empty():
		return 0
	if not story.pending_choice.is_empty():
		return 0
	return clock.advance(delta)


func day() -> int:
	return clock.day()


## Подписки образуют ссылочные циклы (часы держат Simulation через сигнал, и
## наоборот), а RefCounted такие циклы сам не разрывает. Для одной партии это
## неважно, но при повторном «начать заново» в рамках сессии старые симуляции
## оставались бы в памяти вместе со всеми снепшотами.
func dispose() -> void:
	if clock.day_started.is_connected(_on_day_started):
		clock.day_started.disconnect(_on_day_started)
	if world.chronicle.entry_added.is_connected(_on_chronicle_entry):
		world.chronicle.entry_added.disconnect(_on_chronicle_entry)
	for connection in story.ending_reached.get_connections():
		story.ending_reached.disconnect(connection["callable"])
	snapshots.clear()


func _on_day_started(day: int) -> void:
	# Снепшот берётся ДО обработки дня: восстановление даёт ровно то состояние,
	# с которого день начинался, и replay воспроизводит его один в один.
	snapshots.maybe_capture(day)

	tier2.step_day(day)
	trade.step_day(day)
	relations.step_day(day)
	war.step_day(day)
	loyalty.step_day(day)
	story.step_day(day)

	day_advanced.emit(day)


func _on_chronicle_entry(entry: Dictionary) -> void:
	if int(entry.get("importance", 0)) >= Chronicle.HIGH:
		_significant_event = true


## Прямое проматывание на N дней (используется тестами и отладкой).
func step_days(days: int) -> void:
	clock.run_days(days)


## «Скачок вперёд до следующего значимого события» (§2). Значимость — это
## importance >= HIGH в хронике, то есть ровно то, что игрок и так увидел бы
## в ленте. Прерывается также на сюжетной развилке.
func skip_to_next_event(max_days: int = -1) -> int:
	if max_days <= 0:
		max_days = Balance.int_at("time/skip_max_days", 120)
	_significant_event = false
	var start := day()
	while not _significant_event and (day() - start) < max_days:
		if not story.pending_choice.is_empty() or not world.ending_id.is_empty():
			break
		clock.run_ticks(clock.ticks_per_day)
	return day() - start


## Перемотка назад: ближайший снепшот + ускоренный forward-replay (§3).
func rewind_days(days: int) -> bool:
	var target := maxi(0, day() - maxi(1, days))
	var idx := snapshots.index_at_or_before(target)
	if idx < 0:
		return false
	replaying = true
	var restored := snapshots.restore(idx)
	if restored < 0:
		replaying = false
		return false
	story.pending_choice = {}
	var to_replay := target - restored
	if to_replay > 0:
		clock.run_days(to_replay)
	replaying = false
	rewound.emit(day())
	return true


func can_rewind() -> bool:
	return snapshots.count() > 0 and day() > snapshots.earliest_day()


# ------------------------------------------------------------------ сохранение

func save_game(path: String) -> Error:
	return snapshots.save_to_file(path)


func load_game(path: String) -> bool:
	if not snapshots.load_from_file(path):
		return false
	# Карта не хранится в файле — она детерминирована по seed. Курсор занятых
	# площадок при этом сбрасывать нельзя, иначе расселение начнёт выдавать
	# уже занятые точки.
	var site_cursor := world.next_site_index
	world.generate_map()
	world.next_site_index = site_cursor
	rewound.emit(day())
	return true
