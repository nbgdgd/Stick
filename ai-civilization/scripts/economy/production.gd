class_name Production
extends RefCounted

## Разрешение производственного графа за один игровой день для одного поселения.
##
## Функция чистая относительно всего, кроме переданного Settlement: фракция
## передаётся «снимком» (view) из простых значений, скопированным до запуска
## потоков. Без этого WorkerThreadPool читал бы живые объекты фракции,
## пока главный поток их меняет.

## Экономический вес продукции — метрика «остров против материка» (§6, отношения).
const OUTPUT_WEIGHTS := [0.15, 0.6, 0.35, 0.8, 1.6, 2.2, 6.0]


## Снимок фракции для потоков. Только Dictionary/float/bool — ничего живого.
static func make_view(unlocked: Dictionary, mult: Dictionary, at_war: bool) -> Dictionary:
	return {
		"unlocked": unlocked.duplicate(),
		"mult": mult.duplicate(),
		"at_war": at_war,
	}


static func _job_key(job: int) -> String:
	match job:
		Job.SCAVENGE: return "scavenge"
		Job.POWER: return "power"
		Job.SMELT: return "smelt"
		Job.REFINE: return "refine"
		Job.FAB: return "fab"
		Job.ASSEMBLE: return "assemble"
		Job.RESEARCH: return "research"
		Job.MILITARY: return "military"
	return ""


## Выбор рабочего процесса для специальности.
##
## Старший разблокированный рецепт берётся только если он способен работать
## хотя бы на долю мощности: солнечный массив без сплава выдаёт ноль энергии, и
## слепое «всегда самый новый процесс» отправляло поселение в спираль голода
## сразу после разблокировки энергонезависимости. При нехватке входов
## производство откатывается на предыдущий процесс — сжигание лома, ручную
## плавку, кустарное оружие.
static func _select_recipe(s: Settlement, job: int, unlocked: Dictionary, job_rate: float) -> Dictionary:
	var threshold := Balance.num("economy/recipe_fallback_threshold", 0.25)
	var best: Recipe = null
	var best_runs := 0.0
	for entry in Recipe.unlocked_for_job(job, unlocked):
		var recipe := entry as Recipe
		var capacity := job_rate * recipe.rate
		if recipe.scales_with_deposit:
			capacity *= s.deposit_richness()
		if capacity <= 0.0:
			continue

		# Пропускная способность ограничена и рабочими, и наличием входов —
		# именно из этого рождаются каскадные дефициты вниз по цепочке.
		var runs := capacity
		for i in Res.COUNT:
			var need := recipe.inputs[i]
			if need > 0.0:
				runs = minf(runs, s.stock[i] / need)
		if recipe.scales_with_deposit and recipe.outputs[Res.SCRAP] > 0.0:
			runs = minf(runs, s.deposit / recipe.outputs[Res.SCRAP])

		if runs >= capacity * threshold:
			return { "recipe": recipe, "runs": runs }
		if runs > best_runs:
			best = recipe
			best_runs = runs
	return { "recipe": best, "runs": best_runs }


static func run_chains(s: Settlement, view: Dictionary) -> void:
	var unlocked: Dictionary = view.get("unlocked", {})
	var mult: Dictionary = view.get("mult", {})
	var global_rate := float(mult.get("all_rates", 1.0))

	s.last_output = 0.0

	for job in Job.COUNT:
		var worker_count := s.workers(job)
		if worker_count <= 0.0:
			continue
		var job_rate := worker_count * global_rate * float(mult.get(_job_key(job), 1.0))
		if job_rate <= 0.0:
			continue

		var selected := _select_recipe(s, job, unlocked, job_rate)
		var recipe: Recipe = selected["recipe"]
		if recipe == null:
			continue
		var runs := float(selected["runs"])
		if runs <= 0.0001:
			continue

		for i in Res.COUNT:
			if recipe.inputs[i] > 0.0:
				s.stock[i] -= recipe.inputs[i] * runs
			if recipe.outputs[i] > 0.0:
				var produced := recipe.outputs[i] * runs
				s.stock[i] += produced
				s.last_output += produced * OUTPUT_WEIGHTS[i]

		if recipe.scales_with_deposit and recipe.outputs[Res.SCRAP] > 0.0:
			s.deposit = maxf(0.0, s.deposit - runs * recipe.outputs[Res.SCRAP])

		if recipe.strength_gain > 0.0:
			var gain := recipe.strength_gain * runs
			s.military_strength += gain
			s.last_output += gain * 1.2

	s.clamp_stocks()


## Мультипликаторы по умолчанию (всё по 1.0) — базис, на который тех-дерево
## и сюжетные решения накладывают свои эффекты.
static func base_multipliers() -> Dictionary:
	return {
		"all_rates": 1.0,
		"scavenge": 1.0,
		"power": 1.0,
		"smelt": 1.0,
		"refine": 1.0,
		"fab": 1.0,
		"assemble": 1.0,
		"research": 1.0,
		"military": 1.0,
		"combat": 1.0,
		"death": 1.0,
		"upkeep": 1.0,
		"trade": 1.0,
		"recycle": 1.0,
	}
