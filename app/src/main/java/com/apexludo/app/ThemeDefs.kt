package com.apexludo.app

import android.graphics.Color

/**
 * Visual design system: six board themes (dynamic lighting engine) plus independent
 * board skins and token styles. Colours are plain ARGB ints so the renderer can cache
 * shaders cheaply.
 */
data class Theme(
    val id: String,
    val nameRes: Int,
    val bgTop: Int,
    val bgBottom: Int,
    val boardBase: Int,
    val boardEdge: Int,
    val cellFill: Int,
    val cellEdge: Int,
    val safeTint: Int,
    val homeTint: Int,
    val accent: Int,
    val text: Int,
    val textDim: Int,
    val panel: Int,
    val panelEdge: Int,
    val playerColors: IntArray
) {
    fun colorFor(seat: Int): Int = playerColors[seat % playerColors.size]
}

object Themes {

    private val CLASSIC_COLORS = intArrayOf(
        Color.rgb(232, 67, 79),    // red
        Color.rgb(52, 192, 122),   // green
        Color.rgb(242, 178, 51),   // yellow
        Color.rgb(62, 123, 234)    // blue
    )

    private val CONTRAST_COLORS = intArrayOf(
        Color.rgb(255, 45, 45),
        Color.rgb(0, 230, 118),
        Color.rgb(255, 214, 0),
        Color.rgb(41, 121, 255)
    )

    val DAY = Theme(
        "day", R.string.theme_day,
        bgTop = Color.rgb(238, 243, 250), bgBottom = Color.rgb(214, 224, 238),
        boardBase = Color.rgb(252, 253, 255), boardEdge = Color.rgb(196, 208, 224),
        cellFill = Color.rgb(240, 244, 250), cellEdge = Color.rgb(214, 223, 235),
        safeTint = Color.rgb(226, 235, 246), homeTint = Color.rgb(232, 238, 247),
        accent = Color.rgb(24, 140, 130), text = Color.rgb(22, 30, 44),
        textDim = Color.rgb(104, 118, 138),
        panel = Color.rgb(255, 255, 255), panelEdge = Color.rgb(214, 222, 234),
        playerColors = CLASSIC_COLORS
    )

    val NIGHT = Theme(
        "night", R.string.theme_night,
        bgTop = Color.rgb(13, 17, 28), bgBottom = Color.rgb(6, 9, 16),
        boardBase = Color.rgb(24, 30, 44), boardEdge = Color.rgb(44, 54, 74),
        cellFill = Color.rgb(33, 40, 57), cellEdge = Color.rgb(48, 58, 79),
        safeTint = Color.rgb(44, 56, 80), homeTint = Color.rgb(38, 47, 68),
        accent = Color.rgb(63, 224, 200), text = Color.rgb(240, 245, 252),
        textDim = Color.rgb(150, 163, 184),
        panel = Color.rgb(22, 27, 40), panelEdge = Color.rgb(48, 58, 79),
        playerColors = CLASSIC_COLORS
    )

    val CYBER = Theme(
        "cyber", R.string.theme_cyber,
        bgTop = Color.rgb(16, 6, 32), bgBottom = Color.rgb(4, 2, 12),
        boardBase = Color.rgb(26, 10, 46), boardEdge = Color.rgb(120, 40, 200),
        cellFill = Color.rgb(38, 16, 62), cellEdge = Color.rgb(90, 30, 150),
        safeTint = Color.rgb(56, 24, 92), homeTint = Color.rgb(46, 20, 76),
        accent = Color.rgb(0, 240, 255), text = Color.rgb(232, 250, 255),
        textDim = Color.rgb(150, 170, 200),
        panel = Color.rgb(22, 8, 40), panelEdge = Color.rgb(120, 40, 200),
        playerColors = intArrayOf(
            Color.rgb(255, 61, 129), Color.rgb(0, 255, 178),
            Color.rgb(255, 214, 0), Color.rgb(0, 178, 255)
        )
    )

    val ROYAL = Theme(
        "royal", R.string.theme_royal,
        bgTop = Color.rgb(30, 22, 10), bgBottom = Color.rgb(14, 10, 4),
        boardBase = Color.rgb(42, 32, 18), boardEdge = Color.rgb(168, 132, 58),
        cellFill = Color.rgb(56, 43, 24), cellEdge = Color.rgb(120, 94, 40),
        safeTint = Color.rgb(78, 60, 30), homeTint = Color.rgb(66, 50, 26),
        accent = Color.rgb(226, 184, 92), text = Color.rgb(250, 240, 218),
        textDim = Color.rgb(186, 166, 128),
        panel = Color.rgb(38, 29, 16), panelEdge = Color.rgb(140, 110, 52),
        playerColors = intArrayOf(
            Color.rgb(212, 62, 70), Color.rgb(70, 168, 112),
            Color.rgb(226, 184, 92), Color.rgb(72, 116, 200)
        )
    )

    val FOREST = Theme(
        "forest", R.string.theme_forest,
        bgTop = Color.rgb(12, 32, 26), bgBottom = Color.rgb(6, 18, 14),
        boardBase = Color.rgb(22, 48, 38), boardEdge = Color.rgb(48, 96, 78),
        cellFill = Color.rgb(30, 60, 48), cellEdge = Color.rgb(52, 100, 82),
        safeTint = Color.rgb(40, 80, 62), homeTint = Color.rgb(36, 70, 56),
        accent = Color.rgb(120, 230, 160), text = Color.rgb(235, 250, 242),
        textDim = Color.rgb(150, 186, 170),
        panel = Color.rgb(20, 44, 36), panelEdge = Color.rgb(52, 100, 82),
        playerColors = intArrayOf(
            Color.rgb(232, 84, 84), Color.rgb(96, 214, 140),
            Color.rgb(240, 200, 80), Color.rgb(84, 156, 232)
        )
    )

    val CONTRAST = Theme(
        "contrast", R.string.theme_contrast,
        bgTop = Color.rgb(0, 0, 0), bgBottom = Color.rgb(0, 0, 0),
        boardBase = Color.rgb(10, 10, 10), boardEdge = Color.rgb(255, 255, 255),
        cellFill = Color.rgb(24, 24, 24), cellEdge = Color.rgb(120, 120, 120),
        safeTint = Color.rgb(40, 40, 40), homeTint = Color.rgb(34, 34, 34),
        accent = Color.rgb(255, 235, 0), text = Color.rgb(255, 255, 255),
        textDim = Color.rgb(200, 200, 200),
        panel = Color.rgb(16, 16, 16), panelEdge = Color.rgb(255, 255, 255),
        playerColors = CONTRAST_COLORS
    )

    val ALL = listOf(DAY, NIGHT, CYBER, ROYAL, FOREST, CONTRAST)

    fun byId(id: String?): Theme = ALL.firstOrNull { it.id == id } ?: DAY
}

/** Board surface material: changes cell rendering, not the rules. */
data class BoardSkin(val id: String, val nameRes: Int, val inset: Float, val rounded: Float)

object BoardSkins {
    val CLASSIC = BoardSkin("classic", R.string.skin_classic, 0.06f, 0.26f)
    val NEON = BoardSkin("neon", R.string.skin_neon, 0.10f, 0.50f)
    val WOOD = BoardSkin("wood", R.string.skin_wood, 0.04f, 0.18f)
    val MARBLE = BoardSkin("marble", R.string.skin_marble, 0.03f, 0.34f)
    val ALL = listOf(CLASSIC, NEON, WOOD, MARBLE)
    fun byId(id: String?): BoardSkin = ALL.firstOrNull { it.id == id } ?: CLASSIC
}

/** Token silhouette. */
data class TokenSkin(val id: String, val nameRes: Int)

object TokenSkins {
    val PAWN = TokenSkin("pawn", R.string.token_pawn)
    val DRAGON = TokenSkin("dragon", R.string.token_dragon)
    val CAR = TokenSkin("car", R.string.token_car)
    val GEM = TokenSkin("gem", R.string.token_gem)
    val ALL = listOf(PAWN, DRAGON, CAR, GEM)
    fun byId(id: String?): TokenSkin = ALL.firstOrNull { it.id == id } ?: PAWN
}
