class_name FormationSystem
extends RefCounted

const FORMATION_DAMAGE_MULTIPLIER := 0.7

static func is_unit_in_formation(unit, units: Array) -> bool:
	if unit == null or not unit.is_alive():
		return false

	var positions := {}
	for other in units:
		if other.team == unit.team and other.is_alive():
			positions[other.grid_position] = true

	return _has_line_containing(unit.grid_position, positions, Vector2i.RIGHT) or _has_line_containing(unit.grid_position, positions, Vector2i.DOWN)

static func damage_taken_multiplier(unit, units: Array) -> float:
	if is_unit_in_formation(unit, units):
		return FORMATION_DAMAGE_MULTIPLIER
	return 1.0

static func _has_line_containing(origin: Vector2i, positions: Dictionary, direction: Vector2i) -> bool:
	var count := 1
	var cursor := origin + direction
	while positions.has(cursor):
		count += 1
		cursor += direction

	cursor = origin - direction
	while positions.has(cursor):
		count += 1
		cursor -= direction

	return count >= 3
