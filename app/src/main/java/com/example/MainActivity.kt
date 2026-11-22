package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AppRoute
import com.example.data.CafeRepository
import com.example.data.LocalCafeCache
import com.example.data.NetworkMonitor
import com.example.data.OrderStatus
import com.example.data.OrderSyncSnapshot
import com.example.data.UiState
import com.example.ui.CafeViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutGoogle
import com.example.ui.components.InitialAppPasswordDialog
import com.example.ui.components.PinLockDialog
import com.example.ui.screens.CashierScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TicketerScreen
import com.example.ui.theme.BunaPosTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BunaPosTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val localCache = remember(context) { LocalCafeCache(context.applicationContext) }
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    val uid = user?.uid

    val isWorkspaceLocked by remember(uid, localCache) {
        if (uid != null) localCache.observeWorkspaceLocked(uid) else flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)

    val cachedConfig by remember(uid, localCache) {
        if (uid != null) localCache.observeCachedConfig(uid) else flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    if (user == null || isWorkspaceLocked) {
        AuthScreen(
            linkedUser = user,
            isWorkspaceLocked = isWorkspaceLocked,
            cachedConfig = cachedConfig,
            onAuthSuccess = { currentUser = auth.currentUser },
            onUnlockWithPassword = { unlockedUid ->
                localCache.setWorkspaceLocked(unlockedUid, false)
            }
        )
    } else {
        key(uid) {
            AuthenticatedCafeApp(
                currentUserId = uid!!,
                userEmail = user.email,
                localCache = localCache,
                onSignedOut = { currentUser = auth.currentUser }
            )
        }
    }
}

@Composable
private fun AuthenticatedCafeApp(
    currentUserId: String,
    userEmail: String?,
    localCache: LocalCafeCache,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: CafeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = CafeRepository.createConfiguredFirestore(app)
                val cache = LocalCafeCache(app)
                val repo = CafeRepository(db = db, localCache = cache)
                val net = NetworkMonitor(app)
                CafeViewModel(
                    repository = repo,
                    networkMonitor = net,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val route by viewModel.route.collectAsStateWithLifecycle()
    val pinPromptRole by viewModel.pinPromptRole.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val config by viewModel.configState.collectAsStateWithLifecycle()
    val hasLoadedConfig by viewModel.hasLoadedConfig.collectAsStateWithLifecycle()
    val menuItems by viewModel.menuState.collectAsStateWithLifecycle()
    val keypadState by viewModel.keypadState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val ticketerKeepAwake by viewModel.ticketerKeepAwake.collectAsStateWithLifecycle()
    val ticketerSoundEnabled by viewModel.ticketerSoundEnabled.collectAsStateWithLifecycle()
    val selectedDayKey by viewModel.selectedDayKey.collectAsStateWithLifecycle()
    val todayOrdersUi by viewModel.todayOrdersState.collectAsStateWithLifecycle()
    val reportOrdersUi by viewModel.ordersState.collectAsStateWithLifecycle()
    val unpaidOrders by viewModel.unpaidOrdersState.collectAsStateWithLifecycle()

    var skippedInitialAppPasswordPrompt by remember(currentUserId) { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.toastMessages.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    val todaySnapshot = (todayOrdersUi as? UiState.Success<OrderSyncSnapshot>)?.data
    val allTodayOrders = todaySnapshot?.orders.orEmpty()
    val activeTodayOrders = remember(allTodayOrders, config.dayEpoch) {
        allTodayOrders.filter { it.dayEpoch == config.dayEpoch }
    }
    val hasPendingWrites = todaySnapshot?.hasPendingWrites ?: false
    val pendingQueueCount = remember(activeTodayOrders) {
        activeTodayOrders.count { it.orderStatus() == OrderStatus.PENDING }
    }

    val reportOrders = (reportOrdersUi as? UiState.Success<OrderSyncSnapshot>)?.data?.orders.orEmpty()
    val effectiveEmail = config.linkedEmail.ifBlank { userEmail.orEmpty() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (route) {
                AppRoute.RoleSelect -> RoleSelectionScreen(
                    userEmail = effectiveEmail.ifBlank { userEmail },
                    config = config,
                    pendingQueueCount = pendingQueueCount,
                    isOnline = isOnline,
                    hasPendingWrites = hasPendingWrites,
                    onSelectRole = viewModel::requestRoleAccess,
                    onSignOut = {
                        if (config.appPasswordSet) {
                            scope.launch {
                                localCache.setWorkspaceLocked(currentUserId, true)
                            }
                        } else {
                            signOutGoogle(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = onSignedOut
                            )
                        }
                    }
                )

                AppRoute.Cashier -> CashierScreen(
                    keypadState = keypadState,
                    config = config,
                    menuItems = menuItems,
                    todayOrders = activeTodayOrders,
                    unpaidOrders = unpaidOrders,
                    isOnline = isOnline,
                    hasPendingWrites = hasPendingWrites,
                    onDigit = viewModel::onDigitPressed,
                    onMultiply = viewModel::onMultiplyPressed,
                    onBackspace = viewModel::onBackspacePressed,
                    onAddNext = { viewModel.onAddNextItem() },
                    onClearTicket = viewModel::onClearTicket,
                    onRemoveLine = viewModel::onRemoveDraftLine,
                    onToggleDraftUnpaid = viewModel::toggleDraftUnpaid,
                    onToggleDraftUrgent = viewModel::toggleDraftUrgent,
                    onUpdateDraftNote = viewModel::updateDraftNote,
                    onSendClicked = viewModel::onSendClicked,
                    onDismissStaffPicker = viewModel::dismissStaffPicker,
                    onSelectStaffAndSend = viewModel::onConfirmSendOrder,
                    onUpdatePendingOrder = viewModel::updatePendingOrder,
                    onCancelPendingOrder = viewModel::cancelPendingOrder,
                    onMarkOrderPaid = viewModel::markOrderPaid,
                    onOpenSettings = { viewModel.navigateTo(AppRoute.Settings) },
                    onOpenReport = {
                        viewModel.setReportDayKey(CafeViewModel.todayDayKey())
                        viewModel.navigateTo(AppRoute.Report)
                    },
                    onExitRole = { viewModel.navigateTo(AppRoute.RoleSelect) }
                )

                AppRoute.Ticketer -> TicketerScreen(
                    orders = activeTodayOrders,
                    isOrdersLoaded = todayOrdersUi is UiState.Success,
                    isOnline = isOnline,
                    hasPendingWrites = hasPendingWrites,
                    keepScreenAwake = ticketerKeepAwake,
                    soundEnabled = ticketerSoundEnabled,
                    onToggleKeepScreenAwake = viewModel::setTicketerKeepAwake,
                    onToggleSoundEnabled = viewModel::setTicketerSoundEnabled,
                    onMarkCompleted = viewModel::markOrderCompleted,
                    onMarkReturned = { orderId -> viewModel.markOrderReturned(orderId) },
                    onExitRole = { viewModel.navigateTo(AppRoute.RoleSelect) }
                )

                AppRoute.Settings -> SettingsScreen(
                    config = config,
                    menuItems = menuItems,
                    userEmail = effectiveEmail.ifBlank { userEmail },
                    onSaveMenuItem = viewModel::saveMenuItem,
                    onDeleteMenuItem = viewModel::deleteMenuItem,
                    onSaveGeneralSettings = viewModel::updateSettings,
                    onSaveAppPassword = { email, pw -> viewModel.saveAppPassword(email, pw) },
                    onRestoreBackup = viewModel::restoreBackup,
                    onShowMessage = { msg ->
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(msg)
                        }
                    },
                    onUnlinkGoogleAccount = {
                        scope.launch {
                            localCache.setWorkspaceLocked(currentUserId, false)
                            signOutGoogle(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = onSignedOut
                            )
                        }
                    },
                    onBack = { viewModel.navigateTo(AppRoute.Cashier) }
                )

                AppRoute.Report -> ReportScreen(
                    selectedDayKey = selectedDayKey,
                    activeDayEpoch = config.dayEpoch,
                    orders = reportOrders,
                    onChangeDayKey = viewModel::setReportDayKey,
                    onStartNewDay = viewModel::startNewDay,
                    onBack = { viewModel.navigateTo(AppRoute.Cashier) }
                )
            }

            val roleToUnlock = pinPromptRole
            if (roleToUnlock != null) {
                PinLockDialog(
                    role = roleToUnlock,
                    errorMessage = pinError,
                    onDismiss = viewModel::dismissPinPrompt,
                    onSubmitPin = viewModel::submitRolePin
                )
            }

            if (hasLoadedConfig && !config.appPasswordSet && !skippedInitialAppPasswordPrompt && effectiveEmail.isNotBlank()) {
                InitialAppPasswordDialog(
                    linkedEmail = effectiveEmail,
                    onSaveAppPassword = { newPassword ->
                        viewModel.saveAppPassword(effectiveEmail, newPassword) { success ->
                            if (success) skippedInitialAppPasswordPrompt = true
                        }
                    },
                    onSkipForLater = {
                        skippedInitialAppPasswordPrompt = true
                    }
                )
            }
        }
    }
}
