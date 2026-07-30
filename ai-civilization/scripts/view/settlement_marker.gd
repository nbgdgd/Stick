class_name SettlementMarker
extends Node2D

## Поселение на карте: постройка из набора спрайтов своей фракции плюс флаг.
##
## Вид постройки выбирается по населению (барак → жилой блок → завод → высотка),
## поэтому размер ячейки читается силуэтом, а не только подписью. Кольцо и дуги
## состояния оставлены, но приглушены: раньше они были главным, что видно, и
## карта выглядела схемой, а не местом.

const SPRITE_SCALE := 2.4
const FLAG_SCALE := 1.5

var settlement: Settlement
var faction_color: Color = Color.WHITE
var sprite_set: String = "blue"
var selected: bool = false
var show_label: bool = true

var _building: Sprite2D
var _flag: Sprite2D
var _turret: Sprite2D
var _last_kind: Texture2D


func _ready() -> void:
	_building = Sprite2D.new()
	_building.centered = true
	_building.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_building.scale = Vector2.ONE * SPRITE_SCALE
	add_child(_building)

	# Флаг сдвинут вправо-вверх от постройки: он опознаёт фракцию на общем плане,
	# когда сама постройка уже неразличима.
	_flag = Sprite2D.new()
	_flag.centered = true
	_flag.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_flag.scale = Vector2.ONE * FLAG_SCALE
	_flag.position = Vector2(14.0, -20.0)
	add_child(_flag)

	# Турель появляется у поселений с заметным вооружением — признак мобилизации.
	_turret = Sprite2D.new()
	_turret.centered = true
	_turret.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_turret.scale = Vector2.ONE * 1.4
	_turret.position = Vector2(-16.0, 10.0)
	_turret.visible = false
	add_child(_turret)

	z_index = 10


func bind(s: Settlement, color: Color, p_sprite_set: String = "blue") -> void:
	settlement = s
	faction_color = color
	sprite_set = p_sprite_set
	position = s.pos
	_apply_sprites()
	queue_redraw()


func refresh() -> void:
	if settlement == null:
		return
	position = settlement.pos
	_apply_sprites()
	queue_redraw()


func _apply_sprites() -> void:
	if _building == null or settlement == null:
		return
	var militarized := settlement.military_strength > 40.0
	var tex := Art.building(sprite_set, settlement.population, militarized)
	if tex != _last_kind:
		_building.texture = tex
		_last_kind = tex
	_flag.texture = Art.flag(sprite_set)
	_turret.visible = settlement.military_strength > 120.0
	if _turret.visible and _turret.texture == null:
		_turret.texture = Art.sprite("building_%s_turret" % sprite_set)
	# Дефицит энергии гасит постройку: аварийный режим видно без инспектора.
	var dim := 1.0 - clampf(settlement.energy_deficit, 0.0, 1.0) * 0.45
	_building.modulate = Color(dim, dim, dim, 1.0)


func radius() -> float:
	if settlement == null:
		return 12.0
	return 12.0 + sqrt(maxf(0.0, settlement.population)) * 0.5


func _draw() -> void:
	if settlement == null:
		return
	var r := radius()

	if selected:
		draw_arc(Vector2.ZERO, r + 6.0, 0.0, TAU, 32, Color(1, 1, 1, 0.9), 2.0, true)
	elif settlement.is_tier1:
		# Тонкое кольцо только у детально симулируемых ячеек: подсказка, где
		# именно живут юниты Tier 1.
		draw_arc(Vector2.ZERO, r + 4.0, 0.0, TAU, 24,
			Color(faction_color.r, faction_color.g, faction_color.b, 0.30), 1.0, true)

	# Полоска лояльности под постройкой вместо двух дуг вокруг неё: дуги
	# перегружали карту и спорили со спрайтом.
	var bar_w := 22.0
	var loyalty_frac := clampf(settlement.loyalty / 100.0, 0.0, 1.0)
	var bar_y := r + 8.0
	draw_line(Vector2(-bar_w * 0.5, bar_y), Vector2(bar_w * 0.5, bar_y), Color(0, 0, 0, 0.45), 3.0)
	draw_line(Vector2(-bar_w * 0.5, bar_y), Vector2(-bar_w * 0.5 + bar_w * loyalty_frac, bar_y),
		Color(0.45, 0.90, 0.60, 0.95) if loyalty_frac > 0.4 else Color(0.98, 0.55, 0.35, 0.95), 3.0)

	if settlement.unrest > 0.35:
		draw_circle(Vector2(bar_w * 0.5 + 5.0, bar_y), 2.5, Color(0.98, 0.42, 0.36))

	if show_label:
		var font := ThemeDB.fallback_font
		var text := "%s · %d" % [settlement.name, int(settlement.population)]
		var size := 11
		var width := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, size).x
		# Подпись с тёмной подложкой: без неё текст терялся на светлом песке.
		draw_string_outline(font, Vector2(-width * 0.5, bar_y + 14.0), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, size, 4, Color(0.04, 0.05, 0.07, 0.9))
		draw_string(font, Vector2(-width * 0.5, bar_y + 14.0), text,
			HORIZONTAL_ALIGNMENT_LEFT, -1, size, Color(0.92, 0.95, 0.97))
