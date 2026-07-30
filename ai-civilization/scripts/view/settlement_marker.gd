class_name SettlementMarker
extends Node2D

## Маркер поселения на карте. Рисуется процедурно: размер — население,
## цвет — фракция, дуги — лояльность и напряжённость. Заполненное кольцо
## означает, что ячейка сейчас в Tier 1 (её юниты видны на экране).

const BASE_RADIUS := 7.0

var settlement: Settlement
var faction_color: Color = Color.WHITE
var selected: bool = false
var show_label: bool = true

var _icon: Sprite2D


func _ready() -> void:
	_icon = Sprite2D.new()
	_icon.texture = Art.sprite("settlement")
	_icon.centered = true
	add_child(_icon)
	z_index = 10


func bind(s: Settlement, color: Color) -> void:
	settlement = s
	faction_color = color
	position = s.pos
	if _icon != null:
		_icon.modulate = color.lerp(Color.WHITE, 0.35)
	queue_redraw()


func refresh() -> void:
	if settlement == null:
		return
	position = settlement.pos
	queue_redraw()


func radius() -> float:
	if settlement == null:
		return BASE_RADIUS
	return BASE_RADIUS + sqrt(maxf(0.0, settlement.population)) * 0.55


func _draw() -> void:
	if settlement == null:
		return
	var r := radius()

	# Кольцо: заполненное — Tier 1, пунктирное по смыслу — агрегированный Tier 2.
	if settlement.is_tier1:
		draw_arc(Vector2.ZERO, r, 0.0, TAU, 28, faction_color, 2.0, true)
	else:
		draw_arc(Vector2.ZERO, r, 0.0, TAU, 20, Color(faction_color.r, faction_color.g, faction_color.b, 0.45), 1.0, true)

	if selected:
		draw_arc(Vector2.ZERO, r + 5.0, 0.0, TAU, 32, Color(1, 1, 1, 0.85), 1.5, true)

	# Лояльность — дуга сверху, напряжённость — дуга снизу.
	var loyalty_frac := clampf(settlement.loyalty / 100.0, 0.0, 1.0)
	draw_arc(Vector2.ZERO, r + 3.0, PI, PI + PI * loyalty_frac, 18,
		Color(0.45, 0.9, 0.6, 0.9), 2.0, true)
	if settlement.unrest > 0.02:
		draw_arc(Vector2.ZERO, r + 3.0, 0.0, PI * clampf(settlement.unrest, 0.0, 1.0), 18,
			Color(0.95, 0.45, 0.35, 0.9), 2.0, true)

	# Дефицит энергии — заметная точка: игрок должен видеть кризис без инспектора.
	if settlement.energy_deficit > 0.15:
		draw_circle(Vector2(0, -r - 8.0), 2.5, Color(1.0, 0.75, 0.2))

	if show_label:
		var font := ThemeDB.fallback_font
		var text := "%s · %d" % [settlement.name, int(settlement.population)]
		var size := 9
		var width := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, size).x
		draw_string(font, Vector2(-width * 0.5, r + 15.0), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, size, Color(0.88, 0.92, 0.95, 0.9))
