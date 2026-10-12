package com.womensafety.system.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.ui.components.ErrorMessage
import com.womensafety.system.ui.components.SuccessMessage
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.PermissionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestScreen(
    viewModel: PermissionViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadRelationships() }

    val pendingRequests = state.relationships.filter {
        it.permissionState == PermissionState.PENDING
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Permission Requests",
                        color = OnSurfaceDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            SuccessMessage(state.actionSuccess)
            if (state.actionSuccess != null) Spacer(modifier = Modifier.height(8.dp))

            ErrorMessage(state.error)
            if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientAmber)
                }
            } else if (pendingRequests.isEmpty()) {
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No pending requests.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceVariantDark
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(pendingRequests) { rel ->

                        GlassCard(
                            accentGradient = Brush.horizontalGradient(
                                colors = listOf(
                                    GradientAmber.copy(alpha = 0.08f),
                                    GradientRose.copy(alpha = 0.04f)
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(
                                    "TRUSTED CONTACT REQUEST",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        letterSpacing = 2.sp
                                    ),
                                    color = GradientAmber,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(GradientRose.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = GradientRose
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            rel.trustedContactName.ifBlank { "Unknown Contact" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OnSurfaceDark
                                        )
                                        if (rel.trustedContactPhone.isNotBlank()) {
                                            Text(
                                                "Phone: ${rel.trustedContactPhone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OnSurfaceVariantDark
                                            )
                                        }
                                        Text(
                                            "Requested: ${DateUtils.formatDateTime(rel.requestedAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurfaceVariantDark
                                        )
                                        Text(
                                            "Status: PENDING",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StatusPending
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.approveRequest(rel.id) },
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StatusApproved
                                        )
                                    ) {
                                        Text("ALLOW", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.denyRequest(rel.id) },
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = StatusDenied
                                        )
                                    ) {
                                        Text("DENY", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
