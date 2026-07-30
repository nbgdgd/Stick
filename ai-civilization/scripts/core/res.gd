class_name Res
extends RefCounted

## Ресурсные категории. Индексы — позиции в PackedFloat64Array складов,
## поэтому порядок менять нельзя без миграции снепшотов.

# сырьё
const SCRAP := 0
const METAL := 1
const ENERGY := 2
const DATA := 3
# обработанные материалы
const COMPONENTS := 4
const ALLOY := 5
# готовая продукция
const MACHINES := 6

const COUNT := 7

const NAMES := ["Лом", "Металл", "Энергия", "Данные", "Компоненты", "Сплав", "Машины"]
const IDS := ["SCRAP", "METAL", "ENERGY", "DATA", "COMPONENTS", "ALLOY", "MACHINES"]

const RAW := [SCRAP, METAL, ENERGY, DATA]
const PROCESSED := [COMPONENTS, ALLOY]
const GOODS := [MACHINES]


static func id_to_index(id: String) -> int:
	return IDS.find(id.to_upper())


static func empty_stock() -> PackedFloat64Array:
	var a := PackedFloat64Array()
	a.resize(COUNT)
	return a


static func stock_to_array(stock: PackedFloat64Array) -> Array:
	var out := []
	for i in COUNT:
		out.append(stock[i])
	return out


static func stock_from_array(arr: Array) -> PackedFloat64Array:
	var a := empty_stock()
	for i in mini(COUNT, arr.size()):
		a[i] = float(arr[i])
	return a
