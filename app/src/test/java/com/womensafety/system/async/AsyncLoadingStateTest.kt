package com.womensafety.system.async

import com.womensafety.system.utils.Validators
import com.womensafety.system.viewmodel.OTPViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncLoadingStateTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testPasswordValidation_meetsStrongRequirements() {
        // Less than 8 chars -> invalid
        assertFalse(Validators.isValidPassword("Sri@1"))
        // Missing uppercase -> invalid
        assertFalse(Validators.isValidPassword("sri@2005"))
        // Missing lowercase -> invalid
        assertFalse(Validators.isValidPassword("SRI@2005"))
        // Missing number -> invalid
        assertFalse(Validators.isValidPassword("Sri@test"))
        // Missing special character -> invalid
        assertFalse(Validators.isValidPassword("Sri2005x"))
        // Valid password
        assertTrue(Validators.isValidPassword("Secure#Pass123"))
    }

    @Test
    fun testPasswordMatch_correctlyValidates() {
        assertFalse(Validators.doPasswordsMatch("Secure#Pass123", "Mismatch#Pass123"))
        assertFalse(Validators.doPasswordsMatch("", ""))
        assertTrue(Validators.doPasswordsMatch("Secure#Pass123", "Secure#Pass123"))
    }

    @Test
    fun testOtpFormat_requiresExactlySixDigits() {
        assertFalse(Validators.isValidOtp("12345"))
        assertFalse(Validators.isValidOtp("1234567"))
        assertFalse(Validators.isValidOtp("12345a"))
        assertTrue(Validators.isValidOtp("123456"))
    }

    @Test
    fun testPhoneNormalization_addsCountryCode() {
        val normalized = Validators.normalizePhone("9876543210")
        assertEquals("+919876543210", normalized)

        val alreadyWithCode = Validators.normalizePhone("+919876543210")
        assertEquals("+919876543210", alreadyWithCode)
    }

    @Test
    fun testUidFormat_requiresCorrectStructure() {
        // Valid 12 digits starting with valid date
        assertTrue(Validators.isValidUidFormat("202610131234"))
        // Invalid length
        assertFalse(Validators.isValidUidFormat("20261013123"))
        // Non-digits
        assertFalse(Validators.isValidUidFormat("20261013123a"))
        // Invalid month
        assertFalse(Validators.isValidUidFormat("202613131234"))
    }

    @Test
    fun testOtpViewModel_initialStateIsNotLoading() {
        val viewModel = OTPViewModel()
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.otpSent)
        assertFalse(viewModel.state.value.isVerified)
    }

    @Test
    fun testOtpViewModel_verifyOtpWithoutSession_clearsLoadingWithError() = runTest {
        val viewModel = OTPViewModel()
        viewModel.verifyOTP("9876543210", "123456")
        testDispatcher.scheduler.advanceUntilIdle()

        // When no active session exists, verifyOTP should not hang: isLoading must be false with error
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.error != null)
    }
}
