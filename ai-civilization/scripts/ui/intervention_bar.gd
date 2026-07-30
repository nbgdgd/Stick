class_name InterventionBar
extends PanelContainer

## Панель вмешательств (§2). Часть воздействий требует точки на карте —
## такие кнопки переводят интерфейс в режим наведения, и следующий тап по
## карте становится целью. Остальные срабатывают сразу.

signal targeting_requested(id: String)
signal immediate_requested(id: String)
signal policy_requested(policy: String)

const TARGETED := {
	"drop_scrap": "Сброс лома",
	"incite_conflict": "Провокация",
	"disaster": "Бедствие",
	"back_uprising": "Восстание",
}

const IMMEDIATE := {
	"open_talks": "Переговоры",
	"leak_tech": "Утечка",
}

var _buttons: Dictionary = {}
var _armed: String = ""
var _policy_button: Button


func _init() -> void:
	add_theme_stylebox_override("panel", UIKit.stylebox(UIKit.BG))
	var row := UIKit.hbox(6)
	add_child(row)

	row.add_child(UIKit.label("Вмешательство", 11, UIKit.TEXT_DIM))

	for id in TARGETED.keys():
		var b := UIKit.toggle(String(TARGETED[id]), "Выберите точку на карте")
		b.pressed.connect(_on_target_button.bind(String(id)))
		row.add_child(b)
		_buttons[id] = b

	for id in IMMEDIATE.keys():
		var b := UIKit.button(String(IMMEDIATE[id]))
		b.pressed.connect(func(): immediate_requested.emit(String(id)))
		row.add_child(b)
		_buttons[id] = b

	row.add_child(VSeparator.new())
	_policy_button = UIKit.button("Производство: по обстановке",
		"Стратегический рычаг: куда направить производство фракции")
	_policy_button.pressed.connect(_cycle_policy)
	row.add_child(_policy_button)


func _on_target_button(id: String) -> void:
	# Наведение всегда одиночное: вооружить второе воздействие, не потратив
	# первое, было бы источником ложных нажатий на телефоне.
	if _armed == id:
		clear_armed()
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


## Кулдауны показываются прямо на кнопке: игрок не должен угадывать,
## почему нажатие ничего не сделало.
func refresh(interventions: Interventions, day: int) -> void:
	for id in _buttons.keys():
		var key := String(id)
		var b: Button = _buttons[id]
		var base := String(TARGETED.get(key, IMMEDIATE.get(key, key)))
		var remaining := interventions.cooldown_remaining(key, day)
		if remaining > 0:
			b.text = "%s (%d)" % [base, remaining]
			b.disabled = true
			if b.toggle_mode and b.button_pressed:
				b.button_pressed = false
				if _armed == key:
					_armed = ""
		else:
			b.text = base
			b.disabled = false
