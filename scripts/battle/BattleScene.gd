extends Node2D

const TacticalUnitScript := preload("res://scripts/battle/Unit.gd")
const TurnManagerScript := preload("res://scripts/battle/TurnManager.gd")
const TacticalGridMapScript := preload("res://scripts/map/GridMap.gd")

var grid := TacticalGridMapScript.new(BattleManager.GRID_WIDTH, BattleManager.GRID_HEIGHT)
var turn_manager := TurnManagerScript.new()
var player_units: Array = []
var enemy_units: Array = []
var selected_unit = null
var move_cells: Array[Vector2i] = []
var attack_cells: Array[Vector2i] = []
var log_messages: Array[String] = []
var level_data: Dictionary = {}

func _ready() -> void:
	level_data = GameManager.load_level()
	_load_units_from_level(level_data)
	turn_manager.start_player_turn(player_units)
	_add_log("阶段 1：选择单位，点击蓝色格移动，点击敌人攻击，Enter 结束回合。")
	queue_redraw()

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		_handle_left_click(screen_to_grid(get_local_mouse_position()))
	elif event is InputEventKey and event.pressed:
		if event.keycode == KEY_ENTER:
			_end_player_turn()
		elif event.keycode == KEY_ESCAPE:
			_clear_selection()
		elif event.keycode == KEY_SPACE and selected_unit != null:
			selected_unit.acted = true
			_add_log("%s 等待。" % selected_unit.display_name)
			_clear_selection()
			_auto_end_player_turn_if_done()
	queue_redraw()

func _draw() -> void:
	_draw_grid()
	_draw_highlights()
	_draw_units()
	_draw_hud()

func _load_units_from_level(data: Dictionary) -> void:
	player_units.clear()
	enemy_units.clear()

	for entry in data.get("player_units", []):
		player_units.append(TacticalUnitScript.from_level_entry(entry, "player"))

	for wave in data.get("enemy_waves", []):
		if int(wave.get("turn", 1)) != 1:
			continue
		for entry in wave.get("units", []):
			enemy_units.append(TacticalUnitScript.from_level_entry(entry, "enemy"))

func _handle_left_click(cell: Vector2i) -> void:
	if not turn_manager.is_player_phase():
		return
	if not grid.is_inside(cell):
		return

	if selected_unit != null:
		var target = _unit_at(cell, "enemy")
		if target != null and grid.distance(selected_unit.grid_position, target.grid_position) <= selected_unit.attack_range:
			_attack(selected_unit, target)
			selected_unit.acted = true
			_clear_selection()
			_check_battle_state()
			_auto_end_player_turn_if_done()
			return

		if move_cells.has(cell) and _unit_at(cell) == null:
			selected_unit.grid_position = cell
			move_cells.clear()
			attack_cells = _attack_cells_for(selected_unit)
			_add_log("%s 移动到 %s。" % [selected_unit.display_name, str(cell)])
			return

	var unit = _unit_at(cell, "player")
	if unit != null and unit.can_act():
		_select_unit(unit)
	else:
		_clear_selection()

func _select_unit(unit) -> void:
	selected_unit = unit
	move_cells = grid.get_cells_in_range(unit.grid_position, unit.move_range, _occupied_cells_for_movement(unit))
	attack_cells = _attack_cells_for(unit)
	_add_log("选择 %s。" % unit.display_name)

func _clear_selection() -> void:
	selected_unit = null
	move_cells.clear()
	attack_cells.clear()

func _attack(attacker, defender) -> void:
	var element_bonus := _element_damage_bonus(attacker.element, defender.element)
	var damage := max(1, attacker.power + element_bonus - defender.defense)
	var applied: int = defender.receive_damage(damage)
	_add_log("%s 攻击 %s，造成 %d 伤害。" % [attacker.display_name, defender.display_name, applied])
	if not defender.is_alive():
		_add_log("%s 被击败。" % defender.display_name)

func _element_damage_bonus(attacker_element: String, defender_element: String) -> int:
	var advantage := {
		"steel": "verdant",
		"verdant": "tide",
		"tide": "flame",
		"flame": "steel"
	}
	if advantage.get(attacker_element, "") == defender_element:
		return 2
	if advantage.get(defender_element, "") == attacker_element:
		return -1
	return 0

func _end_player_turn() -> void:
	if not turn_manager.is_player_phase():
		return
	_clear_selection()
	turn_manager.start_enemy_turn(enemy_units)
	_add_log("敌方回合开始。")
	await _run_enemy_turn()
	_check_battle_state()
	if turn_manager.phase == TurnManagerScript.Phase.ENEMY:
		turn_manager.end_enemy_turn()
		turn_manager.start_player_turn(player_units)
		_add_log("第 %d 回合：玩家回合。" % turn_manager.turn_number)
	queue_redraw()

func _run_enemy_turn() -> void:
	for enemy in enemy_units:
		if not enemy.can_act():
			continue
		var nearest = _nearest_alive_unit(enemy.grid_position, player_units)
		if nearest == null:
			continue
		if grid.distance(enemy.grid_position, nearest.grid_position) <= enemy.attack_range:
			_attack(enemy, nearest)
		else:
			var occupied := _occupied_cells_for_movement(enemy)
			var steps := enemy.move_range
			while steps > 0:
				var next: Vector2i = grid.find_step_toward(enemy.grid_position, nearest.grid_position, occupied)
				if next == enemy.grid_position:
					break
				occupied.erase(enemy.grid_position)
				enemy.grid_position = next
				occupied[enemy.grid_position] = true
				steps -= 1
			_add_log("%s 向 %s 推进。" % [enemy.display_name, nearest.display_name])
		enemy.acted = true
		await get_tree().create_timer(0.15).timeout

func _auto_end_player_turn_if_done() -> void:
	for unit in player_units:
		if unit.can_act():
			return
	_end_player_turn()

func _check_battle_state() -> void:
	if _alive_units(enemy_units).is_empty():
		turn_manager.set_victory()
		_add_log("胜利：敌军已被击败。")
	elif _alive_units(player_units).is_empty() or not _required_units_alive():
		turn_manager.set_defeat()
		_add_log("失败：Elara 倒下。")

func _required_units_alive() -> bool:
	for unit in player_units:
		if unit.unit_id == "lord_elara":
			return unit.is_alive()
	return true

func _alive_units(units: Array) -> Array:
	var result := []
	for unit in units:
		if unit.is_alive():
			result.append(unit)
	return result

func _nearest_alive_unit(from_cell: Vector2i, units: Array):
	var best_unit = null
	var best_distance := 9999
	for unit in units:
		if not unit.is_alive():
			continue
		var current_distance := grid.distance(from_cell, unit.grid_position)
		if current_distance < best_distance:
			best_distance = current_distance
			best_unit = unit
	return best_unit

func _unit_at(cell: Vector2i, team_filter := ""):
	for unit in player_units + enemy_units:
		if not unit.is_alive():
			continue
		if unit.grid_position == cell and (team_filter == "" or unit.team == team_filter):
			return unit
	return null

func _occupied_cells_for_movement(except_unit = null) -> Dictionary:
	var occupied := {}
	for unit in player_units + enemy_units:
		if unit == except_unit or not unit.is_alive():
			continue
		occupied[unit.grid_position] = true
	return occupied

func _attack_cells_for(unit) -> Array[Vector2i]:
	var result: Array[Vector2i] = []
	for x in range(grid.width):
		for y in range(grid.height):
			var cell := Vector2i(x, y)
			if grid.distance(unit.grid_position, cell) <= unit.attack_range:
				result.append(cell)
	return result

func screen_to_grid(position: Vector2) -> Vector2i:
	return Vector2i(floori(position.x / BattleManager.TILE_SIZE), floori(position.y / BattleManager.TILE_SIZE))

func grid_to_screen(cell: Vector2i) -> Vector2:
	return Vector2(cell.x * BattleManager.TILE_SIZE, cell.y * BattleManager.TILE_SIZE)

func _draw_grid() -> void:
	var tile_size := BattleManager.TILE_SIZE
	for x in range(grid.width):
		for y in range(grid.height):
			var rect := Rect2(Vector2(x * tile_size, y * tile_size), Vector2(tile_size, tile_size))
			var fill := Color(0.18, 0.14, 0.09)
			if (x + y) % 2 == 0:
				fill = Color(0.22, 0.17, 0.11)
			draw_rect(rect, fill, true)
			draw_rect(rect, Color(0.56, 0.45, 0.28, 0.55), false, 1.0)

func _draw_highlights() -> void:
	for cell in move_cells:
		draw_rect(_cell_rect(cell), Color(0.1, 0.25, 0.55, 0.45), true)
	for cell in attack_cells:
		draw_rect(_cell_rect(cell), Color(0.55, 0.12, 0.08, 0.35), true)
	if selected_unit != null:
		draw_rect(_cell_rect(selected_unit.grid_position), Color(0.9, 0.72, 0.18, 0.65), false, 3.0)

func _draw_units() -> void:
	for unit in player_units + enemy_units:
		if not unit.is_alive():
			continue
		var center := grid_to_screen(unit.grid_position) + Vector2.ONE * (BattleManager.TILE_SIZE * 0.5)
		var color := Color(0.1, 0.25, 0.55)
		if unit.team == "enemy":
			color = Color(0.45, 0.08, 0.06)
		if unit.acted:
			color = color.darkened(0.35)
		draw_circle(center, 14.0, color)
		draw_circle(center, 15.0, Color(0.79, 0.64, 0.15), false, 2.0)
		draw_string(ThemeDB.fallback_font, center + Vector2(-10, 5), unit.display_name.substr(0, 2), HORIZONTAL_ALIGNMENT_LEFT, -1, 12, Color.WHITE)
		draw_string(ThemeDB.fallback_font, center + Vector2(-18, 27), "%d/%d" % [unit.hp, unit.max_hp], HORIZONTAL_ALIGNMENT_LEFT, -1, 10, Color(0.95, 0.88, 0.72))

func _draw_hud() -> void:
	var x := BattleManager.GRID_WIDTH * BattleManager.TILE_SIZE + 24
	var y := 32
	var phase_text := "玩家回合"
	if turn_manager.phase == TurnManagerScript.Phase.ENEMY:
		phase_text = "敌方回合"
	elif turn_manager.phase == TurnManagerScript.Phase.VICTORY:
		phase_text = "胜利"
	elif turn_manager.phase == TurnManagerScript.Phase.DEFEAT:
		phase_text = "失败"

	draw_string(ThemeDB.fallback_font, Vector2(x, y), "第 %d 回合 - %s" % [turn_manager.turn_number, phase_text], HORIZONTAL_ALIGNMENT_LEFT, -1, 22, Color(0.95, 0.84, 0.52))
	draw_string(ThemeDB.fallback_font, Vector2(x, y + 34), "Enter: 结束回合  Space: 等待  Esc: 取消", HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color(0.86, 0.78, 0.62))

	var line_y := y + 80
	for message in log_messages.slice(max(0, log_messages.size() - 10), log_messages.size()):
		draw_string(ThemeDB.fallback_font, Vector2(x, line_y), message, HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color(0.9, 0.86, 0.76))
		line_y += 22

func _cell_rect(cell: Vector2i) -> Rect2:
	return Rect2(grid_to_screen(cell), Vector2.ONE * BattleManager.TILE_SIZE)

func _add_log(message: String) -> void:
	log_messages.append(message)
	BattleManager.emit_log(message)
	if log_messages.size() > 30:
		log_messages.pop_front()
