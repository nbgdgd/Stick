class_name BattleFX
extends Node2D

## Визуализация боя (§6).
##
## Исход боя считает формула Tier 2 — здесь только показ уже известного
## результата. Ничего из нарисованного не влияет на состояние мира: иначе
## результат зависел бы от того, смотрит ли игрок на бой, и перемотка через
## снепшоты перестала бы воспроизводиться.
##
## Поэтому здесь обычный RandomNumberGenerator, а не SimRng.

class Tracer extends RefCounted:
	var from: Vector2
	var to: Vector2
	var life: float = 0.0
	var total: float = 0.18
	var hostile: bool = true


class Blast extends RefCounted:
	var pos: Vector2
	var life: float = 0.0
	var total: float = 0.55
	var radius: float = 26.0


const TRACER_COLOR_ATTACK := Color(1.0, 0.62, 0.35)
const TRACER_COLOR_DEFEND := Color(0.55, 0.88, 1.0)

var world: World

var _tracers: Array[Tracer] = []
var _blasts: Array[Blast] = []
var _rng := RandomNumberGenerator.new()
var _spawn_timer: float = 0.0


func setup(p_world: World) -> void:
	world = p_world
	z_index = 9
	_rng.randomize()


func step(delta: float) -> void:
	if world == null:
		return
	var active := not world.invasions.is_empty()

	if active:
		_spawn_timer -= delta
		if _spawn_timer <= 0.0:
			_spawn_timer = _rng.randf_range(0.02, 0.07)
			_spawn_volley()

	var i := _tracers.size() - 1
	while i >= 0:
		_tracers[i].life += delta
		if _tracers[i].life >= _tracers[i].total:
			_tracers.remove_at(i)
		i -= 1
	var j := _blasts.size() - 1
	while j >= 0:
		_blasts[j].life += delta
		if _blasts[j].life >= _blasts[j].total:
			_blasts.remove_at(j)
		j -= 1

	if active or not _tracers.is_empty() or not _blasts.is_empty():
		queue_redraw()


## Залп: трассеры между двумя линиями — десант со стороны моря и оборона со
## стороны поселения — плюс редкие разрывы.
func _spawn_volley() -> void:
	for inv in world.invasions:
		var p = inv.get("pos", [0.0, 0.0])
		var centre := Vector2(float(p[0]), float(p[1]))
		var axis := _approach_axis(inv, centre)
		var attack_line := centre - axis * 46.0
		var defend_line := centre + axis * 26.0
		var side := Vector2(-axis.y, axis.x)

		for _k in 4:
			var t := Tracer.new()
			var spread := _rng.randf_range(-52.0, 52.0)
			t.hostile = _rng.randf() < 0.55
			if t.hostile:
				t.from = attack_line + side * spread
				t.to = defend_line + side * _rng.randf_range(-34.0, 34.0)
			else:
				t.from = defend_line + side * spread * 0.6
				t.to = attack_line + side * _rng.randf_range(-46.0, 46.0)
			t.total = _rng.randf_range(0.18, 0.30)
			_tracers.append(t)

		if _rng.randf() < 0.45:
			var b := Blast.new()
			b.pos = centre + side * _rng.randf_range(-44.0, 44.0) + axis * _rng.randf_range(-30.0, 20.0)
			b.radius = _rng.randf_range(16.0, 34.0)
			b.total = _rng.randf_range(0.35, 0.7)
			_blasts.append(b)


## Направление подхода десанта: от ближайшей воды к поселению. Так линия боя
## всегда обращена к морю, а не поставлена случайно.
func _approach_axis(inv: Dictionary, centre: Vector2) -> Vector2:
	var target := world.settlement(int(inv.get("target", -1)))
	if target != null:
		var map_centre := Vector2(world.size, world.size) * 0.5 * world.tile_size
		var away := (target.pos - map_centre)
		if away.length() > 1.0:
			return away.normalized()
	return Vector2.RIGHT


func _draw() -> void:
	for t in _tracers:
		var k := 1.0 - t.life / maxf(0.001, t.total)
		var color := TRACER_COLOR_ATTACK if t.hostile else TRACER_COLOR_DEFEND
		# Трассер летит: рисуем короткий отрезок, бегущий от стрелка к цели.
		var head := t.from.lerp(t.to, 1.0 - k)
		var tail := t.from.lerp(t.to, maxf(0.0, 1.0 - k - 0.12))
		draw_line(tail, head, Color(color.r, color.g, color.b, 0.35 + k * 0.55), 1.6)

	for b in _blasts:
		var k2 := b.life / maxf(0.001, b.total)
		var r := b.radius * (0.35 + k2 * 1.25)
		var alpha := (1.0 - k2) * 0.75
		draw_arc(b.pos, r, 0.0, TAU, 20, Color(1.0, 0.75, 0.35, alpha), 2.4, true)
		draw_circle(b.pos, r * 0.35, Color(1.0, 0.85, 0.5, alpha * 0.5))


func clear() -> void:
	_tracers.clear()
	_blasts.clear()
	queue_redraw()
