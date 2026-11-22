package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CafeBackupData
import com.example.data.CafeConfig
import com.example.data.MenuItem
import com.example.ui.CafeViewModel
import com.example.ui.components.formatBirr
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalPosColors

@Composable
fun SettingsScreen(
    config: CafeConfig,
    menuItems: List<MenuItem>,
    userEmail: String?,
    onSaveMenuItem: (code: Int, name: String, price: Double) -> Unit,
    onDeleteMenuItem: (code: Int) -> Unit,
    onSaveGeneralSettings: (cashierPin: String, ticketerPin: String, alias78To79: Boolean, staff: List<String>) -> Unit,
    onSaveAppPassword: (linkedEmail: String, newPassword: String) -> Unit,
    onRestoreBackup: (CafeBackupData, (Boolean) -> Unit) -> Unit,
    onShowMessage: (String) -> Unit,
    onUnlinkGoogleAccount: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("ምናሌ (1–1000)", "ሰራተኞች", "ፒን (PIN)", "አካውንት")

    var showPasteBackupDialog by remember { mutableStateOf(false) }
    var pasteBackupText by remember { mutableStateOf("") }
    var pendingRestorePreview by remember { mutableStateOf<CafeBackupData?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = CafeViewModel.buildBackupJson(config, menuItems)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                onShowMessage("ባክአፕ ፋይል ተቀምጧል ✓")
            } catch (e: Exception) {
                onShowMessage("ባክአፕ ማስቀመጥ አልተቻለም: ${e.localizedMessage ?: "Error"}")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }.orEmpty()
                CafeViewModel.parseBackupJson(raw).fold(
                    onSuccess = { parsed -> pendingRestorePreview = parsed },
                    onFailure = { err -> onShowMessage(err.localizedMessage ?: "Invalid backup") }
                )
            } catch (e: Exception) {
                onShowMessage("ፋይሉን ማንበብ አልተቻለም: ${e.localizedMessage ?: "Error"}")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("settings_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Cashier"
                                )
                            }
                            Text(
                                text = "ሴቲንግ • Settings",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        Surface(
                            onClick = { selectedTab = 3 },
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .padding(end = 6.dp)
                                .testTag("settings_backup_shortcut")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Backup,
                                    contentDescription = "Backup & Restore",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ባክአፕ",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { idx, title ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                text = {
                                    Text(
                                        text = title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("settings_tab_$idx")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (selectedTab) {
                0 -> MenuSettingsTab(
                    config = config,
                    menuItems = menuItems,
                    onSaveMenuItem = onSaveMenuItem,
                    onDeleteMenuItem = onDeleteMenuItem,
                    onToggleAlias78To79 = { enabled ->
                        onSaveGeneralSettings(
                            config.cashierPin,
                            config.ticketerPin,
                            enabled,
                            config.staff
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 780.dp)
                )
                1 -> StaffSettingsTab(
                    config = config,
                    onSaveStaff = { updatedStaff ->
                        onSaveGeneralSettings(
                            config.cashierPin,
                            config.ticketerPin,
                            config.alias78To79,
                            updatedStaff
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 780.dp)
                )
                2 -> PinSettingsTab(
                    config = config,
                    onSavePins = { newCashierPin, newTicketerPin ->
                        onSaveGeneralSettings(
                            newCashierPin,
                            newTicketerPin,
                            config.alias78To79,
                            config.staff
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 780.dp)
                )
                else -> AccountPasswordSettingsTab(
                    config = config,
                    menuItemCount = menuItems.size,
                    userEmail = userEmail,
                    onSaveAppPassword = onSaveAppPassword,
                    onExportBackupFile = {
                        val fileName = "bunapos_backup_${CafeViewModel.todayDayKey()}.json"
                        exportBackupLauncher.launch(fileName)
                    },
                    onImportBackupFile = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onCopyBackupJson = {
                        val json = CafeViewModel.buildBackupJson(config, menuItems)
                        clipboard.setText(AnnotatedString(json))
                        onShowMessage("ባክአፕ JSON ወደ Clipboard ተቀድቷል ✓")
                    },
                    onOpenPasteBackup = {
                        pasteBackupText = ""
                        showPasteBackupDialog = true
                    },
                    onUnlinkGoogleAccount = onUnlinkGoogleAccount,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 780.dp)
                )
            }
        }
    }

    if (showPasteBackupDialog) {
        AlertDialog(
            onDismissRequest = { showPasteBackupDialog = false },
            title = { Text("ባክአፕ JSON ይለጥፉ • Paste JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ከሌላ ስልክ የተቀዳውን የBunaPOS ባክአፕ JSON እዚህ ይለጥፉ:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = pasteBackupText,
                        onValueChange = { pasteBackupText = it },
                        placeholder = { Text("{\"version\":1, ...}") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("paste_backup_json_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        CafeViewModel.parseBackupJson(pasteBackupText).fold(
                            onSuccess = { parsed ->
                                showPasteBackupDialog = false
                                pendingRestorePreview = parsed
                            },
                            onFailure = { err ->
                                onShowMessage(err.localizedMessage ?: "Invalid backup JSON")
                            }
                        )
                    },
                    enabled = pasteBackupText.isNotBlank()
                ) {
                    Text("አረጋግጥ • Validate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteBackupDialog = false }) {
                    Text("ዝጋ")
                }
            }
        )
    }

    val preview = pendingRestorePreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { pendingRestorePreview = null },
            title = { Text("ባክአፕ ወደነበረበት መልስ?") },
            text = {
                Text(
                    "${preview.menuItems.size} የምናሌ ዕቃዎች (1–1000) እና ${preview.staff.size} ሰራተኞች ተገኝተዋል። ወደዚህ Workspace ይመለሱ?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestorePreview = null
                        onRestoreBackup(preview) {}
                    },
                    modifier = Modifier.testTag("confirm_restore_backup_button")
                ) {
                    Text("መልስ • Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestorePreview = null }) {
                    Text("ተመለስ")
                }
            }
        )
    }
}

@Composable
private fun MenuSettingsTab(
    config: CafeConfig,
    menuItems: List<MenuItem>,
    onSaveMenuItem: (code: Int, name: String, price: Double) -> Unit,
    onDeleteMenuItem: (code: Int) -> Unit,
    onToggleAlias78To79: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current
    var codeInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    val item79 = remember(menuItems) { menuItems.firstOrNull { it.code == 79 } }

    val filteredItems = remember(menuItems, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) menuItems
        else menuItems.filter {
            it.code.toString().contains(q) || it.name.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Explicit 78 -> 79 rule card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, posColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "78 to 79 mapping",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "ቁጥር 78 → 79 ህግ (Map 78 to 79)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (item79 != null) {
                                    "78 ሲመታ ወደ #79 (${item79.name} - ${formatBirr(item79.price)}) ይገባል"
                                } else {
                                    "78 ሲመታ #79 ላይ የተመዘገበውን ያስገባል"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = config.alias78To79,
                        onCheckedChange = onToggleAlias78To79,
                        modifier = Modifier.testTag("settings_alias_78_79_switch")
                    )
                }
            }
        }

        // Add / Edit Item Form (1..1000)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, posColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ዕቃ ጨምር / አስተካክል (1–1000)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() }.take(4)
                                codeInput = filtered
                                val num = filtered.toIntOrNull()
                                if (num != null) {
                                    menuItems.firstOrNull { it.code == num }?.let { match ->
                                        nameInput = match.name
                                        priceInput = if (match.price % 1.0 == 0.0) match.price.toLong().toString() else match.price.toString()
                                    }
                                }
                            },
                            label = { Text("ቁጥር (1-1000)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.35f)
                                .testTag("menu_code_input")
                        )
                        OutlinedTextField(
                            value = priceInput,
                            onValueChange = { raw ->
                                priceInput = raw.filter { it.isDigit() || it == '.' }.take(8)
                            },
                            label = { Text("ዋጋ (ብር)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.35f)
                                .testTag("menu_price_input")
                        )
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(60) },
                        label = { Text("ስም (ምሳሌ: ማኪያቶ)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("menu_name_input")
                    )

                    if (!formError.isNullOrBlank()) {
                        Text(
                            text = formError ?: "",
                            color = posColors.returnRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Button(
                        onClick = {
                            val code = codeInput.toIntOrNull()
                            val price = priceInput.toDoubleOrNull()
                            val cleanName = nameInput.trim()
                            when {
                                code == null || code !in 1..1000 -> formError = "ቁጥር ከ1 እስከ 1000 መሆን አለበት"
                                cleanName.isEmpty() -> formError = "የዕቃ ስም ያስገቡ"
                                price == null || price < 0 -> formError = "ትክክለኛ ዋጋ ያስገቡ"
                                else -> {
                                    formError = null
                                    onSaveMenuItem(code, cleanName, price)
                                    codeInput = ""
                                    nameInput = ""
                                    priceInput = ""
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_menu_item_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("አስቀምጥ • Save Item")
                    }
                }
            }
        }

        // Filter Search
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("በቁጥር (1–1000) ወይም በስም ይፈልጉ...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("menu_search_input")
            )
        }

        items(filteredItems, key = { it.code }) { item ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, posColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "#${item.code}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = formatBirr(item.price),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            codeInput = item.code.toString()
                            nameInput = item.name
                            priceInput = if (item.price % 1.0 == 0.0) item.price.toLong().toString() else item.price.toString()
                        }
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${item.name}")
                    }
                    IconButton(
                        onClick = { onDeleteMenuItem(item.code) }
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete ${item.name}",
                            tint = posColors.returnRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffSettingsTab(
    config: CafeConfig,
    onSaveStaff: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current
    var newStaffName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, posColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "አዲስ ሰራተኛ (አስተናጋጅ) ጨምር",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = newStaffName,
                        onValueChange = { newStaffName = it.take(50) },
                        label = { Text("የሰራተኛ ስም (ምሳሌ: ሰራተኛ 4)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_name_input")
                    )
                    Button(
                        onClick = {
                            val clean = newStaffName.trim()
                            if (clean.isNotEmpty() && !config.staff.contains(clean)) {
                                onSaveStaff(config.staff + clean)
                                newStaffName = ""
                            }
                        },
                        enabled = newStaffName.trim().isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("add_staff_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ሰራተኛ ጨምር • Add Staff")
                    }
                }
            }
        }

        items(config.staff, key = { it }) { staffName ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, posColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = staffName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (config.staff.size > 1) {
                        IconButton(
                            onClick = {
                                onSaveStaff(config.staff.filterNot { it == staffName })
                            }
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove $staffName",
                                tint = posColors.returnRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinSettingsTab(
    config: CafeConfig,
    onSavePins: (cashierPin: String, ticketerPin: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current
    var cashierPin by remember(config.cashierPin) { mutableStateOf(config.cashierPin) }
    var pinError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, posColors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "የካሸር ፒን መቆለፊያ (4–8 አሃዝ)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "ፒን የሚያስፈልገው ለ'ካሸር' ብቻ ነው፤ 'ትኬተር' ያለ ፒን በቀጥታ ይከፈታል።",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = cashierPin,
                    onValueChange = { cashierPin = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("የካሸር ፒን (Cashier PIN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashier_pin_input")
                )

                if (!pinError.isNullOrBlank()) {
                    Text(
                        text = pinError ?: "",
                        color = posColors.returnRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        if (cashierPin.length !in 4..8) {
                            pinError = "የካሸር ፒን ከ4–8 ቁጥር መሆን አለበት"
                        } else {
                            pinError = null
                            onSavePins(cashierPin, config.ticketerPin)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_pins_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ፒን አስቀምጥ • Save PIN")
                }
            }
        }
    }
}

@Composable
private fun AccountPasswordSettingsTab(
    config: CafeConfig,
    menuItemCount: Int,
    userEmail: String?,
    onSaveAppPassword: (linkedEmail: String, newPassword: String) -> Unit,
    onExportBackupFile: () -> Unit,
    onImportBackupFile: () -> Unit,
    onCopyBackupJson: () -> Unit,
    onOpenPasteBackup: () -> Unit,
    onUnlinkGoogleAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val posColors = LocalPosColors.current
    val verifiedEmail = config.linkedEmail.ifBlank { userEmail.orEmpty() }.trim()
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, posColors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "App Password • የመግቢያ የይለፍ ቃል",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Surface(
                        color = if (config.appPasswordSet) posColors.dispatchGreenContainer else posColors.pendingAmberContainer,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (config.appPasswordSet) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = posColors.dispatchGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (config.appPasswordSet) "ተዘጋጅቷል" else "አልተዘጋጀም",
                                color = if (config.appPasswordSet) posColors.dispatchGreenOnContainer else posColors.pendingAmber,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = "የተገናኘ Gmail አድራሻ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = verifiedEmail.ifBlank { "Google Account" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Text(
                    text = "App Password ካዘጋጁ በኋላ ወደዚህ Workspace በGmail እና በApp Password ብቻ (ያለ Google OAuth) መግባት ይችላሉ። እዚህ ማደስ ወይም መቀየር ይችላሉ።",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it.take(64) },
                    label = {
                        Text(if (config.appPasswordSet) "አዲስ App Password (New/Reset)" else "App Password ይፍጠሩ (Min 6)")
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_app_password_input")
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it.take(64) },
                    label = { Text("App Password ያረጋግጡ • Confirm") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_app_password_confirm")
                )

                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg ?: "",
                        color = posColors.returnRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        when {
                            verifiedEmail.isBlank() -> errorMsg = "የተረጋገጠ Gmail አድራሻ አልተገኘም"
                            newPassword.length < 6 -> errorMsg = "App Password ቢያንስ 6 ቁምፊ መሆን አለበት"
                            newPassword != confirmPassword -> errorMsg = "ሁለቱ የይለፍ ቃሎች አይመሳሰሉም"
                            else -> {
                                errorMsg = null
                                onSaveAppPassword(verifiedEmail, newPassword)
                                newPassword = ""
                                confirmPassword = ""
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_app_password_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (config.appPasswordSet) "App Password አድስ • Update" else "App Password አስቀምጥ • Save"
                    )
                }
            }
        }

        // Menu (1-1000) & Staff Backup / Export & Import / Restore Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, posColors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "ምናሌ እና ሰራተኞች ባክአፕ • Backup & Restore",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$menuItemCount የምናሌ ዕቃዎች • ${config.staff.size} ሰራተኞች",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "ምናሌ (1–1000)፣ ዋጋዎች እና የሰራተኞች ዝርዝር ወደ ፋይል አውጥተው ወደ ሌላ ስልክ ማስገባት ይችላሉ።",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportBackupFile,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_backup_file_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("አውጣ • Export")
                    }
                    OutlinedButton(
                        onClick = onImportBackupFile,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("import_backup_file_button")
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("መልስ • Import")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onCopyBackupJson,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_backup_json_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("JSON ቅዳ")
                    }
                    TextButton(
                        onClick = onOpenPasteBackup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("paste_backup_json_button")
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("JSON ለጥፍ")
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onUnlinkGoogleAccount,
            border = BorderStroke(1.dp, posColors.returnRed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = posColors.returnRed),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("unlink_google_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Google አካውንት ቀይር / ውጣ • Unlink Google Account")
        }
    }
}

