package com.womensafety.system.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val isDark = LocalDarkTheme.current

    LaunchedEffect(loginState.isLoggedIn) {
        if (loginState.isLoggedIn) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.clearError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val bgGradient = if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1E1B4B).copy(alpha = 0.5f),
                    BackgroundDark
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFEEF2FF),
                    BackgroundLight
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(padding)
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Sign in to access your safety protection network",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            ErrorMessage(loginState.error)
            if (loginState.error != null) Spacer(modifier = Modifier.height(10.dp))

            SuccessMessage(loginState.successMessage)
            if (loginState.successMessage != null) Spacer(modifier = Modifier.height(10.dp))

            AppTextField(
                value = loginState.email,
                onValueChange = { viewModel.updateLoginEmail(it) },
                label = "Primary Gmail Address",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!loginState.showEmailLinkLogin) {
                PasswordTextField(
                    value = loginState.password,
                    onValueChange = { viewModel.updateLoginPassword(it) },
                    label = "Password",
                    imeAction = ImeAction.Done
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.forgotPassword() }) {
                        Text(
                            text = "Forgot Password?",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                PrimaryButton(
                    text = "LOGIN",
                    onClick = { viewModel.loginWithEmailAndPassword() },
                    isLoading = loginState.isLoading
                )
            } else {
                if (loginState.emailLinkSent) {
                    SuccessMessage("Sign-in link sent to ${loginState.email}. Check your email and tap the link to log in.")
                } else {
                    PrimaryButton(
                        text = "SEND SIGN-IN LINK",
                        onClick = { viewModel.sendEmailLoginLink() },
                        isLoading = loginState.isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SecondaryButton(
                text = if (loginState.showEmailLinkLogin) "LOGIN WITH PASSWORD" else "PASSWORDLESS SIGN-IN LINK",
                onClick = { viewModel.toggleEmailLinkLogin() }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = onNavigateToSignup) {
                    Text(
                        text = "SIGN UP",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
