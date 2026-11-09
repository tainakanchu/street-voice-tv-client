package com.example.streetvoicetv.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UserMessagesTest {

    @Test
    fun `timeouts map to the timeout message`() {
        assertEquals(UserMessages.TIMEOUT, userMessageFor("t", SocketTimeoutException("10000ms")))
    }

    @Test
    fun `network failures map to the network message`() {
        assertEquals(UserMessages.NETWORK, userMessageFor("t", UnknownHostException("streetvoice.com")))
        assertEquals(UserMessages.NETWORK, userMessageFor("t", IOException("reset")))
    }

    @Test
    fun `other failures use the fallback and never expose raw details`() {
        assertEquals(UserMessages.LOAD_FAILED, userMessageFor("t", IllegalStateException("boom")))
        assertEquals(UserMessages.SEARCH_FAILED, userMessageFor("t", null, UserMessages.SEARCH_FAILED))
    }
}
