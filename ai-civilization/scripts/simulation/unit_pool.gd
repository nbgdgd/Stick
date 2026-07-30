class_name UnitPool
extends Node2D

## Пул Tier 1 юнитов (§9): instantiate/queue_free на лету на Android даёт
## заметные микрофризы при каждом переезде камеры, поэтому все корпуса
## создаются один раз и переиспользуются.

var capacity: int = 0

var _free: Array[Tier1Unit] = []
var _active: Array[Tier1Unit] = []


func preallocate(p_capacity: int) -> void:
	capacity = maxi(0, p_capacity)
	for i in capacity:
		var u := Tier1Unit.new()
		u.name = "unit_%d" % i
		add_child(u)
		u.deactivate()
		_free.append(u)


func acquire() -> Tier1Unit:
	if _free.is_empty():
		return null
	var u: Tier1Unit = _free.pop_back()
	_active.append(u)
	return u


func release(u: Tier1Unit) -> void:
	if u == null:
		return
	var index := _active.find(u)
	if index < 0:
		return
	_active.remove_at(index)
	u.deactivate()
	_free.append(u)


func release_all() -> void:
	for u in _active:
		u.deactivate()
		_free.append(u)
	_active.clear()


func active() -> Array[Tier1Unit]:
	return _active


func active_count() -> int:
	return _active.size()


func free_count() -> int:
	return _free.size()
