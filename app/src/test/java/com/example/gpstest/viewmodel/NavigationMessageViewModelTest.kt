package com.example.gpstest.viewmodel

import android.app.Application
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.NavigationMessageFrame
import com.example.gpstest.domain.repository.GnssRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NavigationMessageViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `keeps the latest 200 navigation frames and filters by SVID`() =
        runTest(dispatcher) {
            val frames = MutableSharedFlow<NavigationMessageFrame>(extraBufferCapacity = 210)
            val repository: GnssRepository = mockk()
            every { repository.getNavigationMessages() } returns frames
            val viewModel = NavigationMessageViewModel(mockk<Application>(relaxed = true), repository)

            viewModel.startListening()
            advanceUntilIdle()
            repeat(201) { index ->
                frames.emit(
                    NavigationMessageFrame(
                        constellation = Constellation.GPS,
                        svid = if (index % 2 == 0) 3 else 7,
                        type = 257,
                        status = 1,
                        messageId = 1,
                        submessageId = index,
                        data = byteArrayOf(index.toByte()),
                        timestampMs = index.toLong(),
                    ),
                )
            }
            advanceUntilIdle()

            assertEquals(200, viewModel.uiState.value.frames.size)
            viewModel.setSvidFilter("7")
            assertTrue(
                viewModel.uiState.value.filteredFrames
                    .all { it.svid == 7 },
            )
        }
}
