extends SceneTree

## Инструмент разработки: снимок экрана без устройства и без рук.
##
##   xvfb-run -a godot --path . --resolution 1280x720 \
##       --script scripts/tools/screenshot.gd -- --day 400 --out shot.png
##
## Нужен, чтобы проверять компоновку HUD и читаемость карты на CI и в
## headless-окружении: компиляция сцены ничего не говорит о том, не залезли ли
## панели друг на друга и видно ли вообще остров.

const DEFAULT_OUT := "user://shot.png"
const WARMUP_FRAMES := 4

var game: Node
var frames: int = 0
var target_day: int = 300
var out_path: String = DEFAULT_OUT
var zoom: float = 1.0


func _initialize() -> void:
	_parse_args()
	var scene = load("res://scenes/Main.tscn")
	if scene == null:
		print("screenshot: не удалось загрузить Main.tscn")
		quit(1)
		return
	game = scene.instantiate()
	game.set("randomize_seed", false)
	game.set("world_seed", 20260730)
	root.add_child(game)


func _parse_args() -> void:
	var args := OS.get_cmdline_user_args()
	for i in args.size():
		match args[i]:
			"--day":
				if i + 1 < args.size():
					target_day = int(args[i + 1])
			"--out":
				if i + 1 < args.size():
					out_path = args[i + 1]
			"--zoom":
				if i + 1 < args.size():
					zoom = float(args[i + 1])


func _process(_delta: float) -> bool:
	frames += 1
	if frames == 1:
		# Развилки закрываются автоматически: иначе на снимке будет только
		# модальное окно выбора, а не игра.
		game.sim.story.choice_requested.connect(func(pending: Dictionary):
			var options = pending["choice"].get("options", [])
			if options is Array and not (options as Array).is_empty():
				game._on_choice_made(String(pending["id"]), String(options[0]["id"])))
		# Промотка тиками, а не реальным временем: кадры в headless идут
		# без ограничения, и по delta симуляция бы не сдвинулась.
		game.sim.clock.run_days(target_day)
		game.hud.refresh_slow()
		var capital = game.sim.world.capital_of(game.sim.world.council_id)
		if capital != null:
			game.camera.focus_on(capital.pos)
			game.hud.inspector.show_settlement(capital)
		game.camera.zoom = Vector2(zoom, zoom)
		game.tiers.reevaluate(game.sim.clock.tick_count, game.camera.position, game.camera.view_scale())
		game.sim.clock.set_speed_index(0)
		return false

	if frames >= WARMUP_FRAMES:
		_capture()
		return true
	return false


func _capture() -> void:
	var image := root.get_texture().get_image()
	if image == null:
		print("screenshot: пустой кадр (нет рендера?)")
		quit(1)
		return
	var err := image.save_png(out_path)
	var w = game.sim.world
	print("screenshot: %s · день %d · акт %d · население %.0f · ячеек %d · юнитов Tier 1 %d" % [
		out_path if err == OK else "ОШИБКА %d" % err,
		game.sim.day(), w.act, w.total_population, w.settlements.size(),
		game.tiers.active_units()])
	quit(0 if err == OK else 1)
