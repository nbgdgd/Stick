class_name SimClock
extends RefCounted

## Симуляционный тик, отвязанный от кадра (§9): фиксированный шаг + аккумулятор.
## Render может проседать до 20 fps — темп симуляции от этого не меняется,
## меняется только количество тиков, обработанных за кадр.

signal ticked(tick_index: int)
signal day_started(day: int)

var ticks_per_day: int = 24
var base_ticks_per_second: float = 6.0
var speeds: PackedFloat64Array = PackedFloat64Array([0.0, 1.0, 3.0, 10.0, 30.0])
var max_ticks_per_frame: int = 400

var speed_index: int = 1
var tick_count: int = 0

var _accumulator: float = 0.0


func _init() -> void:
	var t := Balance.section("time")
	ticks_per_day = int(t.get("ticks_per_day", 24))
	base_ticks_per_second = float(t.get("base_ticks_per_second", 6.0))
	max_ticks_per_frame = int(t.get("max_ticks_per_frame", 400))
	var raw = t.get("speeds", [])
	if raw is Array and not raw.is_empty():
		speeds = PackedFloat64Array()
		for v in raw:
			speeds.append(float(v))


func speed() -> float:
	return speeds[clampi(speed_index, 0, speeds.size() - 1)]


func is_paused() -> bool:
	return speed() <= 0.0


func set_speed_index(i: int) -> void:
	speed_index = clampi(i, 0, speeds.size() - 1)
	if is_paused():
		_accumulator = 0.0


func toggle_pause() -> void:
	if is_paused():
		speed_index = maxi(1, speed_index)
		if speeds[speed_index] <= 0.0:
			speed_index = 1
	else:
		speed_index = 0


func speed_label() -> String:
	var s := speed()
	if s <= 0.0:
		return "II"
	return "x%d" % int(s)


## Вызывается из _process. Возвращает число выполненных тиков.
func advance(delta: float) -> int:
	if is_paused():
		return 0
	_accumulator += delta * base_ticks_per_second * speed()
	var budget := max_ticks_per_frame
	var done := 0
	while _accumulator >= 1.0 and done < budget:
		_accumulator -= 1.0
		_step()
		done += 1
	if done >= budget:
		# Не даём аккумулятору расти бесконечно, иначе после лага симуляция
		# будет "догонять" минутами и игра ощутится зависшей.
		_accumulator = minf(_accumulator, 1.0)
	return done


## Прямое проматывание — используется fast-forward'ом после загрузки снепшота
## и headless-тестами. Игнорирует паузу и бюджет кадра.
func run_ticks(count: int) -> void:
	for _i in maxi(0, count):
		_step()


func run_days(days: int) -> void:
	run_ticks(days * ticks_per_day)


func _step() -> void:
	if tick_count % ticks_per_day == 0:
		day_started.emit(day())
	ticked.emit(tick_count)
	tick_count += 1


func day() -> int:
	return tick_count / ticks_per_day


func tick_of_day() -> int:
	return tick_count % ticks_per_day


func day_fraction() -> float:
	return float(tick_of_day()) / float(ticks_per_day)


## Игровая дата для HUD: остров ведёт счёт от момента первого сброса.
func date_label() -> String:
	var d := day()
	return "Год %d · день %d" % [1 + d / 360, 1 + d % 360]


func get_state() -> Dictionary:
	return { "tick_count": tick_count, "speed_index": speed_index }


func set_state(d: Dictionary) -> void:
	tick_count = int(d.get("tick_count", 0))
	speed_index = int(d.get("speed_index", speed_index))
	_accumulator = 0.0
