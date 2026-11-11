package com.dtp.spin.ui

import com.dtp.spin.audio.AudioEngineState
import com.dtp.spin.audio.DeckState
import com.dtp.spin.data.SessionSnapshot
import com.dtp.spin.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DJViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun track(id: Long, uri: String) = TrackEntity(
        id = id,
        uri = uri,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        durationMs = 240_000L,
        sampleRateHz = 48000,
        channels = 2,
    )

    @Test
    fun restore_reloadsSavedDecksAndParams() = runTest {
        val t1 = track(1, "content://a/1")
        val t2 = track(2, "content://a/2")
        val host = FakeHost(
            libraryInitial = listOf(t1, t2),
            sessionInitial = SessionSnapshot(
                deckUri = arrayOf(t1.uri, t2.uri),
                deckPositionMs = longArrayOf(30_000L, 60_000L),
                deckRate = floatArrayOf(1.02f, 0.98f),
                deckVolume = floatArrayOf(0.8f, 0.7f),
                crossfade = -0.5f,
            ),
            permissions = mutableSetOf(t1.uri, t2.uri),
        )
        val vm = DJViewModel(host)
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertEquals(false, ui.restoring)
        assertEquals(t1, ui.deckA.controls.track)
        assertEquals(t2, ui.deckB.controls.track)
        assertEquals(1.02f, ui.deckA.controls.rate, 1e-6f)
        assertEquals(-0.5f, ui.crossfade, 1e-6f)
        // Decoders started from the saved positions.
        assertEquals(30_000L, host.feeders[0].loads.single().third)
        assertEquals(60_000L, host.feeders[1].loads.single().third)
        // Engine params were pushed.
        assertTrue(host.engine.calls.any { it == "setCrossfade(-0.5)" })
        assertTrue(host.engine.calls.any { it.startsWith("setRate(0,1.02") })
    }

    @Test
    fun restore_skipsTrackWithoutPermission() = runTest {
        val t1 = track(1, "content://a/1")
        val host = FakeHost(
            libraryInitial = listOf(t1),
            sessionInitial = SessionSnapshot(deckUri = arrayOf(t1.uri, null)),
            permissions = mutableSetOf(), // revoked
        )
        val vm = DJViewModel(host)
        advanceUntilIdle()

        val ui = vm.uiState.value
        assertNull(ui.deckA.controls.track)
        assertTrue(host.feeders[0].loads.isEmpty())
        assertNotNull(ui.message)
    }

    @Test
    fun play_withoutTrack_showsMessage() = runTest {
        val host = FakeHost()
        val vm = DJViewModel(host)
        advanceUntilIdle()

        vm.play(0)
        advanceUntilIdle()

        assertTrue(host.engine.calls.none { it == "play(0)" })
        assertNotNull(vm.uiState.value.message)
        assertEquals(0, host.serviceStarts)
    }

    @Test
    fun play_withTrack_startsEngineAndService() = runTest {
        val t1 = track(1, "content://a/1")
        val host = FakeHost(
            libraryInitial = listOf(t1),
            permissions = mutableSetOf(t1.uri),
        )
        val vm = DJViewModel(host)
        advanceUntilIdle()
        vm.loadToDeck(0, t1)
        advanceUntilIdle()

        vm.play(0)

        assertTrue(host.engine.calls.contains("play(0)"))
        assertEquals(1, host.serviceStarts)
    }

    @Test
    fun pauseAll_stopsServiceWhenBothIdle() = runTest {
        val t1 = track(1, "content://a/1")
        val host = FakeHost(
            libraryInitial = listOf(t1),
            permissions = mutableSetOf(t1.uri),
        )
        val vm = DJViewModel(host)
        advanceUntilIdle()
        vm.loadToDeck(0, t1)
        advanceUntilIdle()
        vm.play(0)

        vm.pauseAll()
        advanceUntilIdle()

        assertTrue(host.engine.calls.contains("pause(0)"))
        assertTrue(host.serviceStops >= 1)
    }

    @Test
    fun seek_clampsAndForwardsFrames() = runTest {
        val t1 = track(1, "content://a/1")
        val host = FakeHost(
            libraryInitial = listOf(t1),
            permissions = mutableSetOf(t1.uri),
        )
        val vm = DJViewModel(host)
        advanceUntilIdle()
        vm.loadToDeck(0, t1)
        advanceUntilIdle()

        vm.seekTo(0, 5_000L) // 5 s @ 48 kHz = 240000 frames
        assertTrue(host.engine.calls.any { it == "seek(0,240000)" })
        assertEquals(5_000L, host.feeders[0].seeks.last().first)

        vm.seekTo(0, 999_000_000L) // clamps to duration
        assertTrue(host.engine.calls.any { it == "seek(0,11520000)" }) // 240 s * 48 k
    }

    @Test
    fun mixerParams_pushToEngineAndPersist() = runTest {
        val host = FakeHost()
        val vm = DJViewModel(host)
        advanceUntilIdle()

        vm.setTempoSlider(0, 1f) // +8 %
        vm.setVolume(1, 0.5f)
        vm.setGainDb(1, -6f)
        vm.setEq(1, -3f, 0f, 3f)
        vm.setCrossfade(0.25f)
        advanceUntilIdle()

        assertTrue(host.engine.calls.any { it.startsWith("setRate(0,1.08") })
        assertTrue(host.engine.calls.contains("setVolume(1,0.5)"))
        assertTrue(host.engine.calls.any { it.startsWith("setGain(1,0.5") })
        assertTrue(host.engine.calls.contains("setEq"))
        assertTrue(host.engine.calls.contains("setCrossfade(0.25)"))

        val saved = host.sessions.saved
        assertNotNull(saved)
        assertEquals(0.5f, saved!!.deckVolume[1], 1e-6f)
        assertEquals(0.25f, saved.crossfade, 1e-6f)
        assertEquals(1.08f, saved.deckRate[0], 1e-3f)
    }

    @Test
    fun play_afterEnd_seeksToCueFirst() = runTest {
        val t1 = track(1, "content://a/1")
        val host = FakeHost(
            engineInitial = AudioEngineState(
                deckA = DeckState(index = 0, ended = true, durationMs = 240_000L),
            ),
            libraryInitial = listOf(t1),
            permissions = mutableSetOf(t1.uri),
        )
        host.library.cues[t1.id] =
            com.dtp.spin.data.CueEntity(trackId = t1.id, positionMs = 10_000L)
        val vm = DJViewModel(host)
        advanceUntilIdle()
        vm.loadToDeck(0, t1)
        advanceUntilIdle()
        host.engine.calls.clear()

        vm.play(0)

        val seekIdx = host.engine.calls.indexOfFirst { it.startsWith("seek(0,") }
        val playIdx = host.engine.calls.indexOf("play(0)")
        assertTrue("expected seek-then-play, got ${host.engine.calls}", seekIdx >= 0 && playIdx > seekIdx)
        assertTrue(host.engine.calls[seekIdx] == "seek(0,480000)") // 10 s cue @ 48 kHz
    }

    @Test
    fun setCue_persistsToLibrary() = runTest {
        val t1 = track(1, "content://a/1")
        val host = FakeHost(
            engineInitial = AudioEngineState(
                deckA = DeckState(index = 0, positionMs = 42_000L, durationMs = 240_000L),
            ),
            libraryInitial = listOf(t1),
            permissions = mutableSetOf(t1.uri),
        )
        val vm = DJViewModel(host)
        advanceUntilIdle()
        vm.loadToDeck(0, t1)
        advanceUntilIdle()

        vm.setCueAtCurrent(0)
        advanceUntilIdle()

        assertEquals(42_000L, host.library.lastUpsert?.positionMs)
        assertEquals(42_000L, vm.uiState.value.deckA.controls.cueMs)
    }

    @Test
    fun deleteTrack_unloadsDeckAndRevokes() = runTest {
        val t1 = track(1, "content://a/1")
        val host = FakeHost(
            libraryInitial = listOf(t1),
            permissions = mutableSetOf(t1.uri),
        )
        val vm = DJViewModel(host)
        advanceUntilIdle()
        vm.loadToDeck(0, t1)
        advanceUntilIdle()

        vm.deleteTrack(t1)
        advanceUntilIdle()

        assertNull(vm.uiState.value.deckA.controls.track)
        assertEquals(listOf(1L), host.library.deleted)
        assertEquals(listOf(t1.uri), host.released)
    }
}
