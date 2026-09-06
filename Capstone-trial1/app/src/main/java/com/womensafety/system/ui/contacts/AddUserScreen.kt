package com.womensafety.system.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.TrustedContactHomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserScreen(
    viewModel: TrustedContactHomeViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.addUserState.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) {
            kotlinx.coroutines.delay(2000)
            viewModel.resetAddUserState()
            onBack()
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Add User", color = OnSurfaceDark) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetAddUserState()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = OnSurfaceDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientDeepPurple.copy(alpha = 0.4f), BackgroundDark)
                    )
                )
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "ADD USER",
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 2.sp),
                fontWeight = FontWeight.Bold,
                color = GradientRose
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Enter the User's current Safety UID to send a connection request.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantDark
            )

            Spacer(modifier = Modifier.height(24.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            if (state.success) {
                GlassCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(
                            StatusApproved.copy(alpha = 0.1f),
                            StatusApproved.copy(alpha = 0.05f)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(StatusApproved.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StatusApproved
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Permission request sent successfully!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = StatusApproved
                        )
                    }
                }
            } else {
                AppTextField(
                    value = state.uid,
                    onValueChange = { viewModel.updateUidInput(it) },
                    label = "Enter Current User UID",
                    keyboardType = KeyboardType.Number
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "UID format: 12 digits (e.g., 202608180472)",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.addUserByUid() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !state.isLoading,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GradientRose
                    )
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = OnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "ADD USER",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
