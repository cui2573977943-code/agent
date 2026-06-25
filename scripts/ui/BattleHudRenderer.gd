class_name BattleHudRenderer
extends RefCounted

const PANEL_X := 836.0
const PANEL_WIDTH := 420.0

static func draw_background(canvas: CanvasItem, viewport_size: Vector2) -> void:
	canvas.draw_rect(Rect2(Vector2.ZERO, viewport_size), Color(0.1, 0.08, 0.06), true)
	canvas.draw_rect(Rect2(Vector2(8, 8), Vector2(820, 704)), Color(0.16, 0.12, 0.08), true)
	canvas.draw_rect(Rect2(Vector2(8, 8), Vector2(820, 704)), Color(0.56, 0.45, 0.28, 0.7), false, 3.0)

static func draw_side_panel(canvas: CanvasItem, viewport_size: Vector2) -> void:
	var panel_rect := Rect2(Vector2(PANEL_X, 8), Vector2(PANEL_WIDTH, viewport_size.y - 16))
	canvas.draw_rect(panel_rect, Color(0.24, 0.18, 0.12, 0.96), true)
	canvas.draw_rect(panel_rect, Color(0.79, 0.64, 0.15, 0.85), false, 3.0)
	canvas.draw_rect(Rect2(panel_rect.position + Vector2(10, 10), panel_rect.size - Vector2(20, 20)), Color(0.34, 0.27, 0.18, 0.55), true)

static func draw_turn_banner(canvas: CanvasItem, turn_number: int, phase_text: String) -> void:
	var banner := Rect2(Vector2(PANEL_X + 24, 24), Vector2(PANEL_WIDTH - 48, 54))
	canvas.draw_rect(banner, Color(0.12, 0.22, 0.38, 0.92), true)
	canvas.draw_rect(banner, Color(0.79, 0.64, 0.15), false, 2.0)
	_draw_text(canvas, Vector2(banner.position.x + 16, banner.position.y + 34), "第 %d 回合 · %s" % [turn_number, phase_text], 20, Color(0.96, 0.9, 0.72))

static func draw_unit_card(canvas: CanvasItem, unit, weapon: Dictionary, preview: Dictionary) -> void:
	var card := Rect2(Vector2(PANEL_X + 24, 92), Vector2(PANEL_WIDTH - 48, 220))
	canvas.draw_rect(card, Color(0.18, 0.14, 0.1, 0.95), true)
	canvas.draw_rect(card, Color(0.79, 0.64, 0.15, 0.75), false, 2.0)

	var portrait_rect := Rect2(card.position + Vector2(16, 16), Vector2(108, 132))
	_draw_portrait(canvas, portrait_rect, unit)

	var text_x := portrait_rect.position.x + portrait_rect.size.x + 18
	_draw_text(canvas, Vector2(text_x, card.position.y + 34), unit.display_name, 22, Color(0.96, 0.9, 0.72))
	_draw_text(canvas, Vector2(text_x, card.position.y + 62), _class_label(unit.class_id), 14, Color(0.86, 0.78, 0.62))
	_draw_text(canvas, Vector2(text_x, card.position.y + 86), "Lv %d  HP %d/%d" % [unit.level, unit.hp, unit.max_hp], 14, Color(0.9, 0.86, 0.76))
	_draw_text(canvas, Vector2(text_x, card.position.y + 110), "武器 %s" % weapon.get("display_name", "无"), 14, Color(0.9, 0.86, 0.76))
	_draw_text(canvas, Vector2(text_x, card.position.y + 134), "经验 %d/100" % unit.experience, 14, Color(0.9, 0.86, 0.76))
	_draw_text(canvas, Vector2(text_x, card.position.y + 158), "元素 %s" % _element_label(unit.element), 14, Color(0.9, 0.86, 0.76))

	if preview.is_empty():
		_draw_text(canvas, Vector2(card.position.x + 16, card.position.y + 188), "战斗预览：当前无射程内目标", 13, Color(0.82, 0.74, 0.58))
	else:
		_draw_text(canvas, Vector2(card.position.x + 16, card.position.y + 176), "战斗预览", 13, Color(0.95, 0.84, 0.52))
		_draw_text(canvas, Vector2(card.position.x + 16, card.position.y + 198), _preview_line(preview), 13, Color(0.9, 0.86, 0.76))

static func draw_empty_unit_card(canvas: CanvasItem) -> void:
	var card := Rect2(Vector2(PANEL_X + 24, 92), Vector2(PANEL_WIDTH - 48, 140))
	canvas.draw_rect(card, Color(0.18, 0.14, 0.1, 0.95), true)
	canvas.draw_rect(card, Color(0.79, 0.64, 0.15, 0.75), false, 2.0)
	_draw_text(canvas, Vector2(card.position.x + 16, card.position.y + 48), "选择我方单位", 20, Color(0.96, 0.9, 0.72))
	_draw_text(canvas, Vector2(card.position.x + 16, card.position.y + 78), "点击地图上的蓝色单位查看立绘框、属性和战斗预览。", 13, Color(0.82, 0.74, 0.58))

static func draw_status_block(canvas: CanvasItem, player_morale: int, enemy_morale: int) -> void:
	var block := Rect2(Vector2(PANEL_X + 24, 326), Vector2(PANEL_WIDTH - 48, 72))
	canvas.draw_rect(block, Color(0.14, 0.11, 0.08, 0.92), true)
	_draw_text(canvas, Vector2(block.position.x + 16, block.position.y + 28), "士气", 14, Color(0.95, 0.84, 0.52))
	_draw_text(canvas, Vector2(block.position.x + 16, block.position.y + 52), "玩家 %d    敌方 %d" % [player_morale, enemy_morale], 14, Color(0.9, 0.86, 0.76))
	_draw_text(canvas, Vector2(block.position.x + 180, block.position.y + 52), "Enter 结束回合  Space 等待", 12, Color(0.78, 0.7, 0.56))

static func draw_log(canvas: CanvasItem, messages: Array[String]) -> void:
	var log_rect := Rect2(Vector2(PANEL_X + 24, 412), Vector2(PANEL_WIDTH - 48, 292))
	canvas.draw_rect(log_rect, Color(0.12, 0.1, 0.08, 0.94), true)
	canvas.draw_rect(log_rect, Color(0.56, 0.45, 0.28, 0.55), false, 1.0)
	_draw_text(canvas, Vector2(log_rect.position.x + 14, log_rect.position.y + 24), "战场记录", 15, Color(0.95, 0.84, 0.52))
	var line_y := log_rect.position.y + 48
	for message in messages.slice(max(0, messages.size() - 11), messages.size()):
		_draw_text(canvas, Vector2(log_rect.position.x + 14, line_y), message, 12, Color(0.9, 0.86, 0.76))
		line_y += 20

static func _draw_portrait(canvas: CanvasItem, rect: Rect2, unit) -> void:
	canvas.draw_rect(rect, Color(0.1, 0.08, 0.06), true)
	canvas.draw_rect(rect, Color(0.79, 0.64, 0.15), false, 2.0)
	var center := rect.position + rect.size * 0.5
	var body_color := _team_color(unit.team)
	canvas.draw_rect(Rect2(center + Vector2(-24, 8), Vector2(48, 58)), body_color, true)
	canvas.draw_circle(center + Vector2(0, -24), 18.0, body_color.lightened(0.15))
	canvas.draw_string(ThemeDB.fallback_font, center + Vector2(-18, 52), unit.display_name.substr(0, 2), HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color(0.96, 0.9, 0.72))

static func _draw_text(canvas: CanvasItem, position: Vector2, text: String, size: int, color: Color) -> void:
	canvas.draw_string(ThemeDB.fallback_font, position, text, HORIZONTAL_ALIGNMENT_LEFT, -1, size, color)

static func _preview_line(preview: Dictionary) -> String:
	var element_text := "中性"
	match preview.get("element_state", "neutral"):
		"advantage":
			element_text = "优势"
		"disadvantage":
			element_text = "劣势"
	return "%s  伤害 %d  命中 %d%%  暴击 %d%%  %s" % [
		preview.get("weapon_name", "Weapon"),
		int(preview.get("damage", 0)),
		int(preview.get("hit_chance", 0)),
		int(preview.get("crit_chance", 0)),
		element_text
	]

static func _class_label(class_id: String) -> String:
	match class_id:
		"bladeguard": return "职业：刃卫"
		"ranger": return "职业：游侠"
		"spearwall": return "职业：枪阵军"
		"scout": return "职业：斥候"
		"mage": return "职业：魔导士"
		"druid": return "职业：德鲁伊"
		"priest": return "职业：祭司"
		"engineer": return "职业：工程兵"
		_: return "职业：未知"

static func _element_label(element: String) -> String:
	match element:
		"steel": return "钢"
		"flame": return "焰"
		"verdant": return "森"
		"tide": return "潮"
		_: return element

static func _team_color(team: String) -> Color:
	if team == "enemy":
		return Color(0.45, 0.1, 0.08)
	return Color(0.12, 0.28, 0.58)
