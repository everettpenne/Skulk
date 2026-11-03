package com.everettpenne.skulk

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

/**
 * Objects nested in a sealed class and a companion that lists them can see each other half-built, depending on which class the app touches
 * first (it differs between devices and between debug and release builds). Each case loads the classes in a fresh class loader, starting
 * from a different one, and checks the lists hold no nulls.
 */
class ClassInitOrderTest {
    private val pkg = "com.everettpenne.skulk.data.model"
    private val urls = System.getProperty("java.class.path").split(File.pathSeparator).map { File(it).toURI().toURL() }.toTypedArray()

    private fun listsIn(host: String, vararg getters: String, first: String) {
        val loader = URLClassLoader(urls, null)
        Class.forName("$pkg.$first", true, loader)
        val companion = Class.forName("$pkg.$host", true, loader).getField("Companion").get(null)
        for (getter in getters) {
            val list = companion.javaClass.getMethod(getter).invoke(companion) as List<*>
            assertTrue("$host.$getter has a null when '$first' is touched first: $list", list.isNotEmpty() && list.none { it == null })
        }
    }

    @Test fun sortListsAreCompleteWhicheverClassIsTouchedFirst() {
        for (first in listOf("ListingSort", "ListingSort\$Best", "ListingSort\$Hot", "ListingSort\$New", "ListingSort\$Controversial"))
            listsIn("ListingSort", "getBasic", "getTopOptions", first = first)
    }

    @Test fun feedListsAreCompleteWhicheverClassIsTouchedFirst() {
        for (first in listOf("PostFeed", "PostFeed\$Home", "PostFeed\$Popular", "PostFeed\$All", "PostFeed\$Saved"))
            listsIn("PostFeed", "getFrontPage", first = first)
    }

    @Test fun defaultSortsAreNeverNull() {
        val loader = URLClassLoader(urls, null)
        val all = Class.forName("$pkg.PostFeed\$All", true, loader).getField("INSTANCE").get(null)
        assertTrue(all.javaClass.getMethod("getDefaultSort").invoke(all) != null)
    }
}
