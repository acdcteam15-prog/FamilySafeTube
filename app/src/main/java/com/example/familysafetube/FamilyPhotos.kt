package com.example.familysafetube

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

/**
 * Imports the parent's photos for family cards: decodes (down-sampled, so a 12-megapixel photo can't
 * exhaust memory), straightens camera rotation from the EXIF tag, limits the size to 1280 px and saves
 * a JPEG in the app's private "quiz_family" folder. The maths lives in [PhotoMath] (unit-tested).
 */
object FamilyPhotos {
    private const val DIR = "quiz_family"
    private const val MAX_PX = 1280
    private const val JPEG_QUALITY = 88

    fun dir(ctx: Context): File = File(ctx.filesDir, DIR).also { it.mkdirs() }

    fun file(ctx: Context, name: String): File = File(dir(ctx), File(name).name)   // .name blocks "../"

    /** Reads [uri], fixes its rotation and size, and writes the result to [dest]. Returns false if it can't be used. */
    fun importPhoto(ctx: Context, uri: Uri, dest: File): Boolean {
        return try {
            val resolver = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            val opts = BitmapFactory.Options().apply {
                inSampleSize = PhotoMath.sampleSize(bounds.outWidth, bounds.outHeight, MAX_PX)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false

            val degrees = resolver.openInputStream(uri)?.use { stream ->
                PhotoMath.rotationDegrees(
                    ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                )
            } ?: 0

            val (scaledW, _) = PhotoMath.scaledSize(decoded.width, decoded.height, MAX_PX)
            val factor = scaledW.toFloat() / decoded.width
            val matrix = Matrix().apply {
                if (degrees != 0) postRotate(degrees.toFloat())
                if (factor < 1f) postScale(factor, factor)
            }
            val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            dest.parentFile?.mkdirs()
            dest.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (result !== decoded) decoded.recycle()
            result.recycle()
            dest.length() > 0L
        } catch (e: Exception) {
            dest.delete()
            false
        } catch (e: OutOfMemoryError) {
            dest.delete()
            false
        }
    }
}
