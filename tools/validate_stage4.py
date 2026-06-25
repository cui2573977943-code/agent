#!/usr/bin/env python3
"""Static validation for stage 4 dynamic terrain and terrain memory."""

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


def validate_terrain_data() -> None:
    terrain_ids = {entry["terrain_id"] for entry in load_json("data/terrains/base_terrains.json")["terrains"]}
    expected = {
        "ancient_battlefield_mist",
        "holy_stone_circle",
        "tidal_channel",
        "crumbling_wall",
        "covenant_altar",
        "canvas_rift",
    }
    missing = expected - terrain_ids
    assert_true(not missing, f"Missing innovative terrains: {missing}")

    terrains = {entry["terrain_id"]: entry for entry in load_json("data/terrains/base_terrains.json")["terrains"]}
    assert_true("spreading" in terrains["ancient_battlefield_mist"]["dynamic_tags"], "Mist must spread.")
    assert_true(terrains["holy_stone_circle"].get("heal_percent") == 5, "Holy stone circle must heal 5%.")
    assert_true(terrains["tidal_channel"].get("blocked_on_even_turns") is True, "Tidal channel must block on even turns.")
    assert_true(terrains["crumbling_wall"]["interactive"].get("destructible") is True, "Crumbling wall must be destructible.")
    assert_true("summon_reinforcement" in terrains["covenant_altar"]["dynamic_tags"], "Covenant altar must summon.")


def validate_scripts() -> None:
    for path in [
        "scripts/map/TerrainSystem.gd",
        "scripts/map/DynamicTerrain.gd",
        "scripts/map/TerrainMemory.gd",
    ]:
        assert_true((ROOT / path).is_file(), f"Missing script: {path}")

    terrain_system = read_text("scripts/map/TerrainSystem.gd")
    for token in ["advance_turn", "_spread_mist", "_apply_turn_start_healing", "mark_element_memory", "combat_modifiers_at"]:
        assert_true(token in terrain_system, f"TerrainSystem.gd missing {token}")

    battle_scene = read_text("scripts/battle/BattleScene.gd")
    for token in ["terrain_system.configure", "terrain_system.combat_modifiers_at", "terrain_system.mark_element_memory", "terrain_system.is_blocked"]:
        assert_true(token in battle_scene, f"BattleScene.gd missing {token}")


def main() -> None:
    validate_terrain_data()
    validate_scripts()
    print("Stage 4 terrain validation passed.")


if __name__ == "__main__":
    main()
