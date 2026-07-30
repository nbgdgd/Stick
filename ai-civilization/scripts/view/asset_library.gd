class_name Art
extends RefCounted

## Слой доступа к графике (§8).
##
## Проект не завязан на конкретные файлы: если в assets/kenney/ лежит нужный
## спрайт (пути описаны в data/art_manifest.json), берётся он; иначе
## генерируется процедурный плейсхолдер того же размера и роли. Арт-пасс
## поэтому сводится к правке манифеста, а не кода — и проект запускается
## сразу, без предварительной загрузки паков.
##
## Все паки, перечисленные в манифесте, — CC0. Лицензию каждого файла нужно
## проверять на странице пака перед добавлением: на OpenGameArt лицензии
## смешаны, и CC0 там не по умолчанию.

const PIXEL := Color(1, 1, 1, 1)

static var _cache: Dictionary = {}
static var _manifest: Dictionary = {}


static func _ensure_manifest() -> void:
	if not _manifest.is_empty():
		return
	var data := Balance.file("art_manifest")
	var s = data.get("sprites", {})
	_manifest = s if s is Dictionary else {}


static func sprite(key: String) -> Texture2D:
	if _cache.has(key):
		return _cache[key]
	_ensure_manifest()
	var tex: Texture2D = null
	var path := String(_manifest.get(key, ""))
	if not path.is_empty() and ResourceLoader.exists(path):
		tex = load(path) as Texture2D
	if tex == null:
		tex = _generate(key)
	_cache[key] = tex
	return tex


static func has_external(key: String) -> bool:
	_ensure_manifest()
	var path := String(_manifest.get(key, ""))
	return not path.is_empty() and ResourceLoader.exists(path)


static func clear_cache() -> void:
	_cache.clear()
	_manifest.clear()


# ------------------------------------------------------------ плейсхолдеры

static func _generate(key: String) -> Texture2D:
	match key:
		"unit_worker": return _robot(Color(0.55, 0.85, 0.95), Color(0.20, 0.35, 0.45))
		"unit_hauler": return _robot(Color(0.80, 0.80, 0.55), Color(0.35, 0.35, 0.20))
		"unit_guard": return _robot(Color(0.95, 0.55, 0.45), Color(0.40, 0.20, 0.18))
		"unit_soldier": return _robot(Color(0.90, 0.85, 0.55), Color(0.35, 0.30, 0.15))
		"scrap_node": return _scrap()
		"settlement": return _settlement()
		"spark": return _dot(Color(1.0, 0.9, 0.6))
		"prop_rock": return _rock()
		"prop_scrap": return _scrap_pile()
		"prop_wreck": return _wreck()
		"prop_debris": return _debris()
	return _dot(Color(1, 0, 1))


## Валун: тёплый серый, светлая грань сверху-слева — совпадает с направлением
## света в шейдере террейна, иначе пропы «висят» отдельно от рельефа.
static func _rock() -> Texture2D:
	var img := Image.create(7, 6, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	var dark := Color(0.28, 0.26, 0.24)
	var body := Color(0.42, 0.40, 0.37)
	var lit := Color(0.56, 0.54, 0.50)
	for p in [Vector2i(2, 1), Vector2i(3, 1), Vector2i(4, 1),
			Vector2i(1, 2), Vector2i(2, 2), Vector2i(3, 2), Vector2i(4, 2), Vector2i(5, 2),
			Vector2i(1, 3), Vector2i(2, 3), Vector2i(3, 3), Vector2i(4, 3), Vector2i(5, 3),
			Vector2i(2, 4), Vector2i(3, 4), Vector2i(4, 4)]:
		img.set_pixel(p.x, p.y, body)
	img.set_pixel(2, 1, lit)
	img.set_pixel(3, 1, lit)
	img.set_pixel(1, 2, lit)
	for p in [Vector2i(2, 4), Vector2i(3, 4), Vector2i(4, 4), Vector2i(5, 3)]:
		img.set_pixel(p.x, p.y, dark)
	return _texture_from(img)


## Куча лома: ржавые обломки с блёстками металла.
static func _scrap_pile() -> Texture2D:
	var img := Image.create(9, 7, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	var rust := Color(0.44, 0.28, 0.16)
	var rust_d := Color(0.30, 0.19, 0.11)
	var metal := Color(0.62, 0.62, 0.64)
	for x in range(1, 8):
		img.set_pixel(x, 5, rust_d)
	for p in [Vector2i(2, 4), Vector2i(3, 4), Vector2i(4, 4), Vector2i(5, 4), Vector2i(6, 4),
			Vector2i(3, 3), Vector2i(4, 3), Vector2i(5, 3), Vector2i(4, 2)]:
		img.set_pixel(p.x, p.y, rust)
	img.set_pixel(4, 2, metal)
	img.set_pixel(6, 4, metal)
	img.set_pixel(2, 4, rust_d)
	img.set_pixel(1, 4, rust)
	img.set_pixel(7, 4, rust)
	return _texture_from(img)


## Остов сброшенного контейнера на берегу — след того, как остров стал свалкой.
static func _wreck() -> Texture2D:
	var img := Image.create(12, 8, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	var hull := Color(0.38, 0.36, 0.34)
	var hull_d := Color(0.24, 0.23, 0.22)
	var rust := Color(0.46, 0.28, 0.15)
	for y in range(2, 6):
		for x in range(1, 11):
			img.set_pixel(x, y, hull if (x + y) % 3 != 0 else rust)
	for x in range(1, 11):
		img.set_pixel(x, 6, hull_d)
		img.set_pixel(x, 1, hull_d if x % 2 == 0 else hull)
	img.set_pixel(1, 2, hull_d)
	img.set_pixel(10, 5, rust)
	return _texture_from(img)


## Мелкий мусор: пара точек, чтобы грунт не был идеально чистым.
static func _debris() -> Texture2D:
	var img := Image.create(5, 4, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	var c := Color(0.36, 0.31, 0.25)
	img.set_pixel(1, 2, c)
	img.set_pixel(2, 2, c)
	img.set_pixel(3, 1, Color(0.44, 0.40, 0.34))
	img.set_pixel(2, 3, Color(0.26, 0.23, 0.19))
	return _texture_from(img)


static func _texture_from(img: Image) -> Texture2D:
	return ImageTexture.create_from_image(img)


## Схематичный корпус 8x8: две «ноги», корпус, светящийся сенсор.
static func _robot(body: Color, dark: Color) -> Texture2D:
	var img := Image.create(8, 8, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	for y in range(1, 6):
		for x in range(2, 6):
			img.set_pixel(x, y, body if y % 2 == 0 else dark)
	img.set_pixel(2, 6, dark)
	img.set_pixel(5, 6, dark)
	img.set_pixel(2, 7, dark)
	img.set_pixel(5, 7, dark)
	img.set_pixel(3, 2, Color(1.0, 0.95, 0.7))
	img.set_pixel(4, 2, Color(1.0, 0.95, 0.7))
	return _texture_from(img)


static func _scrap() -> Texture2D:
	var img := Image.create(6, 6, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	var rust := Color(0.45, 0.32, 0.22)
	var metal := Color(0.60, 0.60, 0.62)
	for p in [Vector2i(1, 3), Vector2i(2, 2), Vector2i(3, 3), Vector2i(4, 2), Vector2i(2, 4), Vector2i(3, 4)]:
		img.set_pixel(p.x, p.y, rust)
	img.set_pixel(2, 3, metal)
	img.set_pixel(3, 2, metal)
	return _texture_from(img)


static func _settlement() -> Texture2D:
	var img := Image.create(16, 16, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	var wall := Color(0.72, 0.74, 0.78)
	var inner := Color(0.30, 0.33, 0.38)
	for x in range(2, 14):
		img.set_pixel(x, 3, wall)
		img.set_pixel(x, 12, wall)
	for y in range(3, 13):
		img.set_pixel(2, y, wall)
		img.set_pixel(13, y, wall)
	for y in range(5, 11):
		for x in range(4, 12):
			img.set_pixel(x, y, inner)
	# труба
	for y in range(1, 6):
		img.set_pixel(7, y, wall)
		img.set_pixel(8, y, wall)
	return _texture_from(img)


static func _dot(c: Color) -> Texture2D:
	var img := Image.create(3, 3, false, Image.FORMAT_RGBA8)
	img.fill(Color(0, 0, 0, 0))
	img.set_pixel(1, 1, c)
	img.set_pixel(0, 1, Color(c.r, c.g, c.b, 0.5))
	img.set_pixel(2, 1, Color(c.r, c.g, c.b, 0.5))
	img.set_pixel(1, 0, Color(c.r, c.g, c.b, 0.5))
	img.set_pixel(1, 2, Color(c.r, c.g, c.b, 0.5))
	return _texture_from(img)


# ------------------------------------------------------------ палитра террейна

static func terrain_color(kind: int, elevation: float) -> Color:
	var shade := 0.85 + elevation * 0.35
	match kind:
		WorldGen.WATER:
			return Color(0.05, 0.09, 0.16).lerp(Color(0.08, 0.17, 0.28), elevation * 2.0)
		WorldGen.SAND:
			return Color(0.42, 0.38, 0.30) * shade
		WorldGen.LAND:
			return Color(0.24, 0.27, 0.24) * shade
		WorldGen.HILL:
			return Color(0.30, 0.30, 0.31) * shade
		WorldGen.DUMP:
			return Color(0.38, 0.29, 0.20) * shade
	return Color.MAGENTA
