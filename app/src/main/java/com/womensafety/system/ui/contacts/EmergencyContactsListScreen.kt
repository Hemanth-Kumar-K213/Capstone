package com.womensafety.system.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.EmergencyContact
import com.womensafety.system.ui.components.ErrorMessage
import com.womensafety.system.ui.components.SuccessMessage
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.EmergencyContactViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsListScreen(
    viewModel: EmergencyContactViewModel,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadEmergencyContacts()
    }

    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            containerColor = SurfaceVariantDark,
            title = {
                Text(
                    "Delete Emergency Contact",
                    color = OnSurfaceDark,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to remove ${contactToDelete?.name} from your emergency contacts?",
                    color = OnSurfaceVariantDark
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = contactToDelete?.id ?: return@TextButton
                        viewModel.deleteContact(id)
                        contactToDelete = null
                    }
                ) {
                    Text("Delete", color = StatusDenied, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Emergency Contacts",
                        color = OnSurfaceDark,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurfaceDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = GradientAmber,
                contentColor = OnPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Contact")
            }
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            GradientDeepPurple.copy(alpha = 0.5f),
                            BackgroundDark
                        )
                    )
                )
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            ErrorMessage(state.error)
            SuccessMessage(state.actionSuccess)

            if (state.error != null || state.actionSuccess != null) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "These contacts will receive automatic SMS alerts with your live coordinates whenever emergency SOS is triggered.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GradientAmber)
                }
            } else if (state.contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(GradientRose.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.ContactPhone,
                                    contentDescription = null,
                                    tint = GradientRose,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No Emergency Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Tap the '+' button below to add friends, parents, or guardians to your SMS safety network.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.contacts, key = { it.id }) { contact ->
                        EmergencyContactCard(
                            contact = contact,
                            onEdit = { onNavigateToEdit(contact.id) },
                            onDelete = { contactToDelete = contact }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyContactCard(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(GradientRose.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = GradientRose,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariantDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Added on ${DateUtils.formatDate(contact.addedAt)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = OnSurfaceVariantDark.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit contact",
                    tint = GradientAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete contact",
                    tint = StatusDenied.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
