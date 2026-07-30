class_name UIKit
extends RefCounted

## Мелкие фабрики для HUD. Интерфейс собирается кодом, а не .tscn: панелей
## немного, зато они целиком зависят от данных симуляции, и держать их в одном
## месте с логикой обновления дешевле, чем синхронизировать со сценой.

const BG := Color(0.055, 0.070, 0.098, 0.92)
const BG_SOFT := Color(0.086, 0.106, 0.145, 0.88)
const LINE := Color(0.22, 0.28, 0.36, 0.9)
const TEXT := Color(0.88, 0.92, 0.96)
const TEXT_DIM := Color(0.58, 0.65, 0.72)
const ACCENT := Color(0.35, 0.85, 0.95)
const WARN := Color(1.0, 0.72, 0.28)
const DANGER := Color(0.98, 0.42, 0.36)
const GOOD := Color(0.45, 0.90, 0.60)

## Минимальная сторона тач-цели. Меньше — на телефоне не попасть.
const TOUCH := 44


static func stylebox(bg: Color = BG, border: int = 1, radius: int = 6) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = bg
	sb.border_color = LINE
	sb.set_border_width_all(border)
	sb.set_corner_radius_all(radius)
	sb.content_margin_left = 10
	sb.content_margin_right = 10
	sb.content_margin_top = 7
	sb.content_margin_bottom = 7
	return sb


static func panel(bg: Color = BG) -> PanelContainer:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", stylebox(bg))
	p.mouse_filter = Control.MOUSE_FILTER_STOP
	return p


static func label(text: String, size: int = 13, color: Color = TEXT) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	return l


static func title(text: String) -> Label:
	var l := label(text, 15, ACCENT)
	return l


static func button(text: String, tip: String = "") -> Button:
	var b := Button.new()
	b.text = text
	b.tooltip_text = tip
	b.custom_minimum_size = Vector2(0, TOUCH)
	b.add_theme_font_size_override("font_size", 13)
	b.focus_mode = Control.FOCUS_NONE
	return b


static func toggle(text: String, tip: String = "") -> Button:
	var b := button(text, tip)
	b.toggle_mode = true
	return b


static func hbox(separation: int = 8) -> HBoxContainer:
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", separation)
	return h


static func vbox(separation: int = 6) -> VBoxContainer:
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", separation)
	return v


static func spacer() -> Control:
	var c := Control.new()
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return c


static func bar(value: float, max_value: float, color: Color, width: int = 90) -> ProgressBar:
	var p := ProgressBar.new()
	p.min_value = 0.0
	p.max_value = maxf(0.0001, max_value)
	p.value = clampf(value, 0.0, max_value)
	p.show_percentage = false
	p.custom_minimum_size = Vector2(width, 8)
	var fill := StyleBoxFlat.new()
	fill.bg_color = color
	fill.set_corner_radius_all(3)
	var bg := StyleBoxFlat.new()
	bg.bg_color = Color(1, 1, 1, 0.10)
	bg.set_corner_radius_all(3)
	p.add_theme_stylebox_override("fill", fill)
	p.add_theme_stylebox_override("background", bg)
	return p


## Цвет по дипломатическому счёту: игрок должен читать состояние трека
## периферийным зрением, не вглядываясь в число.
static func relations_color(value: float) -> Color:
	if value < 20.0:
		return DANGER
	if value < 45.0:
		return WARN
	return GOOD


static func compact(value: float) -> String:
	var v := absf(value)
	if v >= 1000000.0:
		return "%.1fM" % (value / 1000000.0)
	if v >= 1000.0:
		return "%.1fk" % (value / 1000.0)
	if v >= 100.0:
		return str(int(round(value)))
	return "%.1f" % value
