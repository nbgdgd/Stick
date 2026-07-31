class_name TierManager
extends RefCounted

## Переключение Tier 1 ↔ Tier 2 (§0).
##
## Пересчёт — раз в N тиков, а не каждый кадр: список поселений короткий, но
## сама операция включает спавн/возврат десятков юнитов, и делать её на каждом
## кадре бессмысленно. Гистерезис не даёт ячейке на границе радиуса мигать
## между уровнями при малейшем движении камеры.
##
## Бюджет юнитов жёсткий: ближние поселения получают корпуса первыми,
## дальним достаётся то, что осталось. Общее число активных юнитов не зависит
## от населения острова — только от лимита.

var world: World
var pool: UnitPool

var radius: float = 780.0
var hysteresis: float = 240.0
var unit_budget: int = 260
var per_settlement_max: int = 32
var pop_per_unit: float = 5.0
var interval_ticks: int = 12

var _assigned: Dictionary = {}
var _battle_units: Array[Tier1Unit] = []
var _last_reeval_tick: int = -99999


func _init(p_world: World, p_pool: UnitPool) -> void:
	world = p_world
	pool = p_pool
	var t := Balance.section("tier")
	radius = float(t.get("tier1_radius", 780.0))
	hysteresis = float(t.get("tier1_hysteresis", 240.0))
	unit_budget = int(t.get("unit_budget", 260))
	per_settlement_max = int(t.get("units_per_settlement_max", 32))
	pop_per_unit = maxf(1.0, float(t.get("pop_per_visual_unit", 5.0)))
	interval_ticks = maxi(1, int(t.get("reeval_interval_ticks", 12)))
	pool.preallocate(unit_budget)


func should_reevaluate(tick: int) -> bool:
	return tick - _last_reeval_tick >= interval_ticks


func reevaluate(tick: int, camera_center: Vector2, view_scale: float = 1.0) -> void:
	_last_reeval_tick = tick
	var effective_radius := radius * maxf(0.35, view_scale)

	# Сортируем по расстоянию: бюджет расходуется от ближних к дальним.
	var ranked: Array = []
	for s in world.settlements:
		var d := s.pos.distance_to(camera_center)
		var limit := effective_radius + (hysteresis if s.is_tier1 else 0.0)
		if d <= limit:
			ranked.append({ "s": s, "d": d })
		else:
			_release_settlement(s)
	ranked.sort_custom(func(a, b): return float(a["d"]) < float(b["d"]))

	var budget := unit_budget - _battle_units.size()
	for entry in ranked:
		var s: Settlement = entry["s"]
		if budget <= 0:
			_release_settlement(s)
			continue
		var desired := clampi(int(ceil(s.population / pop_per_unit)), 1, per_settlement_max)
		desired = mini(desired, budget)
		budget -= desired
		_set_units(s, desired)

	_sync_battles()

	# Отражаем итог в состоянии поселений — HUD и инспектор это показывают.
	for s in world.settlements:
		s.visual_units = (_assigned.get(s.id, []) as Array).size()
		s.is_tier1 = s.visual_units > 0


func _set_units(s: Settlement, desired: int) -> void:
	var list: Array = _assigned.get(s.id, [])
	while list.size() > desired:
		pool.release(list.pop_back())
	var faction := world.faction(s.faction_id)
	while list.size() < desired:
		var u := pool.acquire()
		if u == null:
			break
		var kind := Tier1Unit.Kind.WORKER
		var roll := list.size() % 5
		if roll == 3:
			kind = Tier1Unit.Kind.HAULER
		elif roll == 4 and s.military_strength > 1.0:
			kind = Tier1Unit.Kind.GUARD
		u.configure(kind, s.id, s.pos, pop_per_unit, SimRng.hash_seed(world.world_seed, s.id * 131 + list.size()),
			faction.sprite_set if faction != null else "blue")
		list.append(u)
	_assigned[s.id] = list
	for u: Tier1Unit in list:
		u.home_pos = s.pos


func _release_settlement(s: Settlement) -> void:
	var list: Array = _assigned.get(s.id, [])
	for u: Tier1Unit in list:
		pool.release(u)
	_assigned.erase(s.id)
	s.is_tier1 = false
	s.visual_units = 0


## Упрощённая тактическая визуализация боя: группа корпусов у точки десанта.
## Исход уже посчитан формулой Tier 2 — здесь только показ.
func _sync_battles() -> void:
	for u in _battle_units:
		pool.release(u)
	_battle_units.clear()
	if world.invasions.is_empty():
		return
	var per_battle := mini(Balance.int_at("combat/tier1_battle_units", 24), pool.free_count())
	for inv in world.invasions:
		var p = inv.get("pos", [0.0, 0.0])
		var pos := Vector2(float(p[0]), float(p[1]))
		# Две линии: десант со стороны моря, оборона со стороны поселения.
		# Толпа, бегающая вокруг точки, читалась как суета, а не как бой.
		var target := world.settlement(int(inv.get("target", -1)))
		var map_centre := Vector2(world.size, world.size) * 0.5 * world.tile_size
		var axis := Vector2.RIGHT
		if target != null and (target.pos - map_centre).length() > 1.0:
			axis = (target.pos - map_centre).normalized()
		var side := Vector2(-axis.y, axis.x)

		for i in per_battle:
			var u := pool.acquire()
			if u == null:
				return
			var attacker := i % 2 == 0
			var kind := Tier1Unit.Kind.SOLDIER if attacker else Tier1Unit.Kind.GUARD
			var rank := float(i / 2) - float(per_battle) * 0.25
			var line_pos := pos + axis * (-42.0 if attacker else 24.0) + side * rank * 15.0
			u.configure(kind, -1, line_pos, pop_per_unit,
				SimRng.hash_seed(world.world_seed, i * 977 + int(inv.get("target", 0))),
				"red" if attacker else "blue")
			u.state = Tier1Unit.State.FIGHT
			u.wander_radius = 18.0
			u.home_pos = line_pos
			u.target = line_pos + side * randf_range(-8.0, 8.0)
			_battle_units.append(u)


func step(delta: float) -> void:
	for u in pool.active():
		u.step(delta, world)


## Полный сброс — после перемотки времени или загрузки: id поселений могли
## измениться, держать старые привязки нельзя.
func reset() -> void:
	pool.release_all()
	_assigned.clear()
	_battle_units.clear()
	_last_reeval_tick = -99999
	for s in world.settlements:
		s.is_tier1 = false
		s.visual_units = 0


func active_units() -> int:
	return pool.active_count()


func tier1_settlements() -> int:
	var n := 0
	for s in world.settlements:
		if s.is_tier1:
			n += 1
	return n
