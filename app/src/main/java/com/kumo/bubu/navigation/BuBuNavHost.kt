package com.kumo.bubu.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.kumo.bubu.BuBuApplication
import com.kumo.bubu.R
import com.kumo.bubu.core.ui.components.BuBuBottomNavigation
import com.kumo.bubu.feature.dashboard.DashboardRoute
import com.kumo.bubu.feature.dashboard.DashboardViewModel
import com.kumo.bubu.feature.fuel.FuelFormRoute
import com.kumo.bubu.feature.fuel.FuelFormViewModel
import com.kumo.bubu.feature.fuel.FuelEconomyReviewRoute
import com.kumo.bubu.feature.fuel.FuelEconomyReviewViewModel
import com.kumo.bubu.feature.history.VehicleHistoryRoute
import com.kumo.bubu.feature.history.VehicleHistoryViewModel
import com.kumo.bubu.feature.reports.ReportsRoute
import com.kumo.bubu.feature.reports.ReportsViewModel
import com.kumo.bubu.feature.reminder.RemindersRoute
import com.kumo.bubu.feature.reminder.RemindersViewModel
import com.kumo.bubu.domain.model.ReportSource
import com.kumo.bubu.feature.settings.SettingsScreen
import com.kumo.bubu.feature.settings.StatutoryReminderSettingsViewModel
import com.kumo.bubu.feature.settings.CsvExportDialog
import com.kumo.bubu.feature.settings.CsvExportEvent
import com.kumo.bubu.feature.settings.CsvExportViewModel
import com.kumo.bubu.feature.settings.BackupConfirmationDialog
import com.kumo.bubu.feature.settings.BackupViewModel
import com.kumo.bubu.feature.settings.BackupReminderViewModel
import com.kumo.bubu.feature.settings.RestorePreviewDialog
import com.kumo.bubu.feature.settings.RestoreViewModel
import com.kumo.bubu.feature.settings.CloudBackupListDialog
import com.kumo.bubu.feature.settings.CloudBackupViewModel
import com.kumo.bubu.feature.settings.GoogleDriveAuthorizationCoordinator
import com.kumo.bubu.feature.settings.GoogleDriveAuthorizationGateway
import com.kumo.bubu.domain.repository.CloudBackupRepository
import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.feature.service.ServiceFormRoute
import com.kumo.bubu.feature.service.ServiceFormViewModel
import com.kumo.bubu.feature.service.ServiceRecordsRoute
import com.kumo.bubu.feature.service.ServiceRecordsViewModel
import com.kumo.bubu.feature.service.ServiceSettingsScreen
import com.kumo.bubu.feature.service.ServiceTypeManagementRoute
import com.kumo.bubu.feature.service.ServiceTypeManagementViewModel
import com.kumo.bubu.feature.expense.ExpenseFormRoute
import com.kumo.bubu.feature.expense.ExpenseFormViewModel
import com.kumo.bubu.feature.expense.ExpenseRecordsRoute
import com.kumo.bubu.feature.expense.ExpenseRecordsViewModel
import com.kumo.bubu.feature.vehicle.VehicleFormRoute
import com.kumo.bubu.feature.vehicle.VehicleFormViewModel
import com.kumo.bubu.feature.vehicle.VehiclesRoute
import com.kumo.bubu.feature.vehicle.VehiclesViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File

private const val ADD_VEHICLE_ROUTE = "vehicle/add"
private const val VEHICLE_ID_ARGUMENT = "vehicleId"
private const val EDIT_VEHICLE_ROUTE = "vehicle/edit/{$VEHICLE_ID_ARGUMENT}"
private const val VEHICLE_HISTORY_ROUTE = "vehicle/history/{$VEHICLE_ID_ARGUMENT}"
private const val ADD_FUEL_ROUTE = "fuel/add"
private const val ADD_FUEL_FOR_VEHICLE_ROUTE = "fuel/add/{$VEHICLE_ID_ARGUMENT}"
private const val FUEL_ID_ARGUMENT = "fuelId"
private const val EDIT_FUEL_ROUTE = "fuel/edit/{$FUEL_ID_ARGUMENT}"
private const val FUEL_ECONOMY_REVIEW_ROUTE = "fuel/economy-review"
private const val ADD_SERVICE_ROUTE = "service/add"
private const val ADD_SERVICE_FOR_VEHICLE_ROUTE = "service/add/{$VEHICLE_ID_ARGUMENT}"
private const val SERVICE_TYPE_ID_ARGUMENT = "serviceTypeId"
private const val ADD_SERVICE_WITH_TYPE_ROUTE = "$ADD_SERVICE_FOR_VEHICLE_ROUTE?$SERVICE_TYPE_ID_ARGUMENT={$SERVICE_TYPE_ID_ARGUMENT}"
private const val SERVICE_RECORDS_ROUTE = "service/records"
private const val SERVICE_ID_ARGUMENT = "serviceId"
private const val EDIT_SERVICE_ROUTE = "service/edit/{$SERVICE_ID_ARGUMENT}"
private const val SERVICE_SETTINGS_ROUTE = "service/settings"
private const val SERVICE_TYPE_MANAGEMENT_ROUTE = "service/settings/types"
private const val EXPENSE_RECORDS_ROUTE = "expense/records"
private const val ADD_EXPENSE_ROUTE = "expense/add"
private const val EXPENSE_ID_ARGUMENT = "expenseId"
private const val EDIT_EXPENSE_ROUTE = "expense/edit/{$EXPENSE_ID_ARGUMENT}"
private const val REMINDERS_ROUTE = "reminders"
private const val REMINDER_ID_ARGUMENT = "reminderId"
private const val REMINDER_ROUTE = "$REMINDERS_ROUTE/{$REMINDER_ID_ARGUMENT}"

@Composable
fun BuBuNavHost(
    initialReminderId: Long? = null,
    initialOpenReminders: Boolean = false,
    onNotificationHandled: () -> Unit = {},
    cloudBackupRepositoryOverride: CloudBackupRepository? = null,
    googleAuthorizationFactory: (Context) -> GoogleDriveAuthorizationGateway = ::GoogleDriveAuthorizationCoordinator,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val application = LocalContext.current.applicationContext as BuBuApplication
    val vehicleRepository = application.container.vehicleRepository
    val fuelRepository = application.container.fuelRepository
    val fuelPriceRepository = application.container.fuelPriceRepository
    val serviceRepository = application.container.serviceRepository
    val expenseRepository = application.container.expenseRepository
    val reportRepository = application.container.reportRepository
    val reminderRepository = application.container.reminderRepository
    val csvExportRepository = application.container.csvExportRepository
    val backupRepository = application.container.backupRepository
    val restoreRepository = application.container.restoreRepository
    val cloudBackupRepository = cloudBackupRepositoryOverride ?: application.container.cloudBackupRepository

    var interruptedMaintenanceRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val navigationReady = backStackEntry != null
    LaunchedEffect(initialReminderId, initialOpenReminders, navigationReady) {
        if (!navigationReady) return@LaunchedEffect
        val destination = when {
            initialReminderId != null -> "$REMINDERS_ROUTE/$initialReminderId"
            initialOpenReminders -> REMINDERS_ROUTE
            else -> return@LaunchedEffect
        }
        if (initialReminderId == null &&
            runCatching { navController.getBackStackEntry(REMINDERS_ROUTE) }.isSuccess
        ) {
            navController.currentBackStackEntry
                ?.takeIf { it.destination.route == ADD_SERVICE_WITH_TYPE_ROUTE }
                ?.arguments?.let { arguments ->
                    interruptedMaintenanceRoute?.let { navController.clearBackStack(it) }
                    val vehicleId = arguments.getLong(VEHICLE_ID_ARGUMENT)
                    val serviceTypeId = arguments.getLong(SERVICE_TYPE_ID_ARGUMENT)
                    interruptedMaintenanceRoute = "service/add/$vehicleId?serviceTypeId=$serviceTypeId"
                }
            navController.popBackStack(REMINDERS_ROUTE, inclusive = false, saveState = true)
        }
        navController.navigate(destination) { launchSingleTop = true }
        onNotificationHandled()
    }

    Scaffold(
        bottomBar = {
            if (TopLevelDestination.entries.any { it.route == currentRoute }) {
                BuBuBottomNavigation(navController)
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DASHBOARD.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                val viewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.factory(vehicleRepository, fuelRepository, reminderRepository, serviceRepository),
                )
                DashboardRoute(
                    viewModel = viewModel,
                    onAddVehicle = { navController.navigate(ADD_VEHICLE_ROUTE) },
                    onOpenVehicleHistory = { vehicleId -> navController.navigate("vehicle/history/$vehicleId") },
                    onAddFuel = { vehicleId -> navController.navigate("fuel/add/$vehicleId") },
                    onAddService = { vehicleId -> navController.navigate("service/add/$vehicleId") },
                    onOpenReminders = { navController.navigate(REMINDERS_ROUTE) },
                )
            }
            composable(TopLevelDestination.REMINDERS.route) {
                val viewModel: RemindersViewModel = viewModel(
                    factory = RemindersViewModel.factory(
                        reminderRepository,
                        serviceRepository,
                        vehicleRepository,
                        application.container.reminderNotificationSettings,
                        application.container.reminderNotificationScheduler,
                    ),
                )
                RemindersRoute(
                    viewModel = viewModel,
                    onBack = null,
                    onCreateMaintenance = { vehicleId, serviceTypeId ->
                        val route = "service/add/$vehicleId?serviceTypeId=$serviceTypeId"
                        navController.navigate(route) {
                            restoreState = route == interruptedMaintenanceRoute
                        }
                    },
                )
            }
            composable(
                route = REMINDER_ROUTE,
                arguments = listOf(navArgument(REMINDER_ID_ARGUMENT) { type = NavType.LongType }),
            ) { entry ->
                val reminderId = requireNotNull(entry.arguments?.getLong(REMINDER_ID_ARGUMENT))
                val viewModel: RemindersViewModel = viewModel(
                    factory = RemindersViewModel.factory(
                        reminderRepository,
                        serviceRepository,
                        vehicleRepository,
                        application.container.reminderNotificationSettings,
                        application.container.reminderNotificationScheduler,
                    ),
                )
                RemindersRoute(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    highlightedReminderId = reminderId,
                )
            }
            composable(TopLevelDestination.REPORTS.route) {
                val viewModel: ReportsViewModel = viewModel(
                    factory = ReportsViewModel.factory(
                        vehicleRepository,
                        reportRepository,
                        application.container.reportLayoutSettings,
                    ),
                )
                ReportsRoute(
                    viewModel = viewModel,
                    onOpenSource = { source ->
                        when (source) {
                            is ReportSource.Fuel -> navController.navigate("fuel/edit/${source.recordId}")
                            is ReportSource.Service -> navController.navigate("service/edit/${source.recordId}")
                        }
                    },
                )
            }
            composable("vehicles") {
                val viewModel: VehiclesViewModel = viewModel(
                    factory = VehiclesViewModel.factory(vehicleRepository),
                )
                VehiclesRoute(
                    viewModel = viewModel,
                    onAddVehicle = { navController.navigate(ADD_VEHICLE_ROUTE) },
                    onEditVehicle = { vehicleId -> navController.navigate("vehicle/edit/$vehicleId") },
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                val context = LocalContext.current
                val activity = context as? ComponentActivity
                val coroutineScope = rememberCoroutineScope()
                val csvExportViewModel: CsvExportViewModel = viewModel(
                    factory = CsvExportViewModel.factory(vehicleRepository, csvExportRepository),
                )
                val csvExportState by csvExportViewModel.uiState.collectAsStateWithLifecycle()
                var showCsvExportDialog by rememberSaveable { mutableStateOf(false) }
                val backupViewModel: BackupViewModel = viewModel(
                    factory = BackupViewModel.factory(backupRepository),
                )
                val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
                val backupReminderViewModel: BackupReminderViewModel = viewModel(
                    factory = BackupReminderViewModel.factory(
                        application.container.backupReminderSettings,
                        application.container.backupReminderScheduler,
                    ),
                )
                val backupReminderState by backupReminderViewModel.uiState.collectAsStateWithLifecycle()
                var showBackupDialog by rememberSaveable { mutableStateOf(false) }
                val restoreViewModel: RestoreViewModel = viewModel(
                    factory = RestoreViewModel.factory(restoreRepository, cloudBackupRepository),
                )
                val restoreState by restoreViewModel.uiState.collectAsStateWithLifecycle()
                var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
                var showDeleteRecoveryDialog by rememberSaveable { mutableStateOf(false) }
                val cloudBackupViewModel: CloudBackupViewModel = viewModel(
                    factory = CloudBackupViewModel.factory(cloudBackupRepository),
                )
                val cloudBackupState by cloudBackupViewModel.uiState.collectAsStateWithLifecycle()
                var showCloudBackupList by rememberSaveable { mutableStateOf(false) }
                var backupPendingDeletion by remember { mutableStateOf<CloudBackup?>(null) }
                val cloudAccountEmail = (cloudBackupState.connection as? com.kumo.bubu.domain.model.CloudBackupConnection.Connected)
                    ?.account?.email
                LaunchedEffect(cloudAccountEmail?.lowercase()) {
                    backupPendingDeletion = null
                    showCloudBackupList = false
                }
                val googleDriveAuthorization = remember(context, googleAuthorizationFactory) {
                    googleAuthorizationFactory(context)
                }
                val resolution by cloudBackupViewModel.resolution.collectAsStateWithLifecycle()
                val authorizationRequestId = cloudBackupState.authorizationRequestId
                key(authorizationRequestId) {
                    val authorizationLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartIntentSenderForResult(),
                    ) { result ->
                        val request = authorizationRequestId?.let(cloudBackupViewModel::consumeAuthorizationResult)
                        if (request != null) {
                            if (result.resultCode != android.app.Activity.RESULT_OK) {
                                cloudBackupViewModel.onAuthorizationCancelled()
                                return@rememberLauncherForActivityResult
                            }
                            googleDriveAuthorization.completeAuthorization(
                                data = result.data,
                                account = requireNotNull(request.account),
                                onAuthorized = { account, token -> cloudBackupViewModel.onAuthorized(request.action, account.email, token) },
                                onError = cloudBackupViewModel::onAuthorizationFailed,
                            )
                        }
                    }
                    LaunchedEffect(resolution, authorizationRequestId) {
                        authorizationRequestId?.let(cloudBackupViewModel::consumeResolution)?.let { request ->
                            try {
                                authorizationLauncher.launch(request)
                            } catch (error: Exception) {
                                android.util.Log.w("BuBuDriveAuth", "Unable to launch Google authorization.", error)
                                cloudBackupViewModel.onAuthorizationFailed(com.kumo.bubu.domain.model.CloudBackupError.NotConnected)
                            }
                        }
                    }
                }
                DisposableEffect(cloudBackupViewModel, restoreViewModel, activity) {
                    onDispose {
                        if (activity?.isChangingConfigurations != true) {
                            cloudBackupViewModel.leaveSettings()
                            csvExportViewModel.cancelDestination()
                            restoreViewModel.clear()
                        }
                    }
                }
                LaunchedEffect(cloudBackupViewModel, activity) {
                    cloudBackupViewModel.authorizationRequests.collect { request ->
                        if (!cloudBackupViewModel.isPendingAuthorization(request.id)) return@collect
                        val hostActivity = activity
                        if (hostActivity == null) {
                            cloudBackupViewModel.onAuthorizationFailed(com.kumo.bubu.domain.model.CloudBackupError.NotConnected)
                        } else {
                            googleDriveAuthorization.authorize(
                                activity = hostActivity,
                                onResolution = { requestToLaunch -> cloudBackupViewModel.onResolutionReady(request.id, requestToLaunch) },
                                scope = coroutineScope,
                                onAccountSelected = { account -> cloudBackupViewModel.onAccountSelected(request.id, account) },
                                onAuthorized = { account, token ->
                                    cloudBackupViewModel.consumeAuthorization(request.id)?.let {
                                        cloudBackupViewModel.onAuthorized(it.action, account.email, token)
                                    }
                                },
                                onError = { error ->
                                    cloudBackupViewModel.consumeAuthorization(request.id)?.let {
                                        cloudBackupViewModel.onAuthorizationFailed(error)
                                    }
                                },
                            )
                        }
                    }
                }
                LaunchedEffect(cloudBackupViewModel) {
                    cloudBackupViewModel.restoreRequests.collect { request ->
                        val source = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            File(request.download.localFilePath),
                        )
                        showCloudBackupList = false
                        restoreViewModel.previewCloudDownload(source.toString(), request.download)
                        showRestoreDialog = true
                    }
                }
                LaunchedEffect(restoreState.completed) {
                    if (restoreState.completed) {
                        showRestoreDialog = false
                        restoreViewModel.clear()
                    }
                }
                val csvDestinationRequestId by csvExportViewModel.destinationRequestId.collectAsStateWithLifecycle()
                key(csvDestinationRequestId) {
                    val createCsvExportDocument = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/zip"),
                    ) { destination ->
                        csvDestinationRequestId?.let { csvExportViewModel.onDestinationResult(it, destination?.toString()) }
                    }
                    LaunchedEffect(csvDestinationRequestId) {
                        val requestId = csvDestinationRequestId
                        if (requestId != null && csvExportViewModel.consumeDestinationLaunch(requestId)) {
                            try {
                                createCsvExportDocument.launch("bubu-export-${LocalDateTime.now().format(CSV_EXPORT_FILE_TIME_FORMAT)}.zip")
                            } catch (error: Exception) {
                                android.util.Log.w("BuBuCsvExport", "Unable to launch CSV destination selection.", error)
                                csvExportViewModel.onDestinationLaunchFailed(requestId)
                            }
                        }
                    }
                }
                val createBackupDocument = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
                ) { destination ->
                    destination?.let { uri -> backupViewModel.createBackup(uri.toString()) }
                }
                val openBackupDocument = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { source ->
                    source?.let { uri ->
                        restoreViewModel.preview(uri.toString())
                        showRestoreDialog = true
                    }
                }
                val createRecoveryDocument = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
                ) { destination ->
                    destination?.let { uri -> restoreViewModel.exportRecoveryBackup(uri.toString()) }
                }
                val statutorySettingsViewModel: StatutoryReminderSettingsViewModel = viewModel(
                    factory = StatutoryReminderSettingsViewModel.factory(
                        application.container.statutoryReminderSettings,
                        reminderRepository,
                    ),
                )
                val statutorySettingsState by
                    statutorySettingsViewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    onManageVehicles = { navController.navigate("vehicles") },
                    onManageServiceSettings = { navController.navigate(SERVICE_SETTINGS_ROUTE) },
                    onReviewFuelEconomy = { navController.navigate(FUEL_ECONOMY_REVIEW_ROUTE) },
                    statutoryReminderSettingsUiState = statutorySettingsState,
                    onTaxAndFeeEnabledChange = statutorySettingsViewModel::setTaxAndFeeEnabled,
                    csvExportUiState = csvExportState,
                    onExportCsv = {
                        csvExportViewModel.onEvent(CsvExportEvent.ClearResult)
                        showCsvExportDialog = true
                    },
                    backupUiState = backupState,
                    onCreateBackup = {
                        backupViewModel.clearResult()
                        showBackupDialog = true
                    },
                    backupReminderUiState = backupReminderState,
                    onBackupReminderEnabledChange = backupReminderViewModel::setEnabled,
                    restoreUiState = restoreState,
                    onRestoreBackup = {
                        restoreViewModel.clear()
                        openBackupDocument.launch(arrayOf("*/*"))
                    },
                    onExportRecoveryBackup = {
                        restoreState.recoveryBackup?.let { recovery ->
                            createRecoveryDocument.launch(recovery.fileName)
                        }
                    },
                    onDeleteRecoveryBackup = { showDeleteRecoveryDialog = true },
                    cloudBackupUiState = cloudBackupState,
                    onConnectGoogleDrive = cloudBackupViewModel::connect,
                    onCreateCloudBackup = cloudBackupViewModel::upload,
                    onRestoreCloudBackup = {
                        showCloudBackupList = true
                        cloudBackupViewModel.loadBackups()
                    },
                    onDisconnectGoogleDrive = {
                        val email = (cloudBackupState.connection as? com.kumo.bubu.domain.model.CloudBackupConnection.Connected)
                            ?.account?.email
                        if (email == null) return@SettingsScreen
                        googleDriveAuthorization.revoke(
                            accountEmail = email,
                            onComplete = cloudBackupViewModel::disconnected,
                            onError = cloudBackupViewModel::onAuthorizationFailed,
                        )
                    },
                )
                if (showCloudBackupList) {
                    CloudBackupListDialog(
                        state = cloudBackupState,
                        onDismiss = { showCloudBackupList = false },
                        onRefresh = cloudBackupViewModel::loadBackups,
                        onDownload = cloudBackupViewModel::download,
                        onDelete = { backup -> backupPendingDeletion = backup },
                    )
                }
                backupPendingDeletion?.let { backup ->
                    AlertDialog(
                        onDismissRequest = { backupPendingDeletion = null },
                        title = { Text(stringResource(R.string.cloud_backup_delete_title)) },
                        text = { Text(stringResource(R.string.cloud_backup_delete_message, backup.fileName)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    backupPendingDeletion = null
                                    cloudBackupViewModel.delete(backup.id)
                                },
                            ) { Text(stringResource(R.string.cloud_backup_list_delete)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { backupPendingDeletion = null }) {
                                Text(stringResource(R.string.restore_cancel))
                            }
                        },
                    )
                }
                if (showCsvExportDialog) {
                    CsvExportDialog(
                        state = csvExportState,
                        onEvent = csvExportViewModel::onEvent,
                        onConfirm = {
                            if (csvExportViewModel.prepareDestination()) {
                                showCsvExportDialog = false
                            }
                        },
                        onDismiss = { showCsvExportDialog = false },
                    )
                }
                if (showBackupDialog) {
                    BackupConfirmationDialog(
                        onConfirm = {
                            showBackupDialog = false
                            createBackupDocument.launch(
                                "bubu-backup-${LocalDateTime.now().format(CSV_EXPORT_FILE_TIME_FORMAT)}.bubu",
                            )
                        },
                        onDismiss = { showBackupDialog = false },
                    )
                }
                if (showRestoreDialog) {
                    RestorePreviewDialog(
                        state = restoreState,
                        onConfirm = restoreViewModel::restore,
                        onDismiss = {
                            showRestoreDialog = false
                            restoreViewModel.clear()
                        },
                    )
                }
                if (showDeleteRecoveryDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteRecoveryDialog = false },
                        title = { Text(stringResource(R.string.recovery_backup_delete_title)) },
                        text = { Text(stringResource(R.string.recovery_backup_delete_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteRecoveryDialog = false
                                    restoreViewModel.deleteRecoveryBackup()
                                },
                            ) { Text(stringResource(R.string.recovery_backup_delete_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteRecoveryDialog = false }) {
                                Text(stringResource(R.string.restore_cancel))
                            }
                        },
                    )
                }
            }
            composable(SERVICE_SETTINGS_ROUTE) {
                ServiceSettingsScreen(
                    onManageTypes = { navController.navigate(SERVICE_TYPE_MANAGEMENT_ROUTE) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(FUEL_ECONOMY_REVIEW_ROUTE) {
                val viewModel: FuelEconomyReviewViewModel = viewModel(
                    factory = FuelEconomyReviewViewModel.factory(fuelRepository, vehicleRepository),
                )
                FuelEconomyReviewRoute(viewModel, onBack = { navController.popBackStack() })
            }
            composable(SERVICE_TYPE_MANAGEMENT_ROUTE) {
                val viewModel: ServiceTypeManagementViewModel = viewModel(
                    factory = ServiceTypeManagementViewModel.factory(serviceRepository, vehicleRepository),
                )
                ServiceTypeManagementRoute(viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = VEHICLE_HISTORY_ROUTE,
                arguments = listOf(navArgument(VEHICLE_ID_ARGUMENT) { type = NavType.LongType }),
            ) { entry ->
                val vehicleId = requireNotNull(entry.arguments?.getLong(VEHICLE_ID_ARGUMENT))
                val viewModel: VehicleHistoryViewModel = viewModel(
                    factory = VehicleHistoryViewModel.factory(
                        vehicleId = vehicleId,
                        vehicleRepository = vehicleRepository,
                        fuelRepository = fuelRepository,
                        serviceRepository = serviceRepository,
                    ),
                )
                VehicleHistoryRoute(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onEditFuel = { fuelId -> navController.navigate("fuel/edit/$fuelId") },
                    onEditMaintenance = { serviceId -> navController.navigate("service/edit/$serviceId") },
                )
            }
            composable(ADD_VEHICLE_ROUTE) {
                val viewModel: VehicleFormViewModel = viewModel(
                    factory = VehicleFormViewModel.factory(vehicleRepository, draftRepository = application.container.formDraftRepository),
                )
                VehicleFormRoute(
                    viewModel = viewModel,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ADD_FUEL_ROUTE) {
                val viewModel: FuelFormViewModel = viewModel(
                    factory = FuelFormViewModel.factory(fuelRepository, fuelPriceRepository, vehicleRepository, draftRepository = application.container.formDraftRepository),
                )
                FuelFormRoute(
                    viewModel = viewModel,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ADD_FUEL_FOR_VEHICLE_ROUTE,
                arguments = listOf(navArgument(VEHICLE_ID_ARGUMENT) { type = NavType.LongType }),
            ) { entry ->
                val vehicleId = requireNotNull(entry.arguments?.getLong(VEHICLE_ID_ARGUMENT))
                val viewModel: FuelFormViewModel = viewModel(
                    factory = FuelFormViewModel.factory(
                        fuelRepository,
                        fuelPriceRepository,
                        vehicleRepository,
                        initialVehicleId = vehicleId,
                        draftRepository = application.container.formDraftRepository,
                    ),
                )
                FuelFormRoute(viewModel, onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(ADD_SERVICE_ROUTE) {
                val viewModel: ServiceFormViewModel = viewModel(factory = ServiceFormViewModel.factory(serviceRepository, vehicleRepository, draftRepository = application.container.formDraftRepository))
                ServiceFormRoute(viewModel, onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(
                route = ADD_SERVICE_WITH_TYPE_ROUTE,
                arguments = listOf(
                    navArgument(VEHICLE_ID_ARGUMENT) { type = NavType.LongType },
                    navArgument(SERVICE_TYPE_ID_ARGUMENT) { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val vehicleId = requireNotNull(entry.arguments?.getLong(VEHICLE_ID_ARGUMENT))
                val serviceTypeId = entry.arguments?.getLong(SERVICE_TYPE_ID_ARGUMENT)?.takeIf { it >= 0L }
                val viewModel: ServiceFormViewModel = viewModel(
                    factory = ServiceFormViewModel.factory(
                        serviceRepository,
                        vehicleRepository,
                        initialVehicleId = vehicleId,
                        initialServiceTypeId = serviceTypeId,
                        draftRepository = application.container.formDraftRepository,
                    ),
                )
                ServiceFormRoute(viewModel, onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(SERVICE_RECORDS_ROUTE) {
                val viewModel: ServiceRecordsViewModel = viewModel(factory = ServiceRecordsViewModel.factory(serviceRepository, vehicleRepository))
                ServiceRecordsRoute(viewModel, onAdd = { navController.navigate(ADD_SERVICE_ROUTE) }, onEdit = { serviceId -> navController.navigate("service/edit/$serviceId") })
            }
            composable(EXPENSE_RECORDS_ROUTE) {
                val viewModel: ExpenseRecordsViewModel = viewModel(factory = ExpenseRecordsViewModel.factory(expenseRepository))
                ExpenseRecordsRoute(viewModel, onAdd = { navController.navigate(ADD_EXPENSE_ROUTE) }, onEdit = { id -> navController.navigate("expense/edit/$id") })
            }
            composable(ADD_EXPENSE_ROUTE) {
                val viewModel: ExpenseFormViewModel = viewModel(factory = ExpenseFormViewModel.factory(expenseRepository, vehicleRepository))
                ExpenseFormRoute(viewModel, onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(route = EDIT_EXPENSE_ROUTE, arguments = listOf(navArgument(EXPENSE_ID_ARGUMENT) { type = NavType.LongType })) { entry ->
                val expenseId = requireNotNull(entry.arguments?.getLong(EXPENSE_ID_ARGUMENT))
                val viewModel: ExpenseFormViewModel = viewModel(factory = ExpenseFormViewModel.factory(expenseRepository, vehicleRepository, expenseId))
                ExpenseFormRoute(viewModel, onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(route = EDIT_SERVICE_ROUTE, arguments = listOf(navArgument(SERVICE_ID_ARGUMENT) { type = NavType.LongType })) { entry ->
                val serviceId = requireNotNull(entry.arguments?.getLong(SERVICE_ID_ARGUMENT))
                val viewModel: ServiceFormViewModel = viewModel(factory = ServiceFormViewModel.factory(serviceRepository, vehicleRepository, serviceId, draftRepository = application.container.formDraftRepository))
                ServiceFormRoute(viewModel, onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(
                route = EDIT_FUEL_ROUTE,
                arguments = listOf(navArgument(FUEL_ID_ARGUMENT) { type = NavType.LongType }),
            ) { entry ->
                val fuelId = requireNotNull(entry.arguments?.getLong(FUEL_ID_ARGUMENT))
                val viewModel: FuelFormViewModel = viewModel(
                    factory = FuelFormViewModel.factory(fuelRepository, fuelPriceRepository, vehicleRepository, fuelId, draftRepository = application.container.formDraftRepository),
                )
                FuelFormRoute(
                    viewModel = viewModel,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = EDIT_VEHICLE_ROUTE,
                arguments = listOf(navArgument(VEHICLE_ID_ARGUMENT) { type = NavType.LongType }),
            ) { entry ->
                val vehicleId = requireNotNull(entry.arguments?.getLong(VEHICLE_ID_ARGUMENT))
                val viewModel: VehicleFormViewModel = viewModel(
                    factory = VehicleFormViewModel.factory(vehicleRepository, vehicleId, application.container.formDraftRepository),
                )
                VehicleFormRoute(
                    viewModel = viewModel,
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private val CSV_EXPORT_FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
