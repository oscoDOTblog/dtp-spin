package com.dtp.spin.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderImportTest {

    @Test
    fun audioMime_passesRegardlessOfName() {
        assertTrue(isAudioFile("notes.txt", "audio/mpeg"))
        assertTrue(isAudioFile(null, "audio/x-wav"))
    }

    @Test
    fun knownExtensions_passWithoutMime() {
        assertTrue(isAudioFile("set.mp3", null))
        assertTrue(isAudioFile("SET.FLAC", "application/octet-stream"))
        assertTrue(isAudioFile("groove.ogg", null))
        assertTrue(isAudioFile("voice.m4a", ""))
    }

    @Test
    fun nonAudio_rejected() {
        assertFalse(isAudioFile("cover.jpg", "image/jpeg"))
        assertFalse(isAudioFile("notes.txt", null))
        assertFalse(isAudioFile(null, null))
        assertFalse(isAudioFile("track.mp3.bak", null))
    }

    @Test
    fun directoryMime_neverCountsAsAudio() {
        assertFalse(
            isAudioFile(
                "Music",
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
            ),
        )
    }

    @Test
    fun underTree_matchesRootAndDescendantsOnly() {
        assertTrue(isUnderTree("primary:Music", "primary:Music"))
        assertTrue(isUnderTree("primary:Music", "primary:Music/club/set.mp3"))
        assertTrue(isUnderTree("primary:Music", "primary:Music/a/b/c.wav"))
        assertFalse(isUnderTree("primary:Music", "primary:Music2/song.mp3"))
        assertFalse(isUnderTree("primary:Music", "primary:Other/song.mp3"))
    }
}
