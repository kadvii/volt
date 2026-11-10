package com.kadvii.vault.feature.authentication

import com.kadvii.vault.core.security.VaultSessionManager
import com.kadvii.vault.feature.authentication.presentation.AuthNavigationEvent
import com.kadvii.vault.feature.authentication.presentation.StealthAuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAuthRepository : AuthRepository {
    var masterPinStored: String? = "1234"
    var decoyPinStored: String? = "9999"
    var isBio: Boolean = false
    var failedCount: Int = 0
    var lockoutSeconds: Long = 0L

    override fun isVaultSetup(): Boolean = masterPinStored != null
    override fun hasDecoyPin(): Boolean = decoyPinStored != null
    override fun isBiometricEnabled(): Boolean = isBio
    override fun setBiometricEnabled(enabled: Boolean) { isBio = enabled }
    override fun saveMasterPin(pin: String) { masterPinStored = pin }
    override fun saveDecoyPin(pin: String) { decoyPinStored = pin }

    override fun verifyMasterPin(pin: String): Boolean = pin == masterPinStored
    override fun verifyDecoyPin(pin: String): Boolean = pin == decoyPinStored

    override fun getFailedAttempts(): Int = failedCount
    override fun recordFailedAttempt(): Int {
        failedCount++
        if (failedCount >= 5) {
            lockoutSeconds = 30L
        }
        return failedCount
    }
    override fun resetFailedAttempts() {
        failedCount = 0
    }
    override fun getLockoutRemainingSeconds(): Long = lockoutSeconds
}

@OptIn(ExperimentalCoroutinesApi::class)
class StealthAuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeAuthRepository
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var authenticatePinUseCase: AuthenticatePinUseCase
    private lateinit var viewModel: StealthAuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeAuthRepository()
        sessionManager = VaultSessionManager()
        authenticatePinUseCase = AuthenticatePinUseCase(fakeRepo)
        viewModel = StealthAuthViewModel(
            authenticatePinUseCase = authenticatePinUseCase,
            authRepository = fakeRepo,
            vaultSessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onDigitEntered_appendsDigits() {
        viewModel.onDigitEntered('1')
        viewModel.onDigitEntered('2')
        viewModel.onDigitEntered('3')

        assertEquals("123", viewModel.uiState.value.enteredPin)
    }

    @Test
    fun onBackspace_removesLastDigit() {
        viewModel.onDigitEntered('1')
        viewModel.onDigitEntered('2')
        viewModel.onBackspace()

        assertEquals("1", viewModel.uiState.value.enteredPin)
    }

    @Test
    fun onClear_clearsEnteredPin() {
        viewModel.onDigitEntered('1')
        viewModel.onDigitEntered('2')
        viewModel.onClear()

        assertEquals("", viewModel.uiState.value.enteredPin)
    }

    @Test
    fun onSubmitPin_withMasterPin_authenticatesPrimary() {
        viewModel.onDigitEntered('1')
        viewModel.onDigitEntered('2')
        viewModel.onDigitEntered('3')
        viewModel.onDigitEntered('4')

        viewModel.onSubmitPin()

        assertTrue(sessionManager.isAuthenticated)
        assertTrue(sessionManager.isPrimarySession)
        assertEquals(AuthNavigationEvent.NavigateToPrimary, viewModel.uiState.value.navigationEvent)
    }

    @Test
    fun onSubmitPin_withDecoyPin_authenticatesDecoy() {
        viewModel.onDigitEntered('9')
        viewModel.onDigitEntered('9')
        viewModel.onDigitEntered('9')
        viewModel.onDigitEntered('9')

        viewModel.onSubmitPin()

        assertTrue(sessionManager.isAuthenticated)
        assertTrue(sessionManager.isDecoySession)
        assertEquals(AuthNavigationEvent.NavigateToDecoy, viewModel.uiState.value.navigationEvent)
    }

    @Test
    fun onSubmitPin_fiveConsecutiveFailures_triggersDismissToNotes() {
        for (i in 1..4) {
            "0000".forEach { viewModel.onDigitEntered(it) }
            viewModel.onSubmitPin()
            assertTrue(viewModel.uiState.value.isErrorShake)
        }

        // Attempt 5
        "0000".forEach { viewModel.onDigitEntered(it) }
        viewModel.onSubmitPin()

        assertEquals(AuthNavigationEvent.DismissToNotes, viewModel.uiState.value.navigationEvent)
        assertFalse(sessionManager.isAuthenticated)
    }
}

