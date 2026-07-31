class_name ChroniclePanel
extends PanelContainer

## Лента событий (§7).
##
## По умолчанию свёрнута в узкую полосу с последними записями: развёрнутая
## лента занимала больше трети ширины экрана постоянно, а читают её эпизодически.
## Тап по заголовку разворачивает её в полноценный список.
##
## Подписана на Chronicle и добавляет строки по одной; полная перестройка нужна
## только после перемотки времени, когда история заменяется целиком.

signal expand_requested()

const MAX_ROWS := 120
const COMPACT_ROWS := 3

var _list: VBoxContainer
var _scroll: ScrollContainer
var _chronicle: Chronicle
var _expand_button: Button
var _compact: bool = true


func _init() -> void:
	add_theme_stylebox_override("panel", UIKit.stylebox(UIKit.BG))

	var root := UIKit.vbox(2)
	add_child(root)

	var header := UIKit.hbox(4)
	header.add_child(UIKit.label("Хроника", UIKit.FONT_S, UIKit.ACCENT))
	header.add_child(UIKit.spacer())
	_expand_button = UIKit.button("▲", "Развернуть ленту")
	_expand_button.custom_minimum_size = Vector2(UIKit.TOUCH, 34)
	_expand_button.pressed.connect(func(): expand_requested.emit())
	header.add_child(_expand_button)
	root.add_child(header)

	_scroll = ScrollContainer.new()
	_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	root.add_child(_scroll)

	_list = UIKit.vbox(3)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_scroll.add_child(_list)


func set_compact(value: bool) -> void:
	if _compact == value:
		return
	_compact = value
	_expand_button.text = "▲" if _compact else "▼"
	_expand_button.tooltip_text = "Развернуть ленту" if _compact else "Свернуть ленту"
	rebuild()


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
	for entry in _chronicle.recent(COMPACT_ROWS if _compact else MAX_ROWS):
		_append(entry)
	_scroll_to_end()


func _on_entry(entry: Dictionary) -> void:
	_append(entry)
	var limit := COMPACT_ROWS if _compact else MAX_ROWS
	while _list.get_child_count() > limit:
		var first := _list.get_child(0)
		_list.remove_child(first)
		first.queue_free()
	_scroll_to_end()


func _append(entry: Dictionary) -> void:
	var importance := int(entry.get("importance", Chronicle.NORMAL))
	var row := UIKit.hbox(6)

	var day_label := UIKit.label("%d" % int(entry.get("day", 0)), UIKit.FONT_XS, UIKit.TEXT_DIM)
	day_label.custom_minimum_size = Vector2(28, 0)
	day_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	row.add_child(day_label)

	var text := UIKit.label(String(entry.get("text", "")), UIKit.FONT_S, _color_for(importance))
	text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
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
