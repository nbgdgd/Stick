extends Node2D

## Корень игры: связывает симуляцию, представление и HUD.
##
## Единственное место, где эти три слоя знают друг о друге. Симуляция не
## обращается к сцене, представление не меняет состояние мира — поток данных
## строго в одну сторону, и именно поэтому один и тот же код симуляции
## работает в headless-тестах без изменений.

const SAVE_PATH := "user://ai_civilization_save.dat"

@export var world_seed: int = 0
@export var randomize_seed: bool = true
## Принудительный масштаб интерфейса; 0 — считать из плотности экрана.
## Нужен инструментам: под xvfb DisplayServer сообщает 95 ppi, и без override
## снимок экрана показывал бы настольную компоновку вместо телефонной.
@export var ui_scale_override: float = 0.0

var sim: Simulation
var view: WorldView
var camera: CameraRig
var hud: Hud
var tiers: TierManager

var _armed_intervention: String = ""
var _known_invasions: int = 0


func _ready() -> void:
	if randomize_seed and world_seed == 0:
		world_seed = int(Time.get_unix_time_from_system()) & 0x7FFFFFFF

	_apply_ui_scale()

	sim = Simulation.new(world_seed)
	sim.new_game()

	view = WorldView.new()
	view.name = "WorldView"
	add_child(view)
	view.setup(sim.world)
	view.settlement_tapped.connect(_on_settlement_tapped)

	tiers = TierManager.new(sim.world, view.unit_pool)

	camera = CameraRig.new()
	camera.name = "Camera"
	add_child(camera)
	camera.setup(sim.world.map_pixel_size())
	camera.tapped.connect(_on_tapped)
	camera.make_current()
	var capital := sim.world.capital_of(sim.world.council_id)
	if capital != null:
		camera.focus_on(capital.pos)

	hud = Hud.new()
	hud.name = "Hud"
	add_child(hud)
	hud.setup(sim)
	hud.set_speed_index(sim.clock.speed_index)

	hud.speed_selected.connect(_on_speed_selected)
	hud.skip_requested.connect(_on_skip)
	hud.rewind_requested.connect(_on_rewind)
	hud.targeting_requested.connect(_on_targeting)
	hud.immediate_requested.connect(_on_immediate)
	hud.policy_requested.connect(_on_policy)
	hud.choice_made.connect(_on_choice_made)
	hud.save_requested.connect(_on_save)
	hud.load_requested.connect(_on_load)

	sim.day_advanced.connect(_on_day_advanced)
	sim.rewound.connect(_on_rewound)
	sim.ended.connect(_on_ended)
	sim.story.choice_requested.connect(_on_choice_requested)
	sim.story.act_changed.connect(_on_act_changed)

	tiers.reevaluate(sim.clock.tick_count, camera.position, camera.view_scale())


## Приравнивает логическую единицу интерфейса к dp.
##
## Базовый вьюпорт 1280x720 со stretch canvas_items даёт на экране 2344x1080
## коэффициент ровно 1.5. При плотности 461 ppi это значит, что тач-цель 48
## единиц занимала 23dp вместо 48dp, а шрифт 14 читался как 6.8sp — то есть
## интерфейс был ровно вдвое меньше пальцевого минимума. content_scale_factor
## добирает недостающее, и константы в UIKit начинают означать физические dp.
##
## Снизу ограничено единицей: на настольном экране с 96 ppi «честный» пересчёт
## наоборот уменьшил бы интерфейс.
func _apply_ui_scale() -> void:
	var win := get_window()
	if win == null:
		return
	var factor := ui_scale_override
	if factor <= 0.0:
		var base := Vector2(
			float(ProjectSettings.get_setting("display/window/size/viewport_width", 1280)),
			float(ProjectSettings.get_setting("display/window/size/viewport_height", 720)))
		var base_stretch := minf(float(win.size.x) / maxf(1.0, base.x),
			float(win.size.y) / maxf(1.0, base.y))
		var dpi := DisplayServer.screen_get_dpi()
		if dpi <= 0:
			dpi = 160
		var px_per_dp := float(dpi) / 160.0
		factor = px_per_dp / maxf(0.01, base_stretch)
	win.content_scale_factor = clampf(factor, 1.0, 3.0)


func _process(delta: float) -> void:
	sim.process(delta)

	# Tier 1 движется в реальном времени, умноженном на скорость симуляции:
	# на паузе юниты стоят, на x30 суетятся. Пересчёт принадлежности к Tier 1 —
	# по расписанию тиков, не каждый кадр (§0).
	var scaled := delta * maxf(0.0, sim.clock.speed())
	if scaled > 0.0:
		tiers.step(minf(scaled, 0.5))
	if tiers.should_reevaluate(sim.clock.tick_count):
		tiers.reevaluate(sim.clock.tick_count, camera.position, camera.view_scale())
		hud.set_tier_info(tiers.tier1_settlements(), tiers.active_units(), tiers.unit_budget)

	if scaled > 0.0:
		view.battle_fx.step(minf(scaled, 0.5))
	_watch_battles()

	hud.refresh_fast()
	view.refresh()


## Оповещение о бое.
##
## Десант живёт на карте считанные игровые дни, и на ускоренном времени игрок
## просто не успевал его заметить: война была видна только строчкой в хронике.
## При появлении нового десанта камера сама показывает место, а время падает до
## обычного — как уведомление о сражении в стратегиях.
##
## Реакция сделана наблюдением за состоянием мира, а не сигналом из симуляции:
## симуляция не должна знать ни про камеру, ни про HUD.
func _watch_battles() -> void:
	var count := sim.world.invasions.size()
	if count > _known_invasions and Balance.section("combat").get("battle_alert", true):
		var inv = sim.world.invasions[count - 1]
		var p = inv.get("pos", [0.0, 0.0])
		camera.focus_on(Vector2(float(p[0]), float(p[1])))
		var target := sim.world.settlement(int(inv.get("target", -1)))
		hud.set_hint("Десант у «%s»" % (target.name if target != null else "берега"))
		var alert_speed := Balance.int_at("combat/battle_alert_speed_index", 1)
		if sim.clock.speed_index > alert_speed:
			sim.clock.set_speed_index(alert_speed)
			hud.set_speed_index(alert_speed)
	_known_invasions = count


func _on_day_advanced(_day: int) -> void:
	hud.refresh_slow()


func _on_act_changed(act: int, act_name: String, _blurb: String) -> void:
	hud.set_hint("АКТ %d — %s" % [act, act_name])
	# Смена акта — момент, когда игрок должен успеть прочитать ленту.
	sim.clock.set_speed_index(0)
	hud.set_speed_index(0)


func _on_ended(ending: Dictionary) -> void:
	sim.clock.set_speed_index(0)
	hud.set_speed_index(0)
	hud.show_ending(ending)


# ------------------------------------------------------------------ время

func _on_speed_selected(index: int) -> void:
	sim.clock.set_speed_index(index)
	hud.set_speed_index(index)


func _on_skip() -> void:
	var jumped := sim.skip_to_next_event()
	hud.set_hint("Пропущено дней: %d" % jumped if jumped > 0 else "Скачок невозможен: ожидается решение")
	hud.refresh_slow()


func _on_rewind(days: int) -> void:
	if not sim.rewind_days(days):
		hud.set_hint("Снепшотов назад нет")
		return
	hud.set_hint("Перемотка: день %d" % sim.day())


func _on_rewound(_day: int) -> void:
	# История заменена целиком, привязки Tier 1 указывают на исчезнувшие id.
	tiers.reset()
	view.battle_fx.clear()
	_known_invasions = sim.world.invasions.size()
	view.rebuild_markers()
	hud.chronicle_panel.rebuild()
	hud.refresh_slow()
	tiers.reevaluate(sim.clock.tick_count, camera.position, camera.view_scale())


# ------------------------------------------------------------------ ввод

func _on_targeting(id: String) -> void:
	_armed_intervention = id


func _on_tapped(world_position: Vector2) -> void:
	if not _armed_intervention.is_empty():
		_apply_intervention(_armed_intervention, world_position)
		_armed_intervention = ""
		hud.intervention_bar.clear_armed()
		hud.refresh_slow()
		return
	# Радиус захвата растёт при отдалении камеры — иначе в маркер не попасть.
	var tolerance := 20.0 * camera.view_scale()
	if not view.handle_tap(world_position, tolerance):
		hud.inspector.close()


func _apply_intervention(id: String, pos: Vector2) -> void:
	var day := sim.day()
	var ok := false
	match id:
		"drop_scrap": ok = sim.interventions.drop_scrap(pos, day)
		"incite_conflict": ok = sim.interventions.incite_conflict(pos, day)
		"disaster": ok = sim.interventions.disaster(pos, day)
		"back_uprising": ok = sim.interventions.back_uprising(pos, day)
	hud.set_hint("" if ok else "Вмешательство недоступно")


func _on_immediate(id: String) -> void:
	var day := sim.day()
	var ok := false
	match id:
		"open_talks": ok = sim.interventions.open_talks(day)
		"leak_tech":
			# Утечка идёт в пользу отстающей стороны: у игрока один рычаг,
			# а не два почти одинаковых.
			ok = sim.interventions.leak_tech(sim.world.tech_gap() > 0, day)
	hud.set_hint("" if ok else "Вмешательство недоступно")
	hud.refresh_slow()


func _on_policy(policy: String) -> void:
	sim.interventions.set_policy(sim.world.council_id, policy, sim.day())
	hud.refresh_slow()


func _on_settlement_tapped(s: Settlement) -> void:
	hud.inspector.show_settlement(s)


# ------------------------------------------------------------------ сюжет

func _on_choice_requested(pending: Dictionary) -> void:
	sim.clock.set_speed_index(0)
	hud.set_speed_index(0)
	hud.open_choice(pending)


func _on_choice_made(checkpoint_id: String, option_id: String) -> void:
	# Диалог прячет себя сам только при нажатии кнопки игроком. Если выбор
	# закрывается программно, окно оставалось висеть поверх карты и
	# перехватывало касания — после первого чекпоинта карта переставала
	# отзываться на тапы вообще.
	hud.choice_dialog.close()
	sim.story.resolve_choice(checkpoint_id, option_id, sim.day())
	hud.refresh_slow()
	sim.clock.set_speed_index(1)
	hud.set_speed_index(1)


# ------------------------------------------------------------------ сохранение

func _on_save() -> void:
	var err := sim.save_game(SAVE_PATH)
	hud.set_hint("Сохранено" if err == OK else "Ошибка сохранения (%d)" % err)


func _on_load() -> void:
	if not sim.load_game(SAVE_PATH):
		hud.set_hint("Сохранение не найдено")
		return
	view.bake_terrain()
	hud.set_hint("Загружено: день %d" % sim.day())


func _unhandled_key_input(event: InputEvent) -> void:
	if not (event is InputEventKey) or not event.pressed:
		return
	match (event as InputEventKey).keycode:
		KEY_SPACE:
			sim.clock.toggle_pause()
			hud.set_speed_index(sim.clock.speed_index)
		KEY_1: _on_speed_selected(1)
		KEY_2: _on_speed_selected(2)
		KEY_3: _on_speed_selected(3)
		KEY_4: _on_speed_selected(4)
		KEY_TAB: hud.tech_panel.toggle_visible()
		KEY_BRACKETLEFT: _on_rewind(10)
		KEY_BRACKETRIGHT: _on_skip()
