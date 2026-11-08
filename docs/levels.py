#!/usr/bin/env python3
"""The 12 levels of 机关棋阵, each checked by the solver.

For every level: it has a solution, the shortest length (par), and removing each piece or link it is meant to teach makes
it unsolvable or longer. layers[0] is the bottom. Rows run from y=0 to y=4.
"""
import sys
sys.path.insert(0, ".")
from solver import Level, solve

E = ["#####"] * 5


def L1():
    return Level("1 压板与桥", [E, E, [
        "#####",
        "#####",
        "..B.G",
        ".####",
        ".P###"]], {(2, 1, 4): [(2, 2, 2)]}, [("K", 2, 0, 2), ("H", 2, 0, 4)])


def L2():
    return Level("2 两枚杵接力", [E, E, [
        "#####",
        "####G",
        "..B.B",
        "P.#P#",
        ".####"]], {(2, 0, 3): [(2, 2, 2)], (2, 3, 3): [(2, 4, 2)]},
        [("K", 2, 0, 2), ("H1", 2, 0, 4), ("H2", 2, 1, 3)])


def L3():
    return Level("3 引它入门", [E, [
        "###.#", "#####", "#####", "#####", "#####"], [
        "G..T.",
        "####.",
        "####.",
        "#P..#",
        "#####"]], {(2, 1, 3): [(2, 3, 0)]}, [("K", 2, 4, 2), ("H", 2, 2, 3)], [(2, 1, 0, "E")])


def L4():
    return Level("4 连杆穿层", [[
        "#####",
        "#####",
        "..B.U",
        "#####",
        "###P."], [
        "#####",
        "#####",
        "U.B..",
        "#####",
        "#####"], [
        "#####",
        "#####",
        ".G###",
        "#####",
        ".P###"]], {(2, 1, 4): [(0, 2, 2)], (0, 3, 4): [(1, 2, 2)]},
        [("K", 0, 0, 2), ("H1", 2, 0, 4), ("H2", 0, 4, 4)])


def L5():
    return Level("5 掉层连锁", [E, [
        "##L##",
        "#####",
        "..B.U",
        "#####",
        "#####"], [
        "G.T..",
        "###.#",
        "#....",
        "#P..#",
        "#...#"]], {(2, 1, 3): [(2, 2, 0)], (1, 2, 0): [(1, 2, 2)]}, [("K", 1, 0, 2), ("H", 2, 2, 4)], [(2, 0, 0, "E")])


def L6():
    return Level("6 梭压守阵人", [E, [
        "#####", "#####", "#####", "#####", "#..##"], [
        "P....",
        "#.###",
        "#.###",
        ".S###",
        ".BB.G"]], {(2, 0, 0): [(2, 1, 4), (2, 2, 4)]}, [("K", 2, 0, 4), ("S", 2, 1, 3)], [(2, 3, 0, "W")])


def L7():
    return Level("7 转层对位", [E, [
        "..#..",
        "..W..",
        "#####",
        "..#..",
        "..U.."], [
        "#####",
        "#####",
        "###G.",
        "#####",
        "#####"]], {}, [("K", 1, 2, 4 - 1), ("H", 1, 2, 0)])


def L8():
    return Level("8 转层改落点", [E, [
        "###WH",
        "U####",
        ".####",
        "...##",
        "#####"], [
        "###.G",
        "#####",
        ".T###",
        "#P###",
        "#.###"]], {(2, 1, 3): [(2, 1, 2)]}, [("K", 2, 0, 2), ("H1", 2, 1, 4), ("H2", 1, 4, 0)])


def L9():
    return Level("9 它在转层", [E, [
        ".W.##",
        "#####",
        "#####",
        "..#..",
        "..U.."], [
        "#####",
        "#####",
        "#G.B.",
        "####P",
        "####."]], {(2, 4, 3): [(2, 3, 2)]}, [("K", 1, 1, 3), ("H", 2, 4, 4)], [(1, 2, 0, "W")])


BOTTOM = ["#####", "#####", "..B.U", "#####", "#####"]


def L10():
    return Level("10 先后有序", [BOTTOM, [
        "##L##",
        "#####",
        "U.B..",
        "#####",
        "#####"], [
        "G.T..",
        "###.#",
        ".....",
        "#P.P#",
        "#...#"]], {(2, 1, 3): [(2, 2, 0)], (2, 3, 3): [(0, 2, 2)], (1, 2, 0): [(1, 2, 2)]},
        [("K", 0, 0, 2), ("H", 2, 2, 3)], [(2, 0, 0, "E")])


def L11():
    return Level("11 卡住再转", [E, [
        "HW#.#",
        "##.##",
        "##.##",
        "##.##",
        "##.U#"], [
        "P....",
        "#.###",
        "#.#TB",
        ".S##B",
        "G##.."]], {(2, 0, 0): [(2, 3, 2), (2, 4, 2), (2, 4, 3)]},
        [("K", 2, 4, 4), ("S", 2, 1, 3), ("H", 1, 0, 0)], [(2, 3, 0, "W")])


def L12():
    return Level("12 机关匣开", [[
        "#####",
        "#####",
        "..B..",
        "####U",
        "#####"], [
        "W.#.#",
        "##..#",
        "L#B##",
        "##.##",
        "##U##"], [
        "G.T..",
        "###.#",
        ".....",
        "#P.P#",
        "#####"]], {(2, 1, 3): [(2, 2, 0)], (2, 3, 3): [(0, 2, 2)], (1, 0, 2): [(1, 2, 2)]},
        [("K", 0, 0, 2), ("H1", 2, 2, 3), ("H2", 1, 1, 0)], [(2, 0, 0, "E")])


LEVELS = [L1, L2, L3, L4, L5, L6, L7, L8, L9, L10, L11, L12]
# Cells a level cannot do without: replacing one with plain floor must make it unsolvable or clearly longer.
KEY_CELLS = {
    L7: [(1, 2, 1)], L8: [(1, 3, 0)], L9: [(1, 1, 0)], L11: [(1, 1, 0)], L12: [(1, 0, 0)],
}


def without(make, drop=None, unlink=None, replace=None):
    lv = make()
    pieces, patrols, rot, latched = lv.start
    if drop:
        pieces = tuple(p for p in pieces if p[0] != drop)
    if unlink:
        lv.links = {k: v for k, v in lv.links.items() if k != unlink}
    if replace:
        (l, x, y), ch = replace
        row = lv.layers[l][y]
        lv.layers = [list(a) for a in lv.layers]
        lv.layers[l][y] = row[:x] + ch + row[x + 1:]
    lv.start = (pieces, patrols, rot, latched)
    return lv


def check(make):
    lv = make()
    path, states = solve(lv)
    print(f"{lv.name}: par {len(path) if path else 'NONE'}, states {states}")
    if not path:
        return None
    print("   " + " | ".join(m.split(" ")[0] + m.split(" ")[1] for m in path))
    par = len(path)
    for p in lv.start[0]:
        if p[0] == "K":
            continue
        q, _ = solve(without(make, drop=p[0]))
        print(f"   remove {p[0]}: {'unsolvable' if not q else 'par ' + str(len(q))}")
    for k in lv.links:
        q, _ = solve(without(make, unlink=k))
        print(f"   cut link at {k}: {'unsolvable' if not q else 'par ' + str(len(q))}")
    for layer, x, y in KEY_CELLS.get(make, []):
        q, _ = solve(without(make, replace=((layer, x, y), ".")))
        print(f"   wheel at L{layer} ({x},{y}) to floor: {'unsolvable' if not q else 'par ' + str(len(q))}")
    return par


if __name__ == "__main__":
    for m in LEVELS:
        check(m)
