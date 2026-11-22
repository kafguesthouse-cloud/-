package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppRole
import com.example.data.CafeConfig
import com.example.ui.components.SyncStatusPill
import com.example.ui.theme.LocalPosColors

@Composable
fun RoleSelectionScreen(
    userEmail: String?,
    config: CafeConfig,
    pendingQueueCount: Int,
    isOnline: Boolean,
    hasPendingWrites: Boolean,
    onSelectRole: (AppRole) -> Unit,
    onSignOut: () -> Unit
) {
    val posColors = LocalPosColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top workspace bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Coffee,
                        contentDescription = "BunaPOS",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "BunaPOS",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = userEmail ?: "የካፌ አካውንት",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SyncStatusPill(isOnline = isOnline, hasPendingWrites = hasPendingWrites)
                IconButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ሚና ይምረጡ • Select Role",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "የካሸር ክፍል በፒን የተቆለፈ ነው • ትኬተር በቀጥታ ይከፈታል",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(22.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 600.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RoleCard(
                            titleAm = "ካሸር",
                            titleEn = "Cashier • ትዕዛዝ መቀበያ",
                            subtitle = "ትኬት ይጻፉ፣ ወደ ትኬተር ይላኩ፣ ሴቲንግ እና ሪፖርት ይመልከቱ",
                            icon = Icons.Filled.PointOfSale,
                            accentColor = MaterialTheme.colorScheme.primary,
                            badgeText = null,
                            requiresPin = true,
                            onClick = { onSelectRole(AppRole.CASHIER) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_cashier_card")
                        )
                        RoleCard(
                            titleAm = "ትኬተር",
                            titleEn = "Ticketer • ማዘጋጃ እና መሸኛ",
                            subtitle = "የሚመጡ ትዕዛዞችን ይመልከቱ፣ 'ወጥቷል' ወይም 'ተመልሷል' ይበሉ",
                            icon = Icons.AutoMirrored.Filled.FactCheck,
                            accentColor = posColors.dispatchGreen,
                            badgeText = if (pendingQueueCount > 0) "$pendingQueueCount በመጠባበቅ" else null,
                            requiresPin = false,
                            onClick = { onSelectRole(AppRole.TICKETER) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_ticketer_card")
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        RoleCard(
                            titleAm = "ካሸር",
                            titleEn = "Cashier • ትዕዛዝ መቀበያ",
                            subtitle = "ትኬት ይጻፉ፣ ወደ ትኬተር ይላኩ፣ ሴቲንግ እና ሪፖርት ይመልከቱ",
                            icon = Icons.Filled.PointOfSale,
                            accentColor = MaterialTheme.colorScheme.primary,
                            badgeText = null,
                            requiresPin = true,
                            onClick = { onSelectRole(AppRole.CASHIER) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("role_cashier_card")
                        )
                        RoleCard(
                            titleAm = "ትኬተር",
                            titleEn = "Ticketer • ማዘጋጃ እና መሸኛ",
                            subtitle = "የሚመጡ ትዕዛዞችን ይመልከቱ፣ 'ወጥቷል' ወይም 'ተመልሷል' ይበሉ",
                            icon = Icons.AutoMirrored.Filled.FactCheck,
                            accentColor = posColors.dispatchGreen,
                            badgeText = if (pendingQueueCount > 0) "$pendingQueueCount በመጠባበቅ" else null,
                            requiresPin = false,
                            onClick = { onSelectRole(AppRole.TICKETER) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("role_ticketer_card")
                        )
                    }
                }
            }

            if (!config.pinsCustomized) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, posColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ነባር የካሸር ፒን (Default Cashier PIN): 1111 — ትኬተር ያለ ፒን በቀጥታ ይከፈታል",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    titleAm: String,
    titleEn: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String?,
    requiresPin: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, posColors.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = titleAm,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (badgeText != null) {
                        Surface(
                            color = posColors.pendingAmberContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = posColors.pendingAmber,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (requiresPin) Icons.Filled.Lock else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (requiresPin) "PIN Protected" else "Open directly",
                            tint = if (requiresPin) MaterialTheme.colorScheme.onSurfaceVariant else accentColor,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = titleAm,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = titleEn,
                style = MaterialTheme.typography.titleMedium,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
