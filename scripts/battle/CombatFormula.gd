class_name CombatFormula
extends RefCounted

const ElementAffinityScript := preload("res://scripts/battle/ElementAffinity.gd")
const WeaponDataScript := preload("res://scripts/battle/WeaponData.gd")

static func preview(attacker, defender, raw_weapon: Dictionary, terrain := {}) -> Dictionary:
	var weapon: Dictionary = WeaponDataScript.with_defaults(raw_weapon)
	var terrain_hit_mod: int = int(terrain.get("hit_mod", 0))
	var terrain_avoid_mod: int = int(terrain.get("avoid_mod", 0))
	var terrain_defense_mod: int = int(terrain.get("defense_mod", 0))
	var terrain_crit_mod: int = int(terrain.get("crit_mod", 0))
	var bonus_damage: int = int(terrain.get("bonus_damage", 0))
	var damage_taken_multiplier: float = float(terrain.get("damage_taken_multiplier", 1.0))
	var element_multiplier: float = ElementAffinityScript.multiplier(attacker.element, defender.element)
	var base_damage: int = max(0, int(attacker.power) + int(weapon.get("power", 0)) + bonus_damage - int(defender.defense) - terrain_defense_mod)
	var final_damage: int = max(1, int(round(float(base_damage) * element_multiplier * damage_taken_multiplier)))
	var hit_chance: int = clamp(int(attacker.hit) + int(weapon.get("hit", 0)) - int(defender.avoid) + terrain_hit_mod + terrain_avoid_mod, 0, 100)
	var crit_chance: int = clamp(int(weapon.get("crit", 0)) + terrain_crit_mod, 0, 100)

	return {
		"weapon_id": weapon.get("weapon_id", "training_weapon"),
		"weapon_name": weapon.get("display_name", "Training Weapon"),
		"damage": final_damage,
		"hit_chance": hit_chance,
		"crit_chance": crit_chance,
		"element_state": ElementAffinityScript.label(attacker.element, defender.element),
		"element_multiplier": element_multiplier,
		"damage_taken_multiplier": damage_taken_multiplier,
		"bonus_damage": bonus_damage,
		"experience_on_hit": int(weapon.get("experience_on_hit", 10)),
		"experience_on_kill": int(weapon.get("experience_on_kill", 35))
	}

static func resolve(attacker, defender, raw_weapon: Dictionary, terrain := {}) -> Dictionary:
	var result: Dictionary = preview(attacker, defender, raw_weapon, terrain)
	var hit_roll: int = randi_range(1, 100)
	var crit_roll: int = randi_range(1, 100)
	var did_hit: bool = hit_roll <= int(result["hit_chance"])
	var did_crit: bool = did_hit and crit_roll <= int(result["crit_chance"])
	var damage: int = 0

	if did_hit:
		damage = int(result["damage"])
		if did_crit:
			damage *= 3
		defender.receive_damage(damage)

	result["hit_roll"] = hit_roll
	result["crit_roll"] = crit_roll
	result["did_hit"] = did_hit
	result["did_crit"] = did_crit
	result["applied_damage"] = damage
	result["defender_defeated"] = not defender.is_alive()
	return result
