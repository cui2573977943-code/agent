class_name TerrainMemory
extends RefCounted

var memories := {}

func add_memory(cell: Vector2i, element: String, turns: int, modifiers := {}) -> void:
	memories[cell] = {
		"element": element,
		"turns": turns,
		"modifiers": modifiers
	}

func advance_turn() -> void:
	var expired: Array[Vector2i] = []
	for cell in memories.keys():
		memories[cell]["turns"] = int(memories[cell].get("turns", 0)) - 1
		if int(memories[cell]["turns"]) <= 0:
			expired.append(cell)
	for cell in expired:
		memories.erase(cell)

func modifiers_for(cell: Vector2i) -> Dictionary:
	if not memories.has(cell):
		return {}
	return memories[cell].get("modifiers", {})

func has_memory(cell: Vector2i) -> bool:
	return memories.has(cell)
