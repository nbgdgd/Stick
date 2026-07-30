extends SceneTree

## Точка входа для headless-прогона тестов симуляции:
##   godot --headless --path . --script scripts/tests/test_runner.gd
##
## Наследование от SceneTree, а не от Node, нужно чтобы не поднимать сцену,
## рендер и ввод — симуляция от них не зависит по построению.


func _initialize() -> void:
	print("AI Civilization — headless-тесты симуляции")
	var suite := Tests.new()
	var ok := suite.run_all()
	quit(0 if ok else 1)
