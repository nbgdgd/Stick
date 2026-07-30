class_name Story
extends RefCounted

## Сюжетный каркас (§1, §7): акты, чекпоинты и развилка концовок.
##
## Переходы между актами и концовки — не хардкод последовательности, а пороги
## по метрикам симуляции из data/story.json. Заскриптованными остаются только
## сами тексты и точки выбора: без них структура актов растворилась бы в
## процедурном шуме.

signal act_changed(act: int, act_name: String, blurb: String)
signal choice_requested(checkpoint: Dictionary)
signal ending_reached(ending: Dictionary)

var world: World
var relations_system: Relations

var pending_choice: Dictionary = {}

var _acts: Array = []
var _checkpoints: Array = []
var _endings: Array = []


func _init(p_world: World, p_relations: Relations) -> void:
	world = p_world
	relations_system = p_relations
	var data := Balance.file("story")
	_acts = data.get("acts", [])
	_checkpoints = data.get("checkpoints", [])
	_endings = data.get("endings", [])


func step_day(day: int) -> void:
	var m := metrics(day)
	_advance_act(day, m)
	_fire_checkpoints(day, m)
	_check_ending(day, m)


# ------------------------------------------------------------------ метрики

func metrics(day: int) -> Dictionary:
	var council := world.council()
	var humans := world.humans()
	var m := {
		"day": float(day),
		"act": float(world.act),
		"total_population": world.total_population,
		"settlements": float(world.settlements.size()),
		"ai_tech_level": float(council.tech_level()) if council != null else 0.0,
		"human_tech_level": float(humans.tech_level()) if humans != null else 0.0,
		"tech_gap": float(world.tech_gap()),
		"relations": world.relations,
		"at_war": 1.0 if world.war_active else 0.0,
		"war_happened": 1.0 if world.war_happened else 0.0,
		"war_resolved": 1.0 if world.war_resolved else 0.0,
		"days_at_war": float(world.days_at_war),
		"economy_ratio": world.economy_ratio(),
		"ai_output": world.ai_output,
		"ai_military": world.ai_military,
		"min_loyalty": world.min_loyalty,
		"avg_unrest": world.avg_unrest,
		"splinters": float(world.splinter_count()),
		"human_population": humans.population if humans != null else 0.0,
		"human_industry": humans.industry if humans != null else 0.0,
		"human_industry_ratio": (humans.industry / maxf(1.0, humans.industry_initial)) if humans != null else 1.0,
		"war_score": council.war_score if council != null else 0.0,
	}
	if council != null:
		for id in council.unlocked.keys():
			m["tech_%s" % id] = 1.0
	for t in TechTree.ai_techs():
		if not m.has("tech_%s" % t.id):
			m["tech_%s" % t.id] = 0.0
	return m


static func _compare(value: float, op: String, target: float) -> bool:
	match op:
		">=": return value >= target
		"<=": return value <= target
		">": return value > target
		"<": return value < target
		"==": return is_equal_approx(value, target)
		"!=": return not is_equal_approx(value, target)
	return false


static func evaluate(conditions: Array, mode: String, m: Dictionary) -> bool:
	if mode == "none":
		return false
	if conditions.is_empty():
		return mode != "any"
	var any_true := false
	for c in conditions:
		if not (c is Dictionary):
			continue
		var value := float(m.get(String(c.get("metric", "")), 0.0))
		var ok := _compare(value, String(c.get("op", ">=")), float(c.get("value", 0.0)))
		if mode == "all" and not ok:
			return false
		if ok:
			any_true = true
	return any_true if mode == "any" else true


# ------------------------------------------------------------------ акты

func _advance_act(day: int, m: Dictionary) -> void:
	var current := _act_def(world.act)
	if current.is_empty():
		return
	var adv = current.get("advance", {})
	if not (adv is Dictionary):
		return
	if not evaluate(adv.get("conditions", []), String(adv.get("mode", "all")), m):
		return
	var next := _act_def(world.act + 1)
	if next.is_empty():
		return
	world.act += 1
	var act_name := String(next.get("name", "?"))
	var blurb := String(next.get("blurb", ""))
	world.chronicle.post_raw("АКТ %d — %s. %s" % [world.act, act_name, blurb],
		day, Chronicle.CRITICAL, "milestone")
	act_changed.emit(world.act, act_name, blurb)


func _act_def(id: int) -> Dictionary:
	for a in _acts:
		if a is Dictionary and int(a.get("id", -1)) == id:
			return a
	return {}


func act_name() -> String:
	return String(_act_def(world.act).get("name", "?"))


# ------------------------------------------------------------------ чекпоинты

func _fire_checkpoints(day: int, m: Dictionary) -> void:
	if not pending_choice.is_empty():
		return
	for cp in _checkpoints:
		if not (cp is Dictionary):
			continue
		var id := String(cp.get("id", ""))
		if world.fired_checkpoints.has(id):
			continue
		if int(cp.get("act", 1)) > world.act:
			continue
		if not evaluate(cp.get("conditions", []), "all", m):
			continue
		world.fired_checkpoints[id] = day
		var headline := String(cp.get("headline", ""))
		if not headline.is_empty():
			world.chronicle.post_raw(headline, day, Chronicle.CRITICAL, "milestone")
		var choice = cp.get("choice", {})
		if choice is Dictionary and not (choice as Dictionary).is_empty():
			pending_choice = { "id": id, "choice": choice }
			choice_requested.emit(pending_choice)
		return


## Ответ игрока на развилку. Эффекты растянуты во времени не механикой
## таймеров, а тем, что они меняют модификаторы, а не сами числа.
func resolve_choice(checkpoint_id: String, option_id: String, day: int) -> void:
	if pending_choice.is_empty() or String(pending_choice.get("id", "")) != checkpoint_id:
		return
	var choice: Dictionary = pending_choice.get("choice", {})
	pending_choice = {}
	for opt in choice.get("options", []):
		if not (opt is Dictionary) or String(opt.get("id", "")) != option_id:
			continue
		world.flags["choice_%s" % checkpoint_id] = option_id
		world.chronicle.post_raw("Решение: %s" % String(opt.get("label", option_id)),
			day, Chronicle.CRITICAL, "milestone")
		apply_effects(opt.get("effects", {}), day)
		return


func apply_effects(effects, day: int) -> void:
	if not (effects is Dictionary):
		return
	var council := world.council()
	var humans := world.humans()
	for key in (effects as Dictionary).keys():
		var skey := String(key)
		var value = effects[key]
		match skey:
			"flag":
				world.flags[String(value)] = true
			"relations_delta":
				if relations_system != null:
					relations_system.apply_delta(float(value), day)
			"research_bonus":
				if council != null:
					council.research_points += float(value)
			"research_cost":
				if council != null:
					council.research_points = maxf(0.0, council.research_points - float(value))
			"all_loyalty":
				for s in world.settlements:
					s.loyalty = clampf(s.loyalty + float(value), 0.0, 100.0)
			"all_grudge":
				for s in world.settlements:
					s.grudge = maxf(0.0, s.grudge + float(value))
			"scrap_all":
				var per := float(value) / maxf(1.0, float(world.settlements.size()))
				for s in world.settlements:
					s.stock[Res.SCRAP] += per
					s.deposit += per
			"human_tech_bonus_days":
				if humans != null:
					humans.human_tech_days += float(value)
			"unlock_tech":
				if council != null:
					council.unlocked[String(value)] = true
					council.refresh_multipliers()
			_:
				# Всё остальное — постоянные модификаторы производства/боя.
				if council != null and council.mult.has(_mult_key(skey)):
					council.add_story_mult(_mult_key(skey), float(value))


static func _mult_key(effect_key: String) -> String:
	if TechTree.EFFECT_TO_MULT.has(effect_key):
		return TechTree.EFFECT_TO_MULT[effect_key]
	return effect_key.trim_suffix("_mult")


# ------------------------------------------------------------------ концовки

func _check_ending(day: int, m: Dictionary) -> void:
	if not world.ending_id.is_empty():
		return
	if world.act < 4 and not world.flags.has("ai_destroyed"):
		return
	if day < Balance.int_at("endings/min_days_before_ending", 120) and not world.flags.has("ai_destroyed"):
		return
	for e in _endings:
		if not (e is Dictionary):
			continue
		if not evaluate(e.get("conditions", []), "all", m):
			continue
		world.ending_id = String(e.get("id", ""))
		world.chronicle.post_raw("%s — %s" % [String(e.get("title", "")), String(e.get("text", ""))],
			day, Chronicle.CRITICAL, "milestone")
		ending_reached.emit(e)
		return
