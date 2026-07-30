class_name Trade
extends RefCounted

## Обмен ресурсами (§4). Внутри фракции — сглаживание дефицитов, между
## фракцией ИИ и людьми — поток ресурсов плюс модификатор отношений.
##
## Отказ в помощи не бесплатен: ячейка с накопленными обидами не делится,
## и это само становится событием хроники и вкладом в раскол.

const TRADED_RESOURCES := [Res.SCRAP, Res.METAL, Res.ENERGY, Res.COMPONENTS, Res.ALLOY]

var world: World


func _init(p_world: World) -> void:
	world = p_world


func step_day(day: int) -> void:
	var interval := Balance.int_at("trade/interval_days", 3)
	if interval <= 0 or day % interval != 0:
		return
	for f in world.ai_factions():
		_internal_trade(f, day)
	_external_trade(day)


func _internal_trade(f: Faction, day: int) -> void:
	var own := world.settlements_of(f.id)
	if own.size() < 2:
		return
	var surplus_at := Balance.num("trade/surplus_threshold", 120.0)
	var deficit_at := Balance.num("trade/deficit_threshold", 25.0)
	var max_frac := Balance.num("trade/max_transfer_frac", 0.30) * float(f.mult.get("trade", 1.0))
	var max_grudge := Balance.num("loyalty/max_grudge", 40.0)

	for res in TRADED_RESOURCES:
		var donors: Array[Settlement] = []
		var needy: Array[Settlement] = []
		for s in own:
			if s.stock[res] > surplus_at:
				donors.append(s)
			elif s.stock[res] < deficit_at:
				needy.append(s)
		if donors.is_empty() or needy.is_empty():
			continue

		for receiver in needy:
			for donor in donors:
				if donor.stock[res] <= surplus_at:
					continue
				# Обиды блокируют помощь — отсюда и берутся сюжеты о расколе.
				var refuse_chance := donor.grudge / maxf(1.0, max_grudge) * 0.8
				if refuse_chance > 0.05 and donor.rng.chance(refuse_chance):
					world.chronicle.post("grudge", day,
						{ "a": donor.name, "b": receiver.name }, Chronicle.LOW)
					continue
				var available := (donor.stock[res] - surplus_at) * clampf(max_frac, 0.0, 0.9)
				var need := deficit_at * 2.0 - receiver.stock[res]
				var amount := minf(available, maxf(0.0, need))
				if amount <= 0.5:
					continue
				donor.stock[res] -= amount
				receiver.stock[res] += amount
				if amount > 40.0 and world.rng.chance(0.25):
					world.chronicle.post("trade", day, {
						"a": donor.name, "b": receiver.name,
						"n": str(int(amount)), "res": Res.NAMES[res],
					}, Chronicle.LOW)
				break


## Торговля с материком возможна только в мирное время и начиная с Акта 2 —
## до этого остров для людей не субъект, а полигон.
func _external_trade(day: int) -> void:
	if world.war_active or world.act < 2:
		return
	var council := world.council()
	var humans := world.humans()
	if council == null or humans == null:
		return
	var own := world.settlements_of(council.id)
	if own.is_empty():
		return

	# Остров продаёт готовую продукцию, получает сырьё и немного доверия.
	var sold := 0.0
	for s in own:
		var excess := maxf(0.0, s.stock[Res.MACHINES] - 20.0) * 0.10
		if excess <= 0.1:
			continue
		s.stock[Res.MACHINES] -= excess
		s.stock[Res.SCRAP] += excess * 14.0
		sold += excess
	if sold <= 0.1:
		return
	world.relations = minf(Balance.num("relations/max", 100.0),
		world.relations + Balance.num("trade/relation_bonus_per_trade", 0.05))
	humans.industry *= 1.0 + 0.0004 * minf(4.0, sold)
	if world.rng.chance(0.2):
		world.chronicle.post("trade", day, {
			"a": council.name, "b": humans.name,
			"n": str(int(sold)), "res": Res.NAMES[Res.MACHINES],
		}, Chronicle.LOW)
