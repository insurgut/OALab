package ru.lisurgut.oalab

internal data class HomePortraitBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal fun visiblePortrait(mask: IntArray, width: Int, height: Int): HomePortraitBounds? {
    val sample = (minOf(width, height) / 100).coerceAtLeast(1)
    fun filled(x: Int, y: Int) = mask[y * width + x].ushr(24) >= 160
    // A wide run of opaque pixels on a source edge usually means a cropped head, torso or limb.
    val topHits = (0 until width).count { x -> (0 until sample).any { filled(x, it) } }
    val bottomHits = (0 until width).count { x -> (height - sample until height).any { filled(x, it) } }
    val leftHits = (0 until height).count { y -> (0 until sample).any { filled(it, y) } }
    val rightHits = (0 until height).count { y -> (width - sample until width).any { filled(it, y) } }
    if (topHits > width * .05f || bottomHits > width * .08f ||
        leftHits > height * .05f || rightHits > height * .05f
    ) return null

    var left = width
    var top = height
    var right = -1
    var bottom = -1
    var opaque = 0
    for (index in mask.indices) {
        val opacity = mask[index].ushr(24)
        if (opacity >= 128) opaque++
        if (opacity < 32) continue
        val x = index % width
        val y = index / width
        left = minOf(left, x)
        top = minOf(top, y)
        right = maxOf(right, x)
        bottom = maxOf(bottom, y)
    }
    if (opaque < mask.size * .04f || opaque > mask.size * .94f) return null
    val padding = (maxOf(right - left, bottom - top) * .02f).toInt().coerceAtLeast(1)
    return HomePortraitBounds(
        (left - padding).coerceAtLeast(0),
        (top - padding).coerceAtLeast(0),
        (right + 1 + padding).coerceAtMost(width),
        (bottom + 1 + padding).coerceAtMost(height)
    )
}
