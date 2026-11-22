package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.CafeConfig
import com.example.data.CafeRepository
import com.example.data.LocalCafeCache
import com.example.ui.theme.LocalPosColors
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AuthScreen(
    linkedUser: FirebaseUser?,
    isWorkspaceLocked: Boolean,
    cachedConfig: CafeConfig?,
    onAuthSuccess: () -> Unit,
    onUnlockWithPassword: suspend (uid: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    // Mode 0 = Direct Gmail + App Password Login (default whenever workspace is locked/linked)
    // Mode 1 = Initial Google Setup ("በGoogle ይግቡ")
    var selectedMode by remember(linkedUser != null && isWorkspaceLocked) {
        mutableIntStateOf(if (linkedUser != null && isWorkspaceLocked) 0 else 1)
    }

    var isCheckingAutoSignIn by remember { mutableStateOf(linkedUser == null && !isWorkspaceLocked) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Direct Login form state (ONLY Gmail + App Password)
    var gmailInput by remember { mutableStateOf("") }
    var appPasswordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Only attempt silent Google auto-sign-in when there is NO locked workspace session.
    // Never redirect to Google OAuth if the user is logging in via Direct Gmail + App Password.
    LaunchedEffect(linkedUser, isWorkspaceLocked) {
        if (linkedUser == null && !isWorkspaceLocked) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isCheckingAutoSignIn = false
                    onAuthSuccess()
                },
                onUnauthenticated = {
                    isCheckingAutoSignIn = false
                },
                scope = scope
            )
        } else {
            isCheckingAutoSignIn = false
        }
    }

    val posColors = LocalPosColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, posColors.borderSubtle),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Coffee,
                        contentDescription = "BunaPOS Logo",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "BunaPOS • ቡና ፒኦኤስ",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "የካፌ ካሸር እና ትኬተር መቆጣጠሪያ\nFast Cafe Cashier & Ticketer Dispatch",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedMode == 0,
                        onClick = {
                            errorMessage = null
                            selectedMode = 0
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("auth_tab_direct_login")
                    ) {
                        Text("Gmail + Password")
                    }
                    SegmentedButton(
                        selected = selectedMode == 1,
                        onClick = {
                            errorMessage = null
                            selectedMode = 1
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("auth_tab_google_setup")
                    ) {
                        Text("Google ማገናኛ")
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = posColors.returnRedContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = posColors.returnRedOnContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (selectedMode == 0) {
                    // Direct Login using ONLY Gmail + App Password (NO redirect to Google OAuth)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = gmailInput,
                            onValueChange = { gmailInput = it.take(120) },
                            label = { Text("Gmail አድራሻ • Gmail Address") },
                            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("direct_login_email_input")
                        )

                        OutlinedTextField(
                            value = appPasswordInput,
                            onValueChange = { appPasswordInput = it.take(64) },
                            label = { Text("App Password") },
                            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("direct_login_password_input")
                        )

                        Button(
                            onClick = {
                                val enteredEmail = gmailInput.trim().lowercase()
                                val enteredPassword = appPasswordInput
                                if (enteredEmail.isEmpty() || !enteredEmail.contains("@")) {
                                    errorMessage = "ትክክለኛ Gmail አድራሻ ያስገቡ (Enter your Gmail address)"
                                    return@Button
                                }
                                if (enteredPassword.isEmpty()) {
                                    errorMessage = "App Password ያስገቡ (Enter your App Password)"
                                    return@Button
                                }

                                // Do NOT redirect to Google OAuth here. Verify against linked workspace.
                                if (linkedUser == null) {
                                    errorMessage = "ይህ ስልክ/ታብሌት ከካፌው Workspace ጋር ገና አልተገናኘም። " +
                                        "መጀመሪያ 'Google ማገናኛ' የሚለውን በመንካት አንድ ጊዜ የGoogle አካውንቱን ያገናኙ " +
                                        "(Firebase Email/Password ካልተከፈተ በቀር)።"
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val remoteConfig = withTimeoutOrNull(1800L) {
                                            val db = CafeRepository.createConfiguredFirestore(context)
                                            val snap = db.collection("users")
                                                .document(linkedUser.uid)
                                                .collection("config")
                                                .document("settings")
                                                .get()
                                                .await()
                                            val cfg = snap.toObject(CafeConfig::class.java)
                                            if (cfg != null) {
                                                LocalCafeCache(context.applicationContext).saveConfig(linkedUser.uid, cfg)
                                            }
                                            cfg
                                        }
                                        val effectiveConfig = remoteConfig ?: cachedConfig
                                        if (effectiveConfig == null || !effectiveConfig.appPasswordSet) {
                                            errorMessage = "ለዚህ Workspace App Password ገና አልተፈጠረም። መጀመሪያ በGoogle ይግቡና በሴቲንግ ውስጥ App Password ያዘጋጁ።"
                                            isLoading = false
                                            return@launch
                                        }

                                        val expectedEmail = effectiveConfig.linkedEmail
                                            .ifBlank { linkedUser.email ?: "" }
                                            .trim()
                                            .lowercase()

                                        val emailMatches = expectedEmail.isNotEmpty() && enteredEmail == expectedEmail
                                        val passwordMatches = AppPasswordCrypto.verifyAppPassword(
                                            password = enteredPassword,
                                            saltHex = effectiveConfig.appPasswordSalt,
                                            storedHash = effectiveConfig.appPasswordHash
                                        )

                                        if (emailMatches && passwordMatches) {
                                            onUnlockWithPassword(linkedUser.uid)
                                            appPasswordInput = ""
                                            isLoading = false
                                            onAuthSuccess()
                                        } else {
                                            isLoading = false
                                            errorMessage = "Gmail ወይም App Password አልተዛመደም • Incorrect Gmail or App Password"
                                        }
                                    } catch (e: Exception) {
                                        isLoading = false
                                        errorMessage = e.localizedMessage ?: "Login failed"
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("direct_login_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("በማረጋገጥ ላይ...")
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ግባ • Login",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (linkedUser != null) {
                            TextButton(
                                onClick = {
                                    errorMessage = null
                                    signOutGoogle(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onSignOutComplete = {
                                            selectedMode = 1
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("switch_google_account_button")
                            ) {
                                Text("ሌላ የGoogle አካውንት ቀይር • Switch Google Account")
                            }
                        }
                    }
                } else {
                    // Initial Google Account Setup / Workspace Verification
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FeatureRow(
                                icon = Icons.Filled.Sync,
                                text = "መጀመሪያ የካፌውን Workspace እና Gmail ለማረጋገጥ በGoogle ይግቡ"
                            )
                            FeatureRow(
                                icon = Icons.Filled.Key,
                                text = "ከዚያ በኋላ ለሚመጡ መግቢያዎች App Password በመፍጠር በቀጥታ ይግቡ"
                            )
                            FeatureRow(
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                text = "ደካማ ኢንተርኔት ላይም ያለ መቆራረጥ ይሰራል (Low-Bandwidth)"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isLoading = false
                                }
                            )
                        },
                        enabled = !isLoading && !isCheckingAutoSignIn,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading || isCheckingAutoSignIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isCheckingAutoSignIn) "በመፈተሽ ላይ..." else "በመግባት ላይ...",
                                style = MaterialTheme.typography.titleMedium
                            )
                        } else {
                            Text(
                                text = "በGoogle ይግቡ • Sign in with Google",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
