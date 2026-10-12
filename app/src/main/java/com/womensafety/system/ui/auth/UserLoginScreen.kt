package com.womensafety.system.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserLoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onBack: () -> Unit
) {
    val loginState by viewModel.loginState.collectAsState()

    if (loginState.isLoggedIn) {
        onLoginSuccess()
        return
    }

    Scaffold(
        containerColor = SafetyDarkBackground,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SafetyTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SafetyDarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SafetyDarkBackground)
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Sign In",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SafetyTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Enter your credentials to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = SafetyTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            ErrorMessage(loginState.error)
            if (loginState.error != null) Spacer(modifier = Modifier.height(8.dp))

            SuccessMessage(loginState.successMessage)
            if (loginState.successMessage != null) Spacer(modifier = Modifier.height(8.dp))

            AppTextField(
                value = loginState.email,
                onValueChange = { viewModel.updateLoginEmail(it) },
                label = "Email Address",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!loginState.showEmailLinkLogin) {
                PasswordTextField(
                    value = loginState.password,
                    onValueChange = { viewModel.updateLoginPassword(it) },
                    label = "Password"
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { viewModel.forgotPassword() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Forgot Password?", color = SafetyPrimaryBlue)
                }

                Spacer(modifier = Modifier.height(20.dp))

                SafetyPillButton(
                    text = if (loginState.isLoading) "Signing In..." else "Sign In",
                    onClick = { if (!loginState.isLoading) viewModel.loginWithEmailAndPassword() },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (loginState.showEmailLinkLogin) {
                if (loginState.emailLinkSent) {
                    SuccessMessage("Sign-in link sent to ${loginState.email}. Check your email and tap the link to log in.")
                } else {
                    SafetyPillButton(
                        text = if (loginState.isLoading) "Sending..." else "Send Sign-in Link",
                        onClick = { if (!loginState.isLoading) viewModel.sendEmailLoginLink() },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            SafetyOutlinedPillButton(
                text = if (loginState.showEmailLinkLogin) "Use Password Instead" else "Sign In Without Password",
                onClick = { viewModel.toggleEmailLinkLogin() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SafetyTextSecondary
                )
                TextButton(onClick = onNavigateToSignup) {
                    Text(
                        "Sign Up",
                        fontWeight = FontWeight.Bold,
                        color = SafetyPrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
