class_name ChroniclePanel
extends PanelContainer

## Лента событий (§7). Подписана на Chronicle и добавляет строки по одной;
## полная перестройка нужна только после перемотки времени, когда история
## заменяется целиком.

const MAX_ROWS := 120

var _list: VBoxContainer
var _scroll: ScrollContainer
var _chronicle: Chronicle
var _autoscroll: bool = true


func _init() -> void:
	add_theme_stylebox_override("panel", UIKit.stylebox(UIKit.BG))
	custom_minimum_size = Vector2(300, 0)

	var root := UIKit.vbox(4)
	add_child(root)

	var header := UIKit.hbox()
	header.add_child(UIKit.title("Хроника"))
	header.add_child(UIKit.spacer())
	var follow := UIKit.toggle("↓", "Следить за концом ленты")
	follow.button_pressed = true
	follow.custom_minimum_size = Vector2(34, 28)
	follow.toggled.connect(func(on: bool): _autoscroll = on)
	header.add_child(follow)
	root.add_child(header)

	_scroll = ScrollContainer.new()
	_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	root.add_child(_scroll)

	_list = UIKit.vbox(3)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_scroll.add_child(_list)


func bind(chronicle: Chronicle) -> void:
	if _chronicle != null and _chronicle.entry_added.is_connected(_on_entry):
		_chronicle.entry_added.disconnect(_on_entry)
	_chronicle = chronicle
	_chronicle.entry_added.connect(_on_entry)
	rebuild()


func rebuild() -> void:
	for child in _list.get_children():
		child.queue_free()
	if _chronicle == null:
		return
	for entry in _chronicle.recent(MAX_ROWS):
		_append(entry)
	_scroll_to_end()


func _on_entry(entry: Dictionary) -> void:
	_append(entry)
	if _list.get_child_count() > MAX_ROWS:
		_list.get_child(0).queue_free()
	if _autoscroll:
		_scroll_to_end()


func _append(entry: Dictionary) -> void:
	var importance := int(entry.get("importance", Chronicle.NORMAL))
	var row := UIKit.hbox(6)

	var day_label := UIKit.label("%d" % int(entry.get("day", 0)), 10, UIKit.TEXT_DIM)
	day_label.custom_minimum_size = Vector2(30, 0)
	day_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	row.add_child(day_label)

	var text := UIKit.label(String(entry.get("text", "")), 12, _color_for(importance))
	text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	if importance >= Chronicle.CRITICAL:
		text.add_theme_font_size_override("font_size", 13)
	row.add_child(text)

	_list.add_child(row)


static func _color_for(importance: int) -> Color:
	match importance:
		Chronicle.LOW: return UIKit.TEXT_DIM
		Chronicle.HIGH: return UIKit.WARN
		Chronicle.CRITICAL: return UIKit.ACCENT
	return UIKit.TEXT


func _scroll_to_end() -> void:
	# Один кадр нужен, чтобы контейнер пересчитал высоту после добавления строки.
	await get_tree().process_frame
	if is_instance_valid(_scroll):
		_scroll.scroll_vertical = int(_scroll.get_v_scroll_bar().max_value)
