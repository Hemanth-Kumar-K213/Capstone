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
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurfaceDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientDeepPurple.copy(alpha = 0.5f), BackgroundDark)
                    )
                )
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "USER LOGIN",
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 2.sp),
                fontWeight = FontWeight.Bold,
                color = GradientGold
            )

            Spacer(modifier = Modifier.height(32.dp))

            ErrorMessage(loginState.error)
            if (loginState.error != null) Spacer(modifier = Modifier.height(8.dp))

            SuccessMessage(loginState.successMessage)
            if (loginState.successMessage != null) Spacer(modifier = Modifier.height(8.dp))

            AppTextField(
                value = loginState.email,
                onValueChange = { viewModel.updateLoginEmail(it) },
                label = "Primary Gmail",
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
                    Text("Forgot Password?", color = GradientAmber)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.loginWithEmailAndPassword() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !loginState.isLoading,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = GradientAmber)
                ) {
                    if (loginState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("LOGIN", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (loginState.showEmailLinkLogin) {
                if (loginState.emailLinkSent) {
                    SuccessMessage("Sign-in link sent to ${loginState.email}. Check your email and tap the link to log in.")
                } else {
                    Button(
                        onClick = { viewModel.sendEmailLoginLink() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !loginState.isLoading,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = GradientAmber)
                    ) {
                        if (loginState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = OnPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("SEND SIGN-IN LINK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = { viewModel.toggleEmailLinkLogin() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDark)
            ) {
                Text(
                    if (loginState.showEmailLinkLogin) "LOGIN WITH PASSWORD" else "PASSWORDLESS LOGIN"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Don't have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantDark
            )

            TextButton(onClick = onNavigateToSignup) {
                Text(
                    "SIGN UP",
                    fontWeight = FontWeight.Bold,
                    color = GradientGold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
