#!/usr/bin/env python3
"""Static validation for stage 2 combat data and script wiring."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read_text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def load_json(path: str) -> dict:
    return json.loads(read_text(path))


def assert_true(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate_combat_scripts() -> None:
    required_files = [
        "scripts/battle/ElementAffinity.gd",
        "scripts/battle/WeaponData.gd",
        "scripts/battle/CombatFormula.gd",
        "scripts/ui/CombatPreviewPanel.gd",
    ]
    missing = [path for path in required_files if not (ROOT / path).is_file()]
    assert_true(not missing, f"Missing stage 2 scripts: {missing}")

    combat = read_text("scripts/battle/CombatFormula.gd")
    for token in ["hit_chance", "crit_chance", "element_multiplier", "experience_on_hit", "experience_on_kill"]:
        assert_true(token in combat, f"CombatFormula.gd missing {token}")

    scene = read_text("scripts/battle/BattleScene.gd")
    for token in ["CombatFormulaScript.resolve", "CombatFormulaScript.preview", "_award_experience", "BattleHudRendererScript.draw_unit_card"]:
        assert_true(token in scene, f"BattleScene.gd missing {token}")


def validate_weapons() -> None:
    weapons = load_json("data/units/weapons.json")["weapons"]
    weapon_ids = {weapon["weapon_id"] for weapon in weapons}
    for required in ["heirloom_sword", "yew_bow", "training_lance", "short_blade", "ember_tome"]:
        assert_true(required in weapon_ids, f"Missing weapon: {required}")

    for weapon in weapons:
        for key in ["power", "hit", "crit", "min_range", "max_range", "experience_on_hit", "experience_on_kill"]:
            assert_true(key in weapon, f"Weapon {weapon['weapon_id']} missing {key}")
        assert_true(weapon["min_range"] <= weapon["max_range"], f"Invalid weapon range: {weapon['weapon_id']}")


def validate_classes() -> None:
    classes = load_json("data/units/classes.json")["classes"]
    for class_data in classes:
        assert_true("growth_rates" in class_data, f"Class missing growth_rates: {class_data['class_id']}")
        for key in ["max_hp", "power", "defense", "hit", "avoid"]:
            assert_true(key in class_data["growth_rates"], f"Class {class_data['class_id']} missing growth {key}")


def validate_level_loadouts() -> None:
    level = load_json("data/levels/CH01_L01.json")
    weapons = {weapon["weapon_id"] for weapon in load_json("data/units/weapons.json")["weapons"]}
    for unit in level["player_units"]:
        for item_id in unit.get("loadout", [])[:1]:
            assert_true(item_id in weapons, f"Unknown player weapon: {item_id}")
    for wave in level["enemy_waves"]:
        for unit in wave.get("units", []):
            for item_id in unit.get("loadout", [])[:1]:
                assert_true(item_id in weapons, f"Unknown enemy weapon: {item_id}")
        for unit in wave.get("reinforcements", {}).get("units", []):
            for item_id in unit.get("loadout", [])[:1]:
                assert_true(item_id in weapons, f"Unknown reinforcement weapon: {item_id}")


def main() -> None:
    validate_combat_scripts()
    validate_weapons()
    validate_classes()
    validate_level_loadouts()
    print("Stage 2 combat validation passed.")


if __name__ == "__main__":
    main()
