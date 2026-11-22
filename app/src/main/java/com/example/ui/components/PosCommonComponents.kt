package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.AppRole
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPosColors
import kotlin.math.roundToLong

fun formatBirr(amount: Double): String {
    val rounded = (amount * 100.0).roundToLong() / 100.0
    return if (rounded % 1.0 == 0.0) {
        "${rounded.toLong()} ብር"
    } else {
        String.format(java.util.Locale.US, "%.2f ብር", rounded)
    }
}

@Composable
fun SyncStatusPill(
    isOnline: Boolean,
    hasPendingWrites: Boolean,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current
    val (bg, fg, icon, label) = when {
        !isOnline -> Quad(
            posColors.pendingAmberContainer,
            posColors.pendingAmber,
            Icons.Filled.CloudOff,
            "ኦፍላይን"
        )
        hasPendingWrites -> Quad(
            posColors.pendingAmberContainer,
            posColors.pendingAmber,
            Icons.Filled.Sync,
            "Sync…"
        )
        else -> Quad(
            posColors.dispatchGreenContainer,
            posColors.dispatchGreenOnContainer,
            Icons.Filled.CloudDone,
            "Synced"
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.testTag("sync_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun PinLockDialog(
    role: AppRole,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    var pin by remember(role) { mutableStateOf("") }
    val posColors = LocalPosColors.current
    val title = when (role) {
        AppRole.CASHIER -> "ካሸር ፒን (Cashier PIN)"
        AppRole.TICKETER -> "ትኬተር ፒን (Ticketer PIN)"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, posColors.borderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pin_lock_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Masked PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    val slots = maxOf(4, pin.length)
                    for (i in 0 until slots) {
                        val filled = i < pin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        color = posColors.returnRed,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                Surface(
                                    onClick = {
                                        when (key) {
                                            "C" -> pin = ""
                                            "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            else -> if (pin.length < 8) pin += key
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("pin_key_$key")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "DEL") {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Backspace",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pin_cancel_button")
                    ) {
                        Text("ተወው • Cancel")
                    }
                    Button(
                        onClick = { onSubmitPin(pin) },
                        enabled = pin.length >= 4,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pin_unlock_button")
                    ) {
                        Text("ክፈት • Unlock")
                    }
                }
            }
        }
    }
}

@Composable
fun InitialAppPasswordDialog(
    linkedEmail: String,
    onSaveAppPassword: (String) -> Unit,
    onSkipForLater: () -> Unit
) {
    val posColors = LocalPosColors.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onSkipForLater) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, posColors.borderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("initial_app_password_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "App Password ይፍጠሩ",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ለ'$linkedEmail' App Password ይፍጠሩ፤ ከዚያ በኋላ ያለ Google OAuth በቀጥታ መግባት ይችላሉ።",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text("App Password (ቢያንስ 6 ቁምፊ)") },
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_app_password_input")
                )

                androidx.compose.material3.OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it.take(64) },
                    label = { Text("App Password ያረጋግጡ") },
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_app_password_confirm")
                )

                TextButton(
                    onClick = { showPassword = !showPassword },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (showPassword) "ደብቅ • Hide" else "አሳይ • Show")
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg ?: "",
                        color = posColors.returnRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onSkipForLater,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("setup_app_password_skip")
                    ) {
                        Text("በኋላ • Later")
                    }
                    Button(
                        onClick = {
                            when {
                                password.length < 6 -> errorMsg = "ቢያንስ 6 ቁምፊ ያስገቡ"
                                password != confirmPassword -> errorMsg = "አይመሳሰሉም • Mismatch"
                                else -> {
                                    errorMsg = null
                                    onSaveAppPassword(password)
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("setup_app_password_save")
                    ) {
                        Text("አስቀምጥ • Save")
                    }
                }
            }
        }
    }
}

