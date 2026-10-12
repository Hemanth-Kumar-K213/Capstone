package com.womensafety.system.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.theme.*

/**
 * Standard Google Pixel Personal Safety Top App Bar
 */
@Composable
fun SafetyTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    unreadNotificationsCount: Int = 0,
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Balancer spacer on left
        Spacer(modifier = Modifier.width(48.dp))

        // Center Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp
            ),
            color = SafetyTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        // Right Action Icons: Bell & Avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = if (unreadNotificationsCount > 0) SafetyPrimaryBlue else SafetyTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (unreadNotificationsCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(StatusDenied),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadNotificationsCount > 9) "9+" else unreadNotificationsCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Profile",
                    tint = SafetyPrimaryBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Primary Light Blue Pill Button (Matches Pixel Personal Safety CTA)
 */
@Composable
fun SafetyPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = SafetyPrimaryBlue,
    contentColor: Color = SafetyOnPrimaryBlue
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Outlined Pill Button (e.g., "Try a demo", "Manage settings")
 */
@Composable
fun SafetyOutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(100.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF44474E)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = SafetyTextPrimary
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Circular Feature Badge for Hero Header (Location, SOS, Car, Power)
 */
@Composable
fun SafetyHeroBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0xFF1B232E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SafetyPrimaryBlue,
                modifier = Modifier.size(24.dp)
            )
        } else if (text != null) {
            Text(
                text = text,
                color = SafetyPrimaryBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Google Personal Safety Central Illustration (Clover shield + Standing figure)
 */
@Composable
fun SafetyHeroIllustration(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // 1. Draw clover / petal protective shield outline
        val petalColor = Color(0xFF8AB4F8)
        val strokeWidth = 3.dp.toPx()

        // Clover / flower petals behind figure
        val petalRadius = 40.dp.toPx()
        val petalOffset = 30.dp.toPx()

        // Top petal
        drawArc(
            color = petalColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - petalRadius, cy - petalOffset - petalRadius * 1.5f),
            size = Size(petalRadius * 2, petalRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Left petal
        drawArc(
            color = petalColor,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - petalOffset - petalRadius * 1.5f, cy - petalRadius),
            size = Size(petalRadius * 2, petalRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Right petal
        drawArc(
            color = petalColor,
            startAngle = 270f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx + petalOffset - petalRadius * 0.5f, cy - petalRadius),
            size = Size(petalRadius * 2, petalRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Diagonal accent line
        drawLine(
            color = Color(0xFFE88B88),
            start = Offset(cx - 50.dp.toPx(), cy + 10.dp.toPx()),
            end = Offset(cx + 45.dp.toPx(), cy - 40.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 2. Ground baseline
        val groundY = cy + 60.dp.toPx()
        drawLine(
            color = Color(0xFF3C4043),
            start = Offset(cx - 85.dp.toPx(), groundY),
            end = Offset(cx + 85.dp.toPx(), groundY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 3. Figure
        // Head
        val headY = cy - 25.dp.toPx()
        drawCircle(
            color = Color(0xFFC67C4E), // skin tone
            radius = 12.dp.toPx(),
            center = Offset(cx, headY)
        )
        // Hair cap
        drawArc(
            color = Color(0xFF202124),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - 13.dp.toPx(), headY - 13.dp.toPx()),
            size = Size(26.dp.toPx(), 18.dp.toPx())
        )

        // Yellow Torso (Shirt) with hands on hips
        val shirtPath = Path().apply {
            moveTo(cx - 18.dp.toPx(), headY + 12.dp.toPx())
            lineTo(cx + 18.dp.toPx(), headY + 12.dp.toPx())
            lineTo(cx + 15.dp.toPx(), headY + 36.dp.toPx())
            lineTo(cx - 15.dp.toPx(), headY + 36.dp.toPx())
            close()
        }
        drawPath(shirtPath, color = Color(0xFFF9AB00))

        // Arms (Akimbo on hips)
        val armStroke = 8.dp.toPx()
        // Left arm
        drawLine(
            color = Color(0xFFF9AB00),
            start = Offset(cx - 16.dp.toPx(), headY + 14.dp.toPx()),
            end = Offset(cx - 28.dp.toPx(), headY + 26.dp.toPx()),
            strokeWidth = armStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFF9AB00),
            start = Offset(cx - 28.dp.toPx(), headY + 26.dp.toPx()),
            end = Offset(cx - 14.dp.toPx(), headY + 36.dp.toPx()),
            strokeWidth = armStroke,
            cap = StrokeCap.Round
        )
        // Right arm
        drawLine(
            color = Color(0xFFF9AB00),
            start = Offset(cx + 16.dp.toPx(), headY + 14.dp.toPx()),
            end = Offset(cx + 28.dp.toPx(), headY + 26.dp.toPx()),
            strokeWidth = armStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFF9AB00),
            start = Offset(cx + 28.dp.toPx(), headY + 26.dp.toPx()),
            end = Offset(cx + 14.dp.toPx(), headY + 36.dp.toPx()),
            strokeWidth = armStroke,
            cap = StrokeCap.Round
        )

        // Blue Pants (Legs wide stance)
        val legStroke = 12.dp.toPx()
        // Left Leg
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(cx - 6.dp.toPx(), headY + 36.dp.toPx()),
            end = Offset(cx - 22.dp.toPx(), groundY - 4.dp.toPx()),
            strokeWidth = legStroke,
            cap = StrokeCap.Round
        )
        // Right Leg
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(cx + 6.dp.toPx(), headY + 36.dp.toPx()),
            end = Offset(cx + 22.dp.toPx(), groundY - 4.dp.toPx()),
            strokeWidth = legStroke,
            cap = StrokeCap.Round
        )

        // Charcoal Boots/Shoes
        drawRoundRect(
            color = Color(0xFF5F6368),
            topLeft = Offset(cx - 30.dp.toPx(), groundY - 6.dp.toPx()),
            size = Size(18.dp.toPx(), 7.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF5F6368),
            topLeft = Offset(cx + 12.dp.toPx(), groundY - 6.dp.toPx()),
            size = Size(18.dp.toPx(), 7.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        )
    }
}

/**
 * Standard Card in Features Screen (Image 3)
 */
@Composable
fun SafetyFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    primaryButtonText: String? = null,
    onPrimaryButtonClick: (() -> Unit)? = null,
    secondaryButtonText: String? = null,
    onSecondaryButtonClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = SafetyCardBackground
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Icon pill, Title, Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Circular Light Blue Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SafetyPrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SafetyOnPrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = SafetyTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SafetyTextMuted,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = SafetyTextSecondary
            )

            // Buttons row at bottom
            if (primaryButtonText != null || secondaryButtonText != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (secondaryButtonText != null && onSecondaryButtonClick != null) {
                        SafetyOutlinedPillButton(
                            text = secondaryButtonText,
                            onClick = onSecondaryButtonClick
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    if (primaryButtonText != null && onPrimaryButtonClick != null) {
                        SafetyPillButton(
                            text = primaryButtonText,
                            onClick = onPrimaryButtonClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard Grouped Card Section for "Your info" (Image 1)
 */
@Composable
fun SafetyListGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            color = SafetyTextPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = SafetyCardBackground
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Standard Row Item inside SafetyListGroup (Image 1)
 */
@Composable
fun SafetyListRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = SafetyTextPrimary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = SafetyTextPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = SafetyTextMuted
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SafetyTextMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}
