class_name DynamicTerrain
extends RefCounted

static func is_blocked(terrain: Dictionary, turn_number: int) -> bool:
	if terrain.get("blocked_on_even_turns", false) and turn_number % 2 == 0:
		return true
	var move_cost: Dictionary = terrain.get("move_cost", {})
	return int(move_cost.get("infantry", 1)) >= 99

static func should_spread(terrain: Dictionary, turn_number: int) -> bool:
	var spread_every := int(terrain.get("spread_every_turns", 0))
	return spread_every > 0 and turn_number > 0 and turn_number % spread_every == 0

static func heal_amount(terrain: Dictionary, max_hp: int) -> int:
	var heal_percent := int(terrain.get("heal_percent", 0))
	if heal_percent <= 0:
		return 0
	return max(1, int(round(float(max_hp) * float(heal_percent) / 100.0)))

static func terrain_color(terrain_id: String) -> Color:
	match terrain_id:
		"forest":
			return Color(0.18, 0.28, 0.13)
		"stone_bridge":
			return Color(0.32, 0.31, 0.28)
		"canvas_rift":
			return Color(0.45, 0.23, 0.08)
		"ancient_battlefield_mist":
			return Color(0.28, 0.08, 0.08)
		"holy_stone_circle":
			return Color(0.45, 0.36, 0.12)
		"tidal_channel":
			return Color(0.08, 0.19, 0.32)
		"crumbling_wall":
			return Color(0.26, 0.24, 0.21)
		"covenant_altar":
			return Color(0.25, 0.08, 0.18)
		"rubble":
			return Color(0.24, 0.22, 0.18)
		_:
			return Color(0.0, 0.0, 0.0, 0.0)
