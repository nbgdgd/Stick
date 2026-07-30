class_name Chronicle
extends RefCounted

## Нарративный слой (§7): процедурная лента событий.
##
## Шаблоны лежат в data/narrative_templates.json, факты подставляются из
## симуляции. Заскриптованные сюжетные чекпоинты идут через post_raw с
## importance = CRITICAL, поэтому структура актов не растворяется в шуме.

signal entry_added(entry: Dictionary)

enum { LOW = 0, NORMAL = 1, HIGH = 2, CRITICAL = 3 }

const MAX_ENTRIES := 400

var entries: Array = []
var rng: SimRng

var _templates: Dictionary = {}
var _cell_names: Array = []
var _splinter_names: Array = []
var _incident_flavor: Array = []
var _name_cursor: int = 0


func _init(world_seed: int = 0) -> void:
	rng = SimRng.new(SimRng.hash_seed(world_seed, 424242))
	var data := Balance.file("narrative_templates")
	var t = data.get("templates", {})
	_templates = t if t is Dictionary else {}
	_cell_names = data.get("cell_names", [])
	_splinter_names = data.get("splinter_names", [])
	_incident_flavor = data.get("incident_flavor", [])


## Процедурный заголовок из шаблонов категории.
func post(category: String, day: int, facts: Dictionary = {}, importance: int = NORMAL) -> Dictionary:
	var pool = _templates.get(category, [])
	var text := ""
	if pool is Array and not (pool as Array).is_empty():
		text = String(rng.pick(pool)).format(facts)
	else:
		text = "%s %s" % [category, facts]
	return post_raw(text, day, importance, category)


func post_raw(text: String, day: int, importance: int = NORMAL, category: String = "raw") -> Dictionary:
	var entry := {
		"day": day,
		"text": text,
		"importance": importance,
		"category": category,
	}
	entries.append(entry)
	if entries.size() > MAX_ENTRIES:
		entries = entries.slice(entries.size() - MAX_ENTRIES)
	entry_added.emit(entry)
	return entry


func recent(count: int = 40) -> Array:
	if entries.size() <= count:
		return entries.duplicate()
	return entries.slice(entries.size() - count)


func next_cell_name() -> String:
	if _cell_names.is_empty():
		return "Ячейка-%d" % _name_cursor
	var base := String(_cell_names[_name_cursor % _cell_names.size()])
	var cycle := _name_cursor / _cell_names.size()
	_name_cursor += 1
	if cycle == 0:
		return base
	return "%s-%d" % [base, cycle + 1]


func splinter_name() -> String:
	if _splinter_names.is_empty():
		return "Отделившиеся"
	return String(rng.pick(_splinter_names))


func incident_flavor() -> String:
	if _incident_flavor.is_empty():
		return "неустановленный инцидент"
	return String(rng.pick(_incident_flavor))


func get_state() -> Dictionary:
	return {
		"entries": entries.duplicate(true),
		"name_cursor": _name_cursor,
		"rng": rng.get_state(),
	}


func set_state(d: Dictionary) -> void:
	entries = (d.get("entries", []) as Array).duplicate(true)
	_name_cursor = int(d.get("name_cursor", 0))
	var rs = d.get("rng", {})
	if rs is Dictionary:
		rng.set_state(rs)
