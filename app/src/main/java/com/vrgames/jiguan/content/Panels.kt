package com.vrgames.jiguan.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.jiguan.core.Levels
import com.vrgames.jiguan.game.Game

const val PANEL_CARD = "card"
const val PANEL_INTRO = "intro"
const val PANEL_HUD = "hud"
const val PANEL_HINT = "hint"
const val PANEL_CAUGHT = "caught"
const val PANEL_RESULT = "result"
const val PANEL_SHOW = "show"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"
const val PANEL_TAG_POST = "tag_post"
const val PANEL_TAG_LEVER = "tag_lever"
const val LEVELS_ID = "Levels"

val Ink = Color(0xFFF2E9D2)
val Dim = Color(0xFFBFC7B4)
val Gold = Color(0xFFE9C46A)
val Jade = Color(0xFF8FD8C4)
val Cinnabar = Color(0xFFF08A6A)
private val ButtonBg = Color(0xFF2E4038)
private val ButtonLine = Color(0xFF8A7440)
private val GoldText = Color(0xFF241B0C)
val WindowBg = Color(0xF4141E1B)

/** The inner size of each thick bronze frame in assets/panels: the text must fit inside. */
val FRAME_SIZE = mapOf(
    PANEL_CARD to (0.5f to 0.36f),
    PANEL_INTRO to (0.56f to 0.42f),
    PANEL_RESULT to (0.5f to 0.38f),
    PANEL_HUD to (0.34f to 0.4f),
    PANEL_HINT to (0.36f to 0.2f),
    PANEL_CAUGHT to (0.4f to 0.2f),
    PANEL_MENU to (0.34f to 0.4f),
)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

/** Text on the dark lacquer inside a bronze frame; the frame model is added by the stage. */
@Composable
private fun Framed(c: PhysicalLengthConverter, id: String, content: @Composable () -> Unit) {
    val (w, h) = FRAME_SIZE.getValue(id)
    Column(
        Modifier.width(c.m(w)).height(c.m(h)).padding(horizontal = c.m(0.018f), vertical = c.m(0.01f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.008f), Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** The step that moves the game on: the one gold button on a panel. */
@Composable
fun GoldButton(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    // Cast gold: light along the top, deeper below, a bright hairline round it (2026-11-13: the 2D UI was plain).
    Text(
        text,
        Modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFFFFE6A3), Gold, Color(0xFFB98A2E))))
            .border(c.m(0.002f), Color(0xFFFFF4D6), shape).clickable { onClick() }
            .padding(horizontal = c.m(0.032f), vertical = c.m(0.015f)),
        fontSize = c.sz(0.026f),
        color = GoldText,
    )
}

@Composable
fun PlainButton(c: PhysicalLengthConverter, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(
        text,
        Modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF3B5246), ButtonBg, Color(0xFF223029))))
            .border(c.m(0.0015f), if (enabled) ButtonLine else Color(0xFF4A4636), shape).clickable(enabled = enabled) { onClick() }
            .padding(horizontal = c.m(0.016f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.019f),
        color = if (enabled) Ink else Dim,
    )
}

/** A title between two gold rules with a lozenge at their ends, like the bands cast on a bronze. */
@Composable
private fun Title(c: PhysicalLengthConverter, text: String, color: Color = Gold, size: Float = 0.032f) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.01f))) {
        Rule(c, flip = false)
        Text(text, fontSize = c.sz(size), color = color, textAlign = TextAlign.Center)
        Rule(c, flip = true)
    }

@Composable
private fun Rule(c: PhysicalLengthConverter, flip: Boolean) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!flip) Text("◆", fontSize = c.sz(0.011f), color = Gold)
        androidx.compose.foundation.layout.Box(
            Modifier.width(c.m(0.045f)).height(c.m(0.0018f))
                .background(Brush.horizontalGradient(if (flip) listOf(Gold, Color(0x00E9C46A)) else listOf(Color(0x00E9C46A), Gold))),
        )
        if (flip) Text("◆", fontSize = c.sz(0.011f), color = Gold)
    }

/** A small name plate over a part on the desk: its name in gold, how to work it below. */
@Composable
fun NameTag(c: PhysicalLengthConverter, name: String, how: String, note: String? = null) {
    val shape = RoundedCornerShape(c.m(0.008f))
    Column(
        Modifier.clip(shape).background(WindowBg).border(c.m(0.0015f), ButtonLine, shape).padding(horizontal = c.m(0.012f), vertical = c.m(0.007f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(c.m(0.003f)),
    ) {
        Text(name, fontSize = c.sz(0.024f), color = Gold, textAlign = TextAlign.Center)
        if (note != null) Text(note, fontSize = c.sz(0.0145f), color = Cinnabar, textAlign = TextAlign.Center)
        else Text(how, fontSize = c.sz(0.0135f), color = Ink, textAlign = TextAlign.Center)
    }
}

/** The steps taken against the fewest: one bead per step, gold up to the par, cinnabar past it. */
@Composable
private fun StepBeads(c: PhysicalLengthConverter, steps: Int, par: Int) {
    val total = maxOf(par, steps).coerceAtMost(24)
    Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.003f))) {
        for (i in 0 until total) {
            val col = when { i >= steps -> Color(0x553B5246); i < par -> Gold; else -> Cinnabar }
            androidx.compose.foundation.layout.Box(Modifier.width(c.m(0.009f)).height(c.m(0.009f)).clip(RoundedCornerShape(c.m(0.0045f))).background(col))
        }
    }
}

@Composable
private fun Body(c: PhysicalLengthConverter, text: String, color: Color = Ink, size: Float = 0.02f) =
    Text(text, Modifier.fillMaxWidth(), fontSize = c.sz(size), color = color, textAlign = TextAlign.Center)

@Composable
fun CardPanel(c: PhysicalLengthConverter, openLevels: () -> Unit) {
    Game.version
    val p = Game.progress
    Framed(c, PANEL_CARD) {
        Title(c, "机关棋阵", size = 0.046f)
        Body(c, "一只三层的青铜机关匣，共 12 关。棋子不会被吃掉，只会掉到下一层。")
        Body(c, "把「枢」送到顶层的天枢位。", Jade)
        Body(c, "已得 ${p.stars} / ${p.maxStars} 颗星", Dim, 0.018f)
        GoldButton(c, if (p.stars == 0) "开始第 1 关" else "继续") { Game.start() }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.012f))) {
            PlainButton(c, "选关和奖杯") { openLevels() }
            if (p.gold) PlainButton(c, "隐藏演出") { Game.show() }
        }
    }
}

@Composable
fun IntroPanel(c: PhysicalLengthConverter) {
    Game.version
    val l = Game.level
    Framed(c, PANEL_INTRO) {
        Text("第 ${Game.index + 1} / ${Levels.ALL.size} 关 · 第 ${l.chapter} 章 ${Levels.CHAPTERS[l.chapter - 1]}", fontSize = c.sz(0.019f), color = Dim)
        Title(c, l.title)
        Body(c, l.brief)
        Body(c, "目标：把枢送到天枢位。最少 ${l.par} 步。", Jade)
        GoldButton(c, "开始这一关") { Game.begin() }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.012f))) {
            if (Levels.ALL.first { it.chapter == l.chapter } === l) PlainButton(c, if (Game.speaking) "停止旁白" else "听旁白") { Game.replayChapter() }
            PlainButton(c, "返回") { Game.toCard() }
        }
    }
}

@Composable
fun HudPanel(c: PhysicalLengthConverter) {
    Game.version
    val p = Game.play
    Framed(c, PANEL_HUD) {
        Text("第 ${Game.index + 1} / ${Levels.ALL.size} 关 · ${Game.level.title}", fontSize = c.sz(0.021f), color = Gold)
        Text("${p?.steps ?: 0} 步（最少 ${Game.level.par} 步）", fontSize = c.sz(0.02f), color = Ink)
        StepBeads(c, p?.steps ?: 0, Game.level.par)
        Text(if (Game.selected == null) "捏住一枚棋子" else "拿到亮着的格子上松手，或捏一下那一格", fontSize = c.sz(0.017f), color = Jade, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            PlainButton(c, "悔一步", enabled = p?.canUndo == true) { Game.undo() }
            PlainButton(c, "重来") { Game.retry() }
            PlainButton(c, "菜单") { Game.menuHelp = false; Game.menuOpen = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            PlainButton(c, "提示 ${p?.hintsAsked ?: 0}/3") { Game.askHint() }
            PlainButton(c, if (Game.spread) "合拢" else "拉开") { Game.spread = !Game.spread }
        }
        // The lift and the turn (device 2026-11-13); the same as the control post on the desk at the front left.
        Text(
            if (Game.liftUseless) "升降：这一关只有一层，用不着" + (if (Game.firstLiftLevel > 0) "（第 ${Game.firstLiftLevel} 关起用得上）" else "")
            else "升降：第 ${Game.liftStep + 1} / ${Game.liftSteps.size} 档",
            fontSize = c.sz(0.015f), color = Dim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            PlainButton(c, "升一层 ▲", enabled = Game.liftStep < Game.liftSteps.lastIndex) { Game.lift(1) }
            PlainButton(c, "降一层 ▼", enabled = Game.liftStep > 0) { Game.lift(-1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            PlainButton(c, "◀ 向左转") { Game.turn(-1) }
            PlainButton(c, "向右转 ▶") { Game.turn(1) }
        }
    }
}

@Composable
fun HintPanel(c: PhysicalLengthConverter) {
    Game.version
    val p = Game.play
    Framed(c, PANEL_HINT) {
        Text("提示 ${p?.hintsAsked ?: 0} / 3", fontSize = c.sz(0.018f), color = Dim)
        Body(c, Game.hint ?: "")
        if ((p?.hintsAsked ?: 0) < 3) PlainButton(c, "下一条提示") { Game.askHint() }
    }
}

@Composable
fun CaughtPanel(c: PhysicalLengthConverter) {
    Framed(c, PANEL_CAUGHT) {
        Title(c, "枢被守阵人碰到了", Cinnabar, 0.028f)
        GoldButton(c, "悔一步") { Game.undo() }
        PlainButton(c, "重来") { Game.retry() }
    }
}

@Composable
fun ResultPanel(c: PhysicalLengthConverter, openLevels: () -> Unit) {
    Game.version
    val p = Game.play
    val s = p?.stars() ?: 0
    val nowTrophies = Game.progress.trophies
    Framed(c, PANEL_RESULT) {
        Title(c, "解开了", size = 0.036f)
        Text("★".repeat(s) + "☆".repeat(3 - s), fontSize = c.sz(0.04f), color = Gold)
        Body(c, "${p?.steps ?: 0} 步，这一关最少 ${Game.level.par} 步。")
        if (nowTrophies > Game.trophiesBefore) Body(c, "得到${listOf("", "铜", "银", "金")[nowTrophies]}杯！" + if (nowTrophies == 3) "隐藏演出已开启。" else "", Jade)
        if (Game.hasNext) GoldButton(c, "下一关：第 ${Game.index + 2} / ${Levels.ALL.size} 关") { Game.next() } else GoldButton(c, "全部解开了，回到开头") { Game.toCard() }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.012f))) {
            PlainButton(c, "再来一次") { Game.retry() }
            PlainButton(c, "选关") { openLevels() }
            if (Game.progress.gold) PlainButton(c, "隐藏演出") { Game.show() }
        }
    }
}

@Composable
fun ShowPanel(c: PhysicalLengthConverter) {
    PlainButton(c, "结束演出，返回") { Game.endShow() }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        Game.note ?: "",
        Modifier.width(c.m(0.42f)).clip(shape).background(Color(0xEE14201C)).border(c.m(0.0015f), ButtonLine, shape).padding(c.m(0.012f)),
        fontSize = c.sz(0.019f),
        color = Ink,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, openLevels: () -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    Game.version
    Framed(c, PANEL_MENU) {
        Title(c, "菜单", size = 0.026f)
        if (Game.menuHelp) {
            // Device 2026-11-14: the lift's post and the lever needed words.
            Text("升降 · 旋转（左前方的铜柱）", fontSize = c.sz(0.017f), color = Gold)
            Text(
                "用两根手指对准金色箭头捏一下（捏合后松开，不要拖）：机关匣升一层 / 降一层，把要看的那一层送到眼前。\n" +
                    "同样捏一下绿色箭头：整只机关匣向左 / 向右转 45°。\n" +
                    "升降只在机关匣有好几层的关卡用得上" + (if (Game.firstLiftLevel > 0) "（从第 ${Game.firstLiftLevel} 关起）" else "") +
                    "；只有一层、或已经升到最高 / 降到最低时，那个金箭头会变暗，捏了铜柱旁会有提示。\n" +
                    "转动每一关都能用。也可以捏住铜柱，往上下拨升降、往左右拨转动。",
                fontSize = c.sz(0.0145f), color = Ink,
            )
            Text("开合（右边的拉杆）", fontSize = c.sz(0.017f), color = Gold)
            Text("捏住拉杆往上抬，三层拉开；往下压，合拢。\n操作面板上的「升一层」「向左转」「合拢 / 拉开」按钮作用相同。", fontSize = c.sz(0.0145f), color = Ink)
            PlainButton(c, "返回") { Game.menuHelp = false }
        } else if (Game.confirmWipe) {
            Body(c, "清除全部星星和奖杯？", Cinnabar)
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f))) {
                PlainButton(c, "清除") { Game.confirmWipe = false; Game.menuOpen = false; Game.wipe() }
                PlainButton(c, "返回") { Game.confirmWipe = false }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "选关") { Game.menuOpen = false; openLevels() }
                PlainButton(c, "操作说明") { Game.menuHelp = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "摆到我正前方") { faceMe() }
                PlainButton(c, "清除进度") { Game.confirmWipe = true }
            }
            // Seated or standing, tall or short: the desk can come up or down (2026-11-13).
            Text("机关匣高度：${Game.heightLabel}", fontSize = c.sz(0.017f), color = Jade)
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
                PlainButton(c, "低一点", enabled = Game.heightAdjust > -Game.HEIGHT_MAX + 1e-6) { Game.nudgeHeight(-Game.HEIGHT_STEP) }
                PlainButton(c, "高一点", enabled = Game.heightAdjust < Game.HEIGHT_MAX - 1e-6) { Game.nudgeHeight(Game.HEIGHT_STEP) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "退出游戏") { quit() }
                PlainButton(c, "返回") { Game.menuOpen = false }
            }
            Text("版本 $version", fontSize = c.sz(0.014f), color = Dim)
        }
    }
}

/** 选关和奖杯: the trophies, then every level as a bronze tile with its stars, by chapter. */
@Composable
fun LevelsWindow() {
    com.vrgames.jiguan.platform.AppExit.CloseWithStage(LEVELS_ID)
    Game.version
    val p = Game.progress
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C2A25), Color(0xFF101815), Color(0xFF0B100E))))
            .padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Seal("阵")
                Text("选关和奖杯", fontSize = 32.sp, color = Gold)
            }
            Text(
                "返回",
                Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3B5246), ButtonBg))).border(1.dp, ButtonLine, RoundedCornerShape(10.dp))
                    .clickable { Game.closeWindow() }.padding(horizontal = 24.dp, vertical = 10.dp),
                fontSize = 22.sp,
                color = Ink,
            )
        }
        Text("总星数 ${p.stars} / ${p.maxStars}", fontSize = 22.sp, color = Ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Trophy(Modifier.weight(1f), "铜杯", "全部解开", p.bronze, Color(0xFFD08A4E))
            Trophy(Modifier.weight(1f), "银杯", "前两章全部三星", p.silver, Color(0xFFD9DEE2))
            Trophy(Modifier.weight(1f), "金杯", "全部三星 · 开启隐藏演出", p.gold, Gold)
        }
        val chapters = Levels.ALL.withIndex().groupBy { it.value.chapter }
        for ((ch, items) in chapters) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.width(6.dp).height(26.dp).background(Jade))
                Text("第 $ch 章 · ${Levels.CHAPTERS[ch - 1]}", fontSize = 24.sp, color = Jade)
            }
            for (row in items.chunked(3)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((k, l) in row) {
                    val open = p.open(l)
                    val st = p.best[l.id] ?: 0
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (open) Brush.verticalGradient(listOf(Color(0xFF34483F), Color(0xFF1F2C27))) else Brush.verticalGradient(listOf(Color(0xFF1E2522), Color(0xFF161B19))))
                            .border(1.dp, if (st == 3) Gold else ButtonLine, RoundedCornerShape(12.dp))
                            .clickable(enabled = open) { Game.closeWindow(); Game.intro(l) }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("第 ${k + 1} 关", fontSize = 18.sp, color = Dim)
                        Text(l.title, fontSize = 23.sp, color = if (open) Ink else Dim)
                        Text(if (open) "★".repeat(st) + "☆".repeat(3 - st) else "未开放", fontSize = 22.sp, color = Gold)
                    }
                }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Seal(ch: String) =
    Text(
        ch,
        Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFB0352A)).border(2.dp, Color(0xFFE8B4A0), RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
        fontSize = 24.sp,
        color = Color(0xFFFBEADF),
    )

@Composable
private fun Trophy(modifier: Modifier, name: String, rule: String, got: Boolean, metal: Color) =
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (got) Color(0x332E4038) else Color(0x1A2E4038))
            .border(1.dp, if (got) metal else Color(0xFF3A3F38), RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(name, fontSize = 22.sp, color = if (got) metal else Dim)
        Text(rule, fontSize = 16.sp, color = if (got) Ink else Dim, textAlign = TextAlign.Center)
        Text(if (got) "已得" else "未得", fontSize = 16.sp, color = if (got) Jade else Dim)
    }
