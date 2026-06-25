extends Node2D

const TacticalUnitScript := preload("res://scripts/battle/Unit.gd")
const TurnManagerScript := preload("res://scripts/battle/TurnManager.gd")
const TacticalGridMapScript := preload("res://scripts/map/GridMap.gd")
const CombatFormulaScript := preload("res://scripts/battle/CombatFormula.gd")
const WeaponDataScript := preload("res://scripts/battle/WeaponData.gd")
const CombatPreviewPanelScript := preload("res://scripts/ui/CombatPreviewPanel.gd")
const FormationSystemScript := preload("res://scripts/battle/FormationSystem.gd")
const MoraleSystemScript := preload("res://scripts/battle/MoraleSystem.gd")
const ClassAbilityScript := preload("res://scripts/battle/ClassAbility.gd")

var grid := TacticalGridMapScript.new(BattleManager.GRID_WIDTH, BattleManager.GRID_HEIGHT)
var turn_manager := TurnManagerScript.new()
var player_units: Array = []
var enemy_units: Array = []
var selected_unit = null
var move_cells: Array[Vector2i] = []
var attack_cells: Array[Vector2i] = []
var current_preview: Dictionary = {}
var log_messages: Array[String] = []
var level_data: Dictionary = {}
var morale_system := MoraleSystemScript.new()

func _ready() -> void:
	level_data = GameManager.load_level()
	_load_units_from_level(level_data)
	turn_manager.start_player_turn(player_units)
	_add_log("阶段 3：阵线减伤、士气和刃卫处决窗口已启用。")
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
		var player_unit = TacticalUnitScript.new()
		player_unit.configure_from_level_entry(entry, "player", GameManager.get_class_data(entry.get("class_id", "")))
		player_units.append(player_unit)

	for wave in data.get("enemy_waves", []):
		if int(wave.get("turn", 1)) != 1:
			continue
		for entry in wave.get("units", []):
			var enemy_unit = TacticalUnitScript.new()
			enemy_unit.configure_from_level_entry(entry, "enemy", GameManager.get_class_data(entry.get("class_id", "")))
			enemy_units.append(enemy_unit)

func _handle_left_click(cell: Vector2i) -> void:
	if not turn_manager.is_player_phase():
		return
	if not grid.is_inside(cell):
		return

	if selected_unit != null:
		var target = _unit_at(cell, "enemy")
		if target != null and _is_in_attack_range(selected_unit, target):
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
			_update_preview_for_selected()
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
	_update_preview_for_selected()
	_add_log("选择 %s。" % unit.display_name)

func _clear_selection() -> void:
	selected_unit = null
	move_cells.clear()
	attack_cells.clear()
	current_preview.clear()

func _attack(attacker, defender) -> void:
	var weapon := _weapon_for(attacker)
	var defender_was_in_formation := FormationSystemScript.is_unit_in_formation(defender, player_units + enemy_units)
	var combat_context := _combat_context(attacker, defender)
	var result: Dictionary = CombatFormulaScript.resolve(attacker, defender, weapon, combat_context)
	if result.get("did_hit", false):
		var crit_text := ""
		if result.get("did_crit", false):
			crit_text = " 暴击！"
		_add_log("%s 用 %s 攻击 %s，造成 %d 伤害。%s" % [attacker.display_name, result["weapon_name"], defender.display_name, int(result["applied_damage"]), crit_text])
		if defender_was_in_formation:
			_add_log("%s 受到阵线保护，伤害降低。" % defender.display_name)
		if int(result.get("bonus_damage", 0)) > 0:
			_add_log("%s 触发 %s。" % [attacker.display_name, ClassAbilityScript.signature_label(attacker.class_id)])
		_award_experience(attacker, int(result["experience_on_hit"]))
	else:
		_add_log("%s 攻击 %s，但未命中。" % [attacker.display_name, defender.display_name])
	if result.get("defender_defeated", false):
		_add_log("%s 被击败。" % defender.display_name)
		_award_experience(attacker, int(result["experience_on_kill"]))
		if defender_was_in_formation:
			var new_morale: int = morale_system.adjust(defender.team, -1)
			_add_log("%s 阵线被突破，士气变为 %d。" % [defender.team, new_morale])

func _award_experience(unit, amount: int) -> void:
	if unit.team != "player":
		return
	var level_results: Array[String] = unit.gain_experience(amount)
	_add_log("%s 获得 %d 经验（%d/100）。" % [unit.display_name, amount, unit.experience])
	for result in level_results:
		_add_log("%s 升级：%s。" % [unit.display_name, result])

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
		if _is_in_attack_range(enemy, nearest):
			_attack(enemy, nearest)
		else:
			var occupied := _occupied_cells_for_movement(enemy)
			var steps: int = int(enemy.move_range)
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
	var weapon := _weapon_for(unit)
	var min_range := WeaponDataScript.min_range(weapon)
	var max_range := WeaponDataScript.max_range(weapon)
	for x in range(grid.width):
		for y in range(grid.height):
			var cell := Vector2i(x, y)
			var distance := grid.distance(unit.grid_position, cell)
			if distance >= min_range and distance <= max_range:
				result.append(cell)
	return result

func _weapon_for(unit) -> Dictionary:
	var weapon := GameManager.get_weapon_data(unit.weapon_id)
	if weapon.is_empty():
		return WeaponDataScript.default_weapon()
	return weapon

func _combat_context(attacker, defender) -> Dictionary:
	return {
		"damage_taken_multiplier": FormationSystemScript.damage_taken_multiplier(defender, player_units + enemy_units),
		"bonus_damage": ClassAbilityScript.execution_bonus_damage(attacker, defender),
		"hit_mod": morale_system.hit_modifier(attacker.team)
	}

func _is_in_attack_range(attacker, defender) -> bool:
	var weapon := _weapon_for(attacker)
	var distance := grid.distance(attacker.grid_position, defender.grid_position)
	return distance >= WeaponDataScript.min_range(weapon) and distance <= WeaponDataScript.max_range(weapon)

func _update_preview_for_selected() -> void:
	current_preview.clear()
	if selected_unit == null:
		return
	var target = _nearest_alive_unit(selected_unit.grid_position, enemy_units)
	if target == null:
		return
	var weapon := _weapon_for(selected_unit)
	var distance := grid.distance(selected_unit.grid_position, target.grid_position)
	if distance < WeaponDataScript.min_range(weapon) or distance > WeaponDataScript.max_range(weapon):
		return
	current_preview = CombatFormulaScript.preview(selected_unit, target, weapon, _combat_context(selected_unit, target))

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
	draw_string(ThemeDB.fallback_font, Vector2(x, y + 56), "士气 玩家:%d 敌方:%d" % [morale_system.value("player"), morale_system.value("enemy")], HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color(0.86, 0.78, 0.62))

	var line_y := y + 102
	if not current_preview.is_empty():
		draw_string(ThemeDB.fallback_font, Vector2(x, line_y), "战斗预览: %s" % CombatPreviewPanelScript.summary(current_preview), HORIZONTAL_ALIGNMENT_LEFT, -1, 14, Color(0.95, 0.84, 0.52))
		line_y += 28
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
