class_name WorldView
extends Node2D

## Отрисовка мира.
##
## Террейн: в текстуру пишутся только высота и насыщенность лома (192×192), а
## палитра, рельефное затенение, прибой и дизеринг считаются шейдером
## (assets/shaders/terrain.gdshader). Запекание готовой картинки 3072×3072
## отвергнуто: при одном пикселе на тайл карта выглядела блоками, а при
## суперсэмплинге стоила секунды простоя на старте и ~10 МБ текстуры.
##
## Поверх террейна — слой пропов (камни, кучи лома, обломки), маркеры поселений
## и юниты Tier 1.

signal settlement_tapped(settlement: Settlement)

const TERRAIN_SHADER := "res://assets/shaders/terrain.gdshader"

var world: World

var terrain_sprite: Sprite2D
var unit_pool: UnitPool
var props: Node2D
var markers: Dictionary = {}
var selected_id: int = -1

var _invasion_layer: Node2D
var _terrain_material: ShaderMaterial


func setup(p_world: World) -> void:
	world = p_world

	terrain_sprite = Sprite2D.new()
	terrain_sprite.centered = false
	# Линейная фильтрация обязательна: именно она даёт гладкий берег на любом
	# зуме. Пиксельность добавляет уже шейдер (дизеринг, крупка металла).
	terrain_sprite.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	add_child(terrain_sprite)

	props = Node2D.new()
	props.name = "Props"
	props.z_index = 1
	add_child(props)

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


## Подготовка террейна. Вызывается один раз на генерацию мира; после перемотки
## времени карта не меняется (она детерминирована по seed).
##
## В текстуру идут только данные: R — высота, G — насыщенность лома. Высота
## нормируется по фактическому диапазону карты, потому что в RGBA8 всего 256
## уровней: без нормировки на сушу приходилось бы меньше сотни, и склоны шли
## бы видимыми ступенями.
func bake_terrain() -> void:
	var size := world.size
	var height_min := INF
	var height_max := -INF
	for i in world.elevation.size():
		height_min = minf(height_min, world.elevation[i])
		height_max = maxf(height_max, world.elevation[i])
	if height_max - height_min < 0.0001:
		height_max = height_min + 1.0

	var data := PackedByteArray()
	data.resize(size * size * 4)
	var inv_range := 1.0 / (height_max - height_min)
	for i in size * size:
		var h := (world.elevation[i] - height_min) * inv_range
		var o := i * 4
		data[o] = int(clampf(h, 0.0, 1.0) * 255.0)
		data[o + 1] = int(clampf(world.scrap_field[i], 0.0, 1.0) * 255.0)
		data[o + 2] = 0
		data[o + 3] = 255

	var img := Image.create_from_data(size, size, false, Image.FORMAT_RGBA8, data)
	var tex := ImageTexture.create_from_image(img)
	terrain_sprite.texture = tex
	terrain_sprite.scale = Vector2(world.tile_size, world.tile_size)
	terrain_sprite.position = Vector2.ZERO

	var shader: Shader = load(TERRAIN_SHADER)
	if shader == null:
		push_error("WorldView: не найден шейдер террейна %s" % TERRAIN_SHADER)
		return
	_terrain_material = ShaderMaterial.new()
	_terrain_material.shader = shader
	_terrain_material.set_shader_parameter("data_tex", tex)
	_terrain_material.set_shader_parameter("data_size", Vector2(size, size))
	_terrain_material.set_shader_parameter("height_min", height_min)
	_terrain_material.set_shader_parameter("height_max", height_max)
	_terrain_material.set_shader_parameter("sea_level", Balance.num("world/sea_level", 0.46))
	_terrain_material.set_shader_parameter("tile_px", world.tile_size)
	terrain_sprite.material = _terrain_material

	scatter_props()


## Пропы: остров — полигон списания, он обязан быть замусорен. Разброс
## детерминирован от world_seed тем же способом, что и сама карта, поэтому
## пропы не влияют на симуляцию и не участвуют в снепшотах.
func scatter_props() -> void:
	for child in props.get_children():
		child.queue_free()

	var rng := SimRng.new(SimRng.hash_seed(world.world_seed, 90210))
	var sea := Balance.num("world/sea_level", 0.46)
	var placed := 0
	var budget := Balance.int_at("world/prop_budget", 420)

	for tile_y in range(1, world.size - 1):
		for tile_x in range(1, world.size - 1):
			if placed >= budget:
				break
			var i := tile_y * world.size + tile_x
			var kind := world.terrain[i]
			if kind == WorldGen.WATER:
				continue
			var junk := world.scrap_field[i]
			var above := world.elevation[i] - sea

			var key := ""
			var chance := 0.0
			if kind == WorldGen.DUMP or junk > 0.70:
				key = "prop_scrap"
				chance = 0.11
			elif kind == WorldGen.HILL or above > 0.28:
				key = "prop_rock"
				chance = 0.06
			elif kind == WorldGen.SAND:
				key = "prop_wreck"
				chance = 0.035
			else:
				key = "prop_debris"
				chance = 0.045
			if not rng.chance(chance):
				continue

			var s := Sprite2D.new()
			s.texture = Art.sprite(key)
			s.centered = true
			s.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
			s.position = world.world_pos(Vector2i(tile_x, tile_y)) \
				+ Vector2(rng.randf_range(-6.0, 6.0), rng.randf_range(-6.0, 6.0))
			s.scale = Vector2.ONE * rng.randf_range(1.3, 2.3)
			s.rotation = rng.randf_range(0.0, TAU) if key != "prop_wreck" else 0.0
			s.modulate = Color(1, 1, 1).lerp(Color(0.72, 0.68, 0.62), rng.randf() * 0.5)
			props.add_child(s)
			placed += 1
		if placed >= budget:
			break


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
