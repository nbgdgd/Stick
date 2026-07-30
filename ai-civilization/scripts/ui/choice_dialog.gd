class_name ChoiceDialog
extends PanelContainer

## Сюжетная развилка (§2): единственное место, где решение подаётся явным
## UI-выбором, а не выводится из симуляции. Пока диалог открыт, время стоит —
## Simulation.process сам не тикает при незакрытом выборе.

signal option_chosen(checkpoint_id: String, option_id: String)

var _checkpoint_id: String = ""
var _body: VBoxContainer


func _init() -> void:
	add_theme_stylebox_override("panel", UIKit.stylebox(Color(0.07, 0.09, 0.13, 0.98), 2, 8))
	custom_minimum_size = Vector2(460, 0)
	visible = false
	_body = UIKit.vbox(10)
	add_child(_body)


func open(pending: Dictionary) -> void:
	_checkpoint_id = String(pending.get("id", ""))
	var choice: Dictionary = pending.get("choice", {})
	for child in _body.get_children():
		child.queue_free()

	_body.add_child(UIKit.title("Развилка"))

	var prompt := UIKit.label(String(choice.get("prompt", "")), 15, UIKit.TEXT)
	prompt.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_body.add_child(prompt)

	var detail_text := String(choice.get("detail", ""))
	if not detail_text.is_empty():
		var detail := UIKit.label(detail_text, 12, UIKit.TEXT_DIM)
		detail.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		_body.add_child(detail)

	var sep := HSeparator.new()
	_body.add_child(sep)

	for opt in choice.get("options", []):
		if not (opt is Dictionary):
			continue
		_body.add_child(_option_row(opt))

	visible = true


func _option_row(opt: Dictionary) -> Control:
	var wrap := UIKit.vbox(2)
	var b := UIKit.button(String(opt.get("label", "?")))
	b.add_theme_font_size_override("font_size", 14)
	var option_id := String(opt.get("id", ""))
	b.pressed.connect(func(): _choose(option_id))
	wrap.add_child(b)

	var desc := UIKit.label(String(opt.get("desc", "")), 11, UIKit.TEXT_DIM)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	wrap.add_child(desc)
	return wrap


func _choose(option_id: String) -> void:
	visible = false
	option_chosen.emit(_checkpoint_id, option_id)


func close() -> void:
	visible = false
	_checkpoint_id = ""
