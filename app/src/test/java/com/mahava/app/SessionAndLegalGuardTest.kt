package com.mahava.app

import com.mahava.app.data.auth.AuthRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAndLegalGuardTest {
    @Test fun onlyDefinitiveRefreshRejectionKillsSession() {
        assertTrue(AuthRepository.isRefreshRejected(401))
        assertFalse(AuthRepository.isRefreshRejected(500))
        assertFalse(AuthRepository.isRefreshRejected(429))
        assertFalse(AuthRepository.isRefreshRejected(0))
    }
}
