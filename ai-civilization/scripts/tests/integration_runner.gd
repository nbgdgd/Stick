extends SceneTree

## Интеграционный прогон всей игры без окна:
##   godot --headless --path . --script scripts/tests/integration_runner.gd
##
## Тесты симуляции (tests.gd) не поднимают сцену вообще — это правильно, но
## тогда никем не проверены Tier 1, пул юнитов, отрисовка и HUD. Здесь
## запускается настоящая Main.tscn и прокручивается несколько сотен игровых
## дней на максимальной скорости, с дёрганием тех же рычагов, что нажимает
## игрок: наведение вмешательства, тап по карте, перемотка, сохранение.

const TOTAL_FRAMES := 1500
const SAVE_PATH := "user://ai_civilization_integration.dat"

var game: Node
var frames: int = 0
var failures: Array[String] = []
var checks: int = 0
var _peak_units: int = 0
var _choices_answered: int = 0


func _initialize() -> void:
	print("AI Civilization — интеграционный прогон сцены")
	var scene = load("res://scenes/Main.tscn")
	if scene == null:
		print("  ✗ не удалось загрузить res://scenes/Main.tscn")
		quit(1)
		return
	game = scene.instantiate()
	game.set("randomize_seed", false)
	game.set("world_seed", 20260730)
	root.add_child(game)
	# _ready() узла откладывается до старта дерева, поэтому обращаться к
	# game.sim здесь ещё нельзя — вся проводка делается на первом кадре.


func _wire() -> void:
	game.sim.story.choice_requested.connect(func(pending: Dictionary):
		var options = pending["choice"].get("options", [])
		if options is Array and not (options as Array).is_empty():
			_choices_answered += 1
			game._on_choice_made(String(pending["id"]), String(options[0]["id"])))

	check(game.sim != null, "симуляция не создана")
	check(game.view != null and game.view.terrain_sprite.texture != null, "террейн не запечён")
	check(game.hud != null, "HUD не собран")
	check(game.tiers != null, "TierManager не создан")
	game.sim.clock.set_speed_index(4)


func _process(_delta: float) -> bool:
	frames += 1
	if frames == 1:
		_wire()
		return false

	# Headless-кадры идут без ограничения по времени, поэтому delta почти
	# нулевая и по реальному времени симуляция не сдвинулась бы вообще.
	# Тик прокручивается явно: один игровой день на кадр.
	game.sim.clock.run_ticks(game.sim.clock.ticks_per_day)
	_peak_units = maxi(_peak_units, game.tiers.active_units())

	match frames:
		60:
			# Камера у столицы — рядом обязаны быть юниты Tier 1.
			check(game.tiers.tier1_settlements() >= 1, "ни одно поселение не попало в Tier 1")
			check(game.tiers.active_units() > 0, "юниты Tier 1 не заспавнились")
			check(game.view.markers.size() == game.sim.world.settlements.size(),
				"маркеров %d при %d поселениях" % [game.view.markers.size(), game.sim.world.settlements.size()])
		120:
			# Тап по столице должен открыть инспектор.
			var capital = game.sim.world.capital_of(game.sim.world.council_id)
			if capital != null:
				game._on_tapped(capital.pos)
				check(game.hud.inspector.visible, "инспектор не открылся по тапу в поселение")
				check(game.view.selected_id == capital.id, "поселение не выделилось")
		180:
			# Наведённое вмешательство должно тратиться ровно на один тап.
			game._on_targeting("drop_scrap")
			var capital2 = game.sim.world.capital_of(game.sim.world.council_id)
			var deposit_before: float = capital2.deposit if capital2 != null else 0.0
			game._on_tapped(capital2.pos if capital2 != null else Vector2.ZERO)
			check(capital2 == null or capital2.deposit > deposit_before,
				"сброс лома не увеличил отвал")
			check(game.hud.intervention_bar.armed().is_empty(), "режим наведения не сбросился")
			check(not game.sim.interventions.can_use("drop_scrap", game.sim.day()),
				"кулдаун вмешательства не установлен")
		240:
			# Камера уезжает за пределы острова — Tier 1 обязан опустеть.
			game.camera.focus_on(Vector2(-4000, -4000))
			game.tiers.reevaluate(game.sim.clock.tick_count, game.camera.position, game.camera.view_scale())
			check(game.tiers.active_units() == 0 or game.sim.world.invasions.size() > 0,
				"юниты не освободились при уезде камеры: %d активных" % game.tiers.active_units())
			var capital3 = game.sim.world.capital_of(game.sim.world.council_id)
			if capital3 != null:
				game.camera.focus_on(capital3.pos)
		300:
			check(game.tiers.active_units() > 0, "юниты не вернулись при возврате камеры")
			check(game.tiers.active_units() <= game.tiers.unit_budget,
				"превышен бюджет юнитов: %d > %d" % [game.tiers.active_units(), game.tiers.unit_budget])
		420:
			var before: int = game.sim.day()
			game._on_rewind(15)
			check(game.sim.day() < before, "перемотка назад не сработала в живой сцене")
			check(game.view.markers.size() == game.sim.world.settlements.size(),
				"маркеры не пересобрались после перемотки")
			check(game.tiers.active_units() > 0, "Tier 1 не восстановился после перемотки")
		480:
			check(game.sim.save_game(SAVE_PATH) == OK, "сохранение из сцены не удалось")
			var day_before: int = game.sim.day()
			check(game.sim.load_game(SAVE_PATH), "загрузка из сцены не удалась")
			check(game.sim.day() == day_before, "загрузка вернула другой день")
			game._on_rewound(game.sim.day())
			DirAccess.remove_absolute(ProjectSettings.globalize_path(SAVE_PATH))

	if frames >= TOTAL_FRAMES:
		_report()
		return true
	return false


func check(condition: bool, message: String) -> void:
	checks += 1
	if not condition:
		failures.append(message)
		print("  ✗ %s" % message)


func _report() -> void:
	var w = game.sim.world
	print("")
	print("  день %d · акт %d · население %.0f · ячеек %d · технологий %d" % [
		game.sim.day(), w.act, w.total_population, w.settlements.size(),
		w.council().unlocked.size() if w.council() != null else 0])
	print("  отношения %.1f · война(была) %s · расколов %d · развилок отвечено %d" % [
		w.relations, str(w.war_happened), w.splinter_count(), _choices_answered])
	print("  пик активных юнитов Tier 1: %d из %d бюджета" % [_peak_units, game.tiers.unit_budget])
	print("  записей в хронике: %d · снепшотов: %d · концовка: %s" % [
		w.chronicle.entries.size(), game.sim.snapshots.count(),
		w.ending_id if not w.ending_id.is_empty() else "—"])
	print("")
	print("──────────────────────────────────────────────")
	print("Проверок: %d, провалено: %d" % [checks, failures.size()])
	quit(0 if failures.is_empty() else 1)
