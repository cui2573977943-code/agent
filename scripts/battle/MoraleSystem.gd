class_name MoraleSystem
extends RefCounted

var morale := {
	"player": 0,
	"enemy": 0
}

func adjust(team: String, delta: int) -> int:
	morale[team] = int(morale.get(team, 0)) + delta
	return int(morale[team])

func value(team: String) -> int:
	return int(morale.get(team, 0))

func hit_modifier(team: String) -> int:
	if value(team) < 0:
		return -5
	return 0
