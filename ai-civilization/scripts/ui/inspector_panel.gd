class_name InspectorPanel
extends PanelContainer

## Инспектор поселения. Открывается тапом по маркеру. Показывает, что именно
## считает Tier 2 для этой ячейки — включая уровень детализации, на котором
## она сейчас симулируется.

var _body: VBoxContainer
var _scroll: ScrollContainer
var _settlement: Settlement
var _world: World


func _init() -> void:
	add_theme_stylebox_override("panel", UIKit.stylebox(UIKit.BG))
	custom_minimum_size = Vector2(250, 0)
	visible = false
	# Содержимое кладётся в скролл фиксированной высоты: PanelContainer иначе
	# растёт под контент и уезжает за нижнюю панель вмешательств.
	_scroll = ScrollContainer.new()
	_scroll.custom_minimum_size = Vector2(250, 300)
	_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(_scroll)
	_body = UIKit.vbox(4)
	_body.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_scroll.add_child(_body)


func bind_world(world: World) -> void:
	_world = world


func show_settlement(s: Settlement) -> void:
	_settlement = s
	visible = true
	refresh()


func close() -> void:
	visible = false
	_settlement = null


func current() -> Settlement:
	return _settlement


func refresh() -> void:
	if not visible or _settlement == null or _world == null:
		return
	# Поселение могло быть разрушено войной, пока панель открыта.
	if _world.settlement(_settlement.id) == null:
		close()
		return

	for child in _body.get_children():
		child.queue_free()

	var s := _settlement
	var faction := _world.faction(s.faction_id)

	var header := UIKit.hbox()
	header.add_child(UIKit.title(s.name))
	header.add_child(UIKit.spacer())
	var close_button := UIKit.button("✕")
	close_button.custom_minimum_size = Vector2(32, 28)
	close_button.pressed.connect(close)
	header.add_child(close_button)
	_body.add_child(header)

	_body.add_child(UIKit.label(
		"%s · %s" % [faction.name if faction != null else "?",
			"Tier 1 · %d юнитов" % s.visual_units if s.is_tier1 else "Tier 2 · агрегат"],
		11, faction.color if faction != null else UIKit.TEXT_DIM))

	_row("Население", "%s" % UIKit.compact(s.population))
	_row("Прирост/день", "%s" % UIKit.compact(s.last_growth))
	_row("Выпуск/день", "%s" % UIKit.compact(s.last_output))
	_row("Вооружение", "%s" % UIKit.compact(s.military_strength))

	_body.add_child(HSeparator.new())
	for i in Res.COUNT:
		_row(Res.NAMES[i], UIKit.compact(s.stock[i]),
			UIKit.WARN if (i == Res.ENERGY and s.energy_deficit > 0.1) else UIKit.TEXT)

	_body.add_child(HSeparator.new())
	_meter("Отвал", s.deposit_richness(), 1.0, UIKit.ACCENT)
	_meter("Лояльность", s.loyalty, 100.0, UIKit.GOOD)
	_meter("Напряжённость", s.unrest, 1.0, UIKit.DANGER)
	if s.energy_deficit > 0.01:
		_meter("Дефицит энергии", s.energy_deficit, 1.0, UIKit.WARN)
	if s.grudge > 0.5:
		_meter("Обиды", s.grudge, Balance.num("loyalty/max_grudge", 40.0), UIKit.DANGER)

	_body.add_child(HSeparator.new())
	var jobs := UIKit.label(_jobs_text(s), 10, UIKit.TEXT_DIM)
	jobs.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_body.add_child(jobs)


func _row(name: String, value: String, color: Color = UIKit.TEXT) -> void:
	var h := UIKit.hbox(6)
	h.add_child(UIKit.label(name, 11, UIKit.TEXT_DIM))
	h.add_child(UIKit.spacer())
	h.add_child(UIKit.label(value, 12, color))
	_body.add_child(h)


func _meter(name: String, value: float, max_value: float, color: Color) -> void:
	var h := UIKit.hbox(6)
	h.add_child(UIKit.label(name, 11, UIKit.TEXT_DIM))
	h.add_child(UIKit.spacer())
	h.add_child(UIKit.bar(value, max_value, color, 70))
	_body.add_child(h)


static func _jobs_text(s: Settlement) -> String:
	var parts: Array[String] = []
	for j in Job.COUNT:
		if s.alloc[j] > 0.005:
			parts.append("%s %d%%" % [Job.NAMES[j], int(round(s.alloc[j] * 100.0))])
	return "Занятость: " + ", ".join(parts)
