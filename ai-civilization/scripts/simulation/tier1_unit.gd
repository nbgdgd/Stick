class_name Tier1Unit
extends Node2D

## Tier 1 — детальный юнит в зоне просмотра игрока (§0).
##
## ВАЖНО: юнит — представление, а не источник истины. Он визуализирует уже
## посчитанный агрегат: один корпус на экране «представляет» represents единиц
## населения. Ничего из того, что он делает, не пишется в состояние симуляции.
##
## Это не срезание угла, а требование корректности: если бы юниты влияли на
## экономику, результат зависел бы от того, куда смотрит камера, и перемотка
## через снепшоты (§3) перестала бы воспроизводиться. По той же причине здесь
## обычный RandomNumberGenerator, а не SimRng.

enum Kind { WORKER, HAULER, GUARD, SOLDIER }
enum State { IDLE, TO_TARGET, WORK, RETURN, FIGHT }

## Вид техники по роли; набор (цвет фракции) подставляется отдельно.
const KIND_NAMES := ["truck", "truck_load", "tank", "soldier"]

var kind: int = Kind.WORKER
var home_id: int = -1
var represents: float = 1.0
var state: int = State.IDLE

var home_pos: Vector2 = Vector2.ZERO
var target: Vector2 = Vector2.ZERO
var speed: float = 26.0
var work_left: float = 0.0
var wander_radius: float = 140.0

var sprite_set: String = "blue"

var _sprite: Sprite2D
var _rng := RandomNumberGenerator.new()
var _blocked_turns: int = 0


func _ready() -> void:
	_sprite = Sprite2D.new()
	_sprite.centered = true
	add_child(_sprite)
	set_process(false)


func configure(p_kind: int, p_home_id: int, p_home_pos: Vector2, p_represents: float, seed_value: int, p_sprite_set: String = "blue") -> void:
	kind = p_kind
	sprite_set = p_sprite_set
	home_id = p_home_id
	home_pos = p_home_pos
	represents = p_represents
	_rng.seed = seed_value
	if _sprite != null:
		_sprite.texture = Art.unit(sprite_set, KIND_NAMES[clampi(kind, 0, KIND_NAMES.size() - 1)])
		_sprite.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
		# Техника 16 px рядом с постройкой в 2.4 масштаба должна быть мельче её.
		_sprite.scale = Vector2.ONE * 0.85
	speed = 26.0 + _rng.randf_range(-6.0, 10.0)
	if kind == Kind.SOLDIER or kind == Kind.GUARD:
		speed *= 1.25
	position = home_pos + _random_offset(18.0)
	state = State.IDLE
	work_left = _rng.randf_range(0.0, 1.2)
	visible = true


func tint(color: Color) -> void:
	if _sprite != null:
		_sprite.modulate = color


## Шаг представления. delta уже умножен на текущий множитель скорости времени,
## поэтому на x30 юниты бегают быстрее, а не «телепортируются раз в кадр».
func step(delta: float, world: World) -> void:
	match state:
		State.IDLE:
			work_left -= delta
			if work_left <= 0.0:
				_choose_target(world)
		State.TO_TARGET:
			if _move_towards(target, delta, world):
				state = State.WORK
				work_left = _rng.randf_range(0.8, 2.2)
		State.WORK:
			work_left -= delta
			if work_left <= 0.0:
				state = State.RETURN
				target = home_pos + _random_offset(20.0)
		State.RETURN:
			if _move_towards(target, delta, world):
				state = State.IDLE
				work_left = _rng.randf_range(0.2, 1.0)
		State.FIGHT:
			# Бой уже разрешён формулой Tier 2 — здесь только суета вокруг точки.
			if _move_towards(target, delta, world):
				target = home_pos + _random_offset(34.0)


func _choose_target(world: World) -> void:
	if kind == Kind.GUARD:
		target = home_pos + _random_offset(wander_radius * 0.5)
		state = State.TO_TARGET
		return
	# Сборщики идут к самому «богатому» из нескольких случайных тайлов рядом:
	# на глаз это читается как работа с отвалами, а не как случайное блуждание.
	var best := home_pos
	var best_score := -1.0
	for _i in 4:
		var candidate := home_pos + _random_offset(wander_radius)
		var tile := world.tile_at(candidate)
		if not world.is_land(tile):
			continue
		var score := world.scrap_at(tile)
		if score > best_score:
			best_score = score
			best = candidate
	target = best
	state = State.TO_TARGET


func _random_offset(radius: float) -> Vector2:
	var angle := _rng.randf_range(0.0, TAU)
	return Vector2(cos(angle), sin(angle)) * _rng.randf_range(radius * 0.25, radius)


## Возвращает true, когда цель достигнута. Обход воды — простое отклонение
## направления: полноценная навигация здесь не окупается, юниты живут в
## пределах одного поселения и видны десятки секунд.
func _move_towards(dest: Vector2, delta: float, world: World) -> bool:
	var to := dest - position
	var dist := to.length()
	if dist < 4.0:
		_blocked_turns = 0
		return true
	var dir := to / dist
	var step_len := minf(speed * delta, dist)
	var next := position + dir * step_len
	if not world.is_land_pos(next):
		_blocked_turns += 1
		if _blocked_turns > 6:
			position = home_pos
			_blocked_turns = 0
			return true
		var deflected := dir.rotated(_rng.randf_range(0.7, 2.0) * (1.0 if _rng.randf() < 0.5 else -1.0))
		next = position + deflected * step_len
		if not world.is_land_pos(next):
			return false
	position = next
	if _sprite != null:
		_sprite.flip_h = dir.x < 0.0
	return false


func deactivate() -> void:
	visible = false
	state = State.IDLE
	home_id = -1
