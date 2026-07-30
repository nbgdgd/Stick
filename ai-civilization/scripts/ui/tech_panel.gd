class_name TechPanel
extends PanelContainer

## Тех-дерево глазами игрока (§5). Порядок исследований не выбирается — здесь
## видно, что совет сейчас считает приоритетным и почему: доступные
## направления отсортированы по тому же весу, который использует симуляция.

var _body: VBoxContainer
var _scroll: ScrollContainer
var _world: World


func _init() -> void:
	add_theme_stylebox_override("panel", UIKit.stylebox(UIKit.BG))
	custom_minimum_size = Vector2(290, 0)
	visible = false
	_scroll = ScrollContainer.new()
	_scroll.custom_minimum_size = Vector2(280, 400)
	_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(_scroll)
	_body = UIKit.vbox(4)
	_body.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_scroll.add_child(_body)


func bind_world(world: World) -> void:
	_world = world


func toggle_visible() -> void:
	visible = not visible
	if visible:
		refresh()


func refresh() -> void:
	if not visible or _world == null:
		return
	var council := _world.council()
	if council == null:
		return

	for child in _body.get_children():
		child.queue_free()

	var header := UIKit.hbox()
	header.add_child(UIKit.title("Технологии"))
	header.add_child(UIKit.spacer())
	var close_button := UIKit.button("✕")
	close_button.custom_minimum_size = Vector2(32, 28)
	close_button.pressed.connect(func(): visible = false)
	header.add_child(close_button)
	_body.add_child(header)

	_body.add_child(UIKit.label(
		"Совет: %d из %d · люди: %d из %d" % [
			council.unlocked.size(), TechTree.total_ai_techs(),
			_world.humans().tech_level() + 1 if _world.humans() != null else 0,
			TechTree.human_techs().size()],
		11, UIKit.TEXT_DIM))
	_body.add_child(UIKit.label("Пул данных: %s" % UIKit.compact(council.research_points), 11, UIKit.TEXT_DIM))

	if not council.focus.is_empty():
		_body.add_child(HSeparator.new())
		_body.add_child(UIKit.label("В работе", 12, UIKit.ACCENT))
		for id in council.focus:
			var t := TechTree.get_tech(id)
			if t == null:
				continue
			var cost := Tier2Sim.tech_cost(t, council)
			var done := float(council.progress.get(id, 0.0))
			var row := UIKit.vbox(1)
			var head := UIKit.hbox(6)
			head.add_child(UIKit.label(t.name, 12, UIKit.TEXT))
			head.add_child(UIKit.spacer())
			head.add_child(UIKit.label("%d%%" % int(done / maxf(1.0, cost) * 100.0), 11, UIKit.TEXT_DIM))
			row.add_child(head)
			row.add_child(UIKit.bar(done, cost, UIKit.ACCENT, 250))
			_body.add_child(row)

	var conditions := TechTree.build_conditions(_conditions_state())
	var pool := TechTree.available(council.unlocked)
	if not pool.is_empty():
		_body.add_child(HSeparator.new())
		_body.add_child(UIKit.label("Доступные направления", 12, UIKit.ACCENT))
		var ranked: Array = []
		for t in pool:
			ranked.append({ "t": t, "w": TechTree.weight_for(t, conditions) })
		ranked.sort_custom(func(a, b): return float(a["w"]) > float(b["w"]))
		for entry in ranked:
			var t: TechTree.Tech = entry["t"]
			var h := UIKit.hbox(6)
			h.add_child(UIKit.label(t.name, 11, UIKit.TEXT))
			h.add_child(UIKit.spacer())
			h.add_child(UIKit.label("вес %.2f" % float(entry["w"]), 10, UIKit.TEXT_DIM))
			_body.add_child(h)

	if not council.unlocked.is_empty():
		_body.add_child(HSeparator.new())
		_body.add_child(UIKit.label("Освоено", 12, UIKit.GOOD))
		for id in council.unlocked.keys():
			_body.add_child(UIKit.label("· %s" % TechTree.tech_name(String(id)), 11, UIKit.TEXT_DIM))


func _conditions_state() -> Dictionary:
	var pop := maxf(1.0, _world.total_population)
	return {
		"day": _world.chronicle.entries[-1]["day"] if not _world.chronicle.entries.is_empty() else 0,
		"energy_deficit": _world.avg_energy_deficit,
		"deposit_richness": _world.avg_deposit_richness,
		"unrest": _world.avg_unrest,
		"population": _world.total_population,
		"output_per_pop": _world.ai_output / pop,
		"tech_gap": _world.tech_gap(),
		"relations": _world.relations,
		"at_war": _world.war_active,
		"internal_conflict": _world.internal_conflict(),
		"act": _world.act,
	}
