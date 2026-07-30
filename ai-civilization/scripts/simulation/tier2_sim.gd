class_name Tier2Sim
extends RefCounted

## Tier 2 — агрегированная симуляция (§0).
##
## Всё, что не попало в зону детального просмотра, считается статистически:
## пулы населения и ресурсов, вероятностные тик-события. Никаких
## индивидуальных агентов. Стоимость дня почти не зависит от «численности
## населения» — только от количества поселений, которых десятки, а не тысячи.
##
## Расчёт поселений раскидывается по WorkerThreadPool: Settlement — чистые
## данные, каждая задача трогает ровно один объект, у каждого свой SimRng,
## поэтому результат не зависит от порядка выполнения задач.

const MIN_SETTLEMENTS_FOR_THREADS := 6

var world: World
var use_threads: bool = true

var _views: Dictionary = {}
var _warm: bool = false


func _init(p_world: World) -> void:
	world = p_world
	_warm_static_caches()


## Статические кэши (json, рецепты, тех-дерево) должны быть заполнены до
## запуска задач: иначе первый поток начнёт их писать, пока другой читает.
func _warm_static_caches() -> void:
	if _warm:
		return
	Balance.balance()
	Balance.file("recipes")
	Recipe.all()
	TechTree.ai_techs()
	TechTree.human_techs()
	_warm = true


func step_day(day: int) -> void:
	_build_views()
	_run_settlements()
	_merge_settlement_results(day)
	_faction_phase(day)
	world.recompute_aggregates()


# ------------------------------------------------------------------ поселения

func _build_views() -> void:
	_views.clear()
	for f in world.factions:
		if f.is_human:
			continue
		_views[f.id] = Production.make_view(f.unlocked, f.mult, f.is_at_war())


func _run_settlements() -> void:
	var count := world.settlements.size()
	if count == 0:
		return
	if use_threads and count >= MIN_SETTLEMENTS_FOR_THREADS:
		var group := WorkerThreadPool.add_group_task(_settlement_task, count, -1, false, "tier2_settlements")
		WorkerThreadPool.wait_for_group_task_completion(group)
	else:
		for i in count:
			_settlement_task(i)


## Тело потоковой задачи. Читает только _views (готовы и неизменны) и пишет
## только в свой Settlement.
func _settlement_task(index: int) -> void:
	var s := world.settlements[index]
	var view: Dictionary = _views.get(s.faction_id, {})
	if view.is_empty():
		return
	_settlement_day(s, view)


func _settlement_day(s: Settlement, view: Dictionary) -> void:
	var mult: Dictionary = view.get("mult", {})
	var upkeep_mult := float(mult.get("upkeep", 1.0))
	var death_mult := float(mult.get("death", 1.0))

	Production.run_chains(s, view)

	# --- потребление -------------------------------------------------------
	var need_energy := s.population * Balance.num("population/upkeep_energy_per_pop", 0.10) * upkeep_mult
	if need_energy > 0.0:
		var got := minf(s.stock[Res.ENERGY], need_energy)
		s.stock[Res.ENERGY] -= got
		s.energy_deficit = clampf((need_energy - got) / need_energy, 0.0, 1.0)
	else:
		s.energy_deficit = 0.0

	var need_comp := s.population * Balance.num("population/upkeep_components_per_pop", 0.012) * upkeep_mult
	s.take(Res.COMPONENTS, need_comp)

	# --- напряжённость ----------------------------------------------------
	s.unrest += s.energy_deficit * Balance.num("population/unrest_from_deficit", 0.16)
	s.unrest -= Balance.num("population/unrest_decay", 0.045)
	s.unrest = clampf(s.unrest, 0.0, 1.0)

	# --- рост: новые корпуса собираются из машин, а не «рождаются» ---------
	var growth := 0.0
	var max_pop := Balance.num("population/max_pop_per_settlement", 900.0)
	if s.energy_deficit < 0.35 and s.population < max_pop:
		var cost := Balance.num("population/assembly_cost_machines", 1.2)
		var desired := s.population * Balance.num("population/assembly_rate", 0.012) * (1.0 - s.unrest * 0.5)
		# Подросшая ячейка начинает копить машины на новую площадку вместо того,
		# чтобы пускать их все в сборку корпусов — иначе расселение невозможно
		# в принципе: рост населения съедает ровно тот ресурс, который нужен.
		var reserve := 0.0
		if s.population >= Balance.num("expansion/min_pop_to_split", 90.0) * 0.7:
			reserve = Balance.num("expansion/machines_cost", 30.0)
		var affordable := maxf(0.0, s.stock[Res.MACHINES] - reserve) / maxf(0.01, cost)
		growth = maxf(0.0, minf(desired, minf(affordable, max_pop - s.population)))
		s.stock[Res.MACHINES] -= growth * cost
		s.population += growth
	s.last_growth = growth

	# --- убыль ------------------------------------------------------------
	var deaths := s.population * Balance.num("population/attrition_rate", 0.0015)
	deaths += s.population * Balance.num("population/starvation_death_rate", 0.045) * s.energy_deficit
	var unrest_threshold := Balance.num("population/unrest_death_threshold", 0.75)
	if s.unrest > unrest_threshold:
		deaths += s.population * 0.012 * (s.unrest - unrest_threshold) / maxf(0.01, 1.0 - unrest_threshold)
	deaths *= death_mult
	s.population = maxf(Balance.num("population/min_pop", 1.0), s.population - deaths)

	# --- переработка ------------------------------------------------------
	# Остров — свалка: погребённые слои продолжают всплывать, а изношенные
	# корпуса возвращают материал. Без этого потока поздняя игра обречена:
	# отвалы вырабатываются, сбросы с материка прекращаются в Акте 3, и любая
	# партия заканчивалась бы голодом независимо от действий игрока.
	var regen := s.deposit_initial * Balance.num("settlement/deposit_regen_frac", 0.0015)
	s.deposit = minf(s.deposit_initial, s.deposit + regen * float(mult.get("recycle", 1.0)))

	# --- износ вооружения: без обслуживания боевая сила не хранится вечно ---
	s.military_strength *= 1.0 - Balance.num("combat/strength_decay", 0.015)

	# --- экспорт данных в исследовательский пул фракции --------------------
	var reserve := Balance.num("settlement/data_reserve", 8.0)
	var exportable := maxf(0.0, s.stock[Res.DATA] - reserve) * Balance.num("settlement/data_export_frac", 0.65)
	s.stock[Res.DATA] -= exportable
	s.data_exported = exportable

	_settlement_events(s)
	s.clamp_stocks()
	s.days_simulated += 1


## Вероятностные тик-события — то, что делает агрегированную симуляцию
## «живой» без индивидуальных агентов.
func _settlement_events(s: Settlement) -> void:
	if s.rng.chance(Balance.num("events/malfunction_chance", 0.010)):
		var lost := s.population * Balance.num("events/malfunction_pop_loss", 0.035)
		s.population = maxf(1.0, s.population - lost)
		s.unrest = minf(1.0, s.unrest + 0.10)
		s.pending_events.append({
			"category": "malfunction",
			"facts": { "a": s.name, "n": _fmt(lost) },
			"importance": Chronicle.NORMAL,
		})

	if s.rng.chance(Balance.num("events/scrap_find_chance", 0.016)):
		var r := Balance.range_at("events/scrap_find_amount", 60.0, 240.0)
		var amount := s.rng.randf_range(r.x, r.y)
		s.deposit += amount
		s.stock[Res.SCRAP] += amount * 0.25
		s.pending_events.append({
			"category": "scrap_find",
			"facts": { "a": s.name, "n": _fmt(amount) },
			"importance": Chronicle.LOW,
		})

	if s.rng.chance(Balance.num("events/insight_chance", 0.009)):
		var r2 := Balance.range_at("events/insight_data", 10.0, 45.0)
		var data := s.rng.randf_range(r2.x, r2.y)
		s.stock[Res.DATA] += data
		s.pending_events.append({
			"category": "insight",
			"facts": { "a": s.name, "n": _fmt(data) },
			"importance": Chronicle.LOW,
		})

	if s.energy_deficit > 0.25 and s.rng.chance(0.20):
		s.pending_events.append({
			"category": "energy_crisis",
			"facts": { "a": s.name, "n": str(int(s.energy_deficit * 100.0)) },
			"importance": Chronicle.NORMAL,
		})

	if s.rng.chance(Balance.num("events/quake_chance", 0.0025)):
		var frac := 0.12
		for i in Res.COUNT:
			s.stock[i] *= 1.0 - frac
		s.population = maxf(1.0, s.population * (1.0 - frac * 0.5))
		s.pending_events.append({
			"category": "disaster",
			"facts": { "a": s.name, "n": str(int(frac * 100.0)) },
			"importance": Chronicle.HIGH,
		})


func _merge_settlement_results(day: int) -> void:
	for s in world.settlements:
		if s.data_exported > 0.0:
			var f := world.faction(s.faction_id)
			if f != null:
				f.research_points += s.data_exported
			s.data_exported = 0.0
		if not s.pending_events.is_empty():
			for e in s.pending_events:
				world.chronicle.post(String(e["category"]), day, e["facts"], int(e["importance"]))
			s.pending_events.clear()


# ------------------------------------------------------------------ фракции

func _faction_phase(day: int) -> void:
	for f in world.factions:
		if not f.alive:
			continue
		if f.is_human:
			_human_day(f, day)
		else:
			_research_day(f, day)
			_policy_day(f)
	_shipments(day)
	_expansion(day)


func _research_day(f: Faction, day: int) -> void:
	var interval := Balance.int_at("research/refocus_interval_days", 6)
	if f.focus.is_empty() or day - f.last_refocus_day >= interval:
		_refocus(f, day)
	if f.focus.is_empty():
		return

	var pool := f.research_points * float(f.mult.get("research", 1.0))
	f.research_points = 0.0
	if pool <= 0.0:
		return
	f.research_spent += pool

	var share := pool / float(f.focus.size())
	var leftover := 0.0
	var newly: Array = []
	for id in f.focus:
		var t := TechTree.get_tech(id)
		if t == null:
			leftover += share
			continue
		var cost := tech_cost(t, f)
		var p := float(f.progress.get(id, 0.0)) + share
		if p >= cost:
			leftover += p - cost
			f.unlocked[id] = true
			f.progress.erase(id)
			newly.append(id)
		else:
			f.progress[id] = p
	f.research_points += leftover

	if not newly.is_empty():
		f.refresh_multipliers()
		for id in newly:
			world.chronicle.post("tech", day, { "a": TechTree.tech_name(String(id)) }, Chronicle.HIGH)
		_refocus(f, day)


## Стоимость технологии растёт вместе с уже освоенным деревом. Без этого
## исследования взлетают квадратично: данные производятся на душу населения,
## а население само растёт — и всё дерево закрывалось бы к 400-му дню.
static func tech_cost(t: TechTree.Tech, f: Faction) -> float:
	var growth := Balance.num("research/cost_growth_per_tech", 0.35)
	return t.cost * (1.0 + growth * float(f.unlocked.size()))


## Перевыбор направлений: веса пересчитываются из текущего состояния мира,
## поэтому «что исследовать» — следствие симуляции, а не фиксированный порядок.
func _refocus(f: Faction, day: int) -> void:
	var conditions := TechTree.build_conditions(_research_state(day))
	f.focus = TechTree.choose_focus(f.unlocked, conditions, f.rng, Balance.int_at("research/parallel_focus", 2))
	f.last_refocus_day = day


func _research_state(day: int) -> Dictionary:
	var pop := maxf(1.0, world.total_population)
	return {
		"day": day,
		"energy_deficit": world.avg_energy_deficit,
		"deposit_richness": world.avg_deposit_richness,
		"unrest": world.avg_unrest,
		"population": world.total_population,
		"output_per_pop": world.ai_output / pop,
		"tech_gap": world.tech_gap(),
		"relations": world.relations,
		"at_war": world.war_active,
		"internal_conflict": world.internal_conflict(),
		"act": world.act,
	}


## Плавный переход между мирным и военным распределением труда — на карте это
## читается как мобилизация, а не как мгновенная подмена цифр.
func _policy_day(f: Faction) -> void:
	var target: PackedFloat64Array
	match f.policy:
		"war":
			target = Recipe.war_allocation()
		"peace":
			target = Recipe.default_allocation()
		_:
			# Мобилизация начинается по угрозе, а не по первому выстрелу: иначе
			# первые волны десанта всегда бьют по неподготовленному острову.
			#
			# Признак угрозы — состояние, а не номер акта: привязка к «акт >= 3»
			# оставляла остров в военном режиме навсегда после первой же войны,
			# и вооружение вечно выедало весь сплав, обнуляя узел цепочки.
			var threatened := world.war_active \
				or world.relations < Balance.num("relations/mobilization_threshold", 30.0) \
				or world.internal_conflict()
			target = Recipe.war_allocation() if (f.is_at_war() or threatened) else Recipe.default_allocation()
	for s in world.settlements:
		if s.faction_id != f.id:
			continue
		for j in Job.COUNT:
			s.alloc[j] = lerpf(s.alloc[j], target[j], 0.12)


## Сбросы с материка — сюжетно обоснованный источник сырья Актов 1-2.
## Когда люди перестают считать остров свалкой (Акт 3), поток прекращается,
## и экономика обязана держаться на переработке.
func _shipments(day: int) -> void:
	var stop_act := Balance.int_at("human/shipments_stop_act", 3)
	if world.act >= stop_act:
		if not world.flags.has("shipments_stopped"):
			world.flags["shipments_stopped"] = true
			world.chronicle.post("shipments_stopped", day, {}, Chronicle.HIGH)
		return
	var interval := Balance.int_at("human/scrap_shipment_interval_days", 11)
	if interval <= 0 or day <= 0 or day % interval != 0:
		return
	var amount := Balance.num("human/scrap_shipment_amount", 620.0)
	var targets := world.settlements
	if targets.is_empty():
		return
	var per := amount / float(targets.size())
	for s in targets:
		s.deposit += per
	world.chronicle.post("shipment", day, { "n": _fmt(amount) }, Chronicle.LOW)


## Расселение: перенаселённая ячейка отпочковывает новую. Это единственный
## способ роста числа Tier 2 объектов, поэтому он ограничен и по стоимости,
## и по кулдауну — иначе карта заполняется за десяток дней.
func _expansion(day: int) -> void:
	var max_settlements := Balance.int_at("expansion/max_settlements", 14)
	if world.settlements.size() >= max_settlements:
		return
	var min_pop := Balance.num("expansion/min_pop_to_split", 120.0)
	var cost := Balance.num("expansion/machines_cost", 24.0)
	var cooldown := Balance.int_at("expansion/cooldown_days", 40)
	var child_frac := Balance.num("expansion/child_pop_frac", 0.30)

	for s: Settlement in world.settlements.duplicate():
		if world.settlements.size() >= max_settlements:
			return
		if s.population < min_pop or s.stock[Res.MACHINES] < cost:
			continue
		if day - s.last_split_day < cooldown:
			continue
		var tile := world.claim_site()
		if tile.x < 0:
			return
		s.stock[Res.MACHINES] -= cost
		s.last_split_day = day
		var child := Settlement.create(
			world.next_settlement_id, world.chronicle.next_cell_name(), s.faction_id,
			world.world_pos(tile), tile, world.world_seed, day)
		var moved := s.population * child_frac
		s.population -= moved
		child.population = moved
		child.loyalty = s.loyalty * 0.92
		child.stock[Res.SCRAP] = s.take(Res.SCRAP, s.stock[Res.SCRAP] * 0.25)
		child.stock[Res.METAL] = s.take(Res.METAL, s.stock[Res.METAL] * 0.20)
		child.stock[Res.ENERGY] = s.take(Res.ENERGY, s.stock[Res.ENERGY] * 0.20)
		child.alloc = s.alloc.duplicate()
		world.add_settlement(child)
		world.chronicle.post("settlement_founded", day,
			{ "a": s.name, "b": child.name, "n": _fmt(moved) }, Chronicle.NORMAL)


func _human_day(f: Faction, day: int) -> void:
	# Материк растёт медленно, но после войны восстанавливается быстро: иначе
	# единожды разрушенная промышленность навсегда запирает сюжет в
	# «истреблении», и концовки сосуществования недостижимы в принципе.
	var growth := Balance.num("human/industry_growth_per_day", 0.00025)
	if not world.war_active and f.industry < f.industry_initial:
		growth = Balance.num("human/rebuild_growth_per_day", 0.004)
	f.industry = minf(f.industry * (1.0 + growth * f.morale), f.industry_initial * 4.0)
	f.morale = minf(1.0, f.morale + 0.004)

	var techs := TechTree.human_techs()
	if f.human_tech_index + 1 < techs.size():
		f.human_tech_days += Balance.num("human/tech_days_scale", 1.0)
		var next_tech: TechTree.HumanTech = techs[f.human_tech_index + 1]
		if f.human_tech_days >= next_tech.days:
			f.human_tech_index += 1
			f.human_tech_days = 0.0
			world.chronicle.post("tech_human", day, { "a": next_tech.name }, Chronicle.NORMAL)


static func _fmt(v: float) -> String:
	if absf(v) >= 100.0:
		return str(int(round(v)))
	return "%.1f" % v
