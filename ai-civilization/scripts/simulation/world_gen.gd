class_name WorldGen
extends RefCounted

## Генерация острова-свалки. Детерминирована по seed: тот же seed — та же карта,
## что нужно и для снепшотов, и для воспроизводимых тестов.

const WATER := 0
const SAND := 1
const LAND := 2
const HILL := 3
const DUMP := 4


static func generate(world_seed: int) -> Dictionary:
	var size := Balance.int_at("world/map_size", 192)
	var sea_level := Balance.num("world/sea_level", 0.46)
	var radius_frac := Balance.num("world/island_radius_frac", 0.40)

	var height := FastNoiseLite.new()
	height.seed = world_seed
	height.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	height.frequency = Balance.num("world/noise_frequency", 0.028)
	height.fractal_octaves = 4

	var scrap_noise := FastNoiseLite.new()
	scrap_noise.seed = world_seed ^ 0x5F5F
	scrap_noise.noise_type = FastNoiseLite.TYPE_SIMPLEX
	scrap_noise.frequency = Balance.num("world/scrap_field_frequency", 0.06)

	var terrain := PackedByteArray()
	terrain.resize(size * size)
	var elevation := PackedFloat32Array()
	elevation.resize(size * size)
	var scrap := PackedFloat32Array()
	scrap.resize(size * size)

	var center := Vector2(size, size) * 0.5
	var max_radius := float(size) * radius_frac
	var land_tiles := 0

	for y in size:
		for x in size:
			var i := y * size + x
			var d := Vector2(x, y).distance_to(center) / max_radius
			# Радиальный спад делает из шума именно остров, а не сплошной материк.
			var falloff := clampf(1.0 - pow(d, 2.2), 0.0, 1.0)
			var h := (height.get_noise_2d(float(x), float(y)) * 0.5 + 0.5) * falloff
			elevation[i] = h
			if h < sea_level:
				terrain[i] = WATER
				scrap[i] = 0.0
				continue
			land_tiles += 1
			var s := scrap_noise.get_noise_2d(float(x), float(y)) * 0.5 + 0.5
			scrap[i] = s
			if h < sea_level + 0.04:
				terrain[i] = SAND
			elif s > 0.72:
				terrain[i] = DUMP
			elif h > sea_level + 0.20:
				terrain[i] = HILL
			else:
				terrain[i] = LAND

	return {
		"size": size,
		"terrain": terrain,
		"elevation": elevation,
		"scrap": scrap,
		"land_tiles": land_tiles,
		"sites": _pick_sites(size, terrain, scrap, world_seed),
	}


## Стартовые площадки: самые «богатые» тайлы, разнесённые друг от друга,
## чтобы первые ячейки не слиплись в один пиксель.
static func _pick_sites(size: int, terrain: PackedByteArray, scrap: PackedFloat32Array, world_seed: int) -> Array:
	var rng := SimRng.new(SimRng.hash_seed(world_seed, 31337))
	var candidates: Array = []
	for y in range(2, size - 2):
		for x in range(2, size - 2):
			var i := y * size + x
			if terrain[i] == WATER or terrain[i] == SAND:
				continue
			var score := scrap[i] + rng.randf() * 0.25
			candidates.append({ "tile": Vector2i(x, y), "score": score })
	candidates.sort_custom(func(a, b): return float(a["score"]) > float(b["score"]))

	var min_dist := maxf(6.0, float(size) * 0.10)
	var sites: Array = []
	for c in candidates:
		var tile: Vector2i = c["tile"]
		var ok := true
		for existing in sites:
			if Vector2(tile).distance_to(Vector2(existing as Vector2i)) < min_dist:
				ok = false
				break
		if ok:
			sites.append(tile)
		if sites.size() >= 40:
			break
	return sites


static func is_land(terrain: PackedByteArray, size: int, tile: Vector2i) -> bool:
	if tile.x < 0 or tile.y < 0 or tile.x >= size or tile.y >= size:
		return false
	return terrain[tile.y * size + tile.x] != WATER
