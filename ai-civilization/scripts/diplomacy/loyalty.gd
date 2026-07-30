class_name Loyalty
extends RefCounted

## Лояльность и раскол внутри фракции ИИ (§6).
##
## Лояльность здесь всегда измеряется по отношению к совету: удалённость от
## столицы, накопленные обиды, напряжённость и то, насколько ячейка вообще
## выигрывает от членства. Ниже порога — откол, выше порога возврата —
## воссоединение. Это даёт эмерджентную драму без скриптования каждого события.

const SECESSION_WAR_CHANCE := 0.6

var world: World


func _init(p_world: World) -> void:
	world = p_world


func step_day(day: int) -> void:
	var council := world.council()
	if council == null:
		return
	var capital := world.capital_of(council.id)
	var capital_pos := capital.pos if capital != null else Vector2.ZERO

	var dist_penalty := Balance.num("loyalty/distance_penalty_per_1000px", 0.85)
	var unrest_penalty := Balance.num("loyalty/unrest_penalty", 6.0)
	var council_bonus := Balance.num("loyalty/council_bonus", 0.55)
	var prosperity_bonus := Balance.num("loyalty/prosperity_bonus", 1.1)
	var drift := Balance.num("loyalty/drift_to_center", 0.06)
	var grudge_decay := Balance.num("loyalty/grudge_decay", 0.02)

	var council_strength := _council_strength(council)

	for s: Settlement in world.settlements.duplicate():
		if s.faction_id == world.human_id:
			continue
		var delta := 0.0
		delta -= (s.pos.distance_to(capital_pos) / 1000.0) * dist_penalty
		delta -= s.unrest * unrest_penalty
		delta -= s.grudge * 0.05
		delta += council_strength * council_bonus
		delta += (1.0 - s.energy_deficit) * prosperity_bonus
		delta += (50.0 - s.loyalty) * drift

		s.loyalty = clampf(s.loyalty + delta, 0.0, 100.0)
		s.grudge = maxf(0.0, s.grudge * (1.0 - grudge_decay))

		if s.faction_id == council.id:
			_maybe_secede(s, council, day)
		else:
			_maybe_rejoin(s, council, day)


## «Сила совета» — насколько центр вообще способен что-то удерживать:
## доля столицы в населении плюс наличие связной сети данных.
func _council_strength(council: Faction) -> float:
	var own := world.settlements_of(council.id)
	if own.is_empty():
		return 0.0
	var capital := world.capital_of(council.id)
	var total := 0.0
	for s in own:
		total += s.population
	var share := 0.0 if total <= 0.0 else capital.population / total
	var strength := 0.5 + share * 0.8
	if council.unlocked.has("data_networks"):
		strength += 0.5
	if council.unlocked.has("swarm_logistics"):
		strength += 0.3
	if world.war_active:
		strength += 0.4  # внешняя угроза сплачивает
	return strength


func _maybe_secede(s: Settlement, council: Faction, day: int) -> void:
	if s.loyalty > Balance.num("loyalty/secede_threshold", 22.0):
		return
	if world.settlements_of(council.id).size() <= 1:
		return

	world.chronicle.post("loyalty_drop", day,
		{ "a": s.name, "n": str(int(round(s.loyalty))) }, Chronicle.NORMAL)

	# Ячейка уходит либо к уже существующим отделившимся по соседству,
	# либо создаёт новую фракцию.
	var host := _nearby_splinter(s)
	if host == null:
		host = Faction.create_ai(world.next_faction_id, world.chronicle.splinter_name(),
			_splinter_color(), world.world_seed, day)
		host.parent_id = council.id
		host.unlocked = council.unlocked.duplicate()
		host.policy = "war"
		host.refresh_multipliers()
		world.add_faction(host)
		if world.rng.chance(SECESSION_WAR_CHANCE):
			host.at_war_with[council.id] = true
			council.at_war_with[host.id] = true

	s.faction_id = host.id
	s.loyalty = maxf(s.loyalty, 8.0)
	world.chronicle.post("secession", day, { "a": s.name, "b": host.name }, Chronicle.CRITICAL)


func _maybe_rejoin(s: Settlement, council: Faction, day: int) -> void:
	if s.loyalty < Balance.num("loyalty/rejoin_threshold", 74.0):
		return
	var old := world.faction(s.faction_id)
	s.faction_id = council.id
	world.chronicle.post("rejoin", day,
		{ "a": s.name, "b": old.name if old != null else "?" }, Chronicle.HIGH)
	if old != null and world.settlements_of(old.id).is_empty():
		old.alive = false
		old.at_war_with.clear()
		council.at_war_with.erase(old.id)


func _nearby_splinter(s: Settlement) -> Faction:
	var radius := world.map_pixel_size() * 0.22
	for f in world.factions:
		if f.is_human or f.is_council or not f.alive:
			continue
		for other in world.settlements_of(f.id):
			if other.pos.distance_to(s.pos) <= radius:
				return f
	return null


func _splinter_color() -> Color:
	return Color.from_hsv(world.rng.randf_range(0.02, 0.14), 0.75, 0.95)


## Накопление обид (провокация конфликта игроком, подавление, отказ в помощи).
func add_grudge(s: Settlement, amount: float, day: int, other: Settlement = null) -> void:
	s.grudge = minf(Balance.num("loyalty/max_grudge", 40.0), s.grudge + amount)
	s.loyalty = maxf(0.0, s.loyalty - amount * 0.35)
	if other != null:
		other.grudge = minf(Balance.num("loyalty/max_grudge", 40.0), other.grudge + amount * 0.5)
		world.chronicle.post("grudge", day, { "a": s.name, "b": other.name }, Chronicle.NORMAL)
