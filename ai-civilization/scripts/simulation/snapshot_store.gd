class_name SnapshotStore
extends RefCounted

## Перемотка времени (§3).
##
## Честной обратной симуляции нет — она требует полной истории и хрупкого
## детерминизма во всех направлениях. Вместо неё периодические снепшоты
## состояния: «rewind» = загрузка ближайшего снепшота назад плюс ускоренный
## forward-replay до нужной точки. Игрок видит перемотку, движок делает
## load + fast-forward.
##
## Карта в снепшот не пишется: она детерминирована по world_seed и
## восстанавливается генерацией.

var world: World
var clock: SimClock

var interval_days: int = 5
var max_kept: int = 60

var _snaps: Array = []


func _init(p_world: World, p_clock: SimClock) -> void:
	world = p_world
	clock = p_clock
	interval_days = maxi(1, Balance.int_at("snapshots/interval_days", 5))
	max_kept = maxi(2, Balance.int_at("snapshots/max_kept", 60))


func maybe_capture(day: int) -> void:
	if day % interval_days != 0:
		return
	if not _snaps.is_empty() and int(_snaps[-1]["day"]) == day:
		return
	capture(day)


func capture(day: int) -> void:
	_snaps.append({
		"day": day,
		"world": world.to_dict(),
		"clock": clock.get_state(),
	})
	if _snaps.size() > max_kept:
		_snaps.remove_at(0)


func count() -> int:
	return _snaps.size()


func available_days() -> Array:
	var out := []
	for s in _snaps:
		out.append(int(s["day"]))
	return out


func earliest_day() -> int:
	if _snaps.is_empty():
		return 0
	return int(_snaps[0]["day"])


## Индекс последнего снепшота на день <= target_day.
func index_at_or_before(target_day: int) -> int:
	var found := -1
	for i in _snaps.size():
		if int(_snaps[i]["day"]) <= target_day:
			found = i
		else:
			break
	return found


## Восстанавливает состояние из снепшота. День, на который откатились,
## возвращается вызывающему — дальше он сам догоняет forward-replay'ем.
func restore(index: int) -> int:
	if index < 0 or index >= _snaps.size():
		return -1
	var snap: Dictionary = _snaps[index]
	world.load_dict(snap["world"])
	clock.set_state(snap["clock"])
	# Снепшоты «в будущем» относительно точки восстановления больше не валидны.
	_snaps = _snaps.slice(0, index + 1)
	return int(snap["day"])


func clear() -> void:
	_snaps.clear()


# ------------------------------------------------------------ сохранение на диск

## Бинарная сериализация, а не JSON: состояния SimRng — 64-битные целые,
## которые JSON превратил бы в float и сломал воспроизводимость.
func save_to_file(path: String) -> Error:
	var f := FileAccess.open(path, FileAccess.WRITE)
	if f == null:
		return FileAccess.get_open_error()
	f.store_var({
		"version": 1,
		"day": clock.day(),
		"world": world.to_dict(),
		"clock": clock.get_state(),
	}, true)
	f.close()
	return OK


func load_from_file(path: String) -> bool:
	if not FileAccess.file_exists(path):
		return false
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		return false
	var data = f.get_var(true)
	f.close()
	if not (data is Dictionary) or not (data as Dictionary).has("world"):
		return false
	world.load_dict(data["world"])
	clock.set_state(data.get("clock", {}))
	clear()
	capture(clock.day())
	return true
