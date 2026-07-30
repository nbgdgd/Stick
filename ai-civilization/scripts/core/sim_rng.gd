class_name SimRng
extends RefCounted

## Детерминированный ГПСЧ с сериализуемым состоянием.
##
## Каждый агрегированный объект (поселение, фракция, мир) владеет своим
## экземпляром, засеянным от (world_seed, owner_id). Это даёт две вещи:
##   1) многопоточный расчёт Tier 2 не зависит от порядка выполнения задач —
##      каждый поток трогает только свой SimRng;
##   2) снепшот + forward-replay воспроизводится бит-в-бит, что и делает
##      "перемотку назад" из §3 честной (load + fast-forward).

var _rng := RandomNumberGenerator.new()


func _init(seed_value: int = 0) -> void:
	_rng.seed = seed_value


const _MUL_A := 6364136223846793005
const _MUL_B := 1442695040888963407


## Смешивание (PCG-константы) — дешёвое и хорошо разносящее близкие id,
## чтобы соседние поселения не получали коррелированные последовательности.
static func hash_seed(world_seed: int, salt: int) -> int:
	var x: int = world_seed * _MUL_A + salt * _MUL_B
	x ^= x >> 29
	x *= _MUL_A
	x ^= x >> 32
	return x


func randf() -> float:
	return _rng.randf()


func randf_range(from: float, to: float) -> float:
	return _rng.randf_range(from, to)


func randi_range(from: int, to: int) -> int:
	return _rng.randi_range(from, to)


func chance(p: float) -> bool:
	return _rng.randf() < p


## Небольшой симметричный разброс: 1.0 ± amount.
func jitter(amount: float) -> float:
	return 1.0 + _rng.randf_range(-amount, amount)


func pick(arr: Array):
	if arr.is_empty():
		return null
	return arr[_rng.randi_range(0, arr.size() - 1)]


## Взвешенный выбор индекса. Возвращает -1, если суммарный вес нулевой.
func pick_weighted(weights: PackedFloat64Array) -> int:
	var total := 0.0
	for w in weights:
		total += maxf(0.0, w)
	if total <= 0.0:
		return -1
	var roll := _rng.randf() * total
	var acc := 0.0
	for i in weights.size():
		acc += maxf(0.0, weights[i])
		if roll <= acc:
			return i
	return weights.size() - 1


func get_state() -> Dictionary:
	return { "seed": _rng.seed, "state": _rng.state }


func set_state(d: Dictionary) -> void:
	_rng.seed = int(d.get("seed", 0))
	_rng.state = int(d.get("state", 0))
