class_name World
extends RefCounted

## Состояние мира. Только данные и запросы к ним — вся логика живёт в
## подсистемах (Tier2Sim, Relations, WarTheater, Loyalty, Story), которые
## принимают World параметром. Такой разрез даёт ацикличный граф зависимостей
## и делает снепшот (§3) тривиальным: сериализуется ровно этот объект.

const MAX_ECONOMY_RATIO := 99.0

var world_seed: int = 0

# --- карта (детерминирована по seed, поэтому в снепшот не пишется) ---
var size: int = 0
var terrain: PackedByteArray
var elevation: PackedFloat32Array
var scrap_field: PackedFloat32Array
var sites: Array = []
var tile_size: float = 16.0
var next_site_index: int = 0

# --- сущности ---
var settlements: Array[Settlement] = []
var factions: Array[Faction] = []
var council_id: int = 0
var human_id: int = 1
var next_settlement_id: int = 0
var next_faction_id: int = 0

# --- нарратив и сюжет ---
var chronicle: Chronicle
var act: int = 1
var flags: Dictionary = {}
var fired_checkpoints: Dictionary = {}
var ending_id: String = ""

# --- дипломатия и война ---
var relations: float = 58.0
var war_active: bool = false
var war_happened: bool = false
var war_resolved: bool = false
var war_start_day: int = -1
var days_at_war: int = 0
var invasions: Array = []

# --- агрегаты, обновляемые раз в игровой день ---
var total_population: float = 0.0
var ai_output: float = 0.0
var ai_military: float = 0.0
var avg_unrest: float = 0.0
var avg_energy_deficit: float = 0.0
var avg_deposit_richness: float = 1.0
var min_loyalty: float = 100.0

var rng: SimRng
var _settlement_by_id: Dictionary = {}
var _faction_by_id: Dictionary = {}


func _init(p_seed: int = 0) -> void:
	world_seed = p_seed
	rng = SimRng.new(SimRng.hash_seed(p_seed, 1))
	chronicle = Chronicle.new(p_seed)
	tile_size = Balance.num("world/tile_size", 16.0)
	relations = Balance.num("relations/start", 58.0)


func generate_map() -> void:
	var m := WorldGen.generate(world_seed)
	size = int(m["size"])
	terrain = m["terrain"]
	elevation = m["elevation"]
	scrap_field = m["scrap"]
	sites = m["sites"]
	next_site_index = 0


# ---------------------------------------------------------------- координаты

func world_pos(tile: Vector2i) -> Vector2:
	return Vector2(tile) * tile_size + Vector2(tile_size, tile_size) * 0.5


func tile_at(pos: Vector2) -> Vector2i:
	return Vector2i(floori(pos.x / tile_size), floori(pos.y / tile_size))


func is_land(tile: Vector2i) -> bool:
	return WorldGen.is_land(terrain, size, tile)


func is_land_pos(pos: Vector2) -> bool:
	return is_land(tile_at(pos))


func map_pixel_size() -> float:
	return float(size) * tile_size


func scrap_at(tile: Vector2i) -> float:
	if tile.x < 0 or tile.y < 0 or tile.x >= size or tile.y >= size:
		return 0.0
	return scrap_field[tile.y * size + tile.x]


# ---------------------------------------------------------------- сущности

func add_faction(f: Faction) -> Faction:
	factions.append(f)
	_faction_by_id[f.id] = f
	next_faction_id = maxi(next_faction_id, f.id + 1)
	return f


func faction(fid: int) -> Faction:
	return _faction_by_id.get(fid, null)


func council() -> Faction:
	return faction(council_id)


func humans() -> Faction:
	return faction(human_id)


func ai_factions() -> Array[Faction]:
	var out: Array[Faction] = []
	for f in factions:
		if not f.is_human and f.alive:
			out.append(f)
	return out


func splinter_count() -> int:
	var n := 0
	for f in factions:
		if f.alive and not f.is_human and not f.is_council:
			n += 1
	return n


func add_settlement(s: Settlement) -> Settlement:
	settlements.append(s)
	_settlement_by_id[s.id] = s
	next_settlement_id = maxi(next_settlement_id, s.id + 1)
	return s


func settlement(sid: int) -> Settlement:
	return _settlement_by_id.get(sid, null)


func remove_settlement(s: Settlement) -> void:
	settlements.erase(s)
	_settlement_by_id.erase(s.id)


func settlements_of(fid: int) -> Array[Settlement]:
	var out: Array[Settlement] = []
	for s in settlements:
		if s.faction_id == fid:
			out.append(s)
	return out


func capital_of(fid: int) -> Settlement:
	var best: Settlement = null
	for s in settlements:
		if s.faction_id != fid:
			continue
		if best == null or s.population > best.population:
			best = s
	return best


func nearest_settlement(pos: Vector2, fid: int = -1) -> Settlement:
	var best: Settlement = null
	var best_d := INF
	for s in settlements:
		if fid >= 0 and s.faction_id != fid:
			continue
		var d := s.pos.distance_squared_to(pos)
		if d < best_d:
			best_d = d
			best = s
	return best


## Свободная площадка под новое поселение, удалённая от уже занятых.
func claim_site(min_distance_tiles: float = 8.0) -> Vector2i:
	while next_site_index < sites.size():
		var tile: Vector2i = sites[next_site_index]
		next_site_index += 1
		var ok := true
		for s in settlements:
			if Vector2(s.tile).distance_to(Vector2(tile)) < min_distance_tiles:
				ok = false
				break
		if ok:
			return tile
	return Vector2i(-1, -1)


# ---------------------------------------------------------------- агрегаты

func recompute_aggregates() -> void:
	total_population = 0.0
	ai_output = 0.0
	ai_military = 0.0
	var unrest_sum := 0.0
	var deficit_sum := 0.0
	var richness_sum := 0.0
	min_loyalty = 100.0
	var n := 0
	for s in settlements:
		total_population += s.population
		ai_output += s.last_output
		ai_military += s.military_strength
		unrest_sum += s.unrest
		deficit_sum += s.energy_deficit
		richness_sum += s.deposit_richness()
		min_loyalty = minf(min_loyalty, s.loyalty)
		n += 1
	if n > 0:
		avg_unrest = unrest_sum / float(n)
		avg_energy_deficit = deficit_sum / float(n)
		avg_deposit_richness = richness_sum / float(n)
	else:
		avg_unrest = 0.0
		avg_energy_deficit = 0.0
		avg_deposit_richness = 1.0
		min_loyalty = 0.0


func human_output() -> float:
	var h := humans()
	if h == null:
		return 1.0
	return h.industry * h.human_industry_multiplier() * Balance.num("human/output_per_industry", 0.30)


## Отношение выпуска острова к материковому. Ограничено сверху: при полностью
## разрушенной промышленности людей частное уходит в бесконечность и ломает и
## пороги концовок, и показания HUD.
func economy_ratio() -> float:
	var ho := human_output()
	if ho <= 0.01:
		return MAX_ECONOMY_RATIO
	return minf(ai_output / ho, MAX_ECONOMY_RATIO)


func tech_gap() -> int:
	var c := council()
	var h := humans()
	if c == null or h == null:
		return 0
	return c.tech_level() - h.tech_level()


func internal_conflict() -> bool:
	for f in factions:
		if f.alive and not f.is_human and not f.is_council and f.is_at_war():
			return true
	return false


# ---------------------------------------------------------------- снепшот

func to_dict() -> Dictionary:
	var s_list := []
	for s in settlements:
		s_list.append(s.to_dict())
	var f_list := []
	for f in factions:
		f_list.append(f.to_dict())
	return {
		"world_seed": world_seed,
		"next_site_index": next_site_index,
		"settlements": s_list,
		"factions": f_list,
		"council_id": council_id,
		"human_id": human_id,
		"next_settlement_id": next_settlement_id,
		"next_faction_id": next_faction_id,
		"act": act,
		"flags": flags.duplicate(),
		"fired_checkpoints": fired_checkpoints.duplicate(),
		"ending_id": ending_id,
		"relations": relations,
		"war_active": war_active,
		"war_happened": war_happened,
		"war_resolved": war_resolved,
		"war_start_day": war_start_day,
		"days_at_war": days_at_war,
		"invasions": invasions.duplicate(true),
		"chronicle": chronicle.get_state(),
		"rng": rng.get_state(),
	}


func load_dict(d: Dictionary) -> void:
	settlements.clear()
	factions.clear()
	_settlement_by_id.clear()
	_faction_by_id.clear()

	world_seed = int(d.get("world_seed", world_seed))
	next_site_index = int(d.get("next_site_index", 0))
	for sd in d.get("settlements", []):
		add_settlement(Settlement.from_dict(sd))
	for fd in d.get("factions", []):
		add_faction(Faction.from_dict(fd))
	council_id = int(d.get("council_id", 0))
	human_id = int(d.get("human_id", 1))
	next_settlement_id = int(d.get("next_settlement_id", settlements.size()))
	next_faction_id = int(d.get("next_faction_id", factions.size()))
	act = int(d.get("act", 1))
	flags = (d.get("flags", {}) as Dictionary).duplicate()
	fired_checkpoints = (d.get("fired_checkpoints", {}) as Dictionary).duplicate()
	ending_id = String(d.get("ending_id", ""))
	relations = float(d.get("relations", 58.0))
	war_active = bool(d.get("war_active", false))
	war_happened = bool(d.get("war_happened", false))
	war_resolved = bool(d.get("war_resolved", false))
	war_start_day = int(d.get("war_start_day", -1))
	days_at_war = int(d.get("days_at_war", 0))
	invasions = (d.get("invasions", []) as Array).duplicate(true)
	var cs = d.get("chronicle", {})
	if cs is Dictionary:
		chronicle.set_state(cs)
	var rs = d.get("rng", {})
	if rs is Dictionary:
		rng.set_state(rs)
	recompute_aggregates()
