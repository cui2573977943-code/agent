class_name ClassAbility
extends RefCounted

static func can_execute_low_hp(attacker, defender) -> bool:
	if attacker.class_id != "bladeguard":
		return false
	if defender.max_hp <= 0:
		return false
	return float(defender.hp) / float(defender.max_hp) < 0.3

static func execution_bonus_damage(attacker, defender) -> int:
	if can_execute_low_hp(attacker, defender):
		return max(1, int(attacker.power / 2.0))
	return 0

static func signature_label(class_id: String) -> String:
	match class_id:
		"bladeguard":
			return "处决窗口"
		"spearwall":
			return "阵线值"
		"mage":
			return "以太透支"
		"ranger":
			return "地形共鸣"
		"druid":
			return "形态切换"
		"priest":
			return "圣域铺设"
		"engineer":
			return "战场改造"
		"scout":
			return "迷雾穿透"
		_:
			return "无"
