class_name Hud
extends CanvasLayer

## HUD под телефон (§2, M5).
##
## Компоновка исходит из того, как телефон держат в руках: управление временем и
## вмешательства внизу, под большими пальцами, вверху только показания. Раньше
## всё было наверху мелким шрифтом, и на первом экране висело 17 постоянных
## кнопок при том, что тач-легальных мест в ширину влезает около пятнадцати.
##
## Второстепенное (склады, тех-дерево, полная хроника) убрано в выдвижные
## листы: это нужно по требованию, а не постоянно.
##
## Размеры заданы в логических единицах, приравненных к dp (см. Game и UIKit),
## поэтому 48 здесь — это физические 48dp на любом экране.

signal speed_selected(index: int)
signal skip_requested()
signal rewind_requested(days: int)
signal targeting_requested(id: String)
signal immediate_requested(id: String)
signal policy_requested(policy: String)
signal choice_made(checkpoint_id: String, option_id: String)
signal save_requested()
signal load_requested()

const TOP_H := 54
const BOTTOM_H := 62
const MARGIN := 6

var chronicle_panel: ChroniclePanel
var intervention_bar: InterventionBar
var inspector: InspectorPanel
var tech_panel: TechPanel
var choice_dialog: ChoiceDialog

var _root: Control
var _date_label: Label
var _act_label: Label
var _stat_labels: Dictionary = {}
var _relations_bar: ProgressBar
var _relations_label: Label
var _speed_buttons: Array[Button] = []
var _hint_panel: PanelContainer
var _hint_label: Label
var _hint_cancel: Button
var _tier_label: Label
var _stock_sheet: PanelContainer
var _ending_panel: PanelContainer
var _ending_title: Label
var _ending_text: Label
var _chronicle_expanded: bool = false

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
	_root = Control.new()
	_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_root)

	_build_top_bar()
	_build_bottom_bar()
	_build_chronicle()
	_build_inspector()
	_build_tech()
	_build_stock_sheet()
	_build_hint()
	_build_choice()
	_build_ending()


func _build_top_bar() -> void:
	var top := UIKit.panel()
	top.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top.offset_left = MARGIN
	top.offset_right = -MARGIN
	top.offset_top = MARGIN
	top.offset_bottom = MARGIN + TOP_H
	_root.add_child(top)

	var row := UIKit.hbox(10)
	top.add_child(row)

	var date_box := UIKit.vbox(0)
	_date_label = UIKit.label("—", UIKit.FONT_M, UIKit.TEXT)
	_act_label = UIKit.label("—", UIKit.FONT_XS, UIKit.ACCENT)
	date_box.add_child(_date_label)
	date_box.add_child(_act_label)
	row.add_child(date_box)

	row.add_child(VSeparator.new())
	# Только то, что нужно видеть постоянно. Вооружение и технологии переехали
	# в лист «Ещё»: между двумя войнами они не меняются годами.
	for key in ["Население", "Ячеек", "Выпуск"]:
		var box := UIKit.vbox(0)
		box.add_child(UIKit.label(key, UIKit.FONT_XS, UIKit.TEXT_DIM))
		var value := UIKit.label("—", UIKit.FONT_M, UIKit.TEXT)
		box.add_child(value)
		_stat_labels[key] = value
		row.add_child(box)

	row.add_child(VSeparator.new())
	var rel_box := UIKit.vbox(1)
	rel_box.add_child(UIKit.label("Материк", UIKit.FONT_XS, UIKit.TEXT_DIM))
	_relations_bar = UIKit.bar(58.0, 100.0, UIKit.GOOD, 110)
	rel_box.add_child(_relations_bar)
	_relations_label = UIKit.label("—", UIKit.FONT_XS, UIKit.TEXT_DIM)
	rel_box.add_child(_relations_label)
	row.add_child(rel_box)

	row.add_child(UIKit.spacer())

	var more := UIKit.button("Ещё", "Склады, вооружение, технологии")
	more.custom_minimum_size = Vector2(58, UIKit.TOUCH)
	more.pressed.connect(_toggle_stock_sheet)
	row.add_child(more)

	var tech := UIKit.button("Тех", "Тех-дерево")
	tech.custom_minimum_size = Vector2(58, UIKit.TOUCH)
	tech.pressed.connect(func(): tech_panel.toggle_visible())
	row.add_child(tech)


## Нижняя полоса: слева вмешательства, справа время — обе группы под большими
## пальцами при удержании телефона в ландшафте.
func _build_bottom_bar() -> void:
	intervention_bar = InterventionBar.new()
	intervention_bar.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
	intervention_bar.offset_left = MARGIN
	intervention_bar.offset_top = -(BOTTOM_H + MARGIN)
	intervention_bar.offset_bottom = -MARGIN
	_root.add_child(intervention_bar)
	intervention_bar.targeting_requested.connect(_on_targeting)
	intervention_bar.immediate_requested.connect(func(id): immediate_requested.emit(id))
	intervention_bar.policy_requested.connect(func(p): policy_requested.emit(p))

	var time_panel := UIKit.panel()
	time_panel.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
	time_panel.offset_right = -MARGIN
	time_panel.offset_top = -(BOTTOM_H + MARGIN)
	time_panel.offset_bottom = -MARGIN
	# Расти влево от правого края: иначе контейнер разворачивается наружу
	# экрана и панель времени просто не видна.
	time_panel.grow_horizontal = Control.GROW_DIRECTION_BEGIN
	_root.add_child(time_panel)
	time_panel.add_child(_build_time_controls())


func _build_time_controls() -> Control:
	var row := UIKit.hbox(4)

	var rewind := UIKit.button("◀◀", "Назад на 10 дней")
	rewind.pressed.connect(func(): rewind_requested.emit(10))
	row.add_child(rewind)

	var speeds := Balance.arr("time/speeds", [0.0, 1.0, 3.0, 10.0, 30.0])
	for i in speeds.size():
		var s := float(speeds[i])
		var b := UIKit.toggle("II" if s <= 0.0 else "x%d" % int(s))
		var index := i
		b.pressed.connect(func(): speed_selected.emit(index))
		row.add_child(b)
		_speed_buttons.append(b)

	var skip := UIKit.button("▶|", "До следующего события")
	skip.pressed.connect(func(): skip_requested.emit())
	row.add_child(skip)

	return row


## Хроника по умолчанию свёрнута в узкую полосу с последними событиями:
## развёрнутая лента занимала 38% ширины экрана постоянно.
func _build_chronicle() -> void:
	chronicle_panel = ChroniclePanel.new()
	chronicle_panel.expand_requested.connect(_toggle_chronicle)
	_root.add_child(chronicle_panel)
	_apply_chronicle_layout()


func _apply_chronicle_layout() -> void:
	chronicle_panel.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
	chronicle_panel.offset_right = -MARGIN
	chronicle_panel.offset_bottom = -(BOTTOM_H + MARGIN * 2)
	if _chronicle_expanded:
		chronicle_panel.offset_left = -330
		chronicle_panel.offset_top = -(BOTTOM_H + MARGIN * 2 + 240)
	else:
		chronicle_panel.offset_left = -262
		chronicle_panel.offset_top = -(BOTTOM_H + MARGIN * 2 + 104)
	chronicle_panel.set_compact(not _chronicle_expanded)


func _toggle_chronicle() -> void:
	_chronicle_expanded = not _chronicle_expanded
	_apply_chronicle_layout()


func _build_inspector() -> void:
	inspector = InspectorPanel.new()
	inspector.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
	inspector.offset_left = MARGIN
	inspector.offset_bottom = -(BOTTOM_H + MARGIN * 2)
	inspector.offset_top = -(BOTTOM_H + MARGIN * 2 + 205)
	_root.add_child(inspector)


func _build_tech() -> void:
	tech_panel = TechPanel.new()
	tech_panel.set_anchors_preset(Control.PRESET_CENTER)
	tech_panel.offset_left = -225
	tech_panel.offset_right = 225
	tech_panel.offset_top = -145
	tech_panel.offset_bottom = 145
	_root.add_child(tech_panel)


## Лист «Ещё»: склады совета, вооружение, технологии, состояние Tier 1.
func _build_stock_sheet() -> void:
	_stock_sheet = UIKit.panel(Color(0.06, 0.08, 0.12, 0.97))
	_stock_sheet.set_anchors_preset(Control.PRESET_CENTER)
	_stock_sheet.offset_left = -200
	_stock_sheet.offset_right = 200
	_stock_sheet.offset_top = -145
	_stock_sheet.offset_bottom = 145
	_stock_sheet.visible = false
	_root.add_child(_stock_sheet)

	var box := UIKit.vbox(3)
	_stock_sheet.add_child(box)
	var head := UIKit.hbox()
	head.add_child(UIKit.title("Совет"))
	head.add_child(UIKit.spacer())
	var close := UIKit.button("✕")
	close.pressed.connect(func(): _stock_sheet.visible = false)
	head.add_child(close)
	box.add_child(head)

	var rows := UIKit.vbox(2)
	rows.size_flags_vertical = Control.SIZE_EXPAND_FILL
	box.add_child(rows)
	for i in Res.COUNT:
		var row := UIKit.hbox(8)
		row.add_child(UIKit.label(Res.NAMES[i], UIKit.FONT_S, UIKit.TEXT_DIM))
		row.add_child(UIKit.spacer())
		var v := UIKit.label("—", UIKit.FONT_S, UIKit.TEXT)
		row.add_child(v)
		_stat_labels["res_%d" % i] = v
		rows.add_child(row)

	rows.add_child(HSeparator.new())
	for key in ["Вооружение", "Технологии"]:
		var row2 := UIKit.hbox(8)
		row2.add_child(UIKit.label(key, UIKit.FONT_S, UIKit.TEXT_DIM))
		row2.add_child(UIKit.spacer())
		var v2 := UIKit.label("—", UIKit.FONT_S, UIKit.TEXT)
		row2.add_child(v2)
		_stat_labels[key] = v2
		rows.add_child(row2)

	_tier_label = UIKit.label("—", UIKit.FONT_XS, UIKit.TEXT_DIM)
	rows.add_child(_tier_label)

	# Сохранение и загрузка живут здесь, а не в полосе времени: они нужны
	# несколько раз за партию, а места в нижней полосе на них не хватает.
	var io := UIKit.hbox(6)
	var save := UIKit.button("Сохранить")
	save.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	save.pressed.connect(func():
		_stock_sheet.visible = false
		save_requested.emit())
	io.add_child(save)
	var load_button := UIKit.button("Загрузить")
	load_button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	load_button.pressed.connect(func():
		_stock_sheet.visible = false
		load_requested.emit())
	io.add_child(load_button)
	box.add_child(io)


func _toggle_stock_sheet() -> void:
	_stock_sheet.visible = not _stock_sheet.visible
	if _stock_sheet.visible:
		refresh_slow()


## Подсказка режима наведения — не мелкий текст, а заметная плашка с отменой:
## иначе игрок не понимает, что игра ждёт тапа по карте.
func _build_hint() -> void:
	_hint_panel = UIKit.panel(Color(0.22, 0.15, 0.04, 0.96))
	_hint_panel.set_anchors_preset(Control.PRESET_CENTER_TOP)
	_hint_panel.offset_top = MARGIN + TOP_H + 6
	_hint_panel.offset_left = -190
	_hint_panel.offset_right = 190
	_hint_panel.visible = false
	_root.add_child(_hint_panel)

	var row := UIKit.hbox(8)
	_hint_panel.add_child(row)
	_hint_label = UIKit.label("", UIKit.FONT_M, UIKit.WARN)
	_hint_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hint_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	row.add_child(_hint_label)
	_hint_cancel = UIKit.button("Отмена")
	_hint_cancel.custom_minimum_size = Vector2(80, UIKit.TOUCH)
	_hint_cancel.pressed.connect(_cancel_targeting)
	row.add_child(_hint_cancel)


func _cancel_targeting() -> void:
	intervention_bar.clear_armed()
	targeting_requested.emit("")
	set_hint("")


func _build_choice() -> void:
	choice_dialog = ChoiceDialog.new()
	choice_dialog.set_anchors_preset(Control.PRESET_CENTER)
	choice_dialog.offset_left = -255
	choice_dialog.offset_right = 255
	choice_dialog.offset_top = -150
	choice_dialog.offset_bottom = 150
	_root.add_child(choice_dialog)
	choice_dialog.option_chosen.connect(func(cp, opt): choice_made.emit(cp, opt))


func _build_ending() -> void:
	_ending_panel = UIKit.panel(Color(0.06, 0.08, 0.12, 0.98))
	_ending_panel.set_anchors_preset(Control.PRESET_CENTER)
	_ending_panel.offset_left = -255
	_ending_panel.offset_right = 255
	_ending_panel.offset_top = -105
	_ending_panel.offset_bottom = 105
	_ending_panel.visible = false
	_root.add_child(_ending_panel)

	var box := UIKit.vbox(8)
	_ending_panel.add_child(box)
	_ending_title = UIKit.title("—")
	box.add_child(_ending_title)
	_ending_text = UIKit.label("", UIKit.FONT_S, UIKit.TEXT)
	_ending_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_ending_text.size_flags_vertical = Control.SIZE_EXPAND_FILL
	box.add_child(_ending_text)

	# Без этой кнопки окно финала оставалось на экране навсегда и перекрывало
	# центр карты: игрок не мог ни осмотреть остров, ни ткнуть в поселение.
	var dismiss := UIKit.button("Осмотреть остров")
	dismiss.pressed.connect(func(): _ending_panel.visible = false)
	box.add_child(dismiss)


# ------------------------------------------------------------------ обновление

func _on_targeting(id: String) -> void:
	targeting_requested.emit(id)
	set_hint("Укажите точку на карте" if not id.is_empty() else "", not id.is_empty())


## cancellable = true только для режима наведения: у сообщения о смене акта
## кнопка «Отмена» бессмысленна.
func set_hint(text: String, cancellable: bool = false) -> void:
	_hint_label.text = text
	_hint_panel.visible = not text.is_empty()
	_hint_cancel.visible = cancellable


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
		"+%d" % w.splinter_count() if w.splinter_count() > 0 else ""]
	_stat_labels["Выпуск"].text = "%s (%.1f×)" % [UIKit.compact(w.ai_output), w.economy_ratio()]

	_relations_bar.value = clampf(w.relations, 0.0, 100.0)
	var fill := StyleBoxFlat.new()
	fill.bg_color = UIKit.relations_color(w.relations)
	fill.set_corner_radius_all(3)
	_relations_bar.add_theme_stylebox_override("fill", fill)
	_relations_label.text = "%d · война при %d" % [
		int(round(w.relations)), int(Balance.num("relations/war_threshold", 14.0))]

	# Склады пересчитываются только когда лист открыт: это проход по всем
	# поселениям, а на x30 refresh_slow вызывается несколько раз в секунду.
	if _stock_sheet.visible:
		var totals := Res.empty_stock()
		if council != null:
			for s in w.settlements_of(council.id):
				for i in Res.COUNT:
					totals[i] += s.stock[i]
		for i in Res.COUNT:
			_stat_labels["res_%d" % i].text = UIKit.compact(totals[i])
		_stat_labels["Вооружение"].text = UIKit.compact(w.ai_military)
		_stat_labels["Технологии"].text = "%d / %d (разрыв %+d)" % [
			council.unlocked.size() if council != null else 0,
			TechTree.total_ai_techs(), w.tech_gap()]

	intervention_bar.refresh(_sim.interventions, _sim.day())
	if council != null:
		intervention_bar.set_policy_label(council.policy)
	inspector.refresh()
	tech_panel.refresh()


func set_tier_info(tier1_settlements: int, units: int, budget: int) -> void:
	if _tier_label != null:
		_tier_label.text = "Tier 1: %d ячеек, %d/%d юнитов" % [tier1_settlements, units, budget]


func show_ending(ending: Dictionary) -> void:
	# Развилка и финал — два модальных окна; открытыми одновременно они
	# накладываются друг на друга. Финал всегда закрывает выбор.
	choice_dialog.close()
	_stock_sheet.visible = false
	tech_panel.visible = false
	_ending_title.text = String(ending.get("title", "Финал"))
	_ending_text.text = String(ending.get("text", ""))
	_ending_panel.visible = true


## Закрывает все модальные окна. Нужно и игре (модалка не должна ловить
## касания, предназначенные карте), и тестам ввода.
func close_modals() -> void:
	choice_dialog.close()
	_ending_panel.visible = false
	_stock_sheet.visible = false
	tech_panel.visible = false


func open_choice(pending: Dictionary) -> void:
	_stock_sheet.visible = false
	tech_panel.visible = false
	choice_dialog.open(pending)
