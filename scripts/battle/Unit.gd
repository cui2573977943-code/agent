class_name TacticalUnit
extends RefCounted

var unit_id := ""
var display_name := ""
var team := "player"
var class_id := ""
var element := "steel"
var level := 1
var grid_position := Vector2i.ZERO
var max_hp := 18
var hp := 18
var power := 5
var defense := 2
var hit := 85
var avoid := 5
var move_range := 4
var attack_range := 1
var acted := false

static func from_level_entry(entry: Dictionary, team_name: String) -> TacticalUnit:
	var unit := TacticalUnit.new()
	unit.unit_id = entry.get("id", "")
	unit.display_name = entry.get("display_name", unit.unit_id)
	unit.team = team_name
	unit.class_id = entry.get("class_id", "")
	unit.element = entry.get("element", "steel")
	unit.level = int(entry.get("level", 1))

	var spawn: Array = entry.get("spawn", [0, 0])
	unit.grid_position = Vector2i(int(spawn[0]), int(spawn[1]))
	unit._apply_class_defaults()
	return unit

func _apply_class_defaults() -> void:
	match class_id:
		"bladeguard":
			max_hp = 22
			power = 7
			defense = 3
			hit = 88
			avoid = 12
			move_range = 5
			attack_range = 1
		"ranger":
			max_hp = 18
			power = 5
			defense = 2
			hit = 82
			avoid = 14
			move_range = 5
			attack_range = 2
		"spearwall":
			max_hp = 20
			power = 5
			defense = 4
			hit = 80
			avoid = 4
			move_range = 3
			attack_range = 1
		"scout":
			max_hp = 16
			power = 4
			defense = 1
			hit = 78
			avoid = 16
			move_range = 5
			attack_range = 1
		"mage":
			max_hp = 15
			power = 6
			defense = 1
			hit = 76
			avoid = 6
			move_range = 4
			attack_range = 2
		_:
			max_hp = 18
			power = 5
			defense = 2
			hit = 80
			avoid = 5
			move_range = 4
			attack_range = 1
	hp = max_hp

func is_alive() -> bool:
	return hp > 0

func can_act() -> bool:
	return is_alive() and not acted

func receive_damage(amount: int) -> int:
	var applied := max(0, amount)
	hp = max(0, hp - applied)
	return applied

func reset_for_new_turn() -> void:
	acted = false
