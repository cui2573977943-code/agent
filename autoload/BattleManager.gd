extends Node

const TILE_SIZE := 40
const GRID_WIDTH := 20
const GRID_HEIGHT := 15

signal battle_log(message: String)

func emit_log(message: String) -> void:
	battle_log.emit(message)
