class_name Recipe
extends RefCounted

## Узел производственной цепочки: сырьё → компонент → продукт (§4).
## Определения читаются из data/recipes.json и кэшируются статически.

var id: String = ""
var job: int = 0
var tier: int = 0
var tech: String = ""
var rate: float = 0.0
var scales_with_deposit: bool = false
var strength_gain: float = 0.0
var inputs: PackedFloat64Array
var outputs: PackedFloat64Array

static var _all: Array[Recipe] = []
static var _by_job: Array = []
static var _default_alloc: PackedFloat64Array
static var _war_alloc: PackedFloat64Array


func _init() -> void:
	inputs = Res.empty_stock()
	outputs = Res.empty_stock()


static func _ensure_loaded() -> void:
	if not _all.is_empty():
		return
	var data := Balance.file("recipes")
	var list = data.get("recipes", [])
	_by_job = []
	for _i in Job.COUNT:
		_by_job.append([])
	if list is Array:
		for entry in list:
			if not (entry is Dictionary):
				continue
			var r := Recipe.new()
			r.id = String(entry.get("id", ""))
			r.job = Job.id_to_index(String(entry.get("job", "")))
			if r.job < 0:
				push_error("Recipe %s: неизвестная специальность '%s'" % [r.id, entry.get("job", "")])
				continue
			r.tier = int(entry.get("tier", 0))
			r.tech = String(entry.get("tech", ""))
			r.rate = float(entry.get("rate", 0.0))
			r.scales_with_deposit = bool(entry.get("scales_with_deposit", false))
			r.strength_gain = float(entry.get("strength_gain", 0.0))
			_fill(r.inputs, entry.get("inputs", {}))
			_fill(r.outputs, entry.get("outputs", {}))
			_all.append(r)
			(_by_job[r.job] as Array).append(r)
	for job_list in _by_job:
		(job_list as Array).sort_custom(func(a: Recipe, b: Recipe) -> bool: return a.tier > b.tier)

	_default_alloc = Job.normalized(Job.alloc_from_dict(data.get("default_allocation", {})))
	_war_alloc = Job.normalized(Job.alloc_from_dict(data.get("war_allocation", {})))


static func _fill(target: PackedFloat64Array, d) -> void:
	if not (d is Dictionary):
		return
	for key in (d as Dictionary).keys():
		var idx := Res.id_to_index(String(key))
		if idx >= 0:
			target[idx] = float(d[key])
		else:
			push_error("Recipe: неизвестный ресурс '%s'" % key)


static func all() -> Array[Recipe]:
	_ensure_loaded()
	return _all


static func for_job(job: int) -> Array:
	_ensure_loaded()
	if job < 0 or job >= _by_job.size():
		return []
	return _by_job[job]


## Самый продвинутый рецепт специальности, доступный при текущем наборе техов.
## Именно здесь тех-дерево превращается в экономический эффект: разблокировка
## переключает поселения на следующий tier без какого-либо микроменеджмента.
static func best_for_job(job: int, unlocked: Dictionary) -> Recipe:
	for r in for_job(job):
		var recipe := r as Recipe
		if recipe.tech.is_empty() or unlocked.has(recipe.tech):
			return recipe
	return null


## Все разблокированные рецепты специальности, от старшего tier к младшему.
## Нужны именно все: старший рецепт может простаивать из-за отсутствия входов
## (солнечные массивы без сплава), и тогда поселение обязано откатиться на
## предыдущий процесс, а не остановить производство совсем.
static func unlocked_for_job(job: int, unlocked: Dictionary) -> Array:
	var out: Array = []
	for r in for_job(job):
		var recipe := r as Recipe
		if recipe.tech.is_empty() or unlocked.has(recipe.tech):
			out.append(recipe)
	return out


static func default_allocation() -> PackedFloat64Array:
	_ensure_loaded()
	return _default_alloc.duplicate()


static func war_allocation() -> PackedFloat64Array:
	_ensure_loaded()
	return _war_alloc.duplicate()


static func reload() -> void:
	_all.clear()
	_by_job.clear()
