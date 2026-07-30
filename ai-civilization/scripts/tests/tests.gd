class_name Tests
extends RefCounted

## Headless-тесты симуляции. Запуск:
##   godot --headless --path . --script scripts/tests/test_runner.gd
##
## Каждая веха (§10) должна быть проверяемой отдельно, поэтому тесты разбиты по
## подсистемам и не требуют ни сцены, ни рендера.

var passed: int = 0
var failed: int = 0
var _failures: Array = []


func run_all() -> bool:
	_run("M1 · генерация мира", test_worldgen)
	_run("M1 · экономика живёт 240 дней", test_economy_alive)
	_run("M1 · экономика не вырождается за 1200 дней", test_long_horizon)
	_run("M1 · потоки == однопоточный расчёт", test_thread_determinism)
	_run("M2 · тех-дерево продвигается", test_tech_progression)
	_run("M2 · тех-дерево реагирует на дефицит", test_tech_weights_react)
	_run("M2 · откат на предыдущий процесс при нехватке входов", test_recipe_fallback)
	_run("M3 · отношения деградируют и запускают войну", test_war_trigger)
	_run("M4 · боевая формула симметрична и ограничена", test_combat_formula)
	_run("M4 · раскол при падении лояльности", test_secession)
	_run("M5 · снепшот + rewind воспроизводимы", test_snapshot_rewind)
	_run("M5 · сохранение на диск и загрузка", test_save_load)
	_run("M5 · скачок до следующего события", test_skip_to_event)
	_run("M6 · акты и концовка достигаются", test_story_progression)
	_run("perf · бюджет тика Tier 2", test_perf)

	print("")
	print("──────────────────────────────────────────────")
	print("Пройдено: %d, провалено: %d" % [passed, failed])
	for f in _failures:
		print("  ✗ %s" % f)
	return failed == 0


func _run(title: String, fn: Callable) -> void:
	var before_failed := failed
	print("")
	print("▶ %s" % title)
	fn.call()
	if failed == before_failed:
		print("  ✓ ок")


func check(condition: bool, message: String) -> void:
	if condition:
		passed += 1
	else:
		failed += 1
		_failures.append(message)
		print("  ✗ %s" % message)


func check_near(a: float, b: float, epsilon: float, message: String) -> void:
	check(absf(a - b) <= epsilon, "%s (%f vs %f)" % [message, a, b])


static func make_sim(world_seed: int, threaded: bool = true) -> Simulation:
	var sim := Simulation.new(world_seed)
	sim.tier2.use_threads = threaded
	sim.new_game()
	return sim


static func state_hash(sim: Simulation) -> int:
	return JSON.stringify(sim.world.to_dict()).hash()


static func finite(v: float) -> bool:
	return not (is_nan(v) or is_inf(v))


## Развилки требуют решения игрока. В тестах отвечаем первым вариантом:
## иначе Story перестаёт выдавать новые чекпоинты, а skip_to_next_event
## корректно отказывается прыгать через незакрытый выбор.
static func auto_resolve_choices(sim: Simulation) -> void:
	sim.story.choice_requested.connect(func(pending: Dictionary):
		var options = pending["choice"].get("options", [])
		if options is Array and not (options as Array).is_empty():
			sim.story.resolve_choice(String(pending["id"]), String(options[0]["id"]), sim.day()))


# ------------------------------------------------------------------ M1

func test_worldgen() -> void:
	var sim := make_sim(12345)
	var w := sim.world
	check(w.size > 0, "размер карты нулевой")
	check(w.terrain.size() == w.size * w.size, "террейн не соответствует размеру")
	var land := 0
	for i in w.terrain.size():
		if w.terrain[i] != WorldGen.WATER:
			land += 1
	check(land > 200, "суши слишком мало: %d тайлов" % land)
	check(float(land) / float(w.terrain.size()) < 0.8, "суша занимает почти всю карту — это не остров")
	check(w.settlements.size() == Balance.int_at("world/start_settlements", 3),
		"стартовых поселений: %d" % w.settlements.size())
	check(w.factions.size() == 2, "фракций на старте: %d" % w.factions.size())
	for s in w.settlements:
		check(w.is_land(s.tile), "поселение «%s» стоит в воде" % s.name)

	# Один и тот же seed даёт одну и ту же карту.
	var sim2 := make_sim(12345)
	check(sim2.world.terrain == w.terrain, "карта не детерминирована по seed")


func test_economy_alive() -> void:
	var sim := make_sim(777)
	var start_pop := sim.world.total_population
	sim.step_days(240)
	var w := sim.world

	check(finite(w.total_population), "население стало NaN/inf")
	check(w.total_population > start_pop,
		"население не выросло за 240 дней: %.1f → %.1f" % [start_pop, w.total_population])
	check(w.settlements.size() > Balance.int_at("world/start_settlements", 3),
		"расселение не произошло: поселений %d" % w.settlements.size())
	check(w.ai_output > 0.0, "выпуск нулевой")

	for s in w.settlements:
		check(finite(s.population) and s.population >= 0.0, "население «%s» некорректно" % s.name)
		for i in Res.COUNT:
			check(finite(s.stock[i]) and s.stock[i] >= -0.0001,
				"склад %s в «%s» отрицателен: %f" % [Res.IDS[i], s.name, s.stock[i]])
		check(s.deposit >= 0.0, "отвал «%s» отрицателен" % s.name)
		check(s.loyalty >= 0.0 and s.loyalty <= 100.0, "лояльность «%s» вне диапазона" % s.name)
		check(s.unrest >= 0.0 and s.unrest <= 1.0, "напряжённость «%s» вне диапазона" % s.name)

	check(w.chronicle.entries.size() > 10,
		"хроника почти пуста (%d записей) — симуляция не ощущается живой" % w.chronicle.entries.size())

	# Производственная цепочка действительно прошла до конца графа.
	var total_machines := 0.0
	var total_alloy := 0.0
	for s in w.settlements:
		total_machines += s.stock[Res.MACHINES]
		total_alloy += s.stock[Res.ALLOY]
	print("    население=%.0f поселений=%d выпуск=%.1f машины=%.1f сплав=%.1f техов=%d" % [
		w.total_population, w.settlements.size(), w.ai_output,
		total_machines, total_alloy, w.council().unlocked.size()])


## Поздняя игра — отдельный класс отказов: отвалы выработаны, сбросы с материка
## прекращены в Акте 3, и без переработки любая партия сходится в голод
## независимо от действий игрока. Акты 3-4 в таком случае пустые.
func test_long_horizon() -> void:
	var sim := make_sim(4711)
	auto_resolve_choices(sim)
	var peak := 0.0
	var samples: Array[String] = []
	for i in 12:
		sim.step_days(100)
		peak = maxf(peak, sim.world.total_population)
		if i % 3 == 2:
			samples.append("д%d:%d" % [sim.day(), int(sim.world.total_population)])

	var w := sim.world
	var floor_pop := Balance.num("population/min_pop", 1.0)
	var at_floor := 0
	for s in w.settlements:
		if s.population <= floor_pop + 0.5:
			at_floor += 1
	check(w.settlements.size() > 0, "остров исчез за 1200 дней без войны с людьми")
	check(at_floor == 0,
		"%d из %d ячеек сидят на минимуме населения — экономика выродилась" % [at_floor, w.settlements.size()])
	check(w.total_population > peak * 0.35,
		"население обвалилось с пика %.0f до %.0f" % [peak, w.total_population])
	check(w.avg_deposit_richness > Balance.num("settlement/deposit_min_richness", 0.25) * 0.95,
		"отвалы вычерпаны в ноль, переработка не работает")

	# Ни один узел цепочки не должен стоять в нуле, пока предыдущие переполнены:
	# это означает, что занятость распределена мимо реального спроса.
	var totals := Res.empty_stock()
	for s in w.settlements:
		for i in Res.COUNT:
			totals[i] += s.stock[i]
	check(totals[Res.ALLOY] > 0.0,
		"сплав в нуле при ломе %.0f и компонентах %.0f — узел цепочки голодает" % [
			totals[Res.SCRAP], totals[Res.COMPONENTS]])
	check(totals[Res.MACHINES] > 0.0, "машины в нуле — конец цепочки не работает")
	print("    %s · пик=%.0f · итог=%.0f · богатство отвалов=%.2f · выпуск=%.0f (%.2fx людей)" % [
		", ".join(samples), peak, w.total_population, w.avg_deposit_richness,
		w.ai_output, w.economy_ratio()])

	# Освобождение подписок не должно ронять уже посчитанное состояние.
	var pop_before := w.total_population
	sim.dispose()
	check(is_equal_approx(w.total_population, pop_before), "dispose() испортил состояние мира")
	check(sim.snapshots.count() == 0, "dispose() не очистил снепшоты")


## Ключевая гарантия многопоточности: результат не зависит от порядка задач.
func test_thread_determinism() -> void:
	var a := make_sim(9090, true)
	var b := make_sim(9090, false)
	a.step_days(150)
	b.step_days(150)
	check(state_hash(a) == state_hash(b),
		"многопоточный и однопоточный расчёт разошлись (pop %.4f vs %.4f)" % [
			a.world.total_population, b.world.total_population])


# ------------------------------------------------------------------ M2

func test_tech_progression() -> void:
	var sim := make_sim(4242)
	sim.step_days(400)
	var council := sim.world.council()
	check(council.unlocked.size() >= 3,
		"за 400 дней изучено только %d технологий" % council.unlocked.size())
	check(council.unlocked.has("scrap_utilization"),
		"базовая технология «Утилизация лома» не изучена")
	# Разблокировка обязана переключить поселения на следующий tier рецепта.
	if council.unlocked.has("production_automation"):
		var r := Recipe.best_for_job(Job.SMELT, council.unlocked)
		check(r != null and r.id == "arc_smelt", "автоматизация не переключила плавку на arc_smelt")
	check(council.unlocked.size() < TechTree.total_ai_techs(),
		"всё дерево (%d технологий) закрыто уже к 400-му дню — Актам 3-4 нечего давать" % TechTree.total_ai_techs())
	var human := sim.world.humans()
	check(human.tech_level() >= 1, "человеческая ветка не двигается")
	print("    ИИ: %d технологий, люди: %d" % [council.unlocked.size(), human.tech_level()])


## Веса выбора должны реагировать на состояние симуляции, а не быть константой.
func test_tech_weights_react() -> void:
	var calm := TechTree.build_conditions({ "energy_deficit": 0.0, "at_war": false, "day": 200 })
	var crisis := TechTree.build_conditions({ "energy_deficit": 0.9, "at_war": false, "day": 200 })
	var energy := TechTree.get_tech("energy_independence")
	check(energy != null, "технология energy_independence отсутствует в данных")
	if energy != null:
		var w_calm := TechTree.weight_for(energy, calm)
		var w_crisis := TechTree.weight_for(energy, crisis)
		check(w_crisis > w_calm * 2.0,
			"дефицит энергии не поднимает вес энергонезависимости (%.2f → %.2f)" % [w_calm, w_crisis])

	var war_state := TechTree.build_conditions({ "at_war": true, "day": 300 })
	var military := TechTree.get_tech("military_robotics")
	if military != null:
		check(TechTree.weight_for(military, war_state) > TechTree.weight_for(military, calm) * 2.0,
			"война не поднимает вес военной робототехники")


## Разблокировка старшего процесса не должна останавливать производство, если
## для него нет входов. Солнечный массив требует сплава; поселение без сплава
## обязано вернуться к сжиганию лома, а не выдавать ноль энергии и умирать.
func test_recipe_fallback() -> void:
	var unlocked := { "scrap_utilization": true, "energy_independence": true }
	check(Recipe.best_for_job(Job.POWER, unlocked).id == "solar_array",
		"энергонезависимость не переключила POWER на солнечные массивы")

	var s := Settlement.create(0, "тест", 0, Vector2.ZERO, Vector2i.ZERO, 1, 0)
	s.population = 100.0
	s.alloc = Job.empty_alloc()
	s.alloc[Job.POWER] = 1.0
	s.stock[Res.SCRAP] = 500.0
	s.stock[Res.ALLOY] = 0.0
	s.stock[Res.ENERGY] = 0.0

	Production.run_chains(s, Production.make_view(unlocked, Production.base_multipliers(), false))
	check(s.stock[Res.ENERGY] > 0.0,
		"без сплава поселение не произвело энергии вовсе — откат на сжигание лома не работает")
	check(s.stock[Res.SCRAP] < 500.0, "откат не потратил лом, значит рецепт не запускался")

	# А при наличии сплава должен работать именно старший процесс: та же
	# рабочая сила обязана дать больше энергии.
	var s2 := Settlement.create(1, "тест2", 0, Vector2.ZERO, Vector2i.ZERO, 1, 0)
	s2.population = 100.0
	s2.alloc = Job.empty_alloc()
	s2.alloc[Job.POWER] = 1.0
	s2.stock[Res.SCRAP] = 500.0
	s2.stock[Res.ALLOY] = 100.0
	Production.run_chains(s2, Production.make_view(unlocked, Production.base_multipliers(), false))
	check(s2.stock[Res.ENERGY] > s.stock[Res.ENERGY],
		"со сплавом старший процесс не дал прироста (%.1f vs %.1f)" % [s2.stock[Res.ENERGY], s.stock[Res.ENERGY]])


# ------------------------------------------------------------------ M3

func test_war_trigger() -> void:
	var sim := make_sim(31415)
	auto_resolve_choices(sim)
	var start_relations := sim.world.relations
	sim.step_days(300)
	check(sim.world.relations < start_relations,
		"отношения не деградировали за 300 дней (%.1f → %.1f)" % [start_relations, sim.world.relations])

	# Война должна запускаться сама. Если за 300 дней порог ещё не пробит —
	# пробиваем вручную и проверяем, что триггер срабатывает без игрока.
	var natural := sim.world.war_happened
	if not natural:
		sim.world.act = maxi(sim.world.act, Balance.int_at("relations/war_min_act", 2))
		sim.world.relations = Balance.num("relations/war_threshold", 14.0) - 6.0
		sim.step_days(1)
		check(sim.world.war_active, "война не началась при пробитом пороге отношений")
	check(sim.world.war_happened, "война так и не случилась")
	check(sim.world.act >= 3, "война не перевела сюжет в Акт 3")

	var humans := sim.world.humans()
	var industry_before := humans.industry
	sim.step_days(150)
	check(sim.world.days_at_war > 0, "счётчик дней войны не идёт")
	check(humans.industry > 0.0, "промышленность людей обнулилась — формула войны не ограничена")
	check(not (sim.world.war_active and sim.world.war_resolved), "война одновременно идёт и окончена")
	check(sim.world.settlements.size() >= 1,
		"остров вырезан полностью — при заданном балансе война не должна быть приговором")
	check(sim.world.days_at_war <= Balance.int_at("combat/max_war_days", 220) + 5,
		"война длится дольше предельного срока: %d дней" % sim.world.days_at_war)
	print("    отношения=%.1f война(естественная)=%s идёт=%s дней=%d счёт=%.2f промышленность %.0f → %.0f" % [
		sim.world.relations, str(natural), str(sim.world.war_active), sim.world.days_at_war,
		sim.world.council().war_score, industry_before, humans.industry])


# ------------------------------------------------------------------ M4

func test_combat_formula() -> void:
	var rng := SimRng.new(7)
	# Явный перевес должен побеждать почти всегда.
	var wins := 0
	for i in 200:
		var out := WarTheater.resolve(1000.0, 100.0, 1.0, 1.0, rng)
		if bool(out["attacker_wins"]):
			wins += 1
	check(wins >= 195, "10-кратный перевес даёт только %d/200 побед" % wins)

	# Потери всегда в пределах 0..1 и победитель теряет меньше проигравшего.
	for i in 100:
		var out2 := WarTheater.resolve(rng.randf_range(1.0, 900.0), rng.randf_range(1.0, 900.0), 1.0, 1.0, rng)
		var al := float(out2["attacker_loss"])
		var dl := float(out2["defender_loss"])
		check(al >= 0.0 and al <= 1.0 and dl >= 0.0 and dl <= 1.0, "доли потерь вне диапазона")
		if bool(out2["attacker_wins"]):
			check(al <= dl, "победивший атакующий потерял больше защитника")
		else:
			check(dl <= al, "победивший защитник потерял больше атакующего")

	# Равные силы — примерно равные шансы (проверка отсутствия системного крена).
	var att_wins := 0
	for i in 400:
		if bool(WarTheater.resolve(500.0, 500.0, 1.0, 1.0, rng)["attacker_wins"]):
			att_wins += 1
	check(att_wins > 20 and att_wins < 200,
		"штраф атакующему выродился: %d/400 побед атаки" % att_wins)


func test_secession() -> void:
	var sim := make_sim(2024)
	sim.step_days(200)
	check(sim.world.settlements.size() >= 2, "недостаточно поселений для проверки раскола")

	# Загоняем лояльность в пол — откол обязан произойти сам.
	for s in sim.world.settlements:
		s.loyalty = 5.0
		s.grudge = 30.0
	sim.step_days(3)
	check(sim.world.splinter_count() >= 1, "откол не произошёл при нулевой лояльности")

	var splinter_settlements := 0
	for s in sim.world.settlements:
		if s.faction_id != sim.world.council_id:
			splinter_settlements += 1
	check(splinter_settlements > 0, "отделившаяся фракция без поселений")

	# Возврат: поднимаем лояльность выше порога воссоединения.
	for s in sim.world.settlements:
		s.loyalty = 95.0
	sim.step_days(3)
	check(sim.world.splinter_count() == 0 or splinter_settlements == 0,
		"ячейки не вернулись под управление совета при высокой лояльности")
	print("    расколов было: %d, поселений всего: %d" % [splinter_settlements, sim.world.settlements.size()])


# ------------------------------------------------------------------ M5

func test_snapshot_rewind() -> void:
	var sim := make_sim(55555)
	sim.step_days(60)
	var day_60 := state_hash(sim)
	var pop_60 := sim.world.total_population

	sim.step_days(40)
	var day_100 := state_hash(sim)
	check(day_100 != day_60, "состояние не меняется со временем — симуляция стоит")

	check(sim.rewind_days(40), "перемотка назад не выполнена")
	check(sim.day() == 60, "после перемотки день = %d, ожидался 60" % sim.day())
	check(state_hash(sim) == day_60,
		"состояние после перемотки не совпало (население %.4f vs %.4f)" % [
			sim.world.total_population, pop_60])

	# И forward-replay из восстановленной точки обязан прийти туда же.
	sim.step_days(40)
	check(state_hash(sim) == day_100, "повторный проигрыш вперёд дал другой результат")


func test_save_load() -> void:
	var sim := make_sim(1717)
	sim.step_days(90)
	var before := state_hash(sim)
	var path := "user://test_save.dat"
	check(sim.save_game(path) == OK, "сохранение не удалось")

	var loaded := Simulation.new(0)
	loaded.tier2.use_threads = false
	check(loaded.load_game(path), "загрузка не удалась")
	check(state_hash(loaded) == before, "загруженное состояние не совпало с сохранённым")
	check(loaded.world.terrain == sim.world.terrain, "карта после загрузки отличается")

	# И продолжение с загруженного состояния идентично продолжению исходного.
	sim.tier2.use_threads = false
	sim.step_days(30)
	loaded.step_days(30)
	check(state_hash(loaded) == state_hash(sim), "продолжение после загрузки разошлось")
	DirAccess.remove_absolute(ProjectSettings.globalize_path(path))


func test_skip_to_event() -> void:
	var sim := make_sim(606)
	auto_resolve_choices(sim)
	sim.step_days(30)
	var before := sim.day()
	var jumped := sim.skip_to_next_event(200)
	check(jumped > 0, "скачок вперёд не продвинул время")
	check(sim.day() == before + jumped, "день после скачка не сходится")
	var last = sim.world.chronicle.entries[-1]
	check(int(last["importance"]) >= Chronicle.HIGH or jumped >= 200,
		"скачок остановился не на значимом событии")
	print("    скачок на %d дней до: %s" % [jumped, String(last["text"]).substr(0, 60)])


# ------------------------------------------------------------------ M6

func test_story_progression() -> void:
	var sim := make_sim(8899)
	var acts_seen := [sim.world.act]
	auto_resolve_choices(sim)

	for _i in 30:
		sim.step_days(40)
		if not acts_seen.has(sim.world.act):
			acts_seen.append(sim.world.act)
		if not sim.world.ending_id.is_empty():
			break

	check(sim.world.act >= 2, "сюжет не вышел из Акта 1 за 1200 дней")
	check(sim.world.fired_checkpoints.size() >= 1, "ни один сюжетный чекпоинт не сработал")
	check(not sim.world.ending_id.is_empty() or sim.world.act >= 3,
		"сюжет не дошёл ни до эскалации, ни до концовки")
	print("    акты: %s чекпоинтов: %d концовка: %s" % [
		str(acts_seen), sim.world.fired_checkpoints.size(),
		sim.world.ending_id if not sim.world.ending_id.is_empty() else "—"])


# ------------------------------------------------------------------ perf

## Бюджет тика Tier 2. На PC это заведомо быстро; цифра нужна как база для
## сравнения с профилированием на целевом устройстве (§9).
func test_perf() -> void:
	var sim := make_sim(31337)
	auto_resolve_choices(sim)
	# Замер должен отражать худший случай, а не то, сколько ячеек дал seed,
	# поэтому карта заполняется до предела до начала прогона.
	var cap := Balance.int_at("expansion/max_settlements", 14)
	while sim.world.settlements.size() < cap:
		var tile := sim.world.claim_site(4.0)
		if tile.x < 0:
			break
		sim.world.add_settlement(Settlement.create(sim.world.next_settlement_id,
			"perf-%d" % sim.world.next_settlement_id, sim.world.council_id,
			sim.world.world_pos(tile), tile, sim.world.world_seed, sim.day()))
	sim.step_days(200)
	var settlements := sim.world.settlements.size()

	var start := Time.get_ticks_usec()
	sim.step_days(500)
	var elapsed := Time.get_ticks_usec() - start
	var per_day := float(elapsed) / 500.0
	print("    %d поселений: %.1f мкс на игровой день (%.2f мс на 30 дней)" % [
		settlements, per_day, per_day * 30.0 / 1000.0])
	# x30 при 6 тиках/с — это 7.5 игровых дней в секунду; бюджет с запасом.
	check(per_day < 20000.0, "день Tier 2 стоит %.0f мкс — слишком дорого для x30" % per_day)
