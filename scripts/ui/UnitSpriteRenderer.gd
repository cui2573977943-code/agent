class_name UnitSpriteRenderer
extends RefCounted

static func draw_unit(canvas: CanvasItem, unit, center: Vector2, tile_size: int, is_selected: bool = false) -> void:
	var team_color := Color(0.12, 0.28, 0.58)
	var accent := Color(0.79, 0.64, 0.15)
	if unit.team == "enemy":
		team_color = Color(0.52, 0.1, 0.08)
		accent = Color(0.72, 0.2, 0.16)
	if unit.acted:
		team_color = team_color.darkened(0.35)
		accent = accent.darkened(0.25)

	var body_size := Vector2(tile_size * 0.42, tile_size * 0.52)
	var body_rect := Rect2(center - body_size * 0.5 + Vector2(0, 2), body_size)
	canvas.draw_rect(body_rect, team_color, true)
	canvas.draw_rect(body_rect, accent, false, 2.0)

	var head_radius := tile_size * 0.11
	canvas.draw_circle(center + Vector2(0, -tile_size * 0.18), head_radius, team_color.lightened(0.18))
	canvas.draw_arc(center + Vector2(0, -tile_size * 0.18), head_radius, 0.0, TAU, 24, accent, 2.0)

	_draw_class_glyph(canvas, unit.class_id, center, tile_size, accent)
	_draw_hp_bar(canvas, center, tile_size, unit.hp, unit.max_hp, unit.team == "player")

	if is_selected:
		canvas.draw_arc(center, tile_size * 0.34, 0.0, TAU, 32, Color(0.95, 0.84, 0.52), 3.0)

static func _draw_class_glyph(canvas: CanvasItem, class_id: String, center: Vector2, tile_size: int, accent: Color) -> void:
	var weapon_start := center + Vector2(tile_size * 0.08, -tile_size * 0.02)
	var weapon_end := center + Vector2(tile_size * 0.24, -tile_size * 0.16)
	match class_id:
		"ranger":
			weapon_start = center + Vector2(-tile_size * 0.2, -tile_size * 0.02)
			weapon_end = center + Vector2(tile_size * 0.22, -tile_size * 0.02)
		"mage":
			canvas.draw_circle(center + Vector2(tile_size * 0.16, -tile_size * 0.08), tile_size * 0.07, accent.darkened(0.2))
			return
		"spearwall":
			weapon_start = center + Vector2(tile_size * 0.02, tile_size * 0.12)
			weapon_end = center + Vector2(tile_size * 0.02, -tile_size * 0.28)
		"priest":
			canvas.draw_line(center + Vector2(0, -tile_size * 0.24), center + Vector2(0, tile_size * 0.08), accent, 3.0)
			canvas.draw_line(center + Vector2(-tile_size * 0.08, -tile_size * 0.12), center + Vector2(tile_size * 0.08, -tile_size * 0.12), accent, 3.0)
			return
	canvas.draw_line(weapon_start, weapon_end, accent.lightened(0.2), 3.0)

static func _draw_hp_bar(canvas: CanvasItem, center: Vector2, tile_size: int, hp: int, max_hp: int, is_player: bool) -> void:
	var width := tile_size * 0.72
	var height := 5.0
	var origin := center + Vector2(-width * 0.5, tile_size * 0.28)
	var bar_bg := Rect2(origin, Vector2(width, height))
	canvas.draw_rect(bar_bg, Color(0.12, 0.08, 0.06, 0.9), true)
	var ratio: float = 0.0 if max_hp <= 0 else clamp(float(hp) / float(max_hp), 0.0, 1.0)
	var fill_color := Color(0.18, 0.55, 0.28) if is_player else Color(0.72, 0.18, 0.14)
	canvas.draw_rect(Rect2(origin, Vector2(width * ratio, height)), fill_color, true)
	canvas.draw_rect(bar_bg, Color(0.79, 0.64, 0.15, 0.8), false, 1.0)
