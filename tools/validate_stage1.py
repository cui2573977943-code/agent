#!/usr/bin/env python3
"""Static validation for the stage 1 Godot tactical RPG scaffold."""

from __future__ import annotations

import configparser
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


REQUIRED_FILES = [
    "project.godot",
    "scenes/battle/BattleScene.tscn",
    "autoload/GameManager.gd",
    "autoload/BattleManager.gd",
    "autoload/AudioManager.gd",
    "scripts/battle/BattleScene.gd",
    "scripts/battle/TurnManager.gd",
    "scripts/battle/Unit.gd",
    "scripts/map/GridMap.gd",
    "data/levels/CH01_L01.json",
    "data/ai/difficulty_profiles.json",
    "data/units/classes.json",
    "data/terrains/base_terrains.json",
    "data/dialogues/ch01_l01.json",
    "docs/GDD.md",
    "docs/TECHNICAL_DESIGN.md",
    "docs/PROMPT_AUDIT.md",
]


def read_text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def load_json(path: str) -> dict:
    return json.loads(read_text(path))


def assert_true(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate_required_files() -> None:
    missing = [path for path in REQUIRED_FILES if not (ROOT / path).is_file()]
    assert_true(not missing, f"Missing required files: {missing}")


def validate_project_config() -> None:
    raw = read_text("project.godot")
    assert_true('run/main_scene="res://scenes/battle/BattleScene.tscn"' in raw, "Main scene is not configured.")
    for autoload in ["GameManager", "BattleManager", "AudioManager"]:
        assert_true(f'{autoload}="*res://autoload/{autoload}.gd"' in raw, f"Missing autoload: {autoload}")


def validate_scene_references() -> None:
    scene = read_text("scenes/battle/BattleScene.tscn")
    assert_true('path="res://scripts/battle/BattleScene.gd"' in scene, "BattleScene.tscn does not reference BattleScene.gd.")


def validate_level_data() -> None:
    level = load_json("data/levels/CH01_L01.json")
    assert_true(level["level_id"] == "CH01_L01", "Unexpected level_id.")
    assert_true(level["map_size"] == {"width": 20, "height": 15}, "Stage 1 map must be 20x15.")
    assert_true(len(level.get("player_units", [])) >= 2, "Stage 1 needs at least 2 player units.")
    first_wave = level.get("enemy_waves", [])[0]
    assert_true(len(first_wave.get("units", [])) >= 2, "Stage 1 needs at least 2 initial enemies.")

    dialogue = load_json("data/dialogues/ch01_l01.json")
    dialogue_nodes = dialogue.get("nodes", {})
    for beat_id in level.get("story_beats", {}).values():
        assert_true(beat_id in dialogue_nodes, f"Missing dialogue node referenced by level: {beat_id}")


def validate_difficulty_profiles() -> None:
    profiles = load_json("data/ai/difficulty_profiles.json")["profiles"]
    ids = {profile["difficulty_id"] for profile in profiles}
    assert_true(ids == {"story", "normal", "hard", "nightmare"}, f"Unexpected difficulty ids: {ids}")
    for profile in profiles:
        for key in [
            "max_actions_evaluated",
            "coordination_limit",
            "prediction_depth",
            "can_capture_altars",
            "can_execute_low_hp",
            "can_use_terrain_memory",
            "can_use_overdraw_magic",
            "can_modify_terrain",
            "intent_visibility",
            "utility_weights",
        ]:
            assert_true(key in profile, f"Difficulty profile {profile['difficulty_id']} missing {key}")


def validate_design_data() -> None:
    class_ids = {entry["class_id"] for entry in load_json("data/units/classes.json")["classes"]}
    for required in ["bladeguard", "ranger", "spearwall", "scout", "mage"]:
        assert_true(required in class_ids, f"Missing class data: {required}")

    terrains = load_json("data/terrains/base_terrains.json")["terrains"]
    for terrain in terrains:
        assert_true("hit_mod" in terrain, f"Terrain missing hit_mod: {terrain['terrain_id']}")
        assert_true("avoid_mod" in terrain, f"Terrain missing avoid_mod: {terrain['terrain_id']}")


def validate_gdscript_shape() -> None:
    battle_script = read_text("scripts/battle/BattleScene.gd")
    for token in [
        "func _handle_left_click",
        "func _end_player_turn",
        "func _run_enemy_turn",
        "func _attack",
        "func _draw_grid",
    ]:
        assert_true(token in battle_script, f"BattleScene.gd missing {token}")


def main() -> None:
    validate_required_files()
    validate_project_config()
    validate_scene_references()
    validate_level_data()
    validate_difficulty_profiles()
    validate_design_data()
    validate_gdscript_shape()
    print("Stage 1 scaffold validation passed.")


if __name__ == "__main__":
    main()
