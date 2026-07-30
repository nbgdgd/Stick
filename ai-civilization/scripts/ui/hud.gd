class_name Hud
extends CanvasLayer

## Сборка HUD и тайм-контролов (§2, M5).
##
## HUD ничего не знает о том, как считается симуляция — только читает её
## состояние и отдаёт наружу сигналы о желаниях игрока. Обновление тяжёлых
## частей (панели, ленты) идёт по событию «прошёл день», а не каждый кадр:
## на x30 это разница между 7 обновлениями в секунду и 60.

signal speed_selected(index: int)
signal skip_requested()
signal rewind_requested(days: int)
signal targeting_requested(id: String)
signal immediate_requested(id: String)
signal policy_requested(policy: String)
signal choice_made(checkpoint_id: String, option_id: String)
signal save_requested()
signal load_requested()

var chronicle_panel: ChroniclePanel
var intervention_bar: InterventionBar
var inspector: InspectorPanel
var tech_panel: TechPanel
var choice_dialog: ChoiceDialog

var _date_label: Label
var _act_label: Label
var _stat_labels: Dictionary = {}
var _relations_bar: ProgressBar
var _relations_label: Label
var _speed_buttons: Array[Button] = []
var _hint_label: Label
var _tier_label: Label
var _res_panel: PanelContainer
var _ending_panel: PanelContainer
var _ending_title: Label
var _ending_text: Label

var _sim: Simulation


func setup(sim: Simulation) -> void:
	_sim = sim
	layer = 10
	_build()
	chronicle_panel.bind(sim.world.chronicle)
	inspector.bind_world(sim.world)
	tech_panel.bind_world(sim.world)
	refresh_fast()
	refresh_slow()


# ------------------------------------------------------------------ построение

func _build() -> void:
	var root := Control.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(root)

	# --- верхняя панель -----------------------------------------------------
	var top := UIKit.panel()
	top.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top.offset_left = 8
	top.offset_right = -8
	top.offset_top = 8
	root.add_child(top)

	var top_row := UIKit.hbox(12)
	top.add_child(top_row)

	var date_box := UIKit.vbox(0)
	_date_label = UIKit.label("—", 14, UIKit.TEXT)
	_act_label = UIKit.label("—", 11, UIKit.ACCENT)
	date_box.add_child(_date_label)
	date_box.add_child(_act_label)
	top_row.add_child(date_box)

	top_row.add_child(VSeparator.new())
	for key in ["Население", "Ячеек", "Выпуск", "Вооружение", "Технологии"]:
		var box := UIKit.vbox(0)
		box.add_child(UIKit.label(key, 10, UIKit.TEXT_DIM))
		var value := UIKit.label("—", 13, UIKit.TEXT)
		box.add_child(value)
		_stat_labels[key] = value
		top_row.add_child(box)

	top_row.add_child(VSeparator.new())
	var rel_box := UIKit.vbox(1)
	rel_box.add_child(UIKit.label("Отношения с людьми", 10, UIKit.TEXT_DIM))
	_relations_bar = UIKit.bar(58.0, 100.0, UIKit.GOOD, 120)
	rel_box.add_child(_relations_bar)
	_relations_label = UIKit.label("—", 11, UIKit.TEXT_DIM)
	rel_box.add_child(_relations_label)
	top_row.add_child(rel_box)

	top_row.add_child(UIKit.spacer())
	top_row.add_child(_build_time_controls())

	# --- ресурсы фракции ----------------------------------------------------
	_res_panel = UIKit.panel(UIKit.BG_SOFT)
	_res_panel.set_anchors_preset(Control.PRESET_TOP_LEFT)
	_res_panel.offset_left = 8
	_res_panel.offset_top = 80
	root.add_child(_res_panel)
	var res_box := UIKit.vbox(2)
	_res_panel.add_child(res_box)
	res_box.add_child(UIKit.label("Склады совета", 10, UIKit.TEXT_DIM))
	for i in Res.COUNT:
		var row := UIKit.hbox(8)
		row.add_child(UIKit.label(Res.NAMES[i], 11, UIKit.TEXT_DIM))
		row.add_child(UIKit.spacer())
		var v := UIKit.label("—", 11, UIKit.TEXT)
		row.add_child(v)
		_stat_labels["res_%d" % i] = v
		res_box.add_child(row)
	_tier_label = UIKit.label("—", 10, UIKit.TEXT_DIM)
	res_box.add_child(HSeparator.new())
	res_box.add_child(_tier_label)

	# --- хроника справа -----------------------------------------------------
	chronicle_panel = ChroniclePanel.new()
	chronicle_panel.set_anchors_preset(Control.PRESET_RIGHT_WIDE)
	chronicle_panel.offset_right = -8
	chronicle_panel.offset_left = -318
	chronicle_panel.offset_top = 86
	chronicle_panel.offset_bottom = -78
	root.add_child(chronicle_panel)

	# --- инспектор и тех-дерево --------------------------------------------
	inspector = InspectorPanel.new()
	inspector.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
	inspector.offset_left = 8
	inspector.offset_bottom = -78
	inspector.offset_top = -400
	root.add_child(inspector)

	tech_panel = TechPanel.new()
	tech_panel.set_anchors_preset(Control.PRESET_TOP_RIGHT)
	tech_panel.offset_right = -330
	tech_panel.offset_left = -630
	tech_panel.offset_top = 80
	root.add_child(tech_panel)

	# --- нижняя панель вмешательств ---------------------------------------
	intervention_bar = InterventionBar.new()
	intervention_bar.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	intervention_bar.offset_left = 8
	intervention_bar.offset_right = -8
	intervention_bar.offset_top = -62
	intervention_bar.offset_bottom = -8
	root.add_child(intervention_bar)
	intervention_bar.targeting_requested.connect(_on_targeting)
	intervention_bar.immediate_requested.connect(func(id): immediate_requested.emit(id))
	intervention_bar.policy_requested.connect(func(p): policy_requested.emit(p))

	# --- подсказка режима наведения ---------------------------------------
	_hint_label = UIKit.label("", 13, UIKit.WARN)
	_hint_label.set_anchors_preset(Control.PRESET_CENTER_TOP)
	_hint_label.offset_top = 92
	_hint_label.offset_left = -220
	_hint_label.offset_right = 220
	_hint_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	root.add_child(_hint_label)

	# --- развилка ----------------------------------------------------------
	choice_dialog = ChoiceDialog.new()
	choice_dialog.set_anchors_preset(Control.PRESET_CENTER)
	choice_dialog.offset_left = -240
	choice_dialog.offset_right = 240
	choice_dialog.offset_top = -190
	root.add_child(choice_dialog)
	choice_dialog.option_chosen.connect(func(cp, opt): choice_made.emit(cp, opt))

	_ending_panel = _build_ending_panel()
	root.add_child(_ending_panel)


func _build_time_controls() -> Control:
	var row := UIKit.hbox(4)

	var rewind := UIKit.button("◀◀", "Перемотка назад на 10 дней (снепшот + быстрый проигрыш)")
	rewind.custom_minimum_size = Vector2(52, UIKit.TOUCH)
	rewind.pressed.connect(func(): rewind_requested.emit(10))
	row.add_child(rewind)

	var speeds := Balance.arr("time/speeds", [0.0, 1.0, 3.0, 10.0, 30.0])
	for i in speeds.size():
		var s := float(speeds[i])
		var b := UIKit.toggle("II" if s <= 0.0 else "x%d" % int(s))
		b.custom_minimum_size = Vector2(46, UIKit.TOUCH)
		var index := i
		b.pressed.connect(func(): speed_selected.emit(index))
		row.add_child(b)
		_speed_buttons.append(b)

	var skip := UIKit.button("▶|", "Скачок до следующего значимого события")
	skip.custom_minimum_size = Vector2(52, UIKit.TOUCH)
	skip.pressed.connect(func(): skip_requested.emit())
	row.add_child(skip)

	row.add_child(VSeparator.new())
	var tech := UIKit.button("Тех", "Тех-дерево")
	tech.pressed.connect(func(): tech_panel.toggle_visible())
	row.add_child(tech)

	var save := UIKit.button("Сохр", "Сохранить")
	save.custom_minimum_size = Vector2(52, UIKit.TOUCH)
	save.pressed.connect(func(): save_requested.emit())
	row.add_child(save)
	var load_button := UIKit.button("Загр", "Загрузить")
	load_button.custom_minimum_size = Vector2(52, UIKit.TOUCH)
	load_button.pressed.connect(func(): load_requested.emit())
	row.add_child(load_button)
	return row


func _build_ending_panel() -> PanelContainer:
	var p := UIKit.panel(Color(0.06, 0.08, 0.12, 0.98))
	p.set_anchors_preset(Control.PRESET_CENTER)
	p.offset_left = -300
	p.offset_right = 300
	p.offset_top = -120
	p.visible = false
	var box := UIKit.vbox(8)
	p.add_child(box)
	_ending_title = UIKit.title("—")
	box.add_child(_ending_title)
	_ending_text = UIKit.label("", 13, UIKit.TEXT)
	_ending_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	box.add_child(_ending_text)
	return p


# ------------------------------------------------------------------ обновление

func _on_targeting(id: String) -> void:
	targeting_requested.emit(id)
	set_hint("Укажите точку на карте" if not id.is_empty() else "")


func set_hint(text: String) -> void:
	_hint_label.text = text


func set_speed_index(index: int) -> void:
	for i in _speed_buttons.size():
		_speed_buttons[i].button_pressed = i == index


## Дёшево и каждый кадр: только строка даты.
func refresh_fast() -> void:
	if _sim == null:
		return
	_date_label.text = _sim.clock.date_label()


## Дорого и раз в игровой день: панели, метрики, кулдауны.
func refresh_slow() -> void:
	if _sim == null:
		return
	var w := _sim.world
	var council := w.council()

	_act_label.text = "Акт %d — %s%s" % [w.act, _sim.story.act_name(),
		"  ·  ВОЙНА" if w.war_active else ""]
	_stat_labels["Население"].text = UIKit.compact(w.total_population)
	_stat_labels["Ячеек"].text = "%d%s" % [w.settlements.size(),
		"  (+%d раскол)" % w.splinter_count() if w.splinter_count() > 0 else ""]
	_stat_labels["Выпуск"].text = "%s  (%.2f× людей)" % [UIKit.compact(w.ai_output), w.economy_ratio()]
	_stat_labels["Вооружение"].text = UIKit.compact(w.ai_military)
	_stat_labels["Технологии"].text = "%d / %d  (разрыв %+d)" % [
		council.unlocked.size() if council != null else 0,
		TechTree.total_ai_techs(), w.tech_gap()]

	_relations_bar.value = clampf(w.relations, 0.0, 100.0)
	var fill := StyleBoxFlat.new()
	fill.bg_color = UIKit.relations_color(w.relations)
	fill.set_corner_radius_all(3)
	_relations_bar.add_theme_stylebox_override("fill", fill)
	_relations_label.text = "%d  ·  порог войны %d" % [
		int(round(w.relations)), int(Balance.num("relations/war_threshold", 14.0))]

	var totals := Res.empty_stock()
	if council != null:
		for s in w.settlements_of(council.id):
			for i in Res.COUNT:
				totals[i] += s.stock[i]
	for i in Res.COUNT:
		_stat_labels["res_%d" % i].text = UIKit.compact(totals[i])

	intervention_bar.refresh(_sim.interventions, _sim.day())
	if council != null:
		intervention_bar.set_policy_label(council.policy)
	inspector.refresh()
	tech_panel.refresh()


func set_tier_info(tier1_settlements: int, units: int, budget: int) -> void:
	_tier_label.text = "Tier 1: %d ячеек, %d/%d юнитов" % [tier1_settlements, units, budget]


func show_ending(ending: Dictionary) -> void:
	# Развилка и финал — два модальных окна; открытыми одновременно они
	# накладываются друг на друга. Финал всегда закрывает выбор.
	choice_dialog.close()
	_ending_title.text = String(ending.get("title", "Финал"))
	_ending_text.text = String(ending.get("text", ""))
	_ending_panel.visible = true


func open_choice(pending: Dictionary) -> void:
	choice_dialog.open(pending)
