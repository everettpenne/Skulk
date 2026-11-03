package com.everettpenne.skulk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File

/** Dev: renders the launcher icon (plain, as a circle and as a themed one-colour icon) to PNGs so the artwork can be seen. Not an assertion test. */
class IconShotTest {
    @Test fun render() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        fun save(name: String, bitmap: Bitmap) = File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val size = 512
        // Full canvas, as the Play listing would show it (rounded square).
        val full = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size); icon.draw(Canvas(full)); save("icon-full", full)
        // The circle mask a launcher may apply.
        val circle = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(circle); c.save(); c.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
        icon.setBounds(0, 0, size, size); icon.draw(c); c.restore(); save("icon-circle", circle)
        // Themed: the monochrome layer tinted over a tonal plate, as Android draws it.
        val themed = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val t = Canvas(themed); t.drawColor(Color.parseColor("#4A3A1F"))
        val mono = icon.monochrome!!.mutate(); mono.setTint(Color.parseColor("#FFD9A0")); mono.setBounds(-size / 4, -size / 4, size + size / 4, size + size / 4)
        t.save(); t.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }); mono.draw(t); t.restore()
        save("icon-themed", themed)
        // Small, as in a launcher grid.
        val small = Bitmap.createScaledBitmap(circle, 96, 96, true); save("icon-96", Bitmap.createScaledBitmap(small, 384, 384, false))
    }

    /** The peeking-worm alternates, as a launcher would draw them (rounded square), saved for a look. */
    @Test fun renderAlternates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val size = 512
        for ((name, id) in listOf("orange" to R.mipmap.ic_launcher_peek_orange, "pearl" to R.mipmap.ic_launcher_peek_pearl, "blue" to R.mipmap.ic_launcher_peek_blue)) {
            val icon = context.getDrawable(id) as AdaptiveIconDrawable
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.clipPath(Path().apply { addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * .24f, size * .24f, Path.Direction.CW) })
            // A launcher shows the middle 72 of the layers' 108: draw them 1.5 times the size, centred, then mask.
            val bleed = size / 4
            for (layer in listOfNotNull(icon.background, icon.foreground)) { layer.setBounds(-bleed, -bleed, size + bleed, size + bleed); layer.draw(c) }
            File(dir, "peek-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
