class_name WorldView
extends Node2D

## Отрисовка мира. Террейн — один раз запечённый Image → ImageTexture: для
## карты 192×192 это дешевле любого TileMap, не требует тайлсета и не зависит
## от внешних ассетов. Маркеры поселений и юниты Tier 1 живут поверх.

signal settlement_tapped(settlement: Settlement)

var world: World

var terrain_sprite: Sprite2D
var unit_pool: UnitPool
var markers: Dictionary = {}
var selected_id: int = -1

var _invasion_layer: Node2D


func setup(p_world: World) -> void:
	world = p_world

	terrain_sprite = Sprite2D.new()
	terrain_sprite.centered = false
	terrain_sprite.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	add_child(terrain_sprite)

	unit_pool = UnitPool.new()
	unit_pool.name = "UnitPool"
	unit_pool.z_index = 5
	add_child(unit_pool)

	_invasion_layer = Node2D.new()
	_invasion_layer.z_index = 8
	_invasion_layer.draw.connect(_draw_invasions)
	add_child(_invasion_layer)

	bake_terrain()
	rebuild_markers()


## Запекание карты в текстуру. Вызывается один раз на генерацию мира;
## после перемотки времени карта не меняется (она детерминирована по seed).
func bake_terrain() -> void:
	var size := world.size
	var img := Image.create(size, size, false, Image.FORMAT_RGBA8)
	for y in size:
		for x in size:
			var i := y * size + x
			img.set_pixel(x, y, Art.terrain_color(world.terrain[i], world.elevation[i]))
	terrain_sprite.texture = ImageTexture.create_from_image(img)
	terrain_sprite.scale = Vector2(world.tile_size, world.tile_size)
	terrain_sprite.position = Vector2.ZERO


func rebuild_markers() -> void:
	for key in markers.keys():
		(markers[key] as SettlementMarker).queue_free()
	markers.clear()
	for s in world.settlements:
		_add_marker(s)


func _add_marker(s: Settlement) -> void:
	var m := SettlementMarker.new()
	add_child(m)
	var f := world.faction(s.faction_id)
	m.bind(s, f.color if f != null else Color.WHITE)
	m.selected = s.id == selected_id
	markers[s.id] = m


## Синхронизация маркеров с состоянием: поселения появляются при расселении и
## исчезают при разрушении, а фракция может смениться при расколе.
func refresh() -> void:
	var alive := {}
	for s in world.settlements:
		alive[s.id] = true
		if not markers.has(s.id):
			_add_marker(s)
		else:
			var m: SettlementMarker = markers[s.id]
			var f := world.faction(s.faction_id)
			var color := f.color if f != null else Color.WHITE
			if color != m.faction_color:
				m.bind(s, color)
			m.selected = s.id == selected_id
			m.refresh()
	for key in markers.keys():
		if not alive.has(key):
			(markers[key] as SettlementMarker).queue_free()
			markers.erase(key)
	_invasion_layer.queue_redraw()


func select(sid: int) -> void:
	selected_id = sid
	refresh()


## Поиск поселения по нажатию: радиус захвата растёт с масштабом, иначе на
## отдалённой камере в маркер невозможно попасть пальцем.
func settlement_at(world_pos: Vector2, tolerance: float = 24.0) -> Settlement:
	var best: Settlement = null
	var best_d := INF
	for s in world.settlements:
		var d := s.pos.distance_to(world_pos)
		var m: SettlementMarker = markers.get(s.id, null)
		var reach := (m.radius() if m != null else 8.0) + tolerance
		if d <= reach and d < best_d:
			best_d = d
			best = s
	return best


func handle_tap(world_pos: Vector2, tolerance: float) -> bool:
	var s := settlement_at(world_pos, tolerance)
	if s == null:
		return false
	select(s.id)
	settlement_tapped.emit(s)
	return true


func _draw_invasions() -> void:
	for inv in world.invasions:
		var p = inv.get("pos", [0.0, 0.0])
		var pos := Vector2(float(p[0]), float(p[1]))
		var pulse := 26.0 + 8.0 * sin(float(Time.get_ticks_msec()) * 0.004)
		_invasion_layer.draw_arc(pos, pulse, 0.0, TAU, 24, Color(1.0, 0.35, 0.25, 0.85), 2.0, true)
		_invasion_layer.draw_arc(pos, pulse * 0.5, 0.0, TAU, 18, Color(1.0, 0.6, 0.3, 0.5), 1.5, true)
