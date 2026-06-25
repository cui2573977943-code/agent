class_name CombatPreviewPanel
extends RefCounted

static func summary(preview: Dictionary) -> String:
	var element_text := "中性"
	match preview.get("element_state", "neutral"):
		"advantage":
			element_text = "优势"
		"disadvantage":
			element_text = "劣势"
	return "%s 伤害:%d 命中:%d%% 暴击:%d%% %s" % [
		preview.get("weapon_name", "Weapon"),
		int(preview.get("damage", 0)),
		int(preview.get("hit_chance", 0)),
		int(preview.get("crit_chance", 0)),
		element_text
	]
