class_name TerrainSystem
extends RefCounted

const DynamicTerrainScript := preload("res://scripts/map/DynamicTerrain.gd")
const TerrainMemoryScript := preload("res://scripts/map/TerrainMemory.gd")

var terrain_catalog := {}
var terrain_by_cell := {}
var memory = TerrainMemoryScript.new()

func configure(catalog_data: Dictionary, level_data: Dictionary) -> void:
	terrain_catalog.clear()
	terrain_by_cell.clear()
	for terrain in catalog_data.get("terrains", []):
		terrain_catalog[terrain.get("terrain_id", "")] = terrain

	for feature in level_data.get("terrain_features", []):
		var terrain_id := _normalize_terrain_id(feature.get("type", "plain"))
		for raw_position in feature.get("positions", []):
			var cell := Vector2i(int(raw_position[0]), int(raw_position[1]))
			terrain_by_cell[cell] = terrain_id

func terrain_id_at(cell: Vector2i) -> String:
	return terrain_by_cell.get(cell, "plain")

func terrain_at(cell: Vector2i) -> Dictionary:
	return terrain_catalog.get(terrain_id_at(cell), terrain_catalog.get("plain", {}))

func combat_modifiers_at(cell: Vector2i) -> Dictionary:
	var terrain := terrain_at(cell)
	var modifiers := {
		"defense_mod": int(terrain.get("defense_mod", 0)),
		"hit_mod": int(terrain.get("hit_mod", 0)),
		"avoid_mod": int(terrain.get("avoid_mod", 0)),
		"crit_mod": int(terrain.get("crit_mod", 0))
	}
	for key in memory.modifiers_for(cell).keys():
		modifiers[key] = int(modifiers.get(key, 0)) + int(memory.modifiers_for(cell)[key])
	return modifiers

func is_blocked(cell: Vector2i, turn_number: int) -> bool:
	return DynamicTerrainScript.is_blocked(terrain_at(cell), turn_number)

func advance_turn(turn_number: int, units: Array) -> Array[String]:
	var logs: Array[String] = []
	memory.advance_turn()
	logs.append_array(_spread_mist(turn_number))
	logs.append_array(_apply_turn_start_healing(units))
	return logs

func mark_element_memory(cell: Vector2i, element: String) -> void:
	if element == "" or element == "none":
		return
	memory.add_memory(cell, element, 3, _memory_modifiers(element))

func _spread_mist(turn_number: int) -> Array[String]:
	var logs: Array[String] = []
	var new_cells: Array[Vector2i] = []
	for cell in terrain_by_cell.keys():
		var terrain := terrain_at(cell)
		if not DynamicTerrainScript.should_spread(terrain, turn_number):
			continue
		for direction in [Vector2i.RIGHT, Vector2i.LEFT, Vector2i.DOWN, Vector2i.UP]:
			var next: Vector2i = cell + direction
			if not terrain_by_cell.has(next):
				new_cells.append(next)
	for cell in new_cells:
		terrain_by_cell[cell] = "ancient_battlefield_mist"
	if not new_cells.is_empty():
		logs.append("古战场雾扩散了 %d 格。" % new_cells.size())
	return logs

func _apply_turn_start_healing(units: Array) -> Array[String]:
	var logs: Array[String] = []
	for unit in units:
		if not unit.is_alive():
			continue
		var terrain := terrain_at(unit.grid_position)
		var amount: int = DynamicTerrainScript.heal_amount(terrain, unit.max_hp)
		if amount <= 0:
			continue
		var before: int = unit.hp
		unit.hp = min(unit.max_hp, unit.hp + amount)
		if unit.hp > before:
			logs.append("%s 受到 %s 治疗，恢复 %d HP。" % [unit.display_name, terrain.get("display_name", "地形"), unit.hp - before])
	return logs

func _memory_modifiers(element: String) -> Dictionary:
	match element:
		"flame":
			return { "hit_mod": -5, "crit_mod": 5 }
		"tide":
			return { "avoid_mod": -5 }
		"verdant":
			return { "defense_mod": 1 }
		"steel":
			return { "defense_mod": -1 }
		_:
			return {}

func _normalize_terrain_id(raw_type: String) -> String:
	match raw_type:
		"blood_mist":
			return "ancient_battlefield_mist"
		"ancient_battlefield":
			return "ancient_battlefield_mist"
		_:
			return raw_type
