#!/usr/bin/env python3
"""Static validation for stage 3 classes, formation, morale, and class abilities."""

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


def validate_eight_classes() -> None:
    class_ids = {entry["class_id"] for entry in load_json("data/units/classes.json")["classes"]}
    expected = {"spearwall", "bladeguard", "mage", "ranger", "druid", "priest", "engineer", "scout"}
    assert_true(class_ids == expected, f"Expected 8 class ids {expected}, got {class_ids}")


def validate_supports() -> None:
    supports = load_json("data/units/supports.json")["supports"]
    assert_true(len(supports) >= 1, "Expected at least one support relationship.")
    first = supports[0]
    for key in ["support_id", "unit_a", "unit_b", "affinity", "combat_bonus_by_affinity"]:
        assert_true(key in first, f"Support entry missing {key}")


def validate_scripts() -> None:
    for path in [
        "scripts/battle/FormationSystem.gd",
        "scripts/battle/MoraleSystem.gd",
        "scripts/battle/ClassAbility.gd",
    ]:
        assert_true((ROOT / path).is_file(), f"Missing script: {path}")

    formation = read_text("scripts/battle/FormationSystem.gd")
    assert_true("FORMATION_DAMAGE_MULTIPLIER := 0.7" in formation, "Formation damage multiplier must be 0.7.")
    assert_true("count >= 3" in formation, "Formation must require 3 contiguous units.")

    scene = read_text("scripts/battle/BattleScene.gd")
    for token in [
        "FormationSystemScript.damage_taken_multiplier",
        "morale_system.adjust",
        "ClassAbilityScript.execution_bonus_damage",
    ]:
        assert_true(token in scene, f"BattleScene.gd missing {token}")


def main() -> None:
    validate_eight_classes()
    validate_supports()
    validate_scripts()
    print("Stage 3 formation validation passed.")


if __name__ == "__main__":
    main()
