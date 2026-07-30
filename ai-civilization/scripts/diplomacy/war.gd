class_name WarTheater
extends RefCounted

## Военный слой (§6).
##
## Разрешение боёв — формула на уровне Tier 2 (сила × технологии × численность
## + случайность). Tier 1 только визуализирует уже посчитанный результат:
## бой не «переигрывается» на экране, иначе детерминизм (а с ним и перемотка
## через снепшоты) ломается, а стоимость реализации улетает непропорционально
## ценности фичи.

const INVASION_VISIBLE_DAYS := 2

var world: World


func _init(p_world: World) -> void:
	world = p_world


# ------------------------------------------------------------------ формула

## Ядро боевого разрешения. Возвращает исход и доли потерь сторон.
static func resolve(attacker: float, defender: float, att_mult: float, def_mult: float, rng: SimRng) -> Dictionary:
	var noise := Balance.num("combat/randomness", 0.16)
	var a := maxf(0.0, attacker) * att_mult * rng.jitter(noise) * Balance.num("combat/attacker_penalty", 0.90)
	var d := maxf(0.0, defender) * def_mult * rng.jitter(noise)
	var attacker_wins := a > d
	var strong := maxf(a, d)
	var weak := minf(a, d)
	# Чем ближе силы, тем дороже победа — иначе перевес в 5% давал бы победу без потерь.
	var closeness := 0.0 if strong <= 0.0 else weak / strong
	var winner_loss := Balance.num("combat/loss_ratio_winner", 0.35) * closeness
	var loser_loss := Balance.num("combat/loss_ratio_loser", 0.85)
	return {
		"attacker_wins": attacker_wins,
		"attacker_loss": winner_loss if attacker_wins else loser_loss,
		"defender_loss": loser_loss if attacker_wins else winner_loss,
		"attacker_power": a,
		"defender_power": d,
	}


# ------------------------------------------------------------------ объявление

static func declare_war(w: World, day: int) -> void:
	if w.war_active:
		return
	var council := w.council()
	var humans := w.humans()
	if council == null or humans == null:
		return
	w.war_active = true
	w.war_happened = true
	w.war_resolved = false
	w.war_start_day = day
	council.at_war_with[humans.id] = true
	humans.at_war_with[council.id] = true
	humans.next_invasion_day = day + 3
	w.chronicle.post("war_declared", day, {}, Chronicle.CRITICAL)
	if w.act < 3:
		w.act = 3


static func end_war(w: World, day: int, reason: String) -> void:
	if not w.war_active:
		return
	w.war_active = false
	w.war_resolved = true
	for f in w.factions:
		if f.is_human:
			f.at_war_with.clear()
	var council := w.council()
	var humans := w.humans()
	if council != null and humans != null:
		council.at_war_with.erase(humans.id)
	w.invasions.clear()
	w.relations = maxf(w.relations, Balance.num("relations/peace_threshold", 46.0) - 6.0)
	w.chronicle.post("ceasefire" if reason == "ceasefire" else "milestone", day,
		{ "a": "Война окончена: %s" % reason }, Chronicle.CRITICAL)


# ------------------------------------------------------------------ день войны

func step_day(day: int) -> void:
	_internal_wars(day)
	if not world.war_active:
		return
	world.days_at_war += 1
	_schedule_invasion(day)
	_resolve_invasions(day)
	_counter_strike(day)
	_check_outcome(day)


func _schedule_invasion(day: int) -> void:
	var humans := world.humans()
	if humans == null or world.settlements.is_empty():
		return
	if day < humans.next_invasion_day:
		return
	humans.next_invasion_day = day + Balance.int_at("human/invasion_interval_days", 14)

	var target := _pick_invasion_target()
	if target == null:
		return
	# Материк вводит силы постепенно: первые волны — разведка боем, а не
	# сразу вся группировка. Иначе исход войны решает единственный первый бой.
	var ramp := Balance.num("human/invasion_ramp_days", 90.0)
	var commitment := clampf(0.35 + 0.65 * float(world.days_at_war) / maxf(1.0, ramp), 0.0, 1.5)
	var force := humans.human_strength() * Balance.num("human/invasion_force_frac", 0.12) * commitment
	world.invasions.append({
		"target": target.id,
		"pos": [target.pos.x, target.pos.y],
		"force": force,
		"days": 0,
	})
	world.chronicle.post("invasion", day, { "a": target.name, "n": _fmt(force) }, Chronicle.HIGH)


## Люди бьют по берегу: цель — прибрежное поселение, а при прочих равных
## самое слабое из них.
func _pick_invasion_target() -> Settlement:
	var best: Settlement = null
	var best_score := -INF
	var center := Vector2(world.size, world.size) * 0.5 * world.tile_size
	for s in world.settlements:
		var coastal := s.pos.distance_to(center)
		var defense := s.military_strength + s.population * Balance.num("combat/settlement_defense_per_pop", 0.09)
		var score := coastal * 0.01 - defense * 0.5
		if score > best_score:
			best_score = score
			best = s
	return best


func _resolve_invasions(day: int) -> void:
	var humans := world.humans()
	var council := world.council()
	if humans == null or council == null:
		return
	var remaining: Array = []
	for inv in world.invasions:
		inv["days"] = int(inv["days"]) + 1
		if int(inv["days"]) < INVASION_VISIBLE_DAYS:
			remaining.append(inv)
			continue
		var target := world.settlement(int(inv["target"]))
		if target == null:
			continue
		var defender := _local_defense(target)
		var outcome := resolve(float(inv["force"]), defender, humans.combat_multiplier(),
			_faction_of(target).combat_multiplier(), world.rng)

		if bool(outcome["attacker_wins"]):
			_raze_settlement(target, day)
			humans.battles_won += 1
			council.battles_lost += 1
			council.war_score = maxf(0.0, council.war_score - 0.08)
			humans.industry *= 1.0 - Balance.num("combat/loss_ratio_winner", 0.35) * 0.02
		else:
			var lost := float(inv["force"]) * float(outcome["attacker_loss"])
			humans.morale = maxf(0.25, humans.morale - Balance.num("human/morale_loss_per_defeat", 0.055))
			humans.industry *= 1.0 - 0.012
			humans.battles_lost += 1
			council.battles_won += 1
			council.war_score = minf(1.0, council.war_score + 0.09)
			_apply_defender_losses(target, float(outcome["defender_loss"]))
			world.chronicle.post("battle_win", day, { "a": target.name, "n": _fmt(lost) }, Chronicle.HIGH)
	world.invasions = remaining


## Оборона поселения — свои силы плюс треть сил соседей: перебросить успевают
## не все, но и в одиночку ячейка не остаётся.
func _local_defense(s: Settlement) -> float:
	var base := s.military_strength + s.population * Balance.num("combat/settlement_defense_per_pop", 0.09)
	var support := 0.0
	for other in world.settlements:
		if other.id == s.id or other.faction_id != s.faction_id:
			continue
		if other.pos.distance_to(s.pos) < world.map_pixel_size() * 0.35:
			support += other.military_strength * 0.30
	return base + support


func _apply_defender_losses(s: Settlement, frac: float) -> void:
	s.military_strength *= 1.0 - clampf(frac, 0.0, 1.0)
	s.population = maxf(1.0, s.population * (1.0 - clampf(frac, 0.0, 1.0) * 0.30))
	s.unrest = minf(1.0, s.unrest + 0.15)


func _raze_settlement(s: Settlement, day: int) -> void:
	world.chronicle.post("battle_loss", day, { "a": s.name }, Chronicle.CRITICAL)
	world.remove_settlement(s)
	if world.settlements.is_empty():
		world.flags["ai_destroyed"] = true
		end_war(world, day, "остров уничтожен")


## Ответный удар по материку: становится возможен, только когда остров уже
## перевешивает по силе. Именно этот механизм ведёт к концовке «истребление».
func _counter_strike(day: int) -> void:
	var humans := world.humans()
	var council := world.council()
	if humans == null or council == null:
		return
	if world.act < 3 or world.ai_military <= 0.0:
		return
	var human_str := maxf(1.0, humans.human_strength())
	var advantage := world.ai_military * council.combat_multiplier() / human_str
	if advantage < 0.9:
		return
	if not world.rng.chance(0.12):
		return
	var frac := clampf(Balance.num("human/mainland_strike_frac", 0.04) * advantage, 0.0, 0.12)
	var destroyed := humans.industry * frac
	humans.industry -= destroyed
	humans.population = maxf(0.0, humans.population * (1.0 - frac * 0.35))
	humans.morale = maxf(0.1, humans.morale - 0.03)
	council.war_score = minf(1.0, council.war_score + 0.05)
	# Удар стоит части военного производства острова.
	for s in world.settlements_of(council.id):
		s.military_strength *= 0.94
	world.chronicle.post("mainland_strike", day, { "n": _fmt(destroyed) }, Chronicle.HIGH)


func _check_outcome(day: int) -> void:
	var humans := world.humans()
	var council := world.council()
	if humans == null or council == null:
		return
	var industry_ratio := humans.industry / maxf(1.0, humans.industry_initial)
	if industry_ratio <= 0.18:
		world.flags["human_industry_broken"] = true
		end_war(world, day, "промышленность материка уничтожена")
		return
	var min_days := 40
	if world.days_at_war >= min_days and council.war_score >= Balance.num("relations/ceasefire_war_score", 0.72):
		end_war(world, day, "ceasefire")
		return
	# Затяжная война обязана иметь выход и в том случае, если остров её не
	# выигрывает: иначе сюжет застревает в Акте 3 навсегда.
	if world.days_at_war >= Balance.int_at("combat/max_war_days", 220):
		end_war(world, day, "истощение сторон")


# ------------------------------------------------------- внутренние конфликты

## Раскол даёт войну без участия людей: совет и отделившиеся дерутся за отвалы.
func _internal_wars(day: int) -> void:
	var council := world.council()
	if council == null:
		return
	for f in world.factions:
		if f.is_human or f.is_council or not f.alive:
			continue
		if not f.at_war_with.has(council.id):
			continue
		if day % 4 != 0:
			continue
		var rebels := world.settlements_of(f.id)
		var loyal := world.settlements_of(council.id)
		if rebels.is_empty() or loyal.is_empty():
			continue
		var rebel_str := _side_strength(rebels)
		var loyal_str := _side_strength(loyal)
		var outcome := resolve(rebel_str, loyal_str, f.combat_multiplier(), council.combat_multiplier(), f.rng)
		var rebel_loss := float(outcome["attacker_loss"])
		var loyal_loss := float(outcome["defender_loss"])
		for s in rebels:
			s.military_strength *= 1.0 - rebel_loss * 0.5
			s.population = maxf(1.0, s.population * (1.0 - rebel_loss * 0.08))
		for s in loyal:
			s.military_strength *= 1.0 - loyal_loss * 0.5
			s.population = maxf(1.0, s.population * (1.0 - loyal_loss * 0.08))
		world.chronicle.post("internal_war", day, { "a": f.name }, Chronicle.NORMAL)


func _side_strength(list: Array[Settlement]) -> float:
	var total := 0.0
	for s in list:
		total += s.military_strength + s.population * Balance.num("combat/settlement_defense_per_pop", 0.09)
	return total


func _faction_of(s: Settlement) -> Faction:
	var f := world.faction(s.faction_id)
	if f == null:
		f = world.council()
	return f


static func _fmt(v: float) -> String:
	if absf(v) >= 100.0:
		return str(int(round(v)))
	return "%.1f" % v
