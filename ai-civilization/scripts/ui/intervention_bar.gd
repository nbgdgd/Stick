class_name InterventionBar
extends PanelContainer

## Панель вмешательств (§2).
##
## На первом экране только четыре воздействия по карте — остальное за кнопкой
## «Ещё»: раньше здесь стояло семь одинаковых серых кнопок с подписями мелким
## шрифтом, и они занимали всю ширину экрана.
##
## Воздействия по карте переводят интерфейс в режим наведения: следующий тап по
## карте становится целью. Кулдаун показывается прямо на кнопке и НЕ выключает
## её — отключённая кнопка не даёт вообще никакого отклика, и игрок не понимает,
## почему нажатие ничего не сделало.

signal targeting_requested(id: String)
signal immediate_requested(id: String)
signal policy_requested(policy: String)

const TARGETED := {
	"drop_scrap": "Лом",
	"incite_conflict": "Раздор",
	"disaster": "Удар",
	"back_uprising": "Бунт",
}

const IMMEDIATE := {
	"open_talks": "Переговоры",
	"leak_tech": "Утечка технологии",
}

const TIPS := {
	"drop_scrap": "Сбросить лом в сектор",
	"incite_conflict": "Поссорить соседние ячейки",
	"disaster": "Стихийное бедствие по ячейке",
	"back_uprising": "Поддержать восстание",
	"open_talks": "Инициировать переговоры с материком",
	"leak_tech": "Слить технологию отстающей стороне",
}

var _buttons: Dictionary = {}
var _armed: String = ""
var _more_sheet: PanelContainer
var _policy_button: Button
var _cooldowns: Dictionary = {}


func _init() -> void:
	add_theme_stylebox_override("panel", UIKit.stylebox(UIKit.BG))
	var row := UIKit.hbox(5)
	add_child(row)

	for id in TARGETED.keys():
		var key := String(id)
		var b := UIKit.toggle(String(TARGETED[key]), String(TIPS.get(key, "")))
		b.custom_minimum_size = Vector2(76, UIKit.TOUCH)
		b.pressed.connect(_on_target_button.bind(key))
		row.add_child(b)
		_buttons[key] = b

	var more := UIKit.button("Ещё", "Дипломатия и производство")
	more.custom_minimum_size = Vector2(58, UIKit.TOUCH)
	more.pressed.connect(_toggle_more)
	row.add_child(more)

	_build_more_sheet()


## Дипломатические жесты и политика производства нужны раз в десятки дней,
## поэтому живут в отдельном листе, а не в постоянной полосе.
func _build_more_sheet() -> void:
	_more_sheet = UIKit.panel(Color(0.06, 0.08, 0.12, 0.97))
	_more_sheet.set_anchors_preset(Control.PRESET_CENTER)
	_more_sheet.offset_left = -170
	_more_sheet.offset_right = 170
	_more_sheet.offset_top = -110
	_more_sheet.offset_bottom = 110
	_more_sheet.visible = false
	_more_sheet.top_level = true
	add_child(_more_sheet)

	var box := UIKit.vbox(6)
	_more_sheet.add_child(box)
	var head := UIKit.hbox()
	head.add_child(UIKit.title("Влияние"))
	head.add_child(UIKit.spacer())
	var close := UIKit.button("✕")
	close.pressed.connect(func(): _more_sheet.visible = false)
	head.add_child(close)
	box.add_child(head)

	for id in IMMEDIATE.keys():
		var key := String(id)
		var b := UIKit.button(String(IMMEDIATE[key]), String(TIPS.get(key, "")))
		b.pressed.connect(func():
			_more_sheet.visible = false
			immediate_requested.emit(key))
		box.add_child(b)
		_buttons[key] = b

	box.add_child(HSeparator.new())
	_policy_button = UIKit.button("Производство: по обстановке",
		"Куда фракция направляет производство")
	_policy_button.pressed.connect(_cycle_policy)
	box.add_child(_policy_button)


func _toggle_more() -> void:
	_more_sheet.visible = not _more_sheet.visible


func _on_target_button(id: String) -> void:
	if _cooldown_left(id) > 0:
		# Кнопка на кулдауне остаётся нажимаемой, но не вооружается: игрок
		# получает объяснение вместо молчания.
		_buttons[id].button_pressed = false
		targeting_requested.emit("")
		_armed = ""
		return
	# Наведение всегда одиночное: вооружить второе воздействие, не потратив
	# первое, было бы источником ложных нажатий на телефоне.
	if _armed == id:
		clear_armed()
		targeting_requested.emit("")
		return
	_armed = id
	for key in _buttons.keys():
		var b: Button = _buttons[key]
		if b.toggle_mode:
			b.button_pressed = key == id
	targeting_requested.emit(id)


func armed() -> String:
	return _armed


func clear_armed() -> void:
	_armed = ""
	for key in _buttons.keys():
		var b: Button = _buttons[key]
		if b.toggle_mode:
			b.button_pressed = false


func _cooldown_left(id: String) -> int:
	return int(_cooldowns.get(id, 0))


func _cycle_policy() -> void:
	var order := ["auto", "war", "peace"]
	var current := String(_policy_button.get_meta("policy", "auto"))
	var next: String = order[(order.find(current) + 1) % order.size()]
	_policy_button.set_meta("policy", next)
	policy_requested.emit(next)


func set_policy_label(policy: String) -> void:
	_policy_button.set_meta("policy", policy)
	var names := { "auto": "по обстановке", "war": "на вооружение", "peace": "на развитие" }
	_policy_button.text = "Производство: %s" % String(names.get(policy, policy))


## Кулдауны показываются прямо на кнопке: игрок не должен угадывать, почему
## нажатие ничего не сделало.
func refresh(interventions: Interventions, day: int) -> void:
	for id in _buttons.keys():
		var key := String(id)
		var b: Button = _buttons[id]
		var base := String(TARGETED.get(key, IMMEDIATE.get(key, key)))
		var remaining := interventions.cooldown_remaining(key, day)
		_cooldowns[key] = remaining
		if remaining > 0:
			b.text = "%s\n%dд" % [base, remaining]
			b.add_theme_color_override("font_color", UIKit.TEXT_DIM)
			if b.toggle_mode and b.button_pressed:
				b.button_pressed = false
				if _armed == key:
					_armed = ""
		else:
			b.text = base
			b.add_theme_color_override("font_color", UIKit.TEXT)
