package com.womensafety.system.ui.auth

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSignupStep1Screen(
    viewModel: AuthViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.signupState.collectAsState()

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Sign Up") },
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

            StepIndicator(currentStep = 1, totalSteps = 6)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Personal Details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Enter your basic information to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            AppTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = "Full Name",
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                value = state.phone,
                onValueChange = { viewModel.updatePhone(it) },
                label = "Phone Number (e.g., +91XXXXXXXXXX)",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                value = state.email,
                onValueChange = { viewModel.updateEmail(it) },
                label = "Primary Gmail",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "NEXT",
                onClick = {
                    if (viewModel.validateStep1()) {
                        onNext()
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
