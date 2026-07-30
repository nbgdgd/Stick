class_name Job
extends RefCounted

## Рабочие специальности. Население поселения распределяется между ними
## долями (allocation), каждая специальность исполняет один рецепт —
## самый высокий разблокированный tier для этой специальности.

const SCAVENGE := 0
const POWER := 1
const SMELT := 2
const REFINE := 3
const FAB := 4
const ASSEMBLE := 5
const RESEARCH := 6
const MILITARY := 7

const COUNT := 8

const IDS := ["SCAVENGE", "POWER", "SMELT", "REFINE", "FAB", "ASSEMBLE", "RESEARCH", "MILITARY"]
const NAMES := ["Сбор", "Энергия", "Плавка", "Сплавы", "Сборка узлов", "Машины", "Расчёты", "Вооружение"]


static func id_to_index(id: String) -> int:
	return IDS.find(id.to_upper())


static func empty_alloc() -> PackedFloat64Array:
	var a := PackedFloat64Array()
	a.resize(COUNT)
	return a


static func alloc_from_dict(d: Dictionary) -> PackedFloat64Array:
	var a := empty_alloc()
	for key in d.keys():
		var idx := id_to_index(String(key))
		if idx >= 0:
			a[idx] = float(d[key])
	return a


## Нормализует доли так, чтобы сумма не превышала 1.0 (недогруз допустим —
## это "простаивающее" население, оно всё равно потребляет upkeep).
static func normalized(alloc: PackedFloat64Array) -> PackedFloat64Array:
	var total := 0.0
	for i in COUNT:
		total += maxf(0.0, alloc[i])
	if total <= 1.0:
		var clean := empty_alloc()
		for i in COUNT:
			clean[i] = maxf(0.0, alloc[i])
		return clean
	var out := empty_alloc()
	for i in COUNT:
		out[i] = maxf(0.0, alloc[i]) / total
	return out
