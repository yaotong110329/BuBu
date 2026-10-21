package com.kumo.bubu.feature.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.BuBuApplication
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import com.kumo.bubu.domain.repository.CloudBackupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudAccountConnectionIntegrationTest {
    private lateinit var repository: CloudBackupRepository
    private lateinit var original: CloudBackupConnection

    @Before fun setUp() = runBlocking<Unit> {
        repository = ApplicationProvider.getApplicationContext<BuBuApplication>().container.cloudBackupRepository
        original = repository.observeConnection().first()
        // NeedsAuthorization does not expose the saved identity for exact restoration.
        assumeTrue(original != CloudBackupConnection.NeedsAuthorization)
    }

    @After fun restoreOriginalConnection() = runBlocking<Unit> {
        when (val previous = original) {
            is CloudBackupConnection.Connected -> repository.rememberConnection(previous.account)
            CloudBackupConnection.NotConnected -> repository.clearConnection()
            CloudBackupConnection.NeedsAuthorization -> Unit // Setup skipped before any mutation.
        }
    }

    @Test fun switchingAccountsNeverCarriesThePreviousBackupTimestamp() = runBlocking<Unit> {
        repository.rememberConnection(CloudBackupAccount("a@example.com", 42))
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("a@example.com", 42)), repository.observeConnection().first())
        repository.markNeedsAuthorization()
        assertEquals(CloudBackupConnection.NeedsAuthorization, repository.observeConnection().first())

        repository.rememberConnection(CloudBackupAccount("b@example.com"))
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("b@example.com")), repository.observeConnection().first())
        repository.clearConnection()
        assertEquals(CloudBackupConnection.NotConnected, repository.observeConnection().first())
        repository.rememberConnection(CloudBackupAccount("b@example.com"))
        assertEquals(CloudBackupConnection.Connected(CloudBackupAccount("b@example.com")), repository.observeConnection().first())
    }
}
