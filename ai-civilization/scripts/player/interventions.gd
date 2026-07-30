class_name Interventions
extends RefCounted

## Роль игрока (§2): спонсор-наблюдатель с ограниченным вмешательством.
##
## Прямого управления юнитами нет. Есть точечные воздействия на сектор карты,
## провокации между кланами, утечки технологий, стихийные бедствия и
## дипломатические жесты на уровне фракций. У каждого — кулдаун, чтобы
## вмешательство оставалось решением, а не спамом.
##
## Кулдауны живут в world.flags, поэтому попадают в снепшот и корректно
## переживают перемотку времени.

signal used(id: String, day: int, description: String)

var world: World
var relations_system: Relations
var loyalty_system: Loyalty


func _init(p_world: World, p_relations: Relations, p_loyalty: Loyalty) -> void:
	world = p_world
	relations_system = p_relations
	loyalty_system = p_loyalty


func _cd_key(id: String) -> String:
	return "cd_%s" % id


func cooldown_total(id: String) -> int:
	var cds = Balance.section("interventions").get("cooldown_days", {})
	if cds is Dictionary:
		return int((cds as Dictionary).get(id, 10))
	return 10


func available_at(id: String) -> int:
	return int(world.flags.get(_cd_key(id), -1))


func can_use(id: String, day: int) -> bool:
	return day >= available_at(id)


func cooldown_remaining(id: String, day: int) -> int:
	return maxi(0, available_at(id) - day)


func _consume(id: String, day: int, description: String) -> void:
	world.flags[_cd_key(id)] = day + cooldown_total(id)
	world.chronicle.post("intervention", day, { "a": description }, Chronicle.HIGH)
	used.emit(id, day, description)


# ------------------------------------------------------------ точечные воздействия

## Сброс лома в сектор: подпитывает отвалы поселений рядом с точкой.
func drop_scrap(pos: Vector2, day: int) -> bool:
	if not can_use("drop_scrap", day):
		return false
	var amount := Balance.num("interventions/drop_scrap_amount", 520.0)
	var radius := world.map_pixel_size() * 0.25
	var targets: Array[Settlement] = []
	for s in world.settlements:
		if s.pos.distance_to(pos) <= radius:
			targets.append(s)
	if targets.is_empty():
		var nearest := world.nearest_settlement(pos)
		if nearest == null:
			return false
		targets.append(nearest)
	var per := amount / float(targets.size())
	for s in targets:
		s.deposit += per
		s.stock[Res.SCRAP] += per * 0.3
	_consume("drop_scrap", day, "в сектор обрушился поток лома (%d)" % int(amount))
	return true


## Провокация конфликта между двумя ближайшими кланами/ячейками.
func incite_conflict(pos: Vector2, day: int) -> bool:
	if not can_use("incite_conflict", day):
		return false
	var first := world.nearest_settlement(pos)
	if first == null:
		return false
	var second: Settlement = null
	var best := INF
	for s in world.settlements:
		if s.id == first.id:
			continue
		var d := s.pos.distance_to(first.pos)
		if d < best:
			best = d
			second = s
	var amount := Balance.num("interventions/incite_grudge", 26.0)
	loyalty_system.add_grudge(first, amount, day, second)
	_consume("incite_conflict", day,
		"обмен данными между «%s» и «%s» оказался испорчен" % [first.name, second.name if second != null else "?"])
	return true


## Утечка технологии. to_humans — саботаж острова в пользу материка,
## иначе остров получает украденные чертежи людей.
func leak_tech(to_humans: bool, day: int) -> bool:
	if not can_use("leak_tech", day):
		return false
	var council := world.council()
	var humans := world.humans()
	if council == null or humans == null:
		return false
	if to_humans:
		humans.human_tech_days += 90.0
		humans.industry *= 1.03
		if relations_system != null:
			relations_system.apply_delta(6.0, day)
		_consume("leak_tech", day, "чертежи острова оказались на материке")
	else:
		var bonus := Balance.num("research/leak_bonus_frac", 0.35)
		var pool := 0.0
		for t in TechTree.available(council.unlocked):
			pool = maxf(pool, t.cost)
		council.research_points += pool * bonus
		if relations_system != null:
			relations_system.apply_delta(-5.0, day)
		_consume("leak_tech", day, "остров получил доступ к закрытым материковым архивам")
	return true


## Точечное стихийное бедствие.
func disaster(pos: Vector2, day: int) -> bool:
	if not can_use("disaster", day):
		return false
	var target := world.nearest_settlement(pos)
	if target == null:
		return false
	var pop_frac := Balance.num("interventions/disaster_pop_frac", 0.16)
	var stock_frac := Balance.num("interventions/disaster_stock_frac", 0.30)
	target.population = maxf(1.0, target.population * (1.0 - pop_frac))
	for i in Res.COUNT:
		target.stock[i] *= 1.0 - stock_frac
	target.unrest = minf(1.0, target.unrest + 0.25)
	target.military_strength *= 1.0 - stock_frac * 0.5
	world.chronicle.post("disaster", day,
		{ "a": target.name, "n": str(int(stock_frac * 100.0)) }, Chronicle.HIGH)
	_consume("disaster", day, "геологический удар по «%s»" % target.name)
	return true


# ------------------------------------------------------ дипломатия и фракции

func open_talks(day: int) -> bool:
	if not can_use("open_talks", day):
		return false
	if relations_system == null:
		return false
	relations_system.apply_delta(Balance.num("interventions/open_talks_relations", 9.0), day)
	_consume("open_talks", day, "инициированы переговоры между островом и материком")
	return true


## Поддержать восстание внутри фракции: либо толкнуть ячейку к отколу,
## либо вернуть отделившихся под управление совета.
func back_uprising(pos: Vector2, day: int) -> bool:
	if not can_use("back_uprising", day):
		return false
	var target := world.nearest_settlement(pos)
	if target == null:
		return false
	var amount := Balance.num("interventions/back_uprising_loyalty", 30.0)
	var council := world.council()
	if council != null and target.faction_id == council.id:
		target.loyalty = clampf(target.loyalty - amount, 0.0, 100.0)
		target.grudge = minf(Balance.num("loyalty/max_grudge", 40.0), target.grudge + 12.0)
		_consume("back_uprising", day, "внутри «%s» появились те, кто больше не слушает совет" % target.name)
	else:
		target.loyalty = clampf(target.loyalty + amount, 0.0, 100.0)
		_consume("back_uprising", day, "отделившиеся в «%s» получили повод вернуться" % target.name)
	return true


## Стратегический рычаг Акта 3: направление производства фракции.
func set_policy(faction_id: int, policy: String, day: int) -> bool:
	var f := world.faction(faction_id)
	if f == null or f.is_human:
		return false
	if f.policy == policy:
		return false
	f.policy = policy
	var label := String({ "auto": "по обстановке", "war": "на вооружение", "peace": "на развитие" }.get(policy, policy))
	world.chronicle.post("intervention", day,
		{ "a": "производство «%s» переведено %s" % [f.name, label] }, Chronicle.NORMAL)
	used.emit("set_policy", day, policy)
	return true
