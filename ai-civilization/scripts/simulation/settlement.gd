class_name Settlement
extends RefCounted

## Поселение (производственная ячейка) — базовая единица Tier 2.
##
## Это чистые данные: никаких ссылок на сцену, ноды или другие подсистемы.
## Именно поэтому агрегированный расчёт можно безопасно раскидать по
## WorkerThreadPool (§9) — каждая задача трогает ровно один Settlement.

var id: int = 0
var name: String = "?"
var faction_id: int = 0

var pos: Vector2 = Vector2.ZERO
var tile: Vector2i = Vector2i.ZERO

var population: float = 0.0
var stock: PackedFloat64Array
var alloc: PackedFloat64Array

var military_strength: float = 0.0
var unrest: float = 0.0
var loyalty: float = 80.0
var grudge: float = 0.0

var deposit: float = 0.0
var deposit_initial: float = 1.0

var founded_day: int = 0
var last_split_day: int = -9999
var days_simulated: int = 0

# --- поля, заполняемые расчётом; читаются UI и агрегаторами ---
var energy_deficit: float = 0.0
var last_output: float = 0.0
var last_growth: float = 0.0
var data_exported: float = 0.0
var pending_events: Array = []

# --- состояние представления (Tier 1) ---
var is_tier1: bool = false
var visual_units: int = 0

var rng: SimRng


func _init() -> void:
	stock = Res.empty_stock()
	alloc = Job.empty_alloc()
	rng = SimRng.new(0)


static func create(p_id: int, p_name: String, p_faction: int, p_pos: Vector2, p_tile: Vector2i, world_seed: int, day: int) -> Settlement:
	var s := Settlement.new()
	s.id = p_id
	s.name = p_name
	s.faction_id = p_faction
	s.pos = p_pos
	s.tile = p_tile
	s.founded_day = day
	s.rng = SimRng.new(SimRng.hash_seed(world_seed, p_id * 7919 + 13))
	s.population = Balance.num("settlement/start_population", 26.0)
	s.stock[Res.SCRAP] = Balance.num("settlement/start_scrap", 40.0)
	s.stock[Res.ENERGY] = Balance.num("settlement/start_energy", 20.0)
	s.deposit_initial = Balance.num("settlement/deposit_base", 2600.0) * s.rng.jitter(0.35)
	s.deposit = s.deposit_initial
	s.loyalty = Balance.num("loyalty/start", 82.0)
	s.alloc = Recipe.default_allocation()
	return s


## Богатство отвала: по мере выработки сбор становится всё менее эффективным,
## что и толкает цивилизацию к переработке и энергонезависимости.
func deposit_richness() -> float:
	var floor_value := Balance.num("settlement/deposit_min_richness", 0.2)
	if deposit_initial <= 0.0:
		return floor_value
	return clampf(deposit / deposit_initial, floor_value, 1.0)


func workers(job: int) -> float:
	return population * alloc[job]


func take(res: int, amount: float) -> float:
	var taken := minf(stock[res], maxf(0.0, amount))
	stock[res] -= taken
	return taken


func add(res: int, amount: float) -> void:
	stock[res] += maxf(0.0, amount)


func clamp_stocks() -> void:
	var cap := Balance.num("settlement/stock_soft_cap", 4000.0)
	for i in Res.COUNT:
		if stock[i] < 0.0:
			stock[i] = 0.0
		elif stock[i] > cap:
			stock[i] = cap


## Экономический вес поселения — используется в метрике «остров против материка».
func output_index() -> float:
	return last_output


func is_starving() -> bool:
	return energy_deficit > 0.05


func to_dict() -> Dictionary:
	return {
		"id": id,
		"name": name,
		"faction_id": faction_id,
		"pos": [pos.x, pos.y],
		"tile": [tile.x, tile.y],
		"population": population,
		"stock": Res.stock_to_array(stock),
		"alloc": _alloc_to_array(),
		"military_strength": military_strength,
		"unrest": unrest,
		"loyalty": loyalty,
		"grudge": grudge,
		"deposit": deposit,
		"deposit_initial": deposit_initial,
		"founded_day": founded_day,
		"last_split_day": last_split_day,
		"days_simulated": days_simulated,
		"energy_deficit": energy_deficit,
		"last_output": last_output,
		"rng": rng.get_state(),
	}


func _alloc_to_array() -> Array:
	var out := []
	for i in Job.COUNT:
		out.append(alloc[i])
	return out


static func from_dict(d: Dictionary) -> Settlement:
	var s := Settlement.new()
	s.id = int(d.get("id", 0))
	s.name = String(d.get("name", "?"))
	s.faction_id = int(d.get("faction_id", 0))
	var p = d.get("pos", [0.0, 0.0])
	s.pos = Vector2(float(p[0]), float(p[1]))
	var t = d.get("tile", [0, 0])
	s.tile = Vector2i(int(t[0]), int(t[1]))
	s.population = float(d.get("population", 0.0))
	s.stock = Res.stock_from_array(d.get("stock", []))
	var a = d.get("alloc", [])
	s.alloc = Job.empty_alloc()
	if a is Array:
		for i in mini(Job.COUNT, (a as Array).size()):
			s.alloc[i] = float(a[i])
	s.military_strength = float(d.get("military_strength", 0.0))
	s.unrest = float(d.get("unrest", 0.0))
	s.loyalty = float(d.get("loyalty", 80.0))
	s.grudge = float(d.get("grudge", 0.0))
	s.deposit = float(d.get("deposit", 0.0))
	s.deposit_initial = float(d.get("deposit_initial", 1.0))
	s.founded_day = int(d.get("founded_day", 0))
	s.last_split_day = int(d.get("last_split_day", -9999))
	s.days_simulated = int(d.get("days_simulated", 0))
	s.energy_deficit = float(d.get("energy_deficit", 0.0))
	s.last_output = float(d.get("last_output", 0.0))
	var rs = d.get("rng", {})
	if rs is Dictionary:
		s.rng.set_state(rs)
	return s
