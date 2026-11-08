#!/usr/bin/env python3
"""Rule prototype for 机关棋阵: exact rules plus a breadth-first solver, to test sample levels before the design doc.

Cells: . floor, # wall, ' ' gap, T trapdoor (open when linked), B bridge (up when linked), P plate (while a heavy
piece stands on it), L latch plate (stays on once pressed), U lift, W wheel (turns its layer 90 degrees clockwise when a
heavy piece enters), G goal.
Pieces: K 枢 one step; H 杵 heavy, one step; S 梭 light, slides until stopped; patrols are heavy and walk straight,
turning back when blocked. Links are stored by the cell's own coordinates on its layer, so they turn with the layer.
"""
from collections import deque

N = 5
DIRS = {"E": (1, 0), "W": (-1, 0), "N": (0, -1), "S": (0, 1)}
BACK = {"E": "W", "W": "E", "N": "S", "S": "N"}


def to_world(x, y, r):
    for _ in range(r % 4):
        x, y = N - 1 - y, x
    return x, y


def to_local(x, y, r):
    for _ in range(r % 4):
        x, y = y, N - 1 - x
    return x, y


class Level:
    def __init__(self, name, layers, links, pieces, patrols=()):
        self.name = name
        self.layers = layers  # layers[0] is the bottom; each is a list of 5 rows of 5 characters
        self.links = links  # (layer, x, y) of a plate -> list of (layer, x, y)
        self.start = (
            tuple(sorted(pieces)),  # (id, layer, x, y); id's first letter is the kind
            tuple(patrols),  # (layer, x, y, dir)
            (0, 0, 0),  # rotation of each layer
            frozenset(),  # latched plates
        )

    def cell(self, layer, x, y, rot):
        lx, ly = to_local(x, y, rot[layer])
        return self.layers[layer][ly][lx], (layer, lx, ly)


def heavy_cells(pieces, patrols):
    cells = [(l, x, y) for i, l, x, y in pieces if i[0] == "H"]
    return cells + [(l, x, y) for l, x, y, _ in patrols]


def active(level, pieces, patrols, rot, latched):
    """Linked cells that are on now, and the latches including any newly pressed."""
    latched = set(latched)
    on = set()
    for l, x, y in heavy_cells(pieces, patrols):
        c, cid = level.cell(l, x, y, rot)
        if c == "L":
            latched.add(cid)
        elif c == "P":
            on.update(level.links.get(cid, []))
    for cid in latched:
        on.update(level.links.get(cid, []))
    return on, frozenset(latched)


def occupied(pieces, patrols, l, x, y):
    return any(p[1:] == (l, x, y) for p in pieces) or any(p[:3] == (l, x, y) for p in patrols)


def walkable(level, l, x, y, rot, on):
    if not (0 <= x < N and 0 <= y < N):
        return False
    c, cid = level.cell(l, x, y, rot)
    if c in "# ":
        return False
    return c != "B" or cid in on


def plugged(level, pieces, patrols, l, x, y, rot, on):
    """An open trapdoor with a piece right below it: nobody can stand on it, so it cannot be crossed."""
    c, cid = level.cell(l, x, y, rot)
    return l > 0 and c == "T" and cid in on and occupied(pieces, patrols, l - 1, x, y)


def turn_layer(level, pieces, patrols, rot, l):
    """Everything on the layer turns with it."""
    def spin(x, y):
        return to_world(x, y, 1)
    new_rot = tuple((r + 1) % 4 if i == l else r for i, r in enumerate(rot))
    new_pieces = tuple(sorted((i, a, *spin(x, y)) if a == l else (i, a, x, y) for i, a, x, y in pieces))
    turn = {"N": "E", "E": "S", "S": "W", "W": "N"}
    new_patrols = tuple((a, *spin(x, y), turn[d]) if a == l else (a, x, y, d) for a, x, y, d in patrols)
    return new_pieces, new_patrols, new_rot


def settle(level, pieces, patrols, rot, latched, entered=()):
    """Falls, lifts, latches and wheels until nothing changes. Returns the settled state."""
    for l, x, y in entered:
        if level.cell(l, x, y, rot)[0] == "W":
            pieces, patrols, rot = turn_layer(level, pieces, patrols, rot, l)
    for _ in range(40):
        on, latched = active(level, pieces, patrols, rot, latched)
        moved = False
        ps = list(pieces)
        for n, (i, l, x, y) in enumerate(ps):
            c, cid = level.cell(l, x, y, rot)
            falls = l > 0 and ((c == "T" and cid in on) or (c == "B" and cid not in on))
            if falls and level.layers[l - 1] and walkable_below(level, l - 1, x, y, rot) and not occupied(ps, patrols, l - 1, x, y):
                ps[n] = (i, l - 1, x, y)
                moved = True
            elif c == "U" and l < 2 and walkable(level, l + 1, x, y, rot, on) and not occupied(ps, patrols, l + 1, x, y) and not open_door(level, l + 1, x, y, rot, on):
                ps[n] = (i, l + 1, x, y)
                moved = True
        pieces = tuple(sorted(ps))
        pa = list(patrols)
        for n, (l, x, y, d) in enumerate(pa):
            c, cid = level.cell(l, x, y, rot)
            falls = l > 0 and ((c == "T" and cid in on) or (c == "B" and cid not in on))
            if falls and walkable_below(level, l - 1, x, y, rot) and not occupied(pieces, pa, l - 1, x, y):
                pa[n] = (l - 1, x, y, d)
                moved = True
        patrols = tuple(pa)
        if not moved:
            return pieces, patrols, rot, latched
    raise RuntimeError("mechanism loop in " + level.name)


def open_door(level, l, x, y, rot, on):
    """A lift does not raise a piece into an open trapdoor: it would drop straight back."""
    c, cid = level.cell(l, x, y, rot)
    return c == "T" and cid in on


def walkable_below(level, l, x, y, rot):
    return level.cell(l, x, y, rot)[0] != "#"


def player_moves(level, state):
    pieces, patrols, rot, latched = state
    on, _ = active(level, pieces, patrols, rot, latched)
    for p in pieces:
        i, l, x, y = p
        rest = tuple(q for q in pieces if q != p)
        for d, (dx, dy) in DIRS.items():
            nx, ny = x + dx, y + dy
            if not walkable(level, l, nx, ny, rot, on) or occupied(rest, patrols, l, nx, ny) or plugged(level, rest, patrols, l, nx, ny, rot, on):
                continue
            if i[0] == "S":  # slides on until something stops it; an open trapdoor catches it
                while True:
                    c, cid = level.cell(l, nx, ny, rot)
                    if c == "T" and cid in on:
                        break
                    ax, ay = nx + dx, ny + dy
                    if not walkable(level, l, ax, ay, rot, on) or occupied(rest, patrols, l, ax, ay) or plugged(level, rest, patrols, l, ax, ay, rot, on):
                        break
                    nx, ny = ax, ay
            yield f"{i} L{l + 1}({x},{y})->({nx},{ny})", (i, l, nx, ny), rest


def step(level, state, moved, rest):
    """One full turn. Returns (next state, 'win' / 'lost' / None)."""
    _, patrols, rot, latched = state
    pieces = tuple(sorted(rest + (moved,)))
    entered = [(moved[1], moved[2], moved[3])] if moved[0][0] == "H" else []
    pieces, patrols, rot, latched = settle(level, pieces, patrols, rot, latched, entered)
    if any(p[0][0] == "K" and level.cell(p[1], p[2], p[3], rot)[0] == "G" for p in pieces):
        return (pieces, patrols, rot, latched), "win"
    for n in range(len(patrols)):
        l, x, y, d = patrols[n]
        on, _ = active(level, pieces, patrols, rot, latched)
        others = patrols[:n] + patrols[n + 1:]
        target = None
        for dd in (d, BACK[d]):
            dx, dy = DIRS[dd]
            nx, ny = x + dx, y + dy
            king = any(p[0][0] == "K" and p[1:] == (l, nx, ny) for p in pieces)
            if king:
                return (pieces, patrols, rot, latched), "lost"
            if walkable(level, l, nx, ny, rot, on) and not occupied(pieces, others, l, nx, ny) and not plugged(level, pieces, others, l, nx, ny, rot, on):
                target = (l, nx, ny, dd)
                break
        if target:
            patrols = patrols[:n] + (target,) + patrols[n + 1:]
            pieces, patrols, rot, latched = settle(level, pieces, patrols, rot, latched, [target[:3]])
    return (pieces, patrols, rot, latched), None


def solve(level, cap=2000000):
    seen = {level.start: None}
    q = deque([level.start])
    while q and len(seen) < cap:
        s = q.popleft()
        for label, moved, rest in player_moves(level, s):
            n, result = step(level, s, moved, rest)
            if result == "lost" or n in seen:
                continue
            seen[n] = (s, label)
            if result == "win":
                path = []
                while seen[n]:
                    n, lab = seen[n]
                    path.append(lab)
                return path[::-1], len(seen)
            q.append(n)
    return None, len(seen)


def report(level):
    path, states = solve(level)
    print(f"{level.name}: {'par ' + str(len(path)) if path else 'NO SOLUTION'}, {states} states searched")
    if path:
        for n, m in enumerate(path, 1):
            print(f"  {n:2} {m}")
    return path


EMPTY = ["#####"] * 5

# The chain from the proposal: the heavy piece on the top plate opens a trapdoor, the patrol drops onto a latch plate on
# the middle layer, and that raises the bridge the key piece needs.
def chain(with_link=True):
    links = {(2, 1, 3): [(2, 2, 0)]}
    if with_link:
        links[(1, 2, 0)] = [(1, 2, 2)]
    return Level(
        "第一例·连锁掉层" + ("" if with_link else "（拆掉闩板联动）"),
        [EMPTY,
         ["##L##",
          "#####",
          "..B.U",
          "#####",
          "#####"],
         ["G.T..",
          "###.#",
          "#....",
          "#P..#",
          "#...#"]],
        links,
        [("K", 1, 0, 2), ("H", 2, 2, 4)],
        [(2, 0, 0, "E")],
    )


# A turning layer: the lift only works once the middle layer has turned it under the open floor above.
def wheel():
    return Level(
        "第二例·转层对位",
        [EMPTY,
         ["..#..",
          "..W..",
          "#####",
          ".....",
          "..U.."],
         ["G.T..",
          "#####",
          "....#",
          "#####",
          "#####"]],
        {},
        [("K", 1, 2, 3), ("H", 1, 2, 0)],
    )


if __name__ == "__main__":
    report(chain())
    report(chain(with_link=False))
    report(wheel())
