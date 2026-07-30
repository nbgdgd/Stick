class_name Balance
extends RefCounted

## Единая точка доступа к data/*.json. Балансировочные параметры не хардкодятся
## в логике (§11) — правка json не требует изменений в скриптах.
##
## Реализовано статикой, а не автолоадом, намеренно: тот же код работает и в
## сцене, и в headless-тестах, запускаемых через `--script` (там автолоады
## не создаются).

const DATA_DIR := "res://data/"

static var _files: Dictionary = {}


static func file(name: String) -> Dictionary:
	if _files.has(name):
		return _files[name]
	var path := DATA_DIR + name + ".json"
	var loaded: Dictionary = {}
	if FileAccess.file_exists(path):
		var text := FileAccess.get_file_as_string(path)
		var parsed = JSON.parse_string(text)
		if parsed is Dictionary:
			loaded = parsed
		else:
			push_error("Balance: %s не является JSON-объектом" % path)
	else:
		push_error("Balance: не найден файл данных %s" % path)
	_files[name] = loaded
	return loaded


## Сброс кэша — используется тестами и hot-reload баланса в редакторе.
static func reload() -> void:
	_files.clear()


static func balance() -> Dictionary:
	return file("balance")


static func section(name: String) -> Dictionary:
	var s = balance().get(name, {})
	return s if s is Dictionary else {}


## Доступ по пути: Balance.num("population/upkeep_energy_per_pop", 0.1)
static func num(path: String, default_value: float = 0.0) -> float:
	var v = _walk(balance(), path)
	if v == null:
		return default_value
	return float(v)


static func int_at(path: String, default_value: int = 0) -> int:
	var v = _walk(balance(), path)
	if v == null:
		return default_value
	return int(v)


static func arr(path: String, default_value: Array = []) -> Array:
	var v = _walk(balance(), path)
	if v is Array:
		return v
	return default_value


## Диапазон [min, max] из json-массива двух чисел.
static func range_at(path: String, default_min: float = 0.0, default_max: float = 1.0) -> Vector2:
	var v = _walk(balance(), path)
	if v is Array and v.size() >= 2:
		return Vector2(float(v[0]), float(v[1]))
	return Vector2(default_min, default_max)


static func _walk(root: Dictionary, path: String):
	var node = root
	for part in path.split("/", false):
		if node is Dictionary and node.has(part):
			node = node[part]
		else:
			return null
	return node
