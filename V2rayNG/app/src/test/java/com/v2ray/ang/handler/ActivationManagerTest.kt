package com.v2ray.ang.handler

import com.v2ray.ang.util.ActivationCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

class ActivationManagerTest {

    private val settingsValues = mutableMapOf<String, String>()

    @Before
    fun prepareStorage() {
        reset(settings)
        whenever(settings.decodeString(any())).thenAnswer { settingsValues[it.getArgument<String>(0)] }
        whenever(settings.decodeString(any(), any<String>())).thenAnswer {
            settingsValues[it.getArgument<String>(0)] ?: it.getArgument<String>(1)
        }
        whenever(settings.encode(any<String>(), any<String>())).thenAnswer {
            settingsValues[it.getArgument<String>(0)] = it.getArgument(1)
            true
        }
        whenever(settings.encode(any<String>(), any<Boolean>())).thenReturn(true)
    }

    @Test
    fun parseSetupReturnsNullForBlankAndNullInput() {
        assertNull(ActivationManager.parseSetup(null))
        assertNull(ActivationManager.parseSetup(""))
        assertNull(ActivationManager.parseSetup("   "))
    }

    @Test
    fun parseSetupReturnsNullForMalformedCode() {
        assertNull(ActivationManager.parseSetup("!!!not-base64!!!"))
    }

    @Test
    fun parseSetupReadsServerFromValidCode() {
        val raw = "{\"v\":1,\"s\":\"http://192.168.1.10:5050\"}"
        val setup = ActivationManager.parseSetup(ActivationCodec.encode(raw.toByteArray(Charsets.UTF_8)))
        assertEquals("http://192.168.1.10:5050", setup?.server)
    }

    @Test
    fun parseSetupIgnoresUnknownFieldsAndBlankServer() {
        val raw = "{\"v\":1}"
        val setup = ActivationManager.parseSetup(ActivationCodec.encode(raw.toByteArray(Charsets.UTF_8)))
        assertNull(setup?.server)
    }

    @Test
    fun resolveServerPersistsOverriddenAddress() {
        val raw = "{\"v\":1,\"s\":\"https://panel.example.com\"}"
        val config = ActivationCodec.encode(raw.toByteArray(Charsets.UTF_8))
        assertEquals("https://panel.example.com", ActivationManager.resolveServer(config))
        assertEquals("https://panel.example.com", settingsValues[ActivationManagerTest.KEY_SERVER])
    }

    @Test
    fun resolveServerKeepsDefaultWhenConfigBlank() {
        assertEquals(ActivationManager.DEFAULT_SERVER, ActivationManager.resolveServer(null))
        assertEquals(ActivationManager.DEFAULT_SERVER, settingsValues[ActivationManagerTest.KEY_SERVER])
    }

    @Test
    fun masterCodeActivatesWithoutContactingThePanel() = runBlocking(Dispatchers.IO) {
        val outcome = ActivationManager.activate("  ${ActivationManager.MASTER_CODE}  ", null)

        assertTrue(outcome is ActivationOutcome.Master)
        assertNull(
            "a master unlock must not need a panel address",
            settingsValues[ActivationManagerTest.KEY_SERVER]
        )
    }

    @Test
    fun blankCodeIsDeniedBeforeAnyPanelRequest() = runBlocking(Dispatchers.IO) {
        val outcome = ActivationManager.activate("   ", null)

        assertEquals(ActivationOutcome.Error(ActivationErrorKind.DENIED), outcome)
        assertNull(settingsValues[ActivationManagerTest.KEY_SERVER])
    }

    companion object {
        private const val KEY_SERVER = "pref_activation_server"
        private val settings get() = MmkvTestHandles.settings

        @BeforeClass
        @JvmStatic
        fun initializeHandles() {
            MmkvTestHandles.bind()
        }
    }
}