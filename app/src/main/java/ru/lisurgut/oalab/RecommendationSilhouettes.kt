/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.lisurgut.oalab

import androidx.compose.ui.geometry.Offset
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// The polygon recipes are from AndroidX MaterialShapes, with no dependency on newer Compose.
internal val recommendationSilhouettes by lazy {
    listOf(arrow(), diamond(), ghostish(), clover4(), softBurst(), flower(), heart(), puffy())
        .map { it.normalized() }
}

private fun arrow(): RoundedPolygon {
    return customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0.892f), CornerRounding(0.313f)),
            PointNRound(Offset(-0.216f, 1.050f), CornerRounding(0.207f)),
            PointNRound(Offset(0.499f, -0.160f), CornerRounding(0.215f, 1.000f)),
            PointNRound(Offset(1.225f, 1.060f), CornerRounding(0.211f)),
        ),
        1,
    )
}

private fun diamond(): RoundedPolygon {
    return customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 1.096f), CornerRounding(0.151f, 0.524f)),
            PointNRound(Offset(0.040f, 0.500f), CornerRounding(0.159f)),
        ),
        2,
    )
}

private fun ghostish(): RoundedPolygon {
    return customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0f), CornerRounding(1.000f)),
            PointNRound(Offset(1f, 0f), CornerRounding(1.000f)),
            PointNRound(Offset(1f, 1.140f), CornerRounding(0.254f, 0.106f)),
            PointNRound(Offset(0.575f, 0.906f), CornerRounding(0.253f)),
        ),
        reps = 1,
        mirroring = true,
    )
}

private fun clover4(): RoundedPolygon {
    return customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0.074f)),
            PointNRound(Offset(0.725f, -0.099f), CornerRounding(0.476f)),
        ),
        reps = 4,
        mirroring = true,
    )
}

private fun softBurst(): RoundedPolygon {
    return customPolygon(
        listOf(
            PointNRound(Offset(0.193f, 0.277f), CornerRounding(0.053f)),
            PointNRound(Offset(0.176f, 0.055f), CornerRounding(0.053f)),
        ),
        reps = 10,
    )
}

private fun flower(): RoundedPolygon {
    return customPolygon(
        listOf(
            PointNRound(Offset(0.370f, 0.187f)),
            PointNRound(Offset(0.416f, 0.049f), CornerRounding(0.381f)),
            PointNRound(Offset(0.479f, 0.001f), CornerRounding(0.095f)),
        ),
        reps = 8,
        mirroring = true,
    )
}

private fun heart(): RoundedPolygon {
    return customPolygon(
        listOf(
            PointNRound(Offset(0.500f, 0.268f), CornerRounding(0.016f)),
            PointNRound(Offset(0.792f, -0.066f), CornerRounding(0.958f)),
            PointNRound(Offset(1.064f, 0.276f), CornerRounding(1.000f)),
            PointNRound(Offset(0.501f, 0.946f), CornerRounding(0.129f)),
        ),
        reps = 1,
        mirroring = true,
    )
}

private fun puffy(): RoundedPolygon {
    return customPolygon(
            listOf(
                PointNRound(Offset(0.500f, 0.053f)),
                PointNRound(Offset(0.545f, -0.040f), CornerRounding(0.405f)),
                PointNRound(Offset(0.670f, -0.035f), CornerRounding(0.426f)),
                PointNRound(Offset(0.717f, 0.066f), CornerRounding(0.574f)),
                PointNRound(Offset(0.722f, 0.128f)),
                PointNRound(Offset(0.777f, 0.002f), CornerRounding(0.360f)),
                PointNRound(Offset(0.914f, 0.149f), CornerRounding(0.660f)),
                PointNRound(Offset(0.926f, 0.289f), CornerRounding(0.660f)),
                PointNRound(Offset(0.881f, 0.346f)),
                PointNRound(Offset(0.940f, 0.344f), CornerRounding(0.126f)),
                PointNRound(Offset(1.003f, 0.437f), CornerRounding(0.255f)),
            ),
            reps = 2,
            mirroring = true,
        )
        .transformed { x, y -> TransformResult(x, y * .742f) }
}

private data class PointNRound(
    val o: Offset,
    val r: CornerRounding = CornerRounding.Unrounded,
)

@Suppress("PrimitiveInCollection")
private fun doRepeat(
    points: List<PointNRound>,
    reps: Int,
    center: Offset,
    mirroring: Boolean,
) =
    if (mirroring) {
        buildList {
            val angles = points.map { (it.o - center).angleDegrees() }
            val distances = points.map { (it.o - center).getDistance() }
            val actualReps = reps * 2
            val sectionAngle = 360f / actualReps
            repeat(actualReps) {
                points.indices.forEach { index ->
                    val i = if (it % 2 == 0) index else points.lastIndex - index
                    if (i > 0 || it % 2 == 0) {
                        val a =
                            (sectionAngle * it +
                                    if (it % 2 == 0) angles[i]
                                    else sectionAngle - angles[i] + 2 * angles[0])
                                .toRadians()
                        val finalPoint = Offset(cos(a), sin(a)) * distances[i] + center
                        add(PointNRound(finalPoint, points[i].r))
                    }
                }
            }
        }
    } else {
        points.size.let { np ->
            (0 until np * reps).map {
                val point = points[it % np].o.rotateDegrees((it / np) * 360f / reps, center)
                PointNRound(point, points[it % np].r)
            }
        }
    }

private fun Offset.rotateDegrees(angle: Float, center: Offset = Offset.Zero) =
    (angle.toRadians()).let { a ->
        val off = this - center
        Offset(off.x * cos(a) - off.y * sin(a), off.x * sin(a) + off.y * cos(a)) + center
    }

private fun Float.toRadians(): Float {
    return this / 360f * 2 * PI.toFloat()
}

private fun Offset.angleDegrees() = atan2(y, x) * 180f / PI.toFloat()

private fun customPolygon(
    pnr: List<PointNRound>,
    reps: Int,
    center: Offset = Offset(0.5f, 0.5f),
    mirroring: Boolean = false,
): RoundedPolygon {
    val actualPoints = doRepeat(pnr, reps, center, mirroring)
    return RoundedPolygon(
        vertices =
            FloatArray(actualPoints.size * 2) { ix ->
                actualPoints[ix / 2].o.let { if (ix % 2 == 0) it.x else it.y }
            },
        perVertexRounding = buildList { for (p in actualPoints) add(p.r) },
        centerX = center.x,
        centerY = center.y,
    )
}
