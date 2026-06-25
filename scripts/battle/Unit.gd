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
var experience := 0
var weapon_id := ""
var growth_rates: Dictionary = {}

func configure_from_level_entry(entry: Dictionary, team_name: String, class_data := {}) -> void:
	unit_id = entry.get("id", "")
	display_name = entry.get("display_name", unit_id)
	team = team_name
	class_id = entry.get("class_id", "")
	element = entry.get("element", "steel")
	level = int(entry.get("level", 1))
	experience = int(entry.get("experience", 0))
	var loadout: Array = entry.get("loadout", [])
	if not loadout.is_empty():
		weapon_id = str(loadout[0])

	var spawn: Array = entry.get("spawn", [0, 0])
	grid_position = Vector2i(int(spawn[0]), int(spawn[1]))
	_apply_class_defaults(class_data)

func _apply_class_defaults(class_data := {}) -> void:
	var base_stats: Dictionary = class_data.get("base_stats", {})
	if base_stats.is_empty():
		match class_id:
			"bladeguard":
				base_stats = { "max_hp": 22, "power": 7, "defense": 3, "hit": 88, "avoid": 12, "move_range": 5, "attack_range": 1 }
			"ranger":
				base_stats = { "max_hp": 18, "power": 5, "defense": 2, "hit": 82, "avoid": 14, "move_range": 5, "attack_range": 2 }
			"spearwall":
				base_stats = { "max_hp": 20, "power": 5, "defense": 4, "hit": 80, "avoid": 4, "move_range": 3, "attack_range": 1 }
			"scout":
				base_stats = { "max_hp": 16, "power": 4, "defense": 1, "hit": 78, "avoid": 16, "move_range": 5, "attack_range": 1 }
			"mage":
				base_stats = { "max_hp": 15, "power": 6, "defense": 1, "hit": 76, "avoid": 6, "move_range": 4, "attack_range": 2 }
			_:
				base_stats = { "max_hp": 18, "power": 5, "defense": 2, "hit": 80, "avoid": 5, "move_range": 4, "attack_range": 1 }

	max_hp = int(base_stats.get("max_hp", max_hp))
	power = int(base_stats.get("power", power))
	defense = int(base_stats.get("defense", defense))
	hit = int(base_stats.get("hit", hit))
	avoid = int(base_stats.get("avoid", avoid))
	move_range = int(base_stats.get("move_range", move_range))
	attack_range = int(base_stats.get("attack_range", attack_range))
	growth_rates = class_data.get("growth_rates", {})
	hp = max_hp

func is_alive() -> bool:
	return hp > 0

func can_act() -> bool:
	return is_alive() and not acted

func receive_damage(amount: int) -> int:
	var applied: int = max(0, amount)
	hp = max(0, hp - applied)
	return applied

func reset_for_new_turn() -> void:
	acted = false

func gain_experience(amount: int) -> Array[String]:
	var results: Array[String] = []
	experience += max(0, amount)
	while experience >= 100:
		experience -= 100
		level += 1
		results.append(_level_up())
	return results

func _level_up() -> String:
	var gains := []
	for stat_name in ["max_hp", "power", "defense", "hit", "avoid"]:
		var rate := int(growth_rates.get(stat_name, 0))
		if rate >= 100 or (rate > 0 and randi_range(1, 100) <= rate):
			match stat_name:
				"max_hp":
					max_hp += 1
					hp += 1
				"power":
					power += 1
				"defense":
					defense += 1
				"hit":
					hit += 1
				"avoid":
					avoid += 1
			gains.append(stat_name)
	if gains.is_empty():
		power += 1
		gains.append("power")
	return "Lv %d: %s +1" % [level, ", ".join(gains)]
