package com.enmanuelgil.storagecleaner

import com.enmanuelgil.storagecleaner.core.Rules
import com.enmanuelgil.storagecleaner.core.Rules.Candidate
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private val MB = 1_000_000L

    @Test fun apkExtensions() {
        assertTrue(Rules.isApk("WhatsApp.APK"))
        assertTrue(Rules.isApk("juego.xapk"))
        assertFalse(Rules.isApk("apk.txt"))
    }

    @Test fun apkRedundantOnlyIfInstalledSameOrNewer() {
        assertTrue(Rules.apkRedundant(10, 10))
        assertTrue(Rules.apkRedundant(11, 10))
        assertFalse(Rules.apkRedundant(9, 10))   // el APK es más nuevo: puede servir para actualizar
        assertFalse(Rules.apkRedundant(null, 10)) // no instalada
        assertFalse(Rules.apkRedundant(10, null)) // APK ilegible
    }

    @Test fun oldDownloadBoundary() {
        val now = 1_000_000_000_000L
        assertFalse(Rules.isOldDownload(now - Rules.OLD_DOWNLOAD_MS, now))
        assertTrue(Rules.isOldDownload(now - Rules.OLD_DOWNLOAD_MS - 1, now))
        assertFalse(Rules.isOldDownload(0, now)) // fecha desconocida: no se marca
    }

    @Test fun insideFolderNotJustSamePrefix() {
        assertTrue(Rules.isInside("/s/Download/a.pdf", "/s/Download"))
        assertTrue(Rules.isInside("/s/Download/sub/a.pdf", "/s/Download/"))
        assertFalse(Rules.isInside("/s/Download_old/a.pdf", "/s/Download"))
        assertFalse(Rules.isInside("/s/Downloads2/a.pdf", "/s/Download"))
    }

    @Test fun largeThresholdMatchesShownUnits() {
        assertTrue(102 * MB >= Rules.LARGE_BYTES) // la app lo muestra como "102 MB": debe contar como grande
        assertFalse(99 * MB >= Rules.LARGE_BYTES)
    }

    @Test fun duplicatesNeedSameSizeQuickAndFullHash() {
        val files = listOf(
            Candidate("/a/x.jpg", 5 * MB, 100), Candidate("/b/x.jpg", 5 * MB, 200), // iguales
            Candidate("/c/y.jpg", 5 * MB, 300),                                     // mismo tamaño, otro principio
            Candidate("/f/w.jpg", 5 * MB, 300),                                     // mismo principio/final, distinto en medio
            Candidate("/d/z.mp4", 9 * MB, 100),                                     // tamaño único
            Candidate("/e/s1", 1000, 1), Candidate("/e/s2", 1000, 1),               // < 1 MB: ignorados
        )
        val quick = mapOf("/a/x.jpg" to "Q1", "/b/x.jpg" to "Q1", "/c/y.jpg" to "Q2", "/f/w.jpg" to "Q1")
        val full = mapOf("/a/x.jpg" to "H1", "/b/x.jpg" to "H1", "/f/w.jpg" to "H9")
        val quickRead = ArrayList<String>(); val fullRead = ArrayList<String>()
        val groups = Rules.duplicateGroups(files,
            quick = { quickRead.add(it.path); quick[it.path] },
            full = { fullRead.add(it.path); full[it.path] })
        assertEquals(1, groups.size)
        assertEquals(listOf("/a/x.jpg", "/b/x.jpg"), groups[0].map { it.path })
        assertFalse("no debe leer archivos de tamaño único", "/d/z.mp4" in quickRead)
        assertFalse("/e/s1" in quickRead)
        assertFalse("la huella rápida distinta evita leerlo entero", "/c/y.jpg" in fullRead)
    }

    @Test fun unreadableFileNeverGrouped() {
        val files = listOf(Candidate("/a", 2 * MB, 1), Candidate("/b", 2 * MB, 1))
        assertTrue(Rules.duplicateGroups(files, { "Q" }, { if (it.path == "/a") "H" else null }).isEmpty())
    }

    @Test fun keeperPrefersCameraThenOldest() {
        val g = Rules.orderKeeperFirst(listOf(
            Candidate("/s/WhatsApp/Media/IMG-1.jpg", 1, 50),
            Candidate("/s/DCIM/Camera/IMG_1.jpg", 1, 900),
            Candidate("/s/Download/IMG_1.jpg", 1, 10),
        ))
        assertEquals("/s/DCIM/Camera/IMG_1.jpg", g[0].path)
        val h = Rules.orderKeeperFirst(listOf(Candidate("/s/b", 1, 20), Candidate("/s/a", 1, 10)))
        assertEquals("/s/a", h[0].path)
    }

    @Test fun onlyUserMediaFoldersAreAutoMarked() {
        assertTrue(Rules.isUserMedia("/storage/emulated/0/DCIM/Camera/a.jpg"))
        assertTrue(Rules.isUserMedia("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/v.mp4"))
        assertFalse(Rules.isUserMedia("/storage/emulated/0/MiJuego/data/pack.obb"))
    }

    @Test fun neverLoseTheLastCopy() {
        val g = listOf("/k", "/a", "/b")
        // El que se conserva sigue ahí: se puede borrar lo marcado.
        assertTrue(Rules.dupDeletionAllowed(g, setOf("/a", "/b")) { it == "/k" })
        // El que se conserva desapareció desde el escaneo y no queda ninguna otra copia sin marcar: NO.
        assertFalse(Rules.dupDeletionAllowed(g, setOf("/a", "/b")) { false })
        // Desmarcó una copia que sigue ahí: vale aunque la "conservada" no esté.
        assertTrue(Rules.dupDeletionAllowed(g, setOf("/k", "/a")) { it == "/b" })
        // Marcó el grupo entero a propósito (se le avisó en la confirmación).
        assertTrue(Rules.dupDeletionAllowed(g, g.toSet()) { false })
    }

    @Test fun overviewAddsUpAndNeverNegative() {
        val o = Rules.overview(
            total = 128_000, free = 28_000,
            appBytes = 20_000, dataBytes = 15_000, cacheBytes = 5_000,
            extTotal = 40_000, images = 10_000, videos = 15_000, audio = 1_000, extAppBytes = 4_000,
        )
        assertEquals(100_000, o.used)
        assertEquals(30_000, o.apps)          // 20k APK + 10k datos sin caché
        assertEquals(5_000, o.cache)
        assertEquals(10_000, o.otherFiles)    // 40k - 26k de fotos/vídeos/audio - 4k de apps
        assertEquals(o.used, o.apps + o.cache + o.images + o.videos + o.audio + o.otherFiles + o.system)
        val weird = Rules.overview(100, 90, 50, 10, 20, 5, 10, 0, 0, 0)
        assertTrue(weird.system >= 0 && weird.otherFiles >= 0 && weird.apps >= 0)
    }

    @Test fun overviewOldAndroidDoesNotCountAppDataTwice() {
        // Android 8-11: no se sabe cuánto de lo compartido es de las apps → "otros" va dentro de sistema.
        val o = Rules.overview(128_000, 28_000, 20_000, 15_000, 5_000, 40_000, 10_000, 15_000, 1_000, null)
        assertFalse(o.otherKnown)
        assertEquals(0, o.otherFiles)
        assertEquals(o.used, o.apps + o.cache + o.images + o.videos + o.audio + o.system)
    }
}
