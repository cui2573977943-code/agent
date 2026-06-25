class_name TacticalGridMap
extends RefCounted

var width := 20
var height := 15
var blocked_cells := {}

func _init(grid_width := 20, grid_height := 15) -> void:
	width = grid_width
	height = grid_height

func is_inside(cell: Vector2i) -> bool:
	return cell.x >= 0 and cell.y >= 0 and cell.x < width and cell.y < height

func is_blocked(cell: Vector2i) -> bool:
	return blocked_cells.has(cell)

func distance(a: Vector2i, b: Vector2i) -> int:
	return abs(a.x - b.x) + abs(a.y - b.y)

func neighbors(cell: Vector2i) -> Array[Vector2i]:
	var result: Array[Vector2i] = []
	var directions := [
		Vector2i.RIGHT,
		Vector2i.LEFT,
		Vector2i.DOWN,
		Vector2i.UP
	]
	for direction in directions:
		var next: Vector2i = cell + direction
		if is_inside(next) and not is_blocked(next):
			result.append(next)
	return result

func get_cells_in_range(start: Vector2i, max_range: int, occupied_cells := {}) -> Array[Vector2i]:
	var result: Array[Vector2i] = []
	var frontier: Array[Vector2i] = [start]
	var cost_so_far := { start: 0 }

	while not frontier.is_empty():
		var current: Vector2i = frontier.pop_front()
		for next in neighbors(current):
			var new_cost: int = cost_so_far[current] + 1
			if new_cost > max_range:
				continue
			if occupied_cells.has(next) and next != start:
				continue
			if not cost_so_far.has(next) or new_cost < cost_so_far[next]:
				cost_so_far[next] = new_cost
				frontier.append(next)
				result.append(next)

	return result

func find_step_toward(start: Vector2i, target: Vector2i, occupied_cells := {}) -> Vector2i:
	var best_cell := start
	var best_distance := distance(start, target)

	for next in neighbors(start):
		if occupied_cells.has(next):
			continue
		var next_distance := distance(next, target)
		if next_distance < best_distance:
			best_distance = next_distance
			best_cell = next

	return best_cell
