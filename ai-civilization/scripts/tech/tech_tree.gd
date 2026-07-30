class_name TechTree
extends RefCounted

## Тех-дерево (§5).
##
## Ветка ИИ нелинейна и эмерджентна: порядок исследований не задан, вес выбора
## каждого направления пересчитывается из состояния симуляции. Дефицит энергии
## сам вытолкнет «Энергонезависимость» наверх, война — «Военную робототехнику».
## Ветка людей, наоборот, линейна и предсказуема по дням — чтобы отставание
## материка читалось как разница в скорости итерации, а не как чит.

class Tech extends RefCounted:
	var id: String = ""
	var name: String = ""
	var desc: String = ""
	var cost: float = 0.0
	var requires: PackedStringArray = PackedStringArray()
	var base_weight: float = 1.0
	var weights: Dictionary = {}
	var effects: Dictionary = {}


class HumanTech extends RefCounted:
	var id: String = ""
	var name: String = ""
	var days: float = 0.0
	var industry_mult: float = 0.0
	var combat_mult: float = 0.0


## Соответствие «эффект технологии → ключ мультипликатора производства».
const EFFECT_TO_MULT := {
	"all_rates_mult": "all_rates",
	"scavenge_mult": "scavenge",
	"power_mult": "power",
	"smelt_mult": "smelt",
	"refine_mult": "refine",
	"fab_mult": "fab",
	"assemble_mult": "assemble",
	"research_mult": "research",
	"military_mult": "military",
	"combat_mult": "combat",
	"death_mult": "death",
	"upkeep_mult": "upkeep",
	"trade_mult": "trade",
	"recycle_mult": "recycle",
}

const FLAG_EFFECTS := ["espionage", "singularity", "relations_pressure"]

static var _ai: Array[Tech] = []
static var _ai_by_id: Dictionary = {}
static var _human: Array[HumanTech] = []


static func _ensure_loaded() -> void:
	if not _ai.is_empty():
		return
	var data := Balance.file("tech_ai")
	var list = data.get("techs", [])
	if list is Array:
		for entry in list:
			if not (entry is Dictionary):
				continue
			var t := Tech.new()
			t.id = String(entry.get("id", ""))
			t.name = String(entry.get("name", t.id))
			t.desc = String(entry.get("desc", ""))
			t.cost = float(entry.get("cost", 100.0))
			var req = entry.get("requires", [])
			if req is Array:
				for r in req:
					t.requires.append(String(r))
			t.base_weight = float(entry.get("base_weight", 1.0))
			var w = entry.get("weights", {})
			t.weights = w if w is Dictionary else {}
			var e = entry.get("effects", {})
			t.effects = e if e is Dictionary else {}
			_ai.append(t)
			_ai_by_id[t.id] = t

	var hdata := Balance.file("tech_human")
	var hlist = hdata.get("techs", [])
	if hlist is Array:
		for entry in hlist:
			if not (entry is Dictionary):
				continue
			var h := HumanTech.new()
			h.id = String(entry.get("id", ""))
			h.name = String(entry.get("name", h.id))
			h.days = float(entry.get("days", 0.0))
			h.industry_mult = float(entry.get("industry_mult", 0.0))
			h.combat_mult = float(entry.get("combat_mult", 0.0))
			_human.append(h)


static func ai_techs() -> Array[Tech]:
	_ensure_loaded()
	return _ai


static func human_techs() -> Array[HumanTech]:
	_ensure_loaded()
	return _human


static func get_tech(id: String) -> Tech:
	_ensure_loaded()
	return _ai_by_id.get(id, null)


static func tech_name(id: String) -> String:
	var t := get_tech(id)
	return t.name if t != null else id


static func total_ai_techs() -> int:
	return ai_techs().size()


## Технологии, чьи предпосылки выполнены и которые ещё не изучены.
static func available(unlocked: Dictionary) -> Array[Tech]:
	var out: Array[Tech] = []
	for t in ai_techs():
		if unlocked.has(t.id):
			continue
		var ok := true
		for r in t.requires:
			if not unlocked.has(r):
				ok = false
				break
		if ok:
			out.append(t)
	return out


## Вес выбора направления. Условия — нормированные 0..1 значения из симуляции,
## так что множитель применяется пропорционально остроте ситуации:
##   вес = base * произведение(1 + (mult - 1) * значение_условия)
static func weight_for(t: Tech, conditions: Dictionary) -> float:
	var w := maxf(0.0001, t.base_weight)
	for key in t.weights.keys():
		var value := clampf(float(conditions.get(key, 0.0)), 0.0, 1.0)
		if value <= 0.0:
			continue
		var mult := float(t.weights[key])
		w *= maxf(0.02, 1.0 + (mult - 1.0) * value)
	return w


## Выбор до `count` параллельных направлений исследования.
static func choose_focus(unlocked: Dictionary, conditions: Dictionary, rng: SimRng, count: int) -> PackedStringArray:
	var pool := available(unlocked)
	var out := PackedStringArray()
	if pool.is_empty():
		return out
	var weights := PackedFloat64Array()
	for t in pool:
		weights.append(weight_for(t, conditions))
	for _i in mini(count, pool.size()):
		var idx := rng.pick_weighted(weights)
		if idx < 0:
			break
		out.append(pool[idx].id)
		weights[idx] = 0.0
	return out


## Суммарные эффекты изученного, наложенные на базовые мультипликаторы.
static func effects_for(unlocked: Dictionary) -> Dictionary:
	var mult := Production.base_multipliers()
	var flags := {}
	for id in unlocked.keys():
		var t := get_tech(String(id))
		if t == null:
			continue
		for key in t.effects.keys():
			var skey := String(key)
			if EFFECT_TO_MULT.has(skey):
				var mkey: String = EFFECT_TO_MULT[skey]
				mult[mkey] = float(mult.get(mkey, 1.0)) + float(t.effects[key])
			elif skey in FLAG_EFFECTS:
				flags[skey] = true
	return { "mult": mult, "flags": flags }


## Нормированные условия для весов выбора. Собираются из состояния мира —
## это и есть «эмерджентность» из §5: симуляция сама задаёт приоритеты.
static func build_conditions(state: Dictionary) -> Dictionary:
	return {
		"early_game": clampf(1.0 - float(state.get("day", 0)) / 90.0, 0.0, 1.0),
		"energy_deficit": clampf(float(state.get("energy_deficit", 0.0)) * 2.5, 0.0, 1.0),
		"scrap_scarcity": clampf(1.0 - float(state.get("deposit_richness", 1.0)), 0.0, 1.0),
		"unrest": clampf(float(state.get("unrest", 0.0)) * 1.5, 0.0, 1.0),
		"population_pressure": clampf(float(state.get("population", 0.0)) / 600.0, 0.0, 1.0),
		"prosperity": clampf(float(state.get("output_per_pop", 0.0)) / 3.0, 0.0, 1.0),
		"tech_behind": clampf(-float(state.get("tech_gap", 0.0)) / 3.0, 0.0, 1.0),
		"threat": clampf((60.0 - float(state.get("relations", 60.0))) / 60.0, 0.0, 1.0),
		"at_war": 1.0 if bool(state.get("at_war", false)) else 0.0,
		"internal_conflict": 1.0 if bool(state.get("internal_conflict", false)) else 0.0,
		"act2": 1.0 if int(state.get("act", 1)) >= 2 else 0.0,
		"act3": 1.0 if int(state.get("act", 1)) >= 3 else 0.0,
	}


static func reload() -> void:
	_ai.clear()
	_ai_by_id.clear()
	_human.clear()
