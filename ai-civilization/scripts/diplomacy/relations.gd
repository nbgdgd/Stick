class_name Relations
extends RefCounted

## Дипломатический трек ИИ ↔ человечество (§6).
##
## Счёт снижается сам, без участия игрока: территориальное расширение,
## экономическая конкуренция, технологический разрыв и случайные инциденты.
## Пробитие порога запускает войну автоматически — это часть сюжетной оси,
## а не решение игрока.

var world: World


func _init(p_world: World) -> void:
	world = p_world


func step_day(day: int) -> void:
	var council := world.council()
	var humans := world.humans()
	if council == null or humans == null:
		return

	var delta := 0.0

	# Территория: остров расширяется за пределы «полигона списания».
	var free := Balance.int_at("relations/territory_free_settlements", 3)
	var extra := maxi(0, world.settlements.size() - free)
	delta -= float(extra) * Balance.num("relations/territory_penalty_per_settlement", 0.16)

	# Экономическая конкуренция: чем ближе остров к материку по выпуску,
	# тем острее реакция. Свыше паритета давление растёт линейно.
	var ratio := world.economy_ratio()
	if ratio > 0.35:
		delta -= (ratio - 0.35) * Balance.num("relations/economy_penalty_scale", 0.55)

	# Технологический разрыв замечают только когда он в пользу острова.
	var gap := world.tech_gap()
	if gap > 0:
		delta -= float(gap) * Balance.num("relations/tech_gap_penalty", 0.30)

	# Перехват спутникового канала — постоянное раздражение.
	if council.has_flag("relations_pressure"):
		delta -= 0.06

	# Медленный дрейф к нейтральной оценке: без событий отношения остывают.
	var drift_to := Balance.num("relations/drift_to", 45.0)
	delta += (drift_to - world.relations) * Balance.num("relations/drift_rate", 0.035)

	world.relations = clampf(
		world.relations + delta,
		Balance.num("relations/min", -100.0),
		Balance.num("relations/max", 100.0))

	_incidents(day)
	_check_war(day)
	_check_peace(day)


func _incidents(day: int) -> void:
	if not world.rng.chance(Balance.num("relations/incident_chance_per_day", 0.022)):
		return
	var sev := Balance.range_at("relations/incident_severity", 4.0, 13.0)
	var amount := world.rng.randf_range(sev.x, sev.y)
	# В Акте 1 остров ещё слишком мал, чтобы инциденты стоили дорого.
	if world.act <= 1:
		amount *= 0.4
	world.relations = maxf(Balance.num("relations/min", -100.0), world.relations - amount)
	world.chronicle.post("incident", day, { "a": world.chronicle.incident_flavor() }, Chronicle.NORMAL)


func _check_war(day: int) -> void:
	if world.war_active:
		return
	if world.act < Balance.int_at("relations/war_min_act", 2):
		return
	if world.relations > Balance.num("relations/war_threshold", 14.0):
		return
	WarTheater.declare_war(world, day)


## Мир возможен только после того, как война уже случилась и выдохлась:
## сам счёт отношений вверх не восстанавливается настолько без событий.
func _check_peace(day: int) -> void:
	if not world.war_active:
		return
	if world.relations < Balance.num("relations/peace_threshold", 46.0):
		return
	WarTheater.end_war(world, day, "ceasefire")


## Изменение отношений извне: сюжетные решения, вмешательства игрока, торговля.
func apply_delta(amount: float, day: int, reason: String = "") -> void:
	world.relations = clampf(
		world.relations + amount,
		Balance.num("relations/min", -100.0),
		Balance.num("relations/max", 100.0))
	if not reason.is_empty():
		world.chronicle.post("relations_shift", day,
			{ "n": str(int(round(world.relations))), "a": reason }, Chronicle.NORMAL)
