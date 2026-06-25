class_name WeaponData
extends RefCounted

static func default_weapon() -> Dictionary:
	return {
		"weapon_id": "training_weapon",
		"display_name": "Training Weapon",
		"power": 1,
		"hit": 75,
		"crit": 0,
		"min_range": 1,
		"max_range": 1,
		"element": "none",
		"experience_on_hit": 10,
		"experience_on_kill": 35
	}

static func with_defaults(raw_weapon: Dictionary) -> Dictionary:
	var merged := default_weapon()
	for key in raw_weapon.keys():
		merged[key] = raw_weapon[key]
	return merged

static func max_range(raw_weapon: Dictionary) -> int:
	return int(with_defaults(raw_weapon).get("max_range", 1))

static func min_range(raw_weapon: Dictionary) -> int:
	return int(with_defaults(raw_weapon).get("min_range", 1))
