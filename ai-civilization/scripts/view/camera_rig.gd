class_name CameraRig
extends Camera2D

## Камера под тач-управление (§10, M7): один палец — панорамирование,
## два — зум, короткое касание без сдвига — тап (цель для вмешательств и
## выбора поселения). Колесо мыши работает тоже: без него неудобно
## отлаживать на настольной машине.

signal tapped(world_position: Vector2)

## Порог тапа задан в dp, а не в пикселях: на экране 461 ppi прежние
## 14 единиц вьюпорта = 1.2 мм, и палец в такое окно не попадает.
const TAP_MAX_DRIFT_DP := 14.0
const TAP_MAX_TIME := 0.6

var min_zoom: float = 0.35
var max_zoom: float = 3.0
var map_size: float = 3072.0

var _touches: Dictionary = {}
var _press_position: Vector2 = Vector2.ZERO
var _press_time: float = 0.0
var _drift: float = 0.0
var _pinch_distance: float = 0.0


func setup(p_map_size: float) -> void:
	map_size = p_map_size
	position = Vector2(map_size, map_size) * 0.5
	zoom = Vector2.ONE
	# Ограничители не дают уехать в пустоту за пределами острова.
	limit_left = -200
	limit_top = -200
	limit_right = int(map_size) + 200
	limit_bottom = int(map_size) + 200
	position_smoothing_enabled = false


func view_scale() -> float:
	# Чем сильнее отдалили камеру, тем больше площади в кадре — Tier 1 радиус
	# должен масштабироваться вместе с этим, иначе на общем плане юниты видны
	# только у одной ячейки.
	return 1.0 / maxf(0.05, zoom.x)


func focus_on(world_position: Vector2) -> void:
	position = world_position


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		_handle_touch(event)
	elif event is InputEventScreenDrag:
		_handle_drag(event)
	elif event is InputEventMouseButton:
		var mb := event as InputEventMouseButton
		if mb.pressed and mb.button_index == MOUSE_BUTTON_WHEEL_UP:
			_apply_zoom(1.12)
		elif mb.pressed and mb.button_index == MOUSE_BUTTON_WHEEL_DOWN:
			_apply_zoom(1.0 / 1.12)


## Перевод dp в единицы вьюпорта. Координаты тач-событий приходят уже
## поделёнными на коэффициент растяжения canvas_items, поэтому порог,
## осмысленный в физических миллиметрах, нужно пересчитывать в оба конца.
func _dp_to_units() -> float:
	var dpi := DisplayServer.screen_get_dpi()
	if dpi <= 0:
		dpi = 160
	var px_per_dp := float(dpi) / 160.0
	var stretch := 1.0
	var win := get_window()
	var logical := get_viewport_rect().size
	if win != null and logical.x > 1.0:
		stretch = float(win.size.x) / logical.x
	return px_per_dp / maxf(0.01, stretch)


func tap_max_drift() -> float:
	return TAP_MAX_DRIFT_DP * _dp_to_units()


func _handle_touch(event: InputEventScreenTouch) -> void:
	if event.pressed:
		_touches[event.index] = event.position
		if _touches.size() == 1:
			_press_position = event.position
			_press_time = 0.0
			_drift = 0.0
		elif _touches.size() == 2:
			_pinch_distance = _current_pinch_distance()
	else:
		var was_single := _touches.size() == 1
		_touches.erase(event.index)
		if was_single and _drift <= tap_max_drift() and _press_time < TAP_MAX_TIME:
			tapped.emit(screen_to_world(event.position))
		if _touches.size() < 2:
			_pinch_distance = 0.0


func _handle_drag(event: InputEventScreenDrag) -> void:
	_touches[event.index] = event.position
	if _touches.size() >= 2:
		var d := _current_pinch_distance()
		if _pinch_distance > 1.0 and d > 1.0:
			_apply_zoom(d / _pinch_distance)
		_pinch_distance = d
		return
	# Смещение от точки нажатия, а НЕ сумма длины пути. Накопление пути
	# отвергало тап тем вернее, чем дольше держишь палец: дрожание на плотном
	# экране добавлялось монотонно, даже если палец вернулся в ту же точку.
	# Именно из-за этого наведённые вмешательства «не работали».
	_drift = maxf(_drift, event.position.distance_to(_press_position))
	# Перемещение в мировых координатах, поэтому карта «прилипает» к пальцу
	# одинаково на любом зуме.
	position -= event.relative / zoom


func _current_pinch_distance() -> float:
	var points := _touches.values()
	if points.size() < 2:
		return 0.0
	return (points[0] as Vector2).distance_to(points[1] as Vector2)


func _apply_zoom(factor: float) -> void:
	var z := clampf(zoom.x * factor, min_zoom, max_zoom)
	zoom = Vector2(z, z)


func screen_to_world(screen_position: Vector2) -> Vector2:
	return get_canvas_transform().affine_inverse() * screen_position


func _process(delta: float) -> void:
	if _touches.size() == 1:
		_press_time += delta
	_keyboard_pan(delta)


## Панорамирование с клавиатуры — только для отладки на настольной машине.
func _keyboard_pan(delta: float) -> void:
	var dir := Vector2.ZERO
	if Input.is_key_pressed(KEY_A) or Input.is_key_pressed(KEY_LEFT):
		dir.x -= 1.0
	if Input.is_key_pressed(KEY_D) or Input.is_key_pressed(KEY_RIGHT):
		dir.x += 1.0
	if Input.is_key_pressed(KEY_W) or Input.is_key_pressed(KEY_UP):
		dir.y -= 1.0
	if Input.is_key_pressed(KEY_S) or Input.is_key_pressed(KEY_DOWN):
		dir.y += 1.0
	if dir != Vector2.ZERO:
		position += dir.normalized() * 600.0 * delta / zoom.x
