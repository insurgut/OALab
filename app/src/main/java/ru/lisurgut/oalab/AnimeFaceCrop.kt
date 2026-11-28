package ru.lisurgut.oalab

import android.content.Context
import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import kotlin.math.roundToInt

internal data class FaceBox(val x: Int, val y: Int, val width: Int, val height: Int)
internal data class FaceSquare(val x: Int, val y: Int, val side: Int)

internal fun squareAroundFace(width: Int, height: Int, face: FaceBox?): FaceSquare {
    val maxSide = minOf(width, height)
    val side = (face?.let { maxOf(it.width, it.height) * 1.38f } ?: (maxSide * .82f))
        .roundToInt().coerceIn(1, maxSide)
    val x = face?.let { it.x + it.width * .50f } ?: width * .50f
    val y = face?.let { it.y + it.height * .40f } ?: height * .34f
    return FaceSquare(
        (x - side / 2f).roundToInt().coerceIn(0, width - side),
        (y - side / 2f).roundToInt().coerceIn(0, height - side),
        side
    )
}

internal class AnimeFaceCrop(context: Context) : Transformation {
    private val app = context.applicationContext
    override val cacheKey = "oalab.AnimeFaceCrop.v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val crop = squareAroundFace(input.width, input.height, AnimeFaceDetector.find(app, input))
        return Bitmap.createBitmap(input, crop.x, crop.y, crop.side, crop.side)
    }
}

private object AnimeFaceDetector {
    private var loaded = false
    private var classifier: CascadeClassifier? = null

    @Synchronized
    fun find(context: Context, bitmap: Bitmap): FaceBox? {
        try {
            if (!loaded) {
                loaded = true
                classifier = load(context)
            }
            val detector = classifier ?: return null
            val color = Mat()
            val gray = Mat()
            val faces = MatOfRect()
            try {
                Utils.bitmapToMat(bitmap, color)
                Imgproc.cvtColor(color, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.equalizeHist(gray, gray)
                var scale = 1.0
                detector.detectMultiScale(gray, faces, 1.1, 4, 0, CvSize(22.0, 22.0), CvSize())
                if (faces.empty() && minOf(gray.rows(), gray.cols()) >= 100) {
                    scale = .75
                    Imgproc.resize(gray, gray, CvSize(), scale, scale, Imgproc.INTER_AREA)
                    detector.detectMultiScale(gray, faces, 1.1, 4, 0, CvSize(22.0, 22.0), CvSize())
                }
                val best = faces.toArray().maxByOrNull { it.width * it.height } ?: return null
                return FaceBox(
                    (best.x / scale).roundToInt(),
                    (best.y / scale).roundToInt(),
                    (best.width / scale).roundToInt(),
                    (best.height / scale).roundToInt()
                )
            } finally {
                faces.release()
                gray.release()
                color.release()
            }
        } catch (_: Exception) {
            return null
        } catch (_: LinkageError) {
            return null
        }
    }

    private fun load(context: Context): CascadeClassifier? {
        if (!OpenCVLoader.initLocal()) return null
        val asset = "lbpcascade_animeface.xml"
        val copy = File.createTempFile("anime-face", ".xml", context.cacheDir)
        try {
            context.assets.open(asset).use { input ->
                copy.outputStream().use { input.copyTo(it) }
            }
            return CascadeClassifier(copy.absolutePath).takeUnless { it.empty() }
        } finally {
            copy.delete()
        }
    }
}
