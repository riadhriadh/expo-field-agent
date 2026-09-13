package expo.modules.fieldagent

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File

/**
 * Loading a picture into a 24dp overlay slot.
 *
 * The bubble is a window that lives for a whole shift. Decoding a 12 megapixel
 * photo into it at full size is how an overlay ends up owning 48 MB of a
 * low-end phone, so nothing here decodes before it has read the bounds.
 */
object Images {

    /**
     * Pure, and separated for exactly that reason: this is the arithmetic that
     * decides how much memory the bubble takes.
     *
     * Returns the power of two BitmapFactory wants, never below 1.
     */
    fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        // Halve while BOTH sides still cover the target: stopping at the first
        // side to cross it is what leaves a panorama blurry on its short edge.
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) {
            sample *= 2
        }
        return sample
    }

    /**
     * Accepts what a host can actually hand over without a download:
     * a `file://` uri, an absolute path, a `content://` uri, or a bare name,
     * which is what `Image.resolveAssetSource(require(...))` returns from a
     * release build — there, the asset is a real drawable resource.
     *
     * `http(s)` is refused on purpose. Downloading belongs to the host, which
     * has its own auth, its own cache and its own idea of when to retry.
     */
    fun load(context: Context, source: String, targetPx: Int): Drawable? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            Bus.error(
                "BUBBLE_IMAGE",
                "setBubbleImage n'accepte pas d'URL distante : telecharge le fichier puis passe son chemin."
            )
            return null
        }

        return when {
            trimmed.startsWith("content://") -> fromStream(context, Uri.parse(trimmed), targetPx)
            trimmed.startsWith("file://") -> fromFile(context, Uri.parse(trimmed).path, targetPx)
            trimmed.startsWith("/") -> fromFile(context, trimmed, targetPx)
            else -> fromResource(context, trimmed)
        }
    }

    private fun fromResource(context: Context, name: String): Drawable? {
        val id = runCatching {
            context.resources.getIdentifier(name, "drawable", context.packageName)
        }.getOrDefault(0)
        if (id == 0) {
            Bus.error("BUBBLE_IMAGE", "image introuvable : $name")
            return null
        }
        return runCatching { context.resources.getDrawable(id, context.theme) }.getOrNull()
    }

    private fun fromFile(context: Context, path: String?, targetPx: Int): Drawable? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile || !file.canRead()) {
            Bus.error("BUBBLE_IMAGE", "fichier illisible : $path")
            return null
        }
        return decode(context, targetPx) { options -> BitmapFactory.decodeFile(path, options) }
    }

    private fun fromStream(context: Context, uri: Uri, targetPx: Int): Drawable? =
        decode(context, targetPx) { options ->
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }

    /** Two passes: bounds first, pixels second, so the big one never happens. */
    private fun decode(
        context: Context,
        targetPx: Int,
        decoder: (BitmapFactory.Options) -> android.graphics.Bitmap?
    ): Drawable? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { decoder(bounds) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
        }
        val bitmap = runCatching { decoder(options) }.getOrNull()
        if (bitmap == null) {
            Bus.error("BUBBLE_IMAGE", "image illisible ou format non supporte")
            return null
        }
        return BitmapDrawable(context.resources, bitmap)
    }
}
