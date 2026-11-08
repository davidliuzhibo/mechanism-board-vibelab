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
    PANEL_HUD to (0.34f to 0.32f),
    PANEL_HINT to (0.36f to 0.2f),
    PANEL_CAUGHT to (0.4f to 0.2f),
    PANEL_MENU to (0.34f to 0.32f),
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
    Text(
        text,
        Modifier.clip(shape).background(Gold).border(c.m(0.002f), Color(0xFFFFF0C8), shape).clickable { onClick() }
            .padding(horizontal = c.m(0.03f), vertical = c.m(0.014f)),
        fontSize = c.sz(0.026f),
        color = GoldText,
    )
}

@Composable
fun PlainButton(c: PhysicalLengthConverter, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(
        text,
        Modifier.clip(shape).background(ButtonBg).border(c.m(0.0015f), ButtonLine, shape).clickable(enabled = enabled) { onClick() }
            .padding(horizontal = c.m(0.016f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.019f),
        color = if (enabled) Ink else Dim,
    )
}

@Composable
private fun Title(c: PhysicalLengthConverter, text: String, color: Color = Gold, size: Float = 0.032f) =
    Text(text, fontSize = c.sz(size), color = color, textAlign = TextAlign.Center)

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
        Text(if (Game.selected == null) "捏住一枚棋子" else "拿到亮着的格子上松手，或捏一下那一格", fontSize = c.sz(0.017f), color = Jade, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            PlainButton(c, "悔一步", enabled = p?.canUndo == true) { Game.undo() }
            PlainButton(c, "重来") { Game.retry() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            PlainButton(c, "提示 ${p?.hintsAsked ?: 0}/3") { Game.askHint() }
            PlainButton(c, if (Game.spread) "合拢" else "拉开") { Game.spread = !Game.spread }
        }
        PlainButton(c, "菜单") { Game.menuOpen = true }
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
        if (Game.confirmWipe) {
            Body(c, "清除全部星星和奖杯？", Cinnabar)
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f))) {
                PlainButton(c, "清除") { Game.confirmWipe = false; Game.menuOpen = false; Game.wipe() }
                PlainButton(c, "返回") { Game.confirmWipe = false }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "选关") { Game.menuOpen = false; openLevels() }
                PlainButton(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "摆到我正前方") { faceMe() }
                PlainButton(c, "清除进度") { Game.confirmWipe = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "退出游戏") { quit() }
                PlainButton(c, "返回") { Game.menuOpen = false }
            }
            Text("版本 $version", fontSize = c.sz(0.014f), color = Dim)
        }
    }
}

/** 选关和奖杯: every level with its stars, by chapter. */
@Composable
fun LevelsWindow() {
    Game.version
    val p = Game.progress
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("选关和奖杯", fontSize = 32.sp, color = Gold)
            Text(
                "返回",
                Modifier.clip(RoundedCornerShape(10.dp)).background(ButtonBg).clickable { Game.closeWindow() }.padding(horizontal = 22.dp, vertical = 10.dp),
                fontSize = 22.sp,
                color = Ink,
            )
        }
        Text("总星数 ${p.stars} / ${p.maxStars}", fontSize = 22.sp, color = Ink)
        Text("铜杯：全部解开${if (p.bronze) "（已得）" else ""}", fontSize = 20.sp, color = if (p.bronze) Jade else Dim)
        Text("银杯：前两章全部三星${if (p.silver) "（已得）" else ""}", fontSize = 20.sp, color = if (p.silver) Jade else Dim)
        Text("金杯：全部三星，开启隐藏演出${if (p.gold) "（已得）" else ""}", fontSize = 20.sp, color = if (p.gold) Jade else Dim)
        for ((k, l) in Levels.ALL.withIndex()) {
            if (k == 0 || Levels.ALL[k - 1].chapter != l.chapter) Text("第 ${l.chapter} 章 · ${Levels.CHAPTERS[l.chapter - 1]}", fontSize = 24.sp, color = Jade)
            val open = p.open(l)
            val st = p.best[l.id] ?: 0
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x262E4038))
                    .clickable(enabled = open) { Game.closeWindow(); Game.intro(l) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${k + 1}. ${l.title}", fontSize = 24.sp, color = if (open) Ink else Dim)
                Text(if (open) "★".repeat(st) + "☆".repeat(3 - st) else "未开放", fontSize = 23.sp, color = Gold)
            }
        }
    }
}
