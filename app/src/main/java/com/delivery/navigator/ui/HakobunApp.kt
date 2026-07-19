package com.delivery.navigator.ui

import androidx.compose.ui.res.stringResource
import com.delivery.navigator.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.delivery.navigator.data.calculateEndOfDaySummary
import com.delivery.navigator.data.calculateNearestRoute
import com.delivery.navigator.data.createCourseAddressFromInput
import com.delivery.navigator.data.createDeliveryPackageFromRegistration
import com.delivery.navigator.data.createEndOfDayCalendarIntent
import com.delivery.navigator.data.createPackagesFromCourse
import com.delivery.navigator.data.currentRouteOrigin
import com.delivery.navigator.data.geocodeAddressPublic
import com.delivery.navigator.data.exportPackages
import com.delivery.navigator.data.fetchDrivingRoutePoints
import com.delivery.navigator.data.hidesMapPin
import com.delivery.navigator.data.importPackages
import com.delivery.navigator.data.LocalDeliveryStore
import com.delivery.navigator.data.regularCourses
import com.delivery.navigator.data.samplePackages
import com.delivery.navigator.data.searchPostalCode
import kotlinx.coroutines.launch
import com.delivery.navigator.model.AccountMenuItem
import com.delivery.navigator.model.AddressCandidate
import com.delivery.navigator.model.DeliveryPackage
import com.delivery.navigator.model.DeliveryStatus
import com.delivery.navigator.model.EndOfDaySummary
import com.delivery.navigator.model.PackageColorType
import com.delivery.navigator.model.PackageRegistrationResult
import com.delivery.navigator.model.PackageShape
import com.delivery.navigator.model.PackageSizeOption
import com.delivery.navigator.model.RegistrationTimeWindow
import com.delivery.navigator.model.RegularCourse
import com.delivery.navigator.model.TimeWindow
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.flow.drop
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import com.delivery.navigator.model.MembershipPlan
import com.delivery.navigator.model.UserProfile
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import android.os.Build
import android.os.LocaleList
import android.app.LocaleManager
import androidx.compose.material3.Switch
import com.delivery.navigator.notification.isNotificationEnabled
import com.delivery.navigator.notification.setNotificationEnabled
import com.delivery.navigator.notification.showAnnouncementNotification
import com.delivery.navigator.notification.scheduleAllTimeWindowAlarms

@Composable
fun HakobunApp() {
    val context = LocalContext.current
    val deliveryStore = remember(context) { LocalDeliveryStore(context) }
    val packages = remember(deliveryStore) {
        mutableStateListOf<DeliveryPackage>().apply {
            val savedPackages = deliveryStore.loadPackages()
            addAll(savedPackages.ifEmpty { samplePackages() })
        }
    }
    val regularCourseList = remember(deliveryStore) {
        mutableStateListOf<RegularCourse>().apply {
            val savedCourses = deliveryStore.loadRegularCourses()
            addAll(savedCourses.ifEmpty { regularCourses() })
        }
    }
    var selectedWindow by remember { mutableStateOf(TimeWindow.All) }
    var selectedMapTimeWindow by remember { mutableStateOf(TimeWindow.All) }
    var selectedPackageCode by remember { mutableStateOf(packages.firstOrNull()?.trackingCode.orEmpty()) }
    var postalCode by remember { mutableStateOf("") }
    var addressCandidate by remember { mutableStateOf<AddressCandidate?>(null) }
    var isRegisteringPackage by remember { mutableStateOf(false) }
    var editingPackageCode by remember { mutableStateOf<String?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var isSupportHubOpen by remember { mutableStateOf(false) }
    var activeMenuItem by remember { mutableStateOf<AccountMenuItem?>(null) }
    var isLoggedIn by remember { mutableStateOf(true) }
    var userProfile by remember { mutableStateOf(deliveryStore.loadUserProfile()) }
    var showPaywallDialog by remember { mutableStateOf(false) }
    val trialRemainingDays = if (userProfile.trialStartMillis > 0L) {
        val elapsed = System.currentTimeMillis() - userProfile.trialStartMillis
        (30 - (elapsed / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(0)
    } else 30
    val isFreeExpired = userProfile.isRegistered &&
        userProfile.plan != MembershipPlan.Premium &&
        trialRemainingDays == 0
    val onRegisterPackageAttempt = {
        if (isFreeExpired) showPaywallDialog = true else isRegisteringPackage = true
    }
    var backupText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var selectedSummaryFilter by remember { mutableStateOf<PackageSummaryFilter?>(null) }
    var redeliveryCandidateCode by remember { mutableStateOf<String?>(null) }
    var isHomeMenuOpen by remember { mutableStateOf(false) }
    var homePanel by remember { mutableStateOf<HomePanel?>(null) }
    var showMapControls by remember { mutableStateOf(true) }
    val fixedRouteNumbers = remember { mutableStateMapOf<String, Int>() }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc -> loc?.let { currentLocation = LatLng(it.latitude, it.longitude) } }
        }
    }
    val persistPackages = { deliveryStore.savePackages(packages) }
    val persistRegularCourses = { deliveryStore.saveRegularCourses(regularCourseList) }
    val cameraOcrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            addressCandidate = AddressCandidate(
                sourceLabel = "OCR",
                postalCode = "",
                address = "画像を取得できませんでした",
                confidenceLabel = "未取得"
            )
            return@rememberLauncherForActivityResult
        }
        recognizeAddressFromBitmap(
            bitmap = bitmap,
            onSuccess = { candidate -> addressCandidate = candidate },
            onFailure = {
                addressCandidate = AddressCandidate(
                    sourceLabel = "OCR",
                    postalCode = "",
                    address = "住所を読み取れませんでした",
                    confidenceLabel = "要確認"
                )
            }
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraOcrLauncher.launch(null)
        } else {
            addressCandidate = AddressCandidate(
                sourceLabel = "カメラ",
                postalCode = "",
                address = "カメラ権限が許可されていません",
                confidenceLabel = "権限確認"
            )
        }
    }
    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenAddress = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spokenAddress.isNotBlank()) {
            addressCandidate = createAddressCandidateFromText("音声入力", spokenAddress)
        }
    }
    LaunchedEffect(packages.size, packages.firstOrNull()?.trackingCode, packages.lastOrNull()?.trackingCode) {
        assignFixedRouteNumbers(fixedRouteNumbers, packages)
    }

    val visiblePackages = packages.filter {
        selectedWindow == TimeWindow.All || it.timeWindow == selectedWindow
    }.filter {
        selectedSummaryFilter?.matches(it) ?: true
    }
    val visibleMapPackages = visiblePackages
        .filterNot { it.status.hidesMapPin() }
        .filter { selectedMapTimeWindow == TimeWindow.All || it.timeWindow == selectedMapTimeWindow }
    val selectedPackage = packages.firstOrNull { it.trackingCode == selectedPackageCode }
        ?: visiblePackages.firstOrNull()
    val redeliveryCandidate = packages.firstOrNull { it.trackingCode == redeliveryCandidateCode }
    val summaryPackages = selectedSummaryFilter
        ?.let { filter -> packages.filter { filter.matches(it) } }
        .orEmpty()
    val onPackageSelected: (String) -> Unit = { code ->
        selectedPackageCode = code
        if (packages.firstOrNull { it.trackingCode == code }?.status == DeliveryStatus.Absent) {
            redeliveryCandidateCode = code
        }
    }

    MaterialTheme {
        if (showPaywallDialog) {
            AlertDialog(
                onDismissRequest = { showPaywallDialog = false },
                title = { Text(stringResource(R.string.paywall_title)) },
                text = { Text(stringResource(R.string.paywall_message)) },
                confirmButton = {
                    Button(onClick = {
                        showPaywallDialog = false
                        activeMenuItem = AccountMenuItem.Account
                    }) { Text(stringResource(R.string.go_premium)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPaywallDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        redeliveryCandidate?.let { packageItem ->
            AlertDialog(
                onDismissRequest = { redeliveryCandidateCode = null },
                title = { Text(stringResource(R.string.redelivery_title)) },
                text = { Text("${packageItem.recipient}\n${packageItem.address}") },
                confirmButton = {
                    Button(
                        onClick = {
                            val index = packages.indexOfFirst { it.trackingCode == packageItem.trackingCode }
                            if (index >= 0) {
                                packages[index] = packageItem.copy(status = DeliveryStatus.Redelivery)
                                persistPackages()
                            }
                            selectedPackageCode = packageItem.trackingCode
                            redeliveryCandidateCode = null
                        }
                    ) { Text(stringResource(R.string.redelivery_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { redeliveryCandidateCode = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        activeMenuItem?.let { item ->
            if (item == AccountMenuItem.Account) {
                UserAccountScreen(
                    userProfile = userProfile,
                    isLoggedIn = isLoggedIn,
                    onBack = { activeMenuItem = null },
                    onLoginToggle = { isLoggedIn = !isLoggedIn },
                    onSaveProfile = { updated ->
                        userProfile = updated
                        deliveryStore.saveUserProfile(updated)
                    }
                )
            } else {
                AccountMenuDetailScreen(
                    item = item,
                    isLoggedIn = isLoggedIn,
                    onBack = { activeMenuItem = null },
                    onLoginToggle = { isLoggedIn = !isLoggedIn }
                )
            }
            return@MaterialTheme
        }

        if (isSupportHubOpen) {
            SupportHubScreen(
                packages = packages,
                isLoggedIn = isLoggedIn,
                onBack = { isSupportHubOpen = false },
                onSelect = { activeMenuItem = it },
                onLoginToggle = { isLoggedIn = !isLoggedIn },
                onGoToBackup = { isSupportHubOpen = false },
                onFinishDay = { summary ->
                    runCatching {
                        context.startActivity(createEndOfDayCalendarIntent(summary))
                    }.onSuccess {
                        packages.clear()
                        fixedRouteNumbers.clear()
                        selectedPackageCode = ""
                        persistPackages()
                    }
                }
            )
            return@MaterialTheme
        }

        if (homePanel == HomePanel.Packages) {
            PackageListScreen(
                packages = if (selectedSummaryFilter == null) visiblePackages else summaryPackages,
                selectedCode = selectedPackage?.trackingCode,
                onBack = { homePanel = null },
                onSelectPackage = onPackageSelected,
                onEditPackage = { editingPackageCode = it }
            )
            return@MaterialTheme
        }

        editingPackageCode?.let { code ->
            val editTarget = packages.firstOrNull { it.trackingCode == code }
            if (editTarget != null) {
                PackageEditScreen(
                    deliveryPackage = editTarget,
                    onBack = { editingPackageCode = null },
                    onSave = { updated ->
                        val index = packages.indexOfFirst { it.trackingCode == code }
                        if (index >= 0) packages[index] = updated
                        persistPackages()
                        editingPackageCode = null
                    }
                )
                return@MaterialTheme
            }
        }

        if (isRegisteringPackage) {
            val registrationAddress = addressCandidate?.address
                ?: selectedPackage?.address
                ?: "東京都千代田区丸の内1-1-1"
            PackageRegistrationScreen(
                initialAddress = registrationAddress,
                courses = regularCourseList,
                onBack = { isRegisteringPackage = false },
                onOpenMap = { addr -> context.startActivity(openMapIntent(addr)) },
                onOpenNavigation = { addr -> context.startActivity(openNavigationIntent(addr)) },
                onAddCourseAddress = { courseCode, address ->
                    coroutineScope.launch {
                        val newAddress = createCourseAddressFromInput(
                            recipient = "",
                            address = address,
                            timeWindow = TimeWindow.Unspecified,
                            memo = "荷物登録画面から追加"
                        )
                        val index = regularCourseList.indexOfFirst { it.code == courseCode }
                        if (index >= 0) {
                            val course = regularCourseList[index]
                            regularCourseList[index] = course.copy(addresses = course.addresses + newAddress)
                            persistRegularCourses()
                        }
                    }
                },
                onRegister = { results ->
                    coroutineScope.launch {
                        results.forEach { result ->
                            packages.add(createDeliveryPackageFromRegistration(result, packages.size))
                        }
                        selectedPackageCode = packages.last().trackingCode
                        persistPackages()
                        isRegisteringPackage = false
                    }
                }
            )
            return@MaterialTheme
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFE9EEF3)) {
            Box(modifier = Modifier.fillMaxSize()) {
                FullScreenDeliveryMap(
                    packages = visibleMapPackages,
                    fixedRouteNumbers = fixedRouteNumbers,
                    selectedCode = selectedPackage?.takeUnless { it.status.hidesMapPin() }?.trackingCode,
                    origin = currentLocation ?: LatLng(currentRouteOrigin().first, currentRouteOrigin().second),
                    onSelect = { selectedPackageCode = it },
                    onNavigate = {
                        context.startActivity(
                            openNavigationIntent(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                label = it.recipient
                            )
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
                MapHomeChrome(
                    packages = packages,
                    selectedFilter = selectedSummaryFilter,
                    selectedMapTimeWindow = selectedMapTimeWindow,
                    showMapControls = showMapControls,
                    isLoggedIn = isLoggedIn,
                    onOpenMenu = { isHomeMenuOpen = true },
                    onOpenSupport = { isSupportHubOpen = true },
                    onRegisterPackage = { onRegisterPackageAttempt() },
                    onToggleMapControls = { showMapControls = !showMapControls },
                    onFilterSelected = { filter ->
                        selectedSummaryFilter = if (selectedSummaryFilter == filter) null else filter
                        homePanel = HomePanel.Packages
                    },
                    onMapTimeWindowSelected = { selectedMapTimeWindow = it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(start = 8.dp, top = 2.dp, end = 8.dp)
                )
                homePanel?.let { panel ->
                    HomePanelSheet(
                        panel = panel,
                        packages = if (selectedSummaryFilter == null) visiblePackages else summaryPackages,
                        courses = regularCourseList,
                        selectedCode = selectedPackage?.trackingCode,
                        backupText = backupText,
                        importText = importText,
                        onClose = { homePanel = null },
                        onSelectPackage = onPackageSelected,
                        onEditPackage = { editingPackageCode = it },
                        onExport = { backupText = exportPackages(packages) },
                        onImportTextChange = { importText = it },
                        onImport = {
                            val importedPackages = importPackages(importText, packages.size)
                            packages.addAll(importedPackages)
                            selectedPackageCode = packages.lastOrNull()?.trackingCode ?: selectedPackageCode
                            persistPackages()
                            coroutineScope.launch {
                                importedPackages.forEach { pkg ->
                                    if (pkg.latitude == 0.0 && pkg.longitude == 0.0 && pkg.address.isNotBlank()) {
                                        val (lat, lng) = com.delivery.navigator.data.geocodeAddressPublic(pkg.address)
                                        val idx = packages.indexOfFirst { it.trackingCode == pkg.trackingCode }
                                        if (idx >= 0) packages[idx] = packages[idx].copy(latitude = lat, longitude = lng)
                                    }
                                }
                                persistPackages()
                            }
                        },
                        onAddCourseAddress = { courseCode, recipient, address, timeWindow, memo ->
                            coroutineScope.launch {
                                val newAddress = createCourseAddressFromInput(recipient, address, timeWindow, memo)
                                val index = regularCourseList.indexOfFirst { it.code == courseCode }
                                if (index >= 0) {
                                    val course = regularCourseList[index]
                                    regularCourseList[index] = course.copy(addresses = course.addresses + newAddress)
                                    persistRegularCourses()
                                }
                            }
                        },
                        onLoadCourse = { course ->
                            val coursePackages = createPackagesFromCourse(course, packages.size)
                            packages.addAll(coursePackages)
                            coursePackages.forEachIndexed { index, packageItem ->
                                fixedRouteNumbers[packageItem.trackingCode] = index + 1
                            }
                            selectedPackageCode = packages.lastOrNull()?.trackingCode ?: selectedPackageCode
                            persistPackages()
                            homePanel = null
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(12.dp)
                    )
                }
                if (isHomeMenuOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x88000000))
                            .clickable { isHomeMenuOpen = false }
                    )
                    HomeSideMenu(
                        isLoggedIn = isLoggedIn,
                        onClose = { isHomeMenuOpen = false },
                        onOpenPanel = {
                            homePanel = it
                            isHomeMenuOpen = false
                        },
                        onRegisterPackage = {
                            isHomeMenuOpen = false
                            onRegisterPackageAttempt()
                        },
                        onOpenSupport = {
                            isHomeMenuOpen = false
                            isSupportHubOpen = true
                        },
                        onOpenAccount = {
                            isHomeMenuOpen = false
                            activeMenuItem = AccountMenuItem.Account
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(318.dp)
                    )
                }
            }
        }
    }
}

private fun assignFixedRouteNumbers(
    fixedRouteNumbers: MutableMap<String, Int>,
    packages: List<DeliveryPackage>
) {
    val currentCodes = packages.map { it.trackingCode }.toSet()
    fixedRouteNumbers.keys.retainAll(currentCodes)

    val unnumberedPackages = packages.filterNot { fixedRouteNumbers.containsKey(it.trackingCode) }
    if (unnumberedPackages.isEmpty()) return

    val nextRouteNumber = (fixedRouteNumbers.values.maxOrNull() ?: 0) + 1
    calculateNearestRoute(unnumberedPackages).forEachIndexed { index, routeStop ->
        fixedRouteNumbers[routeStop.deliveryPackage.trackingCode] = nextRouteNumber + index
    }
}

private enum class PackageSummaryFilter(val label: String) {
    Remaining("残り"),
    Completed("完了"),
    Absent("不在");

    fun matches(deliveryPackage: DeliveryPackage): Boolean {
        return when (this) {
            Remaining -> deliveryPackage.status == DeliveryStatus.Pending ||
                deliveryPackage.status == DeliveryStatus.InProgress ||
                deliveryPackage.status == DeliveryStatus.Redelivery
            Completed -> deliveryPackage.status == DeliveryStatus.Completed
            Absent -> deliveryPackage.status == DeliveryStatus.Absent
        }
    }
}

private enum class HomePanel {
    Packages,
    Courses,
    Backup
}

@Composable
private fun MapHomeChrome(
    packages: List<DeliveryPackage>,
    selectedFilter: PackageSummaryFilter?,
    selectedMapTimeWindow: TimeWindow,
    showMapControls: Boolean,
    isLoggedIn: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSupport: () -> Unit,
    onRegisterPackage: () -> Unit,
    onToggleMapControls: () -> Unit,
    onFilterSelected: (PackageSummaryFilter) -> Unit,
    onMapTimeWindowSelected: (TimeWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val completed = packages.count { it.status == DeliveryStatus.Completed }
    val absent = packages.count { it.status == DeliveryStatus.Absent }
    val remaining = packages.count { PackageSummaryFilter.Remaining.matches(it) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF)), shape = RoundedCornerShape(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenMenu) { Text("☰", style = MaterialTheme.typography.titleLarge) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.delivery_plan_overview), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(if (isLoggedIn) stringResource(R.string.logged_in) else stringResource(R.string.not_logged_in), color = BrandBlue, style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOpenSupport) { Text(stringResource(R.string.notice)) }
                    Button(onClick = onRegisterPackage, modifier = Modifier.height(38.dp)) { Text(stringResource(R.string.register)) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onToggleMapControls, modifier = Modifier.height(36.dp)) {
                Text(if (showMapControls) stringResource(R.string.hide_summary) else stringResource(R.string.show_summary))
            }
        }
        if (showMapControls) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactMetricCard(stringResource(R.string.remaining), remaining.toString(), selectedFilter == PackageSummaryFilter.Remaining, Modifier.weight(1f)) {
                    onFilterSelected(PackageSummaryFilter.Remaining)
                }
                CompactMetricCard(stringResource(R.string.completed), completed.toString(), selectedFilter == PackageSummaryFilter.Completed, Modifier.weight(1f)) {
                    onFilterSelected(PackageSummaryFilter.Completed)
                }
                CompactMetricCard(stringResource(R.string.absent), absent.toString(), selectedFilter == PackageSummaryFilter.Absent, Modifier.weight(1f)) {
                    onFilterSelected(PackageSummaryFilter.Absent)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF)), shape = RoundedCornerShape(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(TimeWindow.All, TimeWindow.Unspecified, TimeWindow.Morning, TimeWindow.Afternoon, TimeWindow.Evening).forEach { window ->
                        FilterChip(
                            selected = selectedMapTimeWindow == window,
                            onClick = { onMapTimeWindowSelected(window) },
                            label = { Text(if (window == TimeWindow.All) stringResource(R.string.all_time_windows) else window.label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetricCard(
    label: String,
    value: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (selected) BrandPurple else Color(0xEEFFFFFF)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = if (selected) Color.White else MutedText, style = MaterialTheme.typography.labelMedium)
            Text(value, color = if (selected) Color.White else Color(0xFF20242C), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeSideMenu(
    isLoggedIn: Boolean,
    onClose: () -> Unit,
    onOpenPanel: (HomePanel) -> Unit,
    onRegisterPackage: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = Color.White) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("×", style = MaterialTheme.typography.headlineSmall) }
                Text(stringResource(R.string.menu), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F6FF)), shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StorkMascot(modifier = Modifier.size(58.dp))
                    Text("HAKOBUN", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(if (isLoggedIn) stringResource(R.string.driver_mode_logged_in) else stringResource(R.string.guest_mode), color = MutedText)
                }
            }
            SideMenuCard(stringResource(R.string.package_registration_menu), stringResource(R.string.package_registration_desc), "＋") { onRegisterPackage() }
            SideMenuCard(stringResource(R.string.course_menu), stringResource(R.string.course_desc), "◇") { onOpenPanel(HomePanel.Courses) }
            SideMenuCard(stringResource(R.string.delivery_list_menu), stringResource(R.string.delivery_list_desc), "☷") { onOpenPanel(HomePanel.Packages) }
            SideMenuCard(stringResource(R.string.backup_menu), stringResource(R.string.backup_desc), "↻") { onOpenPanel(HomePanel.Backup) }
            SideMenuCard(stringResource(R.string.account_menu), stringResource(R.string.account_desc), "👤") { onOpenAccount() }
            SideMenuCard(stringResource(R.string.support_menu), stringResource(R.string.support_desc), "i") { onOpenSupport() }
            Spacer(modifier = Modifier.weight(1f))
            Text(stringResource(R.string.fullscreen_map_hint), color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SideMenuCard(title: String, description: String, mark: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(BrandPurple),
                contentAlignment = Alignment.Center
            ) {
                Text(mark, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HomePanelSheet(
    panel: HomePanel,
    packages: List<DeliveryPackage>,
    courses: List<RegularCourse>,
    selectedCode: String?,
    backupText: String,
    importText: String,
    onClose: () -> Unit,
    onSelectPackage: (String) -> Unit,
    onEditPackage: (String) -> Unit,
    onExport: () -> Unit,
    onImportTextChange: (String) -> Unit,
    onImport: () -> Unit,
    onAddCourseAddress: (String, String, String, TimeWindow, String) -> Unit,
    onLoadCourse: (RegularCourse) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().heightIn(max = 430.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF7FFFFFF)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (panel) {
                        HomePanel.Packages -> stringResource(R.string.panel_delivery_list)
                        HomePanel.Courses -> stringResource(R.string.panel_courses)
                        HomePanel.Backup -> stringResource(R.string.panel_backup)
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
            when (panel) {
                HomePanel.Packages -> PackageList(packages, selectedCode, onSelectPackage, onEditPackage)
                HomePanel.Courses -> RegularCoursePanel(courses, onLoadCourse, onAddCourseAddress)
                HomePanel.Backup -> AddressBackupPanel(backupText, importText, onExport, onImportTextChange, onImport, onClose)
            }
        }
    }
}

@Composable
private fun FullScreenDeliveryMap(
    packages: List<DeliveryPackage>,
    fixedRouteNumbers: Map<String, Int>,
    selectedCode: String?,
    origin: LatLng,
    onSelect: (String) -> Unit,
    onNavigate: (DeliveryPackage) -> Unit,
    modifier: Modifier = Modifier
) {
    val routeStops = calculateNearestRoute(packages)
    val selectedPackage = routeStops.firstOrNull { it.deliveryPackage.trackingCode == selectedCode }?.deliveryPackage
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(origin, 12.5f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapBearing by remember { mutableStateOf(0f) }
    var drivingRoutePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val activeRouteDestination = selectedPackage ?: routeStops.firstOrNull()?.deliveryPackage
    LaunchedEffect(selectedPackage?.trackingCode) {
        if (mapLoaded) {
            selectedPackage?.let {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
            }
        }
    }
    LaunchedEffect(mapLoaded) {
        if (mapLoaded) {
            selectedPackage?.let {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
            }
        }
    }
    LaunchedEffect(activeRouteDestination?.trackingCode) {
        drivingRoutePoints = activeRouteDestination
            ?.let {
                fetchDrivingRoutePoints(
                    origin = origin.latitude to origin.longitude,
                    destination = it.latitude to it.longitude
                ).map { point -> LatLng(point.first, point.second) }
            }
            .orEmpty()
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                compassEnabled = true,
                zoomControlsEnabled = false,
                tiltGesturesEnabled = true,
                rotationGesturesEnabled = true,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true
            ),
            onMapLoaded = { mapLoaded = true }
        ) {
            Marker(
                state = rememberUpdatedMarkerState(position = origin),
                title = "現在地",
                snippet = "ルート計算の基準点",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            ) // marker title/snippet are shown in Google Maps UI (not Composable Text), kept as-is
            if (drivingRoutePoints.isNotEmpty()) {
                Polyline(
                    points = drivingRoutePoints,
                    color = BrandBlue,
                    width = 9f
                )
            } else if (activeRouteDestination != null) {
                Polyline(
                    points = listOf(origin, LatLng(activeRouteDestination.latitude, activeRouteDestination.longitude)),
                    color = Color(0x552457D6),
                    width = 6f
                )
            }
            routeStops.forEach { stop ->
                val item = stop.deliveryPackage
                val routeNumber = fixedRouteNumbers[item.trackingCode] ?: stop.routeNumber
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(item.latitude, item.longitude)),
                    title = "$routeNumber. ${item.recipient}",
                    snippet = "${item.timeWindow.label} / ${item.address}",
                    icon = deliveryMarkerIcon(routeNumber, item),
                    anchor = Offset(0.5f, 1f),
                    onClick = {
                        onSelect(item.trackingCode)
                        false
                    }
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingMapButton(stringResource(R.string.current_location)) {
                if (mapLoaded) cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(origin, 12.5f))
            }
            selectedPackage?.let { item ->
                FloatingMapButton(stringResource(R.string.navi)) { onNavigate(item) }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 14.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MapZoomButton("+") {
                    if (mapLoaded) coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                    }
                }
                MapZoomButton("−") {
                    if (mapLoaded) coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                    }
                }
            }
            MiniCompass(
                onClick = {
                    if (mapLoaded) {
                        mapBearing = 0f
                        cameraPositionState.move(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder(cameraPositionState.position)
                                    .bearing(mapBearing)
                                    .build()
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun MapZoomButton(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(46.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = Color(0xFF20242C), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun FloatingMapButton(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(62.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = CircleShape
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = BrandBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MiniCompass(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .size(46.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xEFFFFFFF)),
        shape = CircleShape
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("▲", color = Color(0xFFD32F2F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("N", color = Color(0xFF20242C), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Header(
    packages: List<DeliveryPackage>,
    selectedFilter: PackageSummaryFilter?,
    onFilterSelected: (PackageSummaryFilter) -> Unit,
    isLoggedIn: Boolean,
    onOpenMenu: () -> Unit,
    onRegisterPackage: () -> Unit
) {
    val completed = packages.count { it.status == DeliveryStatus.Completed }
    val absent = packages.count { it.status == DeliveryStatus.Absent }
    val remaining = packages.count { PackageSummaryFilter.Remaining.matches(it) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StorkMascot(modifier = Modifier.size(58.dp))
                Column {
                    Text("HAKOBUN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.tagline), color = MutedText)
                    Text(if (isLoggedIn) stringResource(R.string.logged_in) else stringResource(R.string.not_logged_in), color = BrandBlue, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = onOpenMenu,
                    modifier = Modifier.height(40.dp)
                ) { Text(stringResource(R.string.notice)) }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onRegisterPackage,
                    modifier = Modifier.height(40.dp)
                ) { Text(stringResource(R.string.package_registration_menu)) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                stringResource(R.string.remaining),
                remaining.toString(),
                Modifier.weight(1f),
                selected = selectedFilter == PackageSummaryFilter.Remaining,
                onClick = { onFilterSelected(PackageSummaryFilter.Remaining) }
            )
            MetricCard(
                stringResource(R.string.completed),
                completed.toString(),
                Modifier.weight(1f),
                selected = selectedFilter == PackageSummaryFilter.Completed,
                onClick = { onFilterSelected(PackageSummaryFilter.Completed) }
            )
            MetricCard(
                stringResource(R.string.absent),
                absent.toString(),
                Modifier.weight(1f),
                selected = selectedFilter == PackageSummaryFilter.Absent,
                onClick = { onFilterSelected(PackageSummaryFilter.Absent) }
            )
        }
    }
}

@Composable
private fun SummaryFilteredPackagePanel(
    filter: PackageSummaryFilter?,
    packages: List<DeliveryPackage>,
    selectedCode: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onClear: () -> Unit
) {
    if (filter == null) return

    WhiteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.summary_list_title, filter.label), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.showing_count, packages.size), color = MutedText)
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.close)) }
        }
        if (packages.isEmpty()) {
            Text(stringResource(R.string.no_packages), color = MutedText)
        } else {
            PackageList(
                packages = packages,
                selectedCode = selectedCode,
                onSelect = onSelect,
                onEdit = onEdit
            )
        }
        if (filter == PackageSummaryFilter.Absent) {
            Text(stringResource(R.string.absent_redelivery_hint), color = MutedText)
        }
    }
}

@Composable
private fun SupportHubScreen(
    packages: List<DeliveryPackage>,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSelect: (AccountMenuItem) -> Unit,
    onLoginToggle: () -> Unit,
    onGoToBackup: () -> Unit,
    onFinishDay: (EndOfDaySummary) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.support_hub_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(64.dp))
            }
            Box {
                AccountMenuPanel(
                    isLoggedIn = isLoggedIn,
                    onSelect = onSelect,
                    onLoginToggle = onLoginToggle
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 2.dp)
                        .width(18.dp)
                        .height(108.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BrandPurple)
                )
            }
            EndOfDayPanel(
                packages = packages,
                onGoToBackup = onGoToBackup,
                onFinishDay = onFinishDay
            )
        }
    }
}

@Composable
private fun AccountMenuPanel(
    isLoggedIn: Boolean,
    onSelect: (AccountMenuItem) -> Unit,
    onLoginToggle: () -> Unit
) {
    WhiteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.account_support_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.account_support_desc), color = MutedText)
            }
            Button(onClick = onLoginToggle) { Text(if (isLoggedIn) stringResource(R.string.logout) else stringResource(R.string.login)) }
        }
        AccountMenuItem.entries.filter { it != AccountMenuItem.Account }.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF6F7F9))
                            .border(1.dp, Color(0xFFE2E7EF), RoundedCornerShape(8.dp))
                            .clickable { onSelect(item) }
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.title, fontWeight = FontWeight.Bold)
                            Text(item.description, color = MutedText, style = MaterialTheme.typography.labelSmall)
                            Text(stringResource(R.string.detail_link), color = BrandPurple, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private const val SUBSCRIPTION_PRODUCT_ID = "hakobun_monthly_premium"

@Composable
private fun UserAccountScreen(
    userProfile: UserProfile,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onLoginToggle: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    var isEditing by remember { mutableStateOf(!userProfile.isRegistered) }
    var driverName by remember { mutableStateOf(userProfile.driverName) }
    var base by remember { mutableStateOf(userProfile.base) }
    var contact by remember { mutableStateOf(userProfile.contact) }
    var email by remember { mutableStateOf(userProfile.email) }
    var billingReady by remember { mutableStateOf(false) }
    var subscribeMessage by remember { mutableStateOf("") }

    val trialRemainingDays = if (userProfile.trialStartMillis > 0L) {
        val elapsed = System.currentTimeMillis() - userProfile.trialStartMillis
        (30 - (elapsed / (1000L * 60 * 60 * 24)).toInt()).coerceAtLeast(0)
    } else 30

    val isPremium = userProfile.plan == MembershipPlan.Premium
    val isFreeExpired = !isPremium && trialRemainingDays == 0 && userProfile.isRegistered

    val billingClient = remember {
        BillingClient.newBuilder(context)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases?.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            onSaveProfile(userProfile.copy(plan = MembershipPlan.Premium))
                            subscribeMessage = "プレミアムプランへの移行が完了しました"
                        }
                    }
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
    }

    DisposableEffect(Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                billingReady = result.responseCode == BillingClient.BillingResponseCode.OK
            }
            override fun onBillingServiceDisconnected() { billingReady = false }
        })
        onDispose { billingClient.endConnection() }
    }

    fun launchSubscription() {
        if (activity == null) { subscribeMessage = "Google Play Billingを起動できません"; return }
        scope.launch {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUBSCRIPTION_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )).build()
            val result = billingClient.queryProductDetails(params)
            val productDetails = result.productDetailsList?.firstOrNull()
            if (productDetails == null) {
                subscribeMessage = "プランが見つかりません（Play Console設定をご確認ください）"
                return@launch
            }
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return@launch
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )).build()
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.user_account_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onLoginToggle) { Text(if (isLoggedIn) stringResource(R.string.logout) else stringResource(R.string.login)) }
            }

            // プロフィールカード
            WhiteCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold)
                    if (userProfile.isRegistered && !isEditing) {
                        TextButton(onClick = { isEditing = true }) { Text(stringResource(R.string.edit)) }
                    }
                }
                if (isEditing) {
                    LabeledField(stringResource(R.string.driver_name_label), stringResource(R.string.driver_name_placeholder), driverName) { driverName = it }
                    LabeledField(stringResource(R.string.base_label), stringResource(R.string.base_placeholder), base) { base = it }
                    LabeledField(stringResource(R.string.contact_label), stringResource(R.string.contact_placeholder), contact) { contact = it }
                    LabeledField(stringResource(R.string.email_label), stringResource(R.string.email_placeholder), email) { email = it }
                    Button(
                        onClick = {
                            val trialStart = if (!userProfile.isRegistered) System.currentTimeMillis() else userProfile.trialStartMillis
                            onSaveProfile(userProfile.copy(
                                driverName = driverName,
                                base = base,
                                contact = contact,
                                email = email,
                                isRegistered = true,
                                trialStartMillis = trialStart
                            ))
                            isEditing = false
                        },
                        enabled = driverName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (userProfile.isRegistered) stringResource(R.string.save_changes) else stringResource(R.string.register_profile)) }
                } else {
                    InfoRow(stringResource(R.string.info_driver_name), userProfile.driverName.ifBlank { stringResource(R.string.not_set) })
                    InfoRow(stringResource(R.string.info_base), userProfile.base.ifBlank { stringResource(R.string.not_set) })
                    InfoRow(stringResource(R.string.info_contact), userProfile.contact.ifBlank { stringResource(R.string.not_set) })
                    InfoRow(stringResource(R.string.info_email), userProfile.email.ifBlank { stringResource(R.string.not_set) })
                    InfoRow(stringResource(R.string.info_login_status), if (isLoggedIn) stringResource(R.string.logged_in) else stringResource(R.string.not_logged_in))
                }
            }

            // プランカード
            WhiteCard {
                Text(stringResource(R.string.plan_billing_title), fontWeight = FontWeight.Bold)
                if (isPremium) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEAF0FF))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.premium_plan), color = BrandBlue, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.premium_features), color = MutedText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFreeExpired) Color(0xFFFFEEEE) else Color(0xFFF6F7F9))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isFreeExpired) stringResource(R.string.free_trial_expired) else stringResource(R.string.free_trial_active),
                                color = if (isFreeExpired) Color(0xFFE53935) else BrandBlue,
                                fontWeight = FontWeight.Bold
                            )
                            if (userProfile.isRegistered && !isFreeExpired) {
                                Text(stringResource(R.string.trial_remaining_days, trialRemainingDays), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.trial_auto_upgrade), color = MutedText, style = MaterialTheme.typography.bodySmall)
                            } else if (!userProfile.isRegistered) {
                                Text(stringResource(R.string.trial_after_register), color = MutedText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, BrandPurple, RoundedCornerShape(8.dp))
                                .background(Color(0xFFF5F0FF))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.free_plan_name), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.free_trial_period), color = BrandPurple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(stringResource(R.string.free_features), style = MaterialTheme.typography.labelSmall, color = MutedText)
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, Color(0xFFE2E7EF), RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.premium_plan_name), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.premium_price), color = BrandPurple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(stringResource(R.string.premium_features_list), style = MaterialTheme.typography.labelSmall, color = MutedText)
                            }
                        }
                    }
                    Button(
                        onClick = { launchSubscription() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                    ) {
                        Text(stringResource(R.string.subscribe_google_pay), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        stringResource(R.string.subscription_terms),
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (subscribeMessage.isNotBlank()) {
                    Text(
                        subscribeMessage,
                        color = if (subscribeMessage.contains("完了")) SuccessGreen else Color(0xFFE53935)
                    )
                }
            }
        }
    }
}

private data class FaqItem(val question: String, val answer: String)

private val faqItems = listOf(
    // ---- 基本操作 ----
    FaqItem(
        "荷物はどうやって登録しますか？",
        "ホーム画面右上の「登録」ボタン、またはサイドメニュー →「荷物登録」から登録できます。\n\n登録方法は3種類あります。\n① 住所を手入力\n② カメラで送り状を撮影してOCR自動読み取り\n③ マイクで音声入力\n\n登録後はホームのマップにピンが表示されます。"
    ),
    FaqItem(
        "1日に登録できる荷物の件数は？",
        "無料プラン（1ヶ月トライアル含む）は5件まで登録できます。\n\nプレミアムプラン（¥980/月）は件数無制限でご利用いただけます。\n\n上限に達すると登録ボタンを押したタイミングでプラン変更のご案内が表示されます。"
    ),
    FaqItem(
        "荷物の配達状況を更新するには？",
        "ホーム画面のマップ上のピン、または配達リストのカードをタップすると詳細画面が開きます。\n\n「完了」「不在」「再配達」「会社へ持ち戻り」などのステータスに変更できます。"
    ),
    FaqItem(
        "地図のピンが正しい場所に表示されません",
        "住所のジオコーディング（住所→緯度経度変換）の精度に依存します。\n\n荷物登録画面の下部にあるマップで、ピンを指で長押しして正しい位置にドラッグすることで手動修正できます。修正した位置は保存されます。"
    ),
    FaqItem(
        "定期コース（集荷）の使い方は？",
        "サイドメニュー →「集荷 / 定期コース」から、あらかじめ登録したコース（A〜D）を今日の配達リストに一括読み込みできます。\n\nコースの登録・編集は同画面内の「コース管理」から行えます。"
    ),
    // ---- OCR / 音声 ----
    FaqItem(
        "カメラで送り状を読み取ったら文字がずれました",
        "以下の点をお試しください。\n\n・明るい場所で撮影する\n・送り状全体がフレームに収まるようにする\n・手ブレしないよう両手で固定する\n・汚れ・折れ・反射がないか確認する\n\nOCR後の各フィールドは手動で修正できます。認識精度は照明・印刷品質に依存します。"
    ),
    FaqItem(
        "音声入力が認識されません",
        "以下を確認してください。\n\n・マイクの使用許可がアプリに付与されているか（設定 → アプリ → HAKOBUN → 権限）\n・静かな環境で話しかける\n・住所は「東京都千代田区丸の内一丁目」のようにゆっくり明確に話す\n\n音声認識はGoogle音声認識エンジンを使用しています。"
    ),
    // ---- 通知 ----
    FaqItem(
        "時間帯の通知が届きません",
        "以下を確認してください。\n\n① 設定 → 通知がONになっているか（アプリ内：お知らせ → 設定項目 → 通知）\n② Androidの設定 → アプリ → HAKOBUN → 通知が「許可」になっているか\n③ 端末の省電力モードがバックグラウンド動作を制限していないか\n\n問題が続く場合は「アラームを再設定」ボタンをタップしてください。"
    ),
    FaqItem(
        "通知を止めたい",
        "アプリ内：サイドメニュー → お知らせ → 設定項目 → 通知のトグルをOFFにしてください。\n\nまたはAndroidの設定 → アプリ → HAKOBUN → 通知 からも無効にできます。"
    ),
    // ---- サブスクリプション ----
    FaqItem(
        "無料トライアルはいつ終わりますか？",
        "ユーザー登録した日から30日間が無料トライアル期間です。\n\nアプリ内のユーザーアカウント画面（サイドメニュー → ユーザーアカウント情報）で残り日数を確認できます。\n\nトライアル終了の24時間前までにキャンセルすれば料金は発生しません。"
    ),
    FaqItem(
        "サブスクリプションをキャンセルするには？",
        "Google Play ストア → アカウントアイコン → 「定期購入」→ HAKOBUN → 「定期購入を解約」から手続きしてください。\n\n解約後も当月末までご利用いただけます。アプリ内からの解約機能は現在準備中です。"
    ),
    FaqItem(
        "決済に失敗しました",
        "Google Play のお支払い方法をご確認ください。\n\nGoogle Play ストア → アカウント → お支払いと定期購入 → お支払い方法\n\nカードの有効期限切れ・残高不足などが原因の場合があります。Google Play のサポートもご参照ください。"
    ),
    // ---- データ・バックアップ ----
    FaqItem(
        "データはどこに保存されますか？",
        "すべてのデータはお使いのスマートフォン内の暗号化ストレージに保存されます。\n\n外部サーバーへの送信・クラウド同期は行いません。アプリをアンインストールするとデータは削除されます。\n\n大切なデータはバックアップ機能（サイドメニュー → バックアップ）でエクスポートしておくことをお勧めします。"
    ),
    FaqItem(
        "機種変更したらデータは引き継げますか？",
        "現在、自動的なデータ引き継ぎ機能はありません。\n\n機種変更前にバックアップ機能でデータをエクスポート（コピー・共有）し、新しい端末でインポートしてください。\n\nインポート機能：サイドメニュー → バックアップ → 「復元」"
    ),
    // ---- その他 ----
    FaqItem(
        "アプリが重い・クラッシュします",
        "以下をお試しください。\n\n① アプリを完全に閉じて再起動する\n② 端末を再起動する\n③ アプリを最新バージョンに更新する（Google Play ストアで確認）\n④ 登録荷物が多い場合は「終了処理」でリセットする\n\n問題が解決しない場合はお問い合わせください。"
    ),
    FaqItem(
        "バグ・改善要望を伝えたい",
        "サイドメニュー → お知らせ →「ユーザーからのフィードバック」からご送信ください。\n\n具体的な状況（操作手順・端末機種・Androidバージョン）をご記入いただくと迅速に対応できます。"
    ),
    FaqItem(
        "お問い合わせ先は？",
        "メール：support@hakobun.example\n\nお問い合わせの際は以下の情報をお知らせください。\n・端末機種・Androidバージョン\n・アプリバージョン\n・問題の再現手順\n\n営業時間内（平日10:00〜18:00）にご返答します。"
    )
)

@Composable
private fun FaqAccordionItem(item: FaqItem) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "Q",
                        fontWeight = FontWeight.Bold,
                        color = BrandPurple,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        item.question,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "A",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        item.answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF333333),
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqContent() {
    val categories = listOf(
        "基本操作" to faqItems.subList(0, 5),
        "OCR・音声入力" to faqItems.subList(5, 7),
        "通知" to faqItems.subList(7, 9),
        "サブスクリプション" to faqItems.subList(9, 12),
        "データ・バックアップ" to faqItems.subList(12, 14),
        "その他" to faqItems.subList(14, 17)
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WhiteCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "よくある質問 (FAQ)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "質問をタップすると回答が表示されます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        categories.forEach { (categoryName, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "▍$categoryName",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandPurple,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                items.forEach { item ->
                    FaqAccordionItem(item)
                }
            }
        }

        WhiteCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "解決しない場合は",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "サイドメニュー →「ユーザーからのフィードバック」からご連絡ください。\nメール：support@hakobun.example",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun TermsSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandPurple
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF333333),
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
        )
    }
}

@Composable
private fun TermsContent() {
    val lastUpdated = "2026年7月19日"
    val contactEmail = "support@hakobun.example"

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ヘッダー
        WhiteCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "HAKOBUN ご利用規約・プライバシーポリシー",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "最終更新日：$lastUpdated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Text(
                    "本アプリをご利用になる前に、以下の規約をお読みください。利用を開始した時点で、本規約に同意したものとみなします。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF333333)
                )
            }
        }

        // 第1条 サービス概要
        WhiteCard {
            TermsSection(
                "第1条　サービス概要",
                "HAKOBUN（以下「本アプリ」）は、配送ドライバーの業務効率化を目的とした配達管理支援アプリです。荷物の登録・ルート管理・配達状況の記録・住所のOCR読み取りなどの機能を提供します。\n\n本アプリは業務補助ツールであり、実際の配送業務における法令遵守・安全運転・配送会社の規定遵守はご利用者ご自身の責任となります。"
            )
        }

        // 第2条 利用資格
        WhiteCard {
            TermsSection(
                "第2条　利用資格",
                "本アプリは18歳以上の方を対象としています。配送業務に従事する方、または業務管理者としてご利用ください。\n\n本アプリをご利用いただくには、Google アカウントが必要な場合があります。虚偽の情報による登録は禁止します。"
            )
        }

        // 第3条 サブスクリプション・課金
        WhiteCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TermsSection(
                    "第3条　サブスクリプション・課金",
                    "【無料トライアル】\n初回ご登録時に1ヶ月間の無料トライアルを提供します。無料期間中はすべての機能をご利用いただけます。無料期間終了の24時間前までにキャンセルしない場合、自動的に有料プランへ移行します。\n\n【有料プラン（プレミアム）】\n料金：¥980 / 月（税込）\n請求：Google Play を通じて毎月自動更新\nキャンセル：Google Playストア「定期購入」画面からいつでも解約可能。解約後も当月末まで利用できます。\n\n【返金ポリシー】\nGoogle Play の返金ポリシーに従います。購入後48時間以内のキャンセルについては、Google Play を通じてご申請ください。\n\n【価格変更】\n価格改定の際は30日前までにアプリ内またはメールにてお知らせします。"
                )
            }
        }

        // 第4条 収集する情報とその利用目的
        WhiteCard {
            TermsSection(
                "第4条　収集する情報とその利用目的",
                "■ 位置情報（GPS）\n目的：配達先へのナビゲーション・現在地表示・配達ルートの最適化\n取得タイミング：アプリ使用中のみ（バックグラウンド取得なし）\n\n■ カメラ・画像\n目的：荷物の送り状OCR読み取りによる住所・氏名の自動入力\n保存先：デバイス内のみ（クラウドへの送信なし）\n\n■ 音声（マイク）\n目的：音声入力による住所・メモの文字起こし\n保存先：デバイス内のみ\n\n■ 入力情報（氏名・住所・電話番号・メールアドレス）\n目的：配達業務の管理・ドライバープロフィールの設定\n保存先：デバイス内の暗号化ストレージ（AES-256-GCM）\n外部送信：なし"
            )
        }

        // 第5条 第三者サービス
        WhiteCard {
            TermsSection(
                "第5条　第三者サービスの利用",
                "■ Google Maps Platform\nマップ表示・ナビゲーション・住所のジオコーディングに使用します。Google のプライバシーポリシー（https://policies.google.com/privacy）が適用されます。\n\n■ Google Play Billing\nサブスクリプション決済に使用します。決済情報はGoogle が管理し、本アプリは保持しません。\n\n■ Google ML Kit（テキスト認識）\nOCR処理はデバイス上でのみ実行され、外部サーバーへのデータ送信は行いません。\n\n■ Google 位置情報サービス\nGPS取得に使用します。"
            )
        }

        // 第6条 データの保管・セキュリティ
        WhiteCard {
            TermsSection(
                "第6条　データの保管・セキュリティ",
                "ご入力いただいた個人情報・業務データは、デバイス内の暗号化ストレージ（Android EncryptedSharedPreferences / AES-256-GCM）に保存されます。\n\nデータの外部サーバーへの送信・クラウド同期は行いません。アプリをアンインストールした場合、すべてのデータはデバイスから削除されます。\n\nバックアップ機能をご利用の場合、エクスポートデータの管理はご利用者ご自身の責任となります。"
            )
        }

        // 第7条 禁止事項
        WhiteCard {
            TermsSection(
                "第7条　禁止事項",
                "以下の行為を禁止します。\n\n・本アプリのリバースエンジニアリング・改ざん・再配布\n・他者の個人情報を無断で入力・利用する行為\n・法令に違反する目的での利用\n・本アプリのサーバー・インフラへの過負荷をかける行為\n・虚偽の情報による登録・課金回避を目的とした不正利用"
            )
        }

        // 第8条 免責事項
        WhiteCard {
            TermsSection(
                "第8条　免責事項",
                "本アプリは現状有姿で提供されます。以下について、運営は責任を負いません。\n\n・地図情報・ルート案内の正確性（必ず道路標識・交通法規を優先してください）\n・OCR読み取り結果の正確性\n・アプリの不具合・通信障害によるデータ損失\n・第三者サービスの障害・仕様変更\n・業務上の損失・配達ミス\n\n重要な配達情報は必ずご自身で確認してください。"
            )
        }

        // 第9条 規約の変更
        WhiteCard {
            TermsSection(
                "第9条　規約の変更",
                "本規約は予告なく変更される場合があります。重要な変更がある場合はアプリ内通知またはメールでお知らせします。変更後も本アプリを継続利用した場合、改定後の規約に同意したものとみなします。"
            )
        }

        // 第10条 準拠法・管轄
        WhiteCard {
            TermsSection(
                "第10条　準拠法・管轄裁判所",
                "本規約は日本法に準拠します。本規約に関する紛争については、東京地方裁判所を第一審の専属的合意管轄裁判所とします。"
            )
        }

        // お問い合わせ
        WhiteCard {
            TermsSection(
                "お問い合わせ",
                "規約・プライバシーに関するご質問は下記までお問い合わせください。\n\nメール：$contactEmail\n\n※ Google Play のデータセキュリティフォームに記載した内容と本ポリシーは整合しています。"
            )
        }
    }
}

@Composable
private fun NotificationToggleRow() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isNotificationEnabled(context)) }
    val scope = rememberCoroutineScope()

    WhiteCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_notification),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (enabled) stringResource(R.string.notif_on_desc) else stringResource(R.string.notif_off_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { newVal ->
                        enabled = newVal
                        setNotificationEnabled(context, newVal)
                    }
                )
            }
            if (enabled) {
                Text(
                    stringResource(R.string.notif_schedule_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showAnnouncementNotification(
                                context,
                                "【HAKOBUN】運営からのお知らせ",
                                "アプリが最新バージョンに更新されました。新機能をご確認ください。"
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.notif_test_announcement), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { scheduleAllTimeWindowAlarms(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.notif_reschedule), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private data class AppLanguage(val tag: String, val label: String)

private val supportedLanguages = listOf(
    AppLanguage("ja", "日本語"),
    AppLanguage("en", "English"),
    AppLanguage("zh-Hans", "中文（简体）"),
    AppLanguage("ko", "한국어"),
    AppLanguage("vi", "Tiếng Việt"),
    AppLanguage("pt-BR", "Português (Brasil)")
)

@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val currentTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val lm = context.getSystemService(LocaleManager::class.java)
        lm.applicationLocales.get(0)?.toLanguageTag() ?: "ja"
    } else "ja"

    val currentLabel = supportedLanguages.firstOrNull { currentTag.startsWith(it.tag) }?.label ?: "日本語"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_language),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currentLabel, style = MaterialTheme.typography.bodyMedium)
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
        }
        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    supportedLanguages.forEach { lang ->
                        val selected = currentTag.startsWith(lang.tag)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val lm = context.getSystemService(LocaleManager::class.java)
                                        lm.applicationLocales = LocaleList.forLanguageTags(lang.tag)
                                    }
                                    expanded = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                lang.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) BrandPurple else Color.Unspecified
                            )
                            if (selected) Text("✓", color = BrandPurple)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountMenuDetailScreen(
    item: AccountMenuItem,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onLoginToggle: () -> Unit
) {
    var feedbackText by remember { mutableStateOf("") }
    var feedbackSubmitted by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onLoginToggle) { Text(if (isLoggedIn) stringResource(R.string.logout) else stringResource(R.string.login)) }
            }
            WhiteCard {
                Text(item.description, color = MutedText)
                when (item) {
                    AccountMenuItem.Feedback -> {
                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = {
                                feedbackText = it
                                feedbackSubmitted = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.feedback_label)) },
                            placeholder = { Text(stringResource(R.string.feedback_placeholder)) },
                            minLines = 4
                        )
                        Button(
                            onClick = { feedbackSubmitted = true },
                            enabled = feedbackText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.send_to_ops)) }
                        if (feedbackSubmitted) {
                            Text(stringResource(R.string.feedback_sent), color = SuccessGreen)
                        }
                    }
                    AccountMenuItem.Announcements -> {
                        InfoRow(stringResource(R.string.announcement_important), stringResource(R.string.announcement_important_text))
                        InfoRow(stringResource(R.string.announcement_update), stringResource(R.string.announcement_update_text))
                        InfoRow(stringResource(R.string.announcement_guide), stringResource(R.string.announcement_guide_text))
                    }
                    AccountMenuItem.Account -> { /* UserAccountScreen で処理 */ }
                    AccountMenuItem.Settings -> {
                        InfoRow(stringResource(R.string.settings_map), stringResource(R.string.settings_map_text))
                        InfoRow(stringResource(R.string.settings_navi), stringResource(R.string.settings_navi_text))
                        NotificationToggleRow()
                        LanguageSelector()
                    }
                    AccountMenuItem.Help -> {
                        FaqContent()
                    }
                    AccountMenuItem.Terms -> {
                        TermsContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun EndOfDayPanel(
    packages: List<DeliveryPackage>,
    onGoToBackup: () -> Unit,
    onFinishDay: (EndOfDaySummary) -> Unit
) {
    val summary = calculateEndOfDaySummary(packages)
    WhiteCard {
        Text(stringResource(R.string.end_of_day_title), fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.end_of_day_desc),
            color = MutedText,
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(
            onClick = onGoToBackup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.backup_link), color = BrandBlue)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric(stringResource(R.string.delivered_stops), summary.deliveredStops.toString(), Modifier.weight(1f))
            SummaryMetric(stringResource(R.string.delivered_packages), summary.deliveredPackages.toString(), Modifier.weight(1f))
            SummaryMetric(stringResource(R.string.returned_packages), summary.returnedPackages.toString(), Modifier.weight(1f))
        }
        Button(onClick = { onFinishDay(summary) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.finish_day_button))
        }
    }
}

@Composable
private fun RegularCoursePanel(
    courses: List<RegularCourse>,
    onLoadCourse: (RegularCourse) -> Unit,
    onAddCourseAddress: (String, String, String, TimeWindow, String) -> Unit
) {
    var selectedCourseCode by remember { mutableStateOf(courses.firstOrNull()?.code ?: "A") }
    val selectedCourse = courses.firstOrNull { it.code == selectedCourseCode } ?: courses.firstOrNull()
    remember(onAddCourseAddress) { onAddCourseAddress }

    WhiteCard {
        Text(stringResource(R.string.regular_courses_title), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.course_address_hint), color = MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            courses.forEach { course ->
                FilterChip(
                    selected = selectedCourseCode == course.code,
                    onClick = { selectedCourseCode = course.code },
                    label = { Text(stringResource(R.string.course_chip_label, course.code)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        selectedCourse?.let { course ->
            Text(stringResource(R.string.course_summary, course.displayName, course.addresses.size), fontWeight = FontWeight.Bold)
            Button(
                onClick = { onLoadCourse(course) },
                enabled = course.addresses.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.load_course_button, course.code))
            }
            course.addresses.takeLast(5).forEach { item ->
                TextBox("${item.recipient}\n${item.address}\n${item.timeWindow.label} / ${item.memo}")
            }
        }
    }
}


@Composable
private fun AddressBackupPanel(
    backupText: String,
    importText: String,
    onExport: () -> Unit,
    onImportTextChange: (String) -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = remember {
        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    }
    WhiteCard {
        Text(stringResource(R.string.backup_data_title), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.backup_data_desc), color = MutedText)
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_backup)) }
        if (backupText.isNotBlank()) {
            TextBox(backupText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("hakobun_backup", backupText)
                        clipboard.setPrimaryClip(clip)
                        onClose()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.copy)) }
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, backupText)
                            putExtra(Intent.EXTRA_SUBJECT, "HAKOBUN 住所バックアップ")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "バックアップを共有"))
                        onClose()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.share)) }
            }
        }
        OutlinedTextField(
            value = importText,
            onValueChange = onImportTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.import_data_label)) },
            placeholder = { Text(stringResource(R.string.import_placeholder)) },
            minLines = 2
        )
        Button(
            onClick = onImport,
            enabled = importText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.import_label)) }
    }
}

@Composable
private fun AddressInputPanel(
    postalCode: String,
    candidate: AddressCandidate?,
    onPostalCodeChange: (String) -> Unit,
    onCameraScan: () -> Unit,
    onVoiceInput: () -> Unit,
    onPostalSearch: () -> Unit,
    onRegisterPackage: () -> Unit,
    onClear: () -> Unit
) {
    WhiteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.address_input_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.address_input_desc), color = MutedText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCameraScan) { Text(stringResource(R.string.camera)) }
                Button(onClick = onVoiceInput) { Text(stringResource(R.string.voice)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = postalCode,
                onValueChange = onPostalCodeChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.postal_code_label)) },
                placeholder = { Text(stringResource(R.string.postal_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(onClick = onPostalSearch, enabled = postalCode.length == 7) { Text(stringResource(R.string.search)) }
        }
        candidate?.let {
            val postalPrefix = it.postalCode.takeIf(String::isNotBlank)?.let { code -> "〒$code " }.orEmpty()
            TextBox("${it.sourceLabel} / ${it.confidenceLabel}\n$postalPrefix${it.address}")
        }
        Button(onClick = onRegisterPackage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.register_package_button)) }
        if (postalCode.isNotEmpty() || candidate != null) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.clear_input)) }
        }
    }
}

@Composable
private fun TimeWindowFilters(selected: TimeWindow, onSelected: (TimeWindow) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeWindow.entries.forEach { window ->
            FilterChip(selected = selected == window, onClick = { onSelected(window) }, label = { Text(window.label) })
        }
    }
}

@Composable
private fun MapTimeWindowFilters(selected: TimeWindow, onSelected: (TimeWindow) -> Unit) {
    WhiteCard {
        Text(stringResource(R.string.map_time_window_title), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.map_time_window_desc), color = MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TimeWindow.All,
                TimeWindow.Unspecified,
                TimeWindow.Morning,
                TimeWindow.Afternoon,
                TimeWindow.Evening
            ).forEach { window ->
                FilterChip(
                    selected = selected == window,
                    onClick = { onSelected(window) },
                    label = { Text(if (window == TimeWindow.All) stringResource(R.string.all_time_windows_filter) else window.label) }
                )
            }
        }
    }
}

@Composable
private fun DeliveryGoogleMap(
    packages: List<DeliveryPackage>,
    fixedRouteNumbers: Map<String, Int>,
    selectedCode: String?,
    origin: LatLng,
    onSelect: (String) -> Unit,
    onNavigate: (DeliveryPackage) -> Unit
) {
    val routeStops = calculateNearestRoute(packages)
    val selectedPackage = routeStops.firstOrNull { it.deliveryPackage.trackingCode == selectedCode }?.deliveryPackage
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(origin, 12.5f)
    }
    var mapBearing by remember { mutableStateOf(0f) }
    var mapLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(selectedPackage?.trackingCode) {
        if (mapLoaded) {
            selectedPackage?.let {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                )
            }
        }
    }
    LaunchedEffect(mapLoaded) {
        if (mapLoaded) {
            selectedPackage?.let {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                )
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.delivery_map_title), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.delivery_map_desc), color = MutedText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = mapLoaded,
                        onClick = {
                            mapBearing = 0f
                            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(origin, 12.5f))
                        }
                    ) {
                        Text(stringResource(R.string.current_location))
                    }
                    Button(
                        enabled = mapLoaded,
                        onClick = {
                            mapBearing = (mapBearing + 90f) % 360f
                            cameraPositionState.move(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder(cameraPositionState.position)
                                        .bearing(mapBearing)
                                        .build()
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.rotate))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = false),
                    uiSettings = MapUiSettings(
                        compassEnabled = true,
                        zoomControlsEnabled = true,
                        tiltGesturesEnabled = true,
                        rotationGesturesEnabled = true,
                        scrollGesturesEnabled = true,
                        zoomGesturesEnabled = true
                    ),
                    onMapLoaded = { mapLoaded = true }
                ) {
                    Marker(
                        state = rememberUpdatedMarkerState(position = origin),
                        title = "現在地",
                        snippet = "ルート計算の基準点",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                    if (routeStops.isNotEmpty()) {
                        Polyline(
                            points = listOf(origin) + routeStops.map {
                                LatLng(it.deliveryPackage.latitude, it.deliveryPackage.longitude)
                            },
                            color = Color(0x552457D6),
                            width = 6f
                        )
                    }
                    routeStops.forEach { stop ->
                        val item = stop.deliveryPackage
                        val routeNumber = fixedRouteNumbers[item.trackingCode] ?: stop.routeNumber
                        Marker(
                            state = rememberUpdatedMarkerState(position = LatLng(item.latitude, item.longitude)),
                            title = "$routeNumber. ${item.recipient}",
                            snippet = "${item.timeWindow.label} / ${item.address}",
                            icon = deliveryMarkerIcon(routeNumber, item),
                            anchor = Offset(0.5f, 1f),
                            onClick = {
                                onSelect(item.trackingCode)
                                false
                            }
                        )
                    }
                    selectedPackage?.let {
                        Polyline(
                            points = listOf(origin, LatLng(it.latitude, it.longitude)),
                            color = BrandBlue,
                            width = 10f
                        )
                    }
                }
            }
            TimeWindowLegend()
            selectedPackage?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = mapLoaded,
                        onClick = {
                            cameraPositionState.move(
                                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.go_to_selected_pin))
                    }
                    Button(onClick = { onNavigate(it) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.start_navi))
                    }
                }
            }
            routeStops.firstOrNull()?.deliveryPackage?.let {
                Text(stringResource(R.string.next_candidate, it.recipient, it.timeWindow.label), color = MutedText)
            }
        }
    }
}

@Composable
private fun PackageDetail(deliveryPackage: DeliveryPackage, onStatusChange: (DeliveryStatus) -> Unit) {
    val context = LocalContext.current
    WhiteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(deliveryPackage.recipient, fontWeight = FontWeight.Bold)
                Text(deliveryPackage.address, color = MutedText)
            }
            StatusPill(deliveryPackage.status)
        }
        Text(stringResource(R.string.package_info, deliveryPackage.trackingCode, deliveryPackage.size, deliveryPackage.colorLabel, deliveryPackage.packageType))
        Text(stringResource(R.string.package_time_cod_locker, deliveryPackage.timeWindow.label, yesNo(deliveryPackage.cod), yesNo(deliveryPackage.hasLocker)))
        Text(deliveryPackage.memo, color = MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onStatusChange(DeliveryStatus.Completed) }) { Text(stringResource(R.string.status_complete)) }
            Button(onClick = { onStatusChange(DeliveryStatus.Absent) }) { Text(stringResource(R.string.status_absent)) }
            Button(onClick = { onStatusChange(DeliveryStatus.ReturnToCompany) }) { Text(stringResource(R.string.status_return)) }
            Button(
                onClick = {
                    context.startActivity(
                        openNavigationIntent(
                            latitude = deliveryPackage.latitude,
                            longitude = deliveryPackage.longitude,
                            label = deliveryPackage.recipient
                        )
                    )
                }
            ) { Text(stringResource(R.string.navi)) }
        }
    }
}

@Composable
private fun PackageListScreen(
    packages: List<DeliveryPackage>,
    selectedCode: String?,
    onBack: () -> Unit,
    onSelectPackage: (String) -> Unit,
    onEditPackage: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F9))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.panel_delivery_list), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.package_count, packages.size), color = BrandBlue, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PackageList(packages, selectedCode, onSelectPackage, onEditPackage)
            }
        }
    }
}

@Composable
private fun PackageList(
    packages: List<DeliveryPackage>,
    selectedCode: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        packages.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(item.trackingCode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (item.trackingCode == selectedCode) Color(0xFFEAF0FF) else Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(item.status.statusColor())
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.trackingCode}  ${item.recipient}", fontWeight = FontWeight.Bold)
                        Text(item.address, color = MutedText)
                    }
                    StatusPill(item.status)
                }
            }
        }
    }
}

@Composable
private fun PackageEditScreen(
    deliveryPackage: DeliveryPackage,
    onBack: () -> Unit,
    onSave: (DeliveryPackage) -> Unit
) {
    val context = LocalContext.current
    var selectedStatus by remember { mutableStateOf(deliveryPackage.status) }
    var recipient by remember { mutableStateOf(deliveryPackage.recipient) }
    var memo by remember { mutableStateOf(deliveryPackage.memo) }
    var selectedTimeWindow by remember { mutableStateOf(deliveryPackage.timeWindow) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F9))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.package_edit_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(48.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WhiteCard {
                    Text(stringResource(R.string.recipient_info_title), fontWeight = FontWeight.Bold)
                    InfoRow(stringResource(R.string.tracking_code_label), deliveryPackage.trackingCode)
                    InfoRow(stringResource(R.string.address_label), deliveryPackage.address)
                    LabeledField(stringResource(R.string.recipient_field_label), stringResource(R.string.recipient_placeholder), recipient) { recipient = it }
                    LabeledField(stringResource(R.string.memo_field_label), stringResource(R.string.memo_placeholder), memo) { memo = it }
                }
                WhiteCard {
                    Text(stringResource(R.string.delivery_status_title), fontWeight = FontWeight.Bold)
                    SegmentedRow(DeliveryStatus.entries, selectedStatus, { it.label }) { selectedStatus = it }
                }
                WhiteCard {
                    Text(stringResource(R.string.time_window_title), fontWeight = FontWeight.Bold)
                    SegmentedRow(TimeWindow.entries, selectedTimeWindow, { it.label }) { selectedTimeWindow = it }
                }
                WhiteCard {
                    Text(stringResource(R.string.navigation_title), fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            context.startActivity(
                                openNavigationIntent(
                                    latitude = deliveryPackage.latitude,
                                    longitude = deliveryPackage.longitude,
                                    label = deliveryPackage.recipient
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.start_navi_button)) }
                }
            }
        }
        Button(
            onClick = {
                onSave(
                    deliveryPackage.copy(
                        recipient = recipient,
                        memo = memo,
                        status = selectedStatus,
                        timeWindow = selectedTimeWindow
                    )
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
        ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PackageRegistrationScreen(
    initialAddress: String,
    courses: List<RegularCourse>,
    onBack: () -> Unit,
    onOpenMap: (String) -> Unit,
    onOpenNavigation: (String) -> Unit,
    onAddCourseAddress: (String, String) -> Unit,
    onRegister: (List<PackageRegistrationResult>) -> Unit
) {
    val context = LocalContext.current
    val addresses = remember {
        mutableStateListOf<String>().apply {
            if (initialAddress.isNotBlank() &&
                initialAddress != "東京都千代田区丸の内1-1-1"
            ) add(initialAddress)
        }
    }
    var postalCode by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<AddressCandidate?>(null) }
    var manualInput by remember { mutableStateOf("") }
    var targetCourseCode by remember { mutableStateOf<String?>(null) }
    var courseSavedMessage by remember { mutableStateOf("") }

    val cameraOcrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            recognizeAddressFromBitmap(
                bitmap = bitmap,
                onSuccess = { candidate = it },
                onFailure = {
                    candidate = AddressCandidate("OCR", "", "住所を読み取れませんでした", "要確認")
                }
            )
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraOcrLauncher.launch(null)
        else candidate = AddressCandidate("カメラ", "", "カメラ権限が許可されていません", "権限確認")
    }
    val voiceInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) candidate = createAddressCandidateFromText("音声入力", spoken)
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var postalSearchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val canAddMore = addresses.size < 5
    val geocodedLocations = remember { mutableStateMapOf<String, LatLng>() }
    val pinOverrides = remember { mutableStateMapOf<String, LatLng>() }
    LaunchedEffect(addresses.size) {
        addresses.forEach { addr ->
            if (!geocodedLocations.containsKey(addr) && addr.isNotBlank()) {
                scope.launch {
                    val (lat, lng) = geocodeAddressPublic(addr)
                    if (lat != 0.0 || lng != 0.0) geocodedLocations[addr] = LatLng(lat, lng)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F9))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.package_registration_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(48.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 登録済み住所リスト
                WhiteCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.delivery_addresses_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.address_count_fraction, addresses.size), color = BrandBlue, fontWeight = FontWeight.Bold)
                    }
                    if (addresses.isEmpty()) {
                        Text(stringResource(R.string.add_address_hint), color = MutedText)
                    } else {
                        addresses.forEachIndexed { index, addr ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF6F7F9))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.address_index_label, index + 1), color = BrandBlue, style = MaterialTheme.typography.labelSmall)
                                    Text(addr, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { onOpenMap(addr) }) { Text(stringResource(R.string.open_map)) }
                                    TextButton(onClick = { addresses.removeAt(index) }) { Text(stringResource(R.string.delete), color = Color(0xFFE53935)) }
                                }
                            }
                        }
                    }
                }

                // 住所入力エリア（5件未満のとき表示）
                if (canAddMore) {
                    WhiteCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.add_address_section), fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                            PackageManager.PERMISSION_GRANTED
                                        ) cameraOcrLauncher.launch(null)
                                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    modifier = Modifier.height(36.dp)
                                ) { Text(stringResource(R.string.camera)) }
                                Button(
                                    onClick = { voiceInputLauncher.launch(createVoiceAddressIntent()) },
                                    modifier = Modifier.height(36.dp)
                                ) { Text(stringResource(R.string.voice)) }
                            }
                        }
                        Text(stringResource(R.string.registration_target), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = targetCourseCode == null,
                                onClick = {
                                    targetCourseCode = null
                                    courseSavedMessage = ""
                                },
                                label = { Text(stringResource(R.string.normal_package)) }
                            )
                        }
                        courses.chunked(2).forEach { rowCourses ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowCourses.forEach { course ->
                                    FilterChip(
                                        selected = targetCourseCode == course.code,
                                        onClick = {
                                            targetCourseCode = course.code
                                            courseSavedMessage = ""
                                        },
                                        label = { Text(stringResource(R.string.course_chip_label, course.code)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowCourses.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Text(
                            targetCourseCode?.let { stringResource(R.string.course_target_hint, it) }
                                ?: stringResource(R.string.normal_target_hint),
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        // 郵便番号検索
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = postalCode,
                                onValueChange = { postalCode = it.filter(Char::isDigit).take(7) },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.postal_code_label)) },
                                placeholder = { Text(stringResource(R.string.postal_placeholder)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Button(
                                onClick = {
                                    postalSearchJob?.cancel()
                                    postalSearchJob = scope.launch {
                                        val result = searchPostalCode(postalCode)
                                        candidate = result
                                        val isValid = result.address.isNotBlank() &&
                                            !result.address.contains("見つかりません") &&
                                            !result.address.contains("エラー")
                                        if (isValid) manualInput = result.address
                                    }
                                },
                                enabled = postalCode.length == 7
                            ) { Text(stringResource(R.string.search)) }
                        }
                        // 住所候補表示（成功・失敗ともに表示）
                        candidate?.let { c ->
                            if (c.address.isNotBlank()) {
                                TextBox("${c.sourceLabel} / ${c.confidenceLabel}\n${c.address}")
                            }
                        }
                        // 直接入力
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.manual_input_label)) },
                            placeholder = { Text(stringResource(R.string.manual_placeholder)) }
                        )
                        Button(
                            onClick = {
                                if (manualInput.isNotBlank()) {
                                    val selectedCourse = targetCourseCode
                                    if (selectedCourse == null) {
                                        addresses.add(manualInput)
                                    } else {
                                        onAddCourseAddress(selectedCourse, manualInput)
                                        courseSavedMessage = "${selectedCourse}コースへ住所を追加しました"
                                    }
                                    manualInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualInput.isNotBlank()
                        ) {
                            Text(targetCourseCode?.let { stringResource(R.string.add_to_course_button, it) } ?: stringResource(R.string.add_manual_button))
                        }
                        if (courseSavedMessage.isNotBlank()) {
                            Text(courseSavedMessage, color = SuccessGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (addresses.isNotEmpty()) {
                RegistrationMapPreview(
                    addresses = addresses,
                    geocodedLocations = geocodedLocations,
                    pinOverrides = pinOverrides,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(68.dp))
        }
        Button(
            onClick = {
                onRegister(
                    addresses.map { addr ->
                        val latLng = pinOverrides[addr] ?: geocodedLocations[addr]
                        PackageRegistrationResult(
                            address = addr,
                            hasLocker = false,
                            nameplate = "",
                            deliveryMemo = "",
                            packageMemo = "",
                            trackingNumber = "",
                            recipientName = "",
                            phoneNumber = "",
                            status = DeliveryStatus.Pending,
                            timeWindow = RegistrationTimeWindow.None,
                            shape = PackageShape.SmallBox,
                            colorType = PackageColorType.Kraft,
                            size = PackageSizeOption.Medium,
                            latitude = latLng?.latitude ?: 0.0,
                            longitude = latLng?.longitude ?: 0.0
                        )
                    }
                )
            },
            enabled = addresses.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                if (addresses.isNotEmpty()) stringResource(R.string.register_count_button, addresses.size) else stringResource(R.string.add_address_first),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RegistrationMapPreview(
    addresses: List<String>,
    geocodedLocations: Map<String, LatLng>,
    pinOverrides: MutableMap<String, LatLng>,
    modifier: Modifier = Modifier
) {
    val validPins = addresses.mapIndexedNotNull { index, addr ->
        val latLng = pinOverrides[addr] ?: geocodedLocations[addr]
        if (latLng != null) Triple(index, addr, latLng) else null
    }
    val defaultCenter = LatLng(35.681236, 139.767125)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(validPins.firstOrNull()?.third ?: defaultCenter, 14f)
    }
    LaunchedEffect(validPins.firstOrNull()?.third) {
        validPins.firstOrNull()?.third?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 14f))
        }
    }
    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false
            )
        ) {
            validPins.forEach { (index, addr, initialLatLng) ->
                key(addr) {
                    val markerState = remember { MarkerState(position = initialLatLng) }
                    LaunchedEffect(markerState) {
                        snapshotFlow { markerState.position }
                            .drop(1)
                            .collect { newPos: LatLng -> pinOverrides[addr] = newPos }
                    }
                    Marker(
                        state = markerState,
                        title = "${index + 1}件目",
                        snippet = addr,
                        draggable = true
                    )
                }
            }
        }
        val hintText = if (validPins.isEmpty()) stringResource(R.string.geocoding_hint) else stringResource(R.string.drag_pin_hint)
        Text(
            text = hintText,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF445064)
        )
    }
}

@Composable
private fun <T> SegmentedRow(items: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    val isSelected = item == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) BrandBlue else Color(0xFFF6F7F9))
                            .border(1.dp, Color(0xFFD2D7DF), RoundedCornerShape(8.dp))
                            .clickable { onSelected(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label(item), color = if (isSelected) Color.White else Color(0xFF445064), fontWeight = FontWeight.Bold)
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun LabeledField(label: String, placeholder: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text(placeholder) })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF6F7F9))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BrandBlue, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFF445064), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.clickable { onClick() } else modifier
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEAF0FF) else Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = if (selected) BrandBlue else MutedText)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF6F7F9))
            .padding(10.dp)
    ) {
        Text(label, color = MutedText, style = MaterialTheme.typography.labelMedium)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TextBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF6F7F9))
            .padding(10.dp)
    ) {
        Text(text, color = Color(0xFF445064), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusPill(status: DeliveryStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(status.statusColor())
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(status.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TimeWindowLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(TimeWindow.Unspecified, stringResource(R.string.legend_none))
        LegendDot(TimeWindow.Morning, stringResource(R.string.legend_morning))
        LegendDot(TimeWindow.Afternoon, stringResource(R.string.legend_afternoon))
        LegendDot(TimeWindow.Evening, stringResource(R.string.legend_evening))
        LegendDot(color = AbsentGray, label = stringResource(R.string.legend_absent))
    }
}

@Composable
private fun LegendDot(timeWindow: TimeWindow, label: String) {
    LegendDot(color = timeWindow.pinColor(), label = label)
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, color = MutedText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StorkMascot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val wing = Path().apply {
            moveTo(size.width * 0.20f, size.height * 0.44f)
            quadraticBezierTo(size.width * 0.08f, size.height * 0.12f, size.width * 0.48f, size.height * 0.28f)
            quadraticBezierTo(size.width * 0.40f, size.height * 0.40f, size.width * 0.25f, size.height * 0.52f)
            close()
        }
        val body = Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.52f)
            quadraticBezierTo(size.width * 0.50f, size.height * 0.28f, size.width * 0.74f, size.height * 0.45f)
            quadraticBezierTo(size.width * 0.62f, size.height * 0.66f, size.width * 0.36f, size.height * 0.66f)
            quadraticBezierTo(size.width * 0.24f, size.height * 0.62f, size.width * 0.30f, size.height * 0.52f)
            close()
        }
        val neck = Path().apply {
            moveTo(size.width * 0.68f, size.height * 0.44f)
            quadraticBezierTo(size.width * 0.76f, size.height * 0.22f, size.width * 0.90f, size.height * 0.32f)
        }
        val beak = Path().apply {
            moveTo(size.width * 0.90f, size.height * 0.33f)
            lineTo(size.width * 0.99f, size.height * 0.38f)
            lineTo(size.width * 0.88f, size.height * 0.40f)
            close()
        }
        val parcel = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.43f)
            lineTo(size.width * 0.88f, size.height * 0.43f)
            lineTo(size.width * 0.88f, size.height * 0.58f)
            lineTo(size.width * 0.70f, size.height * 0.58f)
            close()
        }

        drawPath(wing, color = Color(0xFFEAF0FF))
        drawPath(wing, color = BrandBlue, style = Stroke(width = 2.5f))
        drawPath(body, color = Color.White)
        drawPath(body, color = BrandBlue, style = Stroke(width = 2.5f))
        drawPath(neck, color = BrandBlue, style = Stroke(width = 3f))
        drawCircle(Color(0xFF111827), radius = 1.8f, center = Offset(size.width * 0.86f, size.height * 0.32f))
        drawPath(beak, color = Color(0xFFE26D2D))
        drawPath(parcel, color = Color(0xFFD9A441))
    }
}

private fun TimeWindow.pinColor(): Color = when (this) {
    TimeWindow.Morning -> Color(0xFF2F80ED)
    TimeWindow.Afternoon -> Color(0xFFE08A1E)
    TimeWindow.Evening -> Color(0xFF7A4BD8)
    TimeWindow.Unspecified,
    TimeWindow.All -> Color(0xFF20242C)
}

private fun TimeWindow.markerHue(): Float = when (this) {
    TimeWindow.Morning -> BitmapDescriptorFactory.HUE_AZURE
    TimeWindow.Afternoon -> BitmapDescriptorFactory.HUE_ORANGE
    TimeWindow.Evening -> BitmapDescriptorFactory.HUE_VIOLET
    TimeWindow.Unspecified,
    TimeWindow.All -> BitmapDescriptorFactory.HUE_RED
}

private fun deliveryMarkerIcon(routeNumber: Int, deliveryPackage: DeliveryPackage) =
    when (deliveryPackage.status) {
        DeliveryStatus.Absent -> labeledMarkerIcon("不", AbsentMarkerArgb)
        DeliveryStatus.Redelivery -> labeledMarkerIcon("再", RedeliveryMarkerArgb)
        else -> labeledMarkerIcon(routeNumber.toString(), deliveryPackage.timeWindow.markerArgb())
    }

private fun labeledMarkerIcon(label: String, markerColor: Int) =
    BitmapDescriptorFactory.fromBitmap(createLabeledMarkerBitmap(label, markerColor))

private fun createLabeledMarkerBitmap(label: String, markerColor: Int): Bitmap {
    val width = 104
    val height = 128
    val centerX = width / 2f
    val circleRadius = 42f
    val circleCenterY = 48f
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = markerColor
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.FILL
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = if (label.length <= 2) 34f else 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val pointer = android.graphics.Path().apply {
        moveTo(centerX - 18f, 82f)
        lineTo(centerX, height - 8f)
        lineTo(centerX + 18f, 82f)
        close()
    }

    canvas.drawCircle(centerX + 3f, circleCenterY + 5f, circleRadius, shadowPaint)
    canvas.drawPath(pointer, fillPaint)
    canvas.drawCircle(centerX, circleCenterY, circleRadius, fillPaint)
    canvas.drawCircle(centerX, circleCenterY, circleRadius, strokePaint)

    val textY = circleCenterY - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(label, centerX, textY, textPaint)

    return bitmap
}

private fun TimeWindow.markerArgb(): Int = when (this) {
    TimeWindow.Morning -> android.graphics.Color.rgb(47, 128, 237)
    TimeWindow.Afternoon -> android.graphics.Color.rgb(224, 138, 30)
    TimeWindow.Evening -> android.graphics.Color.rgb(122, 75, 216)
    TimeWindow.Unspecified,
    TimeWindow.All -> android.graphics.Color.rgb(32, 36, 44)
}

private fun DeliveryStatus.statusColor(): Color = when (this) {
    DeliveryStatus.Pending -> Color(0xFF637083)
    DeliveryStatus.InProgress -> BrandBlue
    DeliveryStatus.Completed -> SuccessGreen
    DeliveryStatus.Absent -> AbsentGray
    DeliveryStatus.Redelivery -> RedeliveryGreen
    DeliveryStatus.ReturnToCompany -> Color(0xFFB3261E)
}

private fun openMapIntent(address: String): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
}

private fun openNavigationIntent(address: String): Intent {
    return Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(address)}"))
}

private fun openNavigationIntent(latitude: Double, longitude: Double, label: String): Intent {
    val navigationUri = Uri.Builder()
        .scheme("https")
        .authority("www.google.com")
        .path("maps/dir/")
        .appendQueryParameter("api", "1")
        .appendQueryParameter("destination", "$latitude,$longitude")
        .appendQueryParameter("travelmode", "driving")
        .appendQueryParameter("dir_action", "navigate")
        .appendQueryParameter("hl", "ja")
        .appendQueryParameter("gl", "jp")
        .build()

    return Intent(
        Intent.ACTION_VIEW,
        navigationUri
    ).apply {
        setPackage("com.google.android.apps.maps")
        putExtra(Intent.EXTRA_TITLE, label)
    }
}

private fun recognizeAddressFromBitmap(
    bitmap: Bitmap,
    onSuccess: (AddressCandidate) -> Unit,
    onFailure: () -> Unit
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    recognizer.process(image)
        .addOnSuccessListener { result ->
            onSuccess(createAddressCandidateFromText("OCR読み取り", result.text))
        }
        .addOnFailureListener { e ->
            android.util.Log.e("OCR", "Text recognition failed", e)
            onFailure()
        }
}

private fun createAddressCandidateFromText(sourceLabel: String, sourceText: String): AddressCandidate {
    val postalCode = Regex("""\d{3}[-\s]?\d{4}""")
        .find(sourceText)
        ?.value
        ?.filter(Char::isDigit)
        .orEmpty()
    val address = extractLikelyAddress(sourceText)

    return AddressCandidate(
        sourceLabel = sourceLabel,
        postalCode = postalCode,
        address = address.ifBlank { sourceText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() },
        confidenceLabel = if (address.isNotBlank()) "住所候補" else "要確認"
    )
}

private fun extractLikelyAddress(sourceText: String): String {
    val lines = sourceText
        .lineSequence()
        .map { it.trim().replace("〒", "") }
        .filter { it.isNotBlank() }
        .toList()
    val addressIndex = lines.indexOfFirst { line ->
        listOf("東京都", "北海道", "大阪府", "京都府", "県").any { marker -> line.contains(marker) }
    }
    if (addressIndex < 0) return ""

    return lines
        .drop(addressIndex)
        .take(2)
        .joinToString("")
        .replace(Regex("""\d{3}[-\s]?\d{4}"""), "")
        .trim()
}

private fun createVoiceAddressIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "届け先住所を話してください")
    }
}

private fun yesNo(value: Boolean): String = if (value) "あり" else "なし"

private val BrandBlue = Color(0xFF2457D6)
private val BrandPurple = Color(0xFF6D55B3)
private val MutedText = Color(0xFF637083)
private val SuccessGreen = Color(0xFF177245)
private val AbsentGray = Color(0xFF7A8491)
private val AbsentMarkerArgb = android.graphics.Color.rgb(122, 132, 145)
private val RedeliveryGreen = Color(0xFF00897B)
private val RedeliveryMarkerArgb = android.graphics.Color.rgb(0, 137, 123)

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    showSystemUi = true,
    name = "HakobunApp Preview"
)
@Composable
private fun HakobunAppPreview() {
    MaterialTheme {
        HakobunApp()
    }
}
