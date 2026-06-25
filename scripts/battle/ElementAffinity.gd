class_name ElementAffinity
extends RefCounted

const STEEL := "steel"
const FLAME := "flame"
const VERDANT := "verdant"
const TIDE := "tide"

const ADVANTAGE_MULTIPLIER := 1.2
const DISADVANTAGE_MULTIPLIER := 0.85
const NEUTRAL_MULTIPLIER := 1.0

static func advantage_target(element: String) -> String:
	match element:
		STEEL:
			return VERDANT
		VERDANT:
			return TIDE
		TIDE:
			return FLAME
		FLAME:
			return STEEL
		_:
			return ""

static func multiplier(attacker_element: String, defender_element: String) -> float:
	if advantage_target(attacker_element) == defender_element:
		return ADVANTAGE_MULTIPLIER
	if advantage_target(defender_element) == attacker_element:
		return DISADVANTAGE_MULTIPLIER
	return NEUTRAL_MULTIPLIER

static func label(attacker_element: String, defender_element: String) -> String:
	var value := multiplier(attacker_element, defender_element)
	if value > NEUTRAL_MULTIPLIER:
		return "advantage"
	if value < NEUTRAL_MULTIPLIER:
		return "disadvantage"
	return "neutral"
