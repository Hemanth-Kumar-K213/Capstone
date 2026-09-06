package com.womensafety.system.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSignupStep5Screen(
    viewModel: AuthViewModel,
    onAccountCreated: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.signupState.collectAsState()

    LaunchedEffect(state.accountCreated) {
        if (state.accountCreated) {
            onAccountCreated()
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Create Password") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            StepIndicator(currentStep = 5, totalSteps = 6)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Create Password",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Create a strong password for your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            PasswordTextField(
                value = state.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = "Create Password",
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordTextField(
                value = state.confirmPassword,
                onValueChange = { viewModel.updateConfirmPassword(it) },
                label = "Confirm Password",
                imeAction = ImeAction.Done
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Password must be at least 8 characters with uppercase, lowercase, number, and special character.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "CREATE ACCOUNT",
                onClick = { viewModel.createAccount() },
                isLoading = state.isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
