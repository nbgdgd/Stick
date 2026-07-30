class_name Faction
extends RefCounted

## Фракция. Один класс покрывает три роли:
##   * совет ИИ (основная фракция игрока-наблюдателя);
##   * отколовшиеся кланы (§6, механика лояльности) — те же поля, свой capital;
##   * человечество — is_human, живёт агрегатами материка, без поселений на карте.

var id: int = 0
var name: String = ""
var is_human: bool = false
var is_council: bool = false
var parent_id: int = -1
var alive: bool = true
var color: Color = Color.WHITE
var founded_day: int = 0

## Какой набор спрайтов Kenney рисовать за эту фракцию: "blue" — совет,
## "orange" — отделившиеся, "red" — материк. Хранится строкой, а не цветом,
## потому что файлы ассетов подобраны наборами и оттенок между ними не
## интерполируется.
var sprite_set: String = "blue"

# --- технологии ---
var unlocked: Dictionary = {}
var progress: Dictionary = {}
var focus: PackedStringArray = PackedStringArray()
var research_points: float = 0.0
var research_spent: float = 0.0
var last_refocus_day: int = -999

# --- производные модификаторы ---
var mult: Dictionary = {}
var flags: Dictionary = {}
var story_mult: Dictionary = {}

## Производственная политика: "auto" — симуляция сама переключается между
## мирным и военным распределением; остальные значения выставляет игрок
## как стратегический рычаг Акта 3 (§2), без микроконтроля юнитов.
var policy: String = "auto"

# --- война ---
var at_war_with: Dictionary = {}
var war_score: float = 0.0
var battles_won: int = 0
var battles_lost: int = 0

# --- только для людей ---
var population: float = 0.0
var industry: float = 0.0
var industry_initial: float = 1.0
var morale: float = 1.0
var human_tech_index: int = 0
var human_tech_days: float = 0.0
var next_invasion_day: int = 0

var rng: SimRng


func _init() -> void:
	rng = SimRng.new(0)
	mult = Production.base_multipliers()


static func create_ai(p_id: int, p_name: String, p_color: Color, world_seed: int, day: int) -> Faction:
	var f := Faction.new()
	f.id = p_id
	f.name = p_name
	f.color = p_color
	f.founded_day = day
	f.sprite_set = "blue" if p_id == 0 else "orange"
	f.rng = SimRng.new(SimRng.hash_seed(world_seed, 100003 + p_id))
	f.refresh_multipliers()
	return f


static func create_human(p_id: int, world_seed: int) -> Faction:
	var f := Faction.new()
	f.id = p_id
	f.name = "Человечество"
	f.is_human = true
	# Красный: и в HUD, и на спрайтах материк должен читаться как противник.
	f.color = Color(0.88, 0.42, 0.36)
	f.sprite_set = "red"
	f.rng = SimRng.new(SimRng.hash_seed(world_seed, 777001))
	f.population = Balance.num("human/start_population", 8200.0)
	f.industry = Balance.num("human/start_industry", 1000.0)
	f.industry_initial = f.industry
	f.next_invasion_day = 0
	return f


func tech_level() -> int:
	if is_human:
		return human_tech_index
	return unlocked.size()


func has_flag(flag: String) -> bool:
	return bool(flags.get(flag, false))


func is_at_war() -> bool:
	return not at_war_with.is_empty()


## Пересчёт мультипликаторов: технологии + постоянные эффекты сюжетных решений.
func refresh_multipliers() -> void:
	var eff := TechTree.effects_for(unlocked)
	mult = eff["mult"]
	flags = eff["flags"]
	for key in story_mult.keys():
		mult[key] = float(mult.get(key, 1.0)) + float(story_mult[key])
	# Отрицательные множители ломают экономику — жёсткий нижний предел.
	for key in mult.keys():
		mult[key] = maxf(0.05, float(mult[key]))


func add_story_mult(key: String, delta: float) -> void:
	story_mult[key] = float(story_mult.get(key, 0.0)) + delta
	refresh_multipliers()


func combat_multiplier() -> float:
	var exponent := Balance.num("combat/tech_strength_exponent", 1.25)
	if is_human:
		var m := 1.0
		var techs := TechTree.human_techs()
		for i in mini(human_tech_index + 1, techs.size()):
			m += techs[i].combat_mult
		return m * morale
	return float(mult.get("combat", 1.0)) * pow(1.0 + 0.06 * float(unlocked.size()), exponent)


func human_industry_multiplier() -> float:
	var m := 1.0
	var techs := TechTree.human_techs()
	for i in mini(human_tech_index + 1, techs.size()):
		m += techs[i].industry_mult
	return m


func human_strength() -> float:
	return industry * Balance.num("human/strength_per_industry", 0.085) * combat_multiplier()


func to_dict() -> Dictionary:
	return {
		"id": id,
		"name": name,
		"is_human": is_human,
		"is_council": is_council,
		"parent_id": parent_id,
		"alive": alive,
		"color": [color.r, color.g, color.b],
		"founded_day": founded_day,
		"unlocked": unlocked.duplicate(),
		"progress": progress.duplicate(),
		"focus": Array(focus),
		"research_points": research_points,
		"research_spent": research_spent,
		"last_refocus_day": last_refocus_day,
		"story_mult": story_mult.duplicate(),
		"policy": policy,
		"sprite_set": sprite_set,
		"at_war_with": at_war_with.duplicate(),
		"war_score": war_score,
		"battles_won": battles_won,
		"battles_lost": battles_lost,
		"population": population,
		"industry": industry,
		"industry_initial": industry_initial,
		"morale": morale,
		"human_tech_index": human_tech_index,
		"human_tech_days": human_tech_days,
		"next_invasion_day": next_invasion_day,
		"rng": rng.get_state(),
	}


static func from_dict(d: Dictionary) -> Faction:
	var f := Faction.new()
	f.id = int(d.get("id", 0))
	f.name = String(d.get("name", ""))
	f.is_human = bool(d.get("is_human", false))
	f.is_council = bool(d.get("is_council", false))
	f.parent_id = int(d.get("parent_id", -1))
	f.alive = bool(d.get("alive", true))
	var c = d.get("color", [1.0, 1.0, 1.0])
	f.color = Color(float(c[0]), float(c[1]), float(c[2]))
	f.founded_day = int(d.get("founded_day", 0))
	f.unlocked = (d.get("unlocked", {}) as Dictionary).duplicate()
	f.progress = (d.get("progress", {}) as Dictionary).duplicate()
	f.focus = PackedStringArray()
	for s in d.get("focus", []):
		f.focus.append(String(s))
	f.research_points = float(d.get("research_points", 0.0))
	f.research_spent = float(d.get("research_spent", 0.0))
	f.last_refocus_day = int(d.get("last_refocus_day", -999))
	f.story_mult = (d.get("story_mult", {}) as Dictionary).duplicate()
	f.policy = String(d.get("policy", "auto"))
	f.sprite_set = String(d.get("sprite_set", "blue"))
	f.at_war_with = (d.get("at_war_with", {}) as Dictionary).duplicate()
	f.war_score = float(d.get("war_score", 0.0))
	f.battles_won = int(d.get("battles_won", 0))
	f.battles_lost = int(d.get("battles_lost", 0))
	f.population = float(d.get("population", 0.0))
	f.industry = float(d.get("industry", 0.0))
	f.industry_initial = float(d.get("industry_initial", 1.0))
	f.morale = float(d.get("morale", 1.0))
	f.human_tech_index = int(d.get("human_tech_index", 0))
	f.human_tech_days = float(d.get("human_tech_days", 0.0))
	f.next_invasion_day = int(d.get("next_invasion_day", 0))
	var rs = d.get("rng", {})
	if rs is Dictionary:
		f.rng.set_state(rs)
	f.refresh_multipliers()
	return f
