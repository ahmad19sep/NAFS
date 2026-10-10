package com.ahmad.nafs.ui.auth

import com.ahmad.nafs.core.auth.SignUpGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInputValidatorTest {
    @Test
    fun `sign in requires a valid email and password`() {
        assertEquals(
            "Enter a valid email address.",
            AuthInputValidator.validate(
                AuthUiState(email = "not-an-email", password = "secret"),
            ),
        )
        assertEquals(
            "Enter your password.",
            AuthInputValidator.validate(AuthUiState(email = "user@example.com")),
        )
        assertNull(
            AuthInputValidator.validate(
                AuthUiState(email = " USER@example.com ", password = "secret"),
            ),
        )
    }

    @Test
    fun `sign up requires identity fields and minimum password length`() {
        val base = AuthUiState(
            mode = AuthMode.SIGN_UP,
            email = "user@example.com",
            password = "123456",
        )

        assertEquals("Enter your full name.", AuthInputValidator.validate(base))
        assertEquals(
            "Select your gender.",
            AuthInputValidator.validate(base.copy(name = "A User")),
        )
        assertEquals(
            "Password must be at least 6 characters.",
            AuthInputValidator.validate(
                base.copy(name = "A User", gender = SignUpGender.MALE, password = "12345"),
            ),
        )
        assertNull(
            AuthInputValidator.validate(
                base.copy(name = "A User", gender = SignUpGender.FEMALE),
            ),
        )
    }

    @Test
    fun `password reset only requires a valid email`() {
        assertNull(
            AuthInputValidator.validate(
                AuthUiState(mode = AuthMode.FORGOT_PASSWORD, email = "user@example.com"),
            ),
        )
    }
}
