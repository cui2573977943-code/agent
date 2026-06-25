extends Node

const DEFAULT_LEVEL_PATH := "res://data/levels/CH01_L01.json"
const DEFAULT_DIFFICULTY_PATH := "res://data/ai/difficulty_profiles.json"

var current_level_id := "CH01_L01"
var difficulty_id := "normal"
var level_data: Dictionary = {}
var difficulty_profile: Dictionary = {}

func _ready() -> void:
	load_difficulty(difficulty_id)
	load_level(DEFAULT_LEVEL_PATH)

func load_json(path: String) -> Dictionary:
	if not FileAccess.file_exists(path):
		push_error("Missing JSON file: %s" % path)
		return {}

	var file := FileAccess.open(path, FileAccess.READ)
	var text := file.get_as_text()
	var parsed = JSON.parse_string(text)
	if typeof(parsed) != TYPE_DICTIONARY:
		push_error("Invalid JSON dictionary: %s" % path)
		return {}
	return parsed

func load_level(path: String = DEFAULT_LEVEL_PATH) -> Dictionary:
	level_data = load_json(path)
	current_level_id = level_data.get("level_id", current_level_id)
	return level_data

func load_difficulty(target_difficulty_id: String) -> Dictionary:
	var all_profiles := load_json(DEFAULT_DIFFICULTY_PATH)
	for profile in all_profiles.get("profiles", []):
		if profile.get("difficulty_id", "") == target_difficulty_id:
			difficulty_profile = profile
			difficulty_id = target_difficulty_id
			return difficulty_profile

	push_warning("Difficulty profile not found: %s" % target_difficulty_id)
	difficulty_profile = {}
	return difficulty_profile
