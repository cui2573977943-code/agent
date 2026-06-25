class_name TurnManager
extends RefCounted

enum Phase {
	PLAYER,
	ENEMY,
	VICTORY,
	DEFEAT
}

var phase := Phase.PLAYER
var turn_number := 1

func start_player_turn(units: Array) -> void:
	phase = Phase.PLAYER
	for unit in units:
		if unit.is_alive():
			unit.reset_for_new_turn()

func start_enemy_turn(units: Array) -> void:
	phase = Phase.ENEMY
	for unit in units:
		if unit.is_alive():
			unit.reset_for_new_turn()

func end_enemy_turn() -> void:
	turn_number += 1
	phase = Phase.PLAYER

func set_victory() -> void:
	phase = Phase.VICTORY

func set_defeat() -> void:
	phase = Phase.DEFEAT

func is_player_phase() -> bool:
	return phase == Phase.PLAYER

func is_enemy_phase() -> bool:
	return phase == Phase.ENEMY
