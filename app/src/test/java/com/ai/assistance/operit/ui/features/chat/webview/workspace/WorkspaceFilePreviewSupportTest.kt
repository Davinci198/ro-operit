package com.ai.assistance.operit.ui.features.chat.webview.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MIME resolution and preview routing for workspace files.
 *
 * Only extensions the `when` in [workspaceMimeTypeForPath] handles are covered: an unknown extension
 * falls through to `android.webkit.MimeTypeMap`, which does not exist in a JVM unit test. The same
 * applies to [OpenFileInfo.resolvedMimeType], so every fixture either names a known extension or
 * carries an explicit mime type.
 */
class WorkspaceFilePreviewSupportTest {

    @Test
    fun `text and markup extensions resolve to their mime type`() {
        assertEquals("text/html", workspaceMimeTypeForPath("/workspace/page.html"))
        assertEquals("text/html", workspaceMimeTypeForPath("page.htm"))
        assertEquals("text/css", workspaceMimeTypeForPath("style.css"))
        assertEquals("application/javascript", workspaceMimeTypeForPath("app.js"))
        assertEquals("application/javascript", workspaceMimeTypeForPath("module.mjs"))
        assertEquals("application/javascript", workspaceMimeTypeForPath("common.cjs"))
        assertEquals("application/json", workspaceMimeTypeForPath("config.json"))
        assertEquals("text/plain", workspaceMimeTypeForPath("notes.txt"))
        assertEquals("text/plain", workspaceMimeTypeForPath("notes.md"))
        assertEquals("text/plain", workspaceMimeTypeForPath("run.log"))
        assertEquals("application/xml", workspaceMimeTypeForPath("layout.xml"))
        assertEquals("text/csv", workspaceMimeTypeForPath("data.csv"))
        assertEquals("application/yaml", workspaceMimeTypeForPath("ci.yaml"))
        assertEquals("application/yaml", workspaceMimeTypeForPath("ci.yml"))
    }

    @Test
    fun `the extension is matched case-insensitively`() {
        assertEquals("image/png", workspaceMimeTypeForPath("SHOT.PNG"))
        assertEquals("text/html", workspaceMimeTypeForPath("Page.Html"))
        assertEquals("application/json", workspaceMimeTypeForPath("CONFIG.JSON"))
    }

    @Test
    fun `image audio and video extensions resolve to their mime type`() {
        assertEquals("image/png", workspaceMimeTypeForPath("a/b/shot.png"))
        assertEquals("image/jpeg", workspaceMimeTypeForPath("photo.jpg"))
        assertEquals("image/jpeg", workspaceMimeTypeForPath("photo.jpeg"))
        assertEquals("image/gif", workspaceMimeTypeForPath("anim.gif"))
        assertEquals("image/bmp", workspaceMimeTypeForPath("legacy.bmp"))
        assertEquals("image/webp", workspaceMimeTypeForPath("modern.webp"))
        assertEquals("image/svg+xml", workspaceMimeTypeForPath("icon.svg"))
        assertEquals("image/heic", workspaceMimeTypeForPath("camera.heic"))
        assertEquals("image/heif", workspaceMimeTypeForPath("camera.heif"))
        assertEquals("audio/mpeg", workspaceMimeTypeForPath("track.mp3"))
        assertEquals("audio/wav", workspaceMimeTypeForPath("track.wav"))
        assertEquals("audio/mp4", workspaceMimeTypeForPath("track.m4a"))
        assertEquals("audio/aac", workspaceMimeTypeForPath("track.aac"))
        assertEquals("audio/ogg", workspaceMimeTypeForPath("track.ogg"))
        assertEquals("audio/ogg", workspaceMimeTypeForPath("track.opus"))
        assertEquals("audio/flac", workspaceMimeTypeForPath("track.flac"))
        assertEquals("video/webm", workspaceMimeTypeForPath("clip.webm"))
        assertEquals("video/mp4", workspaceMimeTypeForPath("clip.mp4"))
        assertEquals("video/mp4", workspaceMimeTypeForPath("clip.m4v"))
        assertEquals("video/quicktime", workspaceMimeTypeForPath("clip.mov"))
        assertEquals("video/x-matroska", workspaceMimeTypeForPath("clip.mkv"))
        assertEquals("video/x-msvideo", workspaceMimeTypeForPath("clip.avi"))
        assertEquals("video/3gpp", workspaceMimeTypeForPath("clip.3gp"))
    }

    @Test
    fun `office documents resolve to their mime type`() {
        assertEquals("application/pdf", workspaceMimeTypeForPath("doc/manual.pdf"))
        assertEquals("application/msword", workspaceMimeTypeForPath("doc/legacy.doc"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            workspaceMimeTypeForPath("doc/report.docx")
        )
        assertEquals("application/vnd.ms-excel", workspaceMimeTypeForPath("sheet/legacy.xls"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workspaceMimeTypeForPath("sheet/report.xlsx")
        )
    }

    @Test
    fun `a path without a usable extension stays opaque binary`() {
        assertEquals("application/octet-stream", workspaceMimeTypeForPath("LICENSE"))
        assertEquals("application/octet-stream", workspaceMimeTypeForPath("/workspace/bin/tool"))
        assertEquals("application/octet-stream", workspaceMimeTypeForPath("archive."))
    }

    @Test
    fun `media and office documents open directly, text files open in the editor`() {
        assertTrue(workspaceShouldOpenAsDirectPreview("shot.png"))
        assertTrue(workspaceShouldOpenAsDirectPreview("track.mp3"))
        assertTrue(workspaceShouldOpenAsDirectPreview("clip.mp4"))
        assertTrue(workspaceShouldOpenAsDirectPreview("manual.pdf"))
        assertTrue(workspaceShouldOpenAsDirectPreview("legacy.doc"))
        assertTrue(workspaceShouldOpenAsDirectPreview("report.docx"))
        assertTrue(workspaceShouldOpenAsDirectPreview("legacy.xls"))
        assertTrue(workspaceShouldOpenAsDirectPreview("report.xlsx"))

        assertFalse(workspaceShouldOpenAsDirectPreview("page.html"))
        assertFalse(workspaceShouldOpenAsDirectPreview("notes.md"))
        assertFalse(workspaceShouldOpenAsDirectPreview("config.json"))
        assertFalse(workspaceShouldOpenAsDirectPreview("data.csv"))
        assertFalse(workspaceShouldOpenAsDirectPreview("LICENSE"))
    }

    @Test
    fun `an explicit mime type wins over the path`() {
        val info =
            OpenFileInfo(
                path = "/workspace/inbox/report.dat",
                content = "",
                lastModified = 0L,
                name = "report.dat",
                mimeType = "application/pdf"
            )

        assertEquals("application/pdf", info.resolvedMimeType)
        assertTrue(info.isPdf)
        assertTrue(info.isReadOnlyDocumentPreviewable)
        assertTrue(info.isReadOnlyPreview)
        assertFalse(info.isMediaPreviewable)
    }

    @Test
    fun `a blank mime type falls back to the path`() {
        val info =
            OpenFileInfo(path = "/workspace/media/shot.png", content = "", lastModified = 0L)

        assertEquals("shot.png", info.name)
        assertEquals("image/png", info.resolvedMimeType)
        assertTrue(info.isImage)
        assertTrue(info.isMediaPreviewable)
        assertTrue(info.isReadOnlyPreview)
        assertFalse(info.isPdf)
    }

    @Test
    fun `html and markdown are recognised by name and ignore case`() {
        val html = OpenFileInfo(path = "/workspace/page.html", content = "", lastModified = 0L)
        assertTrue(html.isHtml)
        assertFalse(html.isMarkdown)

        val upperCase =
            OpenFileInfo(
                path = "/workspace/PAGE.HTM",
                content = "",
                lastModified = 0L,
                name = "PAGE.HTM",
                mimeType = "text/html"
            )
        assertTrue(upperCase.isHtml)

        val markdown =
            OpenFileInfo(
                path = "/workspace/notes.markdown",
                content = "",
                lastModified = 0L,
                name = "notes.markdown",
                mimeType = "text/plain"
            )
        assertTrue(markdown.isMarkdown)
        assertFalse(markdown.isHtml)
    }

    @Test
    fun `a plain text file is neither media nor a read-only document`() {
        val notes =
            OpenFileInfo(
                path = "/workspace/notes.txt",
                content = "hello",
                lastModified = 0L,
                name = "notes.txt",
                mimeType = "text/plain"
            )

        assertEquals("text/plain", notes.resolvedMimeType)
        assertFalse(notes.isImage)
        assertFalse(notes.isAudio)
        assertFalse(notes.isVideo)
        assertFalse(notes.isMediaPreviewable)
        assertFalse(notes.isReadOnlyDocumentPreviewable)
        assertFalse(notes.isReadOnlyPreview)
    }

    @Test
    fun `audio and video are media previews but not documents`() {
        val track =
            OpenFileInfo(
                path = "/workspace/media/track.mp3",
                content = "",
                lastModified = 0L,
                name = "track.mp3",
                mimeType = "audio/mpeg"
            )
        val clip =
            OpenFileInfo(
                path = "/workspace/media/clip.mkv",
                content = "",
                lastModified = 0L,
                name = "clip.mkv",
                mimeType = "video/x-matroska"
            )

        assertTrue(track.isAudio)
        assertTrue(track.isMediaPreviewable)
        assertFalse(track.isReadOnlyDocumentPreviewable)
        assertTrue(clip.isVideo)
        assertTrue(clip.isReadOnlyPreview)
    }

    @Test
    fun `word and spreadsheet documents are read-only previews`() {
        val document =
            OpenFileInfo(
                path = "/workspace/report.docx",
                content = "",
                lastModified = 0L,
                name = "report.docx",
                mimeType =
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        val sheet =
            OpenFileInfo(
                path = "/workspace/report.xlsx",
                content = "",
                lastModified = 0L,
                name = "report.xlsx",
                mimeType =
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )

        assertTrue(document.isWordDocument)
        assertTrue(document.isReadOnlyDocumentPreviewable)
        assertFalse(document.isSpreadsheetDocument)
        assertTrue(sheet.isSpreadsheetDocument)
        assertTrue(sheet.isReadOnlyDocumentPreviewable)
        assertFalse(sheet.isWordDocument)
    }
}
