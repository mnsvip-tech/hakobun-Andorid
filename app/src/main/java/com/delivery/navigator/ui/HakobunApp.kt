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
import androidx.compose.runtime.derivedStateOf
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
import com.delivery.navigator.data.defaultRegularCourses
import com.delivery.navigator.data.geocodeAddressPublic
import com.delivery.navigator.data.exportPackages
import com.delivery.navigator.data.fetchDrivingRoutePointsCached
import com.delivery.navigator.data.generateDebugTestPackages
import com.delivery.navigator.data.hidesMapPin
import com.delivery.navigator.data.importPackages
import com.delivery.navigator.data.LocalDeliveryStore
import com.delivery.navigator.data.regularCourses
import com.delivery.navigator.data.samplePackages
import com.delivery.navigator.data.searchPostalCode
import kotlinx.coroutines.launch
import com.delivery.navigator.model.AccountMenuItem
import com.delivery.navigator.model.AddressCandidate
import com.delivery.navigator.model.CourseAddress
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
import com.delivery.navigator.model.toTimeWindow
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
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.delivery.navigator.model.MembershipPlan
import com.delivery.navigator.model.UserProfile
import com.delivery.navigator.model.isFreeTrialExpired
import com.delivery.navigator.model.FREE_TRIAL_DAYS
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
            addAll(savedPackages)
        }
    }
    val regularCourseList = remember(deliveryStore) {
        mutableStateListOf<RegularCourse>().apply {
            val savedCourses = deliveryStore.loadRegularCourses()
            val merged = defaultRegularCourses().map { default ->
                savedCourses.firstOrNull { it.code == default.code } ?: default
            }
            addAll(merged)
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
    var userProfile by remember { mutableStateOf(deliveryStore.loadUserProfile()) }
    val isLoggedIn = userProfile.isRegistered
    val onLoginToggle: () -> Unit = {
        if (userProfile.isRegistered) {
            val loggedOut = userProfile.copy(isRegistered = false)
            userProfile = loggedOut
            deliveryStore.saveUserProfile(loggedOut)
        } else {
            activeMenuItem = AccountMenuItem.Account
        }
    }
    var purchaseUpdateTick by remember { mutableStateOf(0) }
    val billingClient = remember {
        BillingClient.newBuilder(context)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val hasActivePurchase = purchases?.any {
                        it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(SUBSCRIPTION_PRODUCT_ID)
                    } == true
                    if (hasActivePurchase) {
                        val updated = userProfile.copy(plan = MembershipPlan.Premium)
                        userProfile = updated
                        deliveryStore.saveUserProfile(updated)
                        purchaseUpdateTick++
                    }
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
    }
    suspend fun reconcileSubscriptionStatus() {
        if (com.delivery.navigator.BuildConfig.FORCE_PLAN != "NONE") {
            // 実機テスト用ビルド(premiumOn/premiumOff)では課金照会を行わず、強制したプラン状態を維持する。
            val forcedPlan = if (com.delivery.navigator.BuildConfig.FORCE_PLAN == "PREMIUM") MembershipPlan.Premium else MembershipPlan.Free
            if (userProfile.plan != forcedPlan) {
                val updated = userProfile.copy(plan = forcedPlan)
                userProfile = updated
                deliveryStore.saveUserProfile(updated)
            }
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val activePurchase = result.purchasesList.firstOrNull { purchase ->
            purchase.products.contains(SUBSCRIPTION_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (activePurchase != null) {
            if (!activePurchase.isAcknowledged) {
                billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(activePurchase.purchaseToken)
                        .build()
                )
            }
            if (userProfile.plan != MembershipPlan.Premium) {
                val updated = userProfile.copy(plan = MembershipPlan.Premium)
                userProfile = updated
                deliveryStore.saveUserProfile(updated)
            }
        } else if (userProfile.plan == MembershipPlan.Premium) {
            val updated = userProfile.copy(plan = MembershipPlan.Free)
            userProfile = updated
            deliveryStore.saveUserProfile(updated)
        }
    }
    LaunchedEffect(purchaseUpdateTick) {
        if (purchaseUpdateTick > 0) reconcileSubscriptionStatus()
    }
    DisposableEffect(Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    coroutineScope.launch { reconcileSubscriptionStatus() }
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
        onDispose { billingClient.endConnection() }
    }
    var showPaywallDialog by remember { mutableStateOf(false) }
    val isFreeExpired = userProfile.plan != MembershipPlan.Premium && userProfile.isFreeTrialExpired()
    val onRegisterPackageAttempt = {
        when {
            !isLoggedIn -> activeMenuItem = AccountMenuItem.Account
            isFreeExpired -> showPaywallDialog = true
            else -> isRegisteringPackage = true
        }
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
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var locationCallback by remember { mutableStateOf<com.google.android.gms.location.LocationCallback?>(null) }

    fun startLocationUpdates() {
        val priority = if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
        else com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let { currentLocation = LatLng(it.latitude, it.longitude) }
        }
        val request = com.google.android.gms.location.LocationRequest.Builder(priority, 5000L)
            .setMinUpdateIntervalMillis(2000L).build()
        val callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { currentLocation = LatLng(it.latitude, it.longitude) }
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasAny = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasAny) startLocationUpdates()
    }

    DisposableEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        onDispose {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
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
    LaunchedEffect(packages.size, packages.firstOrNull()?.trackingCode, packages.lastOrNull()?.trackingCode, currentLocation) {
        val origin = currentLocation ?: LatLng(currentRouteOrigin().first, currentRouteOrigin().second)
        assignFixedRouteNumbers(fixedRouteNumbers, packages, origin.latitude, origin.longitude)
    }

    val visiblePackages = packages.filter {
        selectedWindow == TimeWindow.All || it.timeWindow == selectedWindow
    }.filter {
        selectedSummaryFilter?.matches(it) ?: true
    }
    val gpsOrigin = currentLocation ?: LatLng(currentRouteOrigin().first, currentRouteOrigin().second)
    val gpsRouteStops by remember(gpsOrigin) {
        derivedStateOf {
            calculateNearestRoute(packages.filterNot { it.status.hidesMapPin() }, gpsOrigin.latitude, gpsOrigin.longitude)
        }
    }
    val gpsRouteNumberMap = remember(gpsRouteStops) {
        gpsRouteStops.associate { it.deliveryPackage.trackingCode to it.routeNumber }
    }
    var nextStopVoiceHint by remember { mutableStateOf<String?>(null) }
    val nextStopVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (isNextDeliveryCommand(spoken)) {
            val currentIndex = gpsRouteStops.indexOfFirst { it.deliveryPackage.trackingCode == selectedPackageCode }
            val next = if (currentIndex >= 0 && currentIndex + 1 < gpsRouteStops.size) {
                gpsRouteStops[currentIndex + 1].deliveryPackage
            } else {
                gpsRouteStops.firstOrNull()?.deliveryPackage
            }
            if (next != null) {
                nextStopVoiceHint = null
                selectedPackageCode = next.trackingCode
            } else {
                nextStopVoiceHint = context.getString(R.string.voice_next_stop_none)
            }
        } else if (spoken.isNotBlank()) {
            nextStopVoiceHint = context.getString(R.string.voice_status_unrecognized, spoken)
        }
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
        homePanel = null
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
                    billingClient = billingClient,
                    onBack = { activeMenuItem = null },
                    onLoginToggle = onLoginToggle,
                    onSaveProfile = { updated ->
                        userProfile = updated
                        deliveryStore.saveUserProfile(updated)
                    },
                    onSeedTestData = {
                        packages.clear()
                        packages.addAll(generateDebugTestPackages(200))
                        persistPackages()
                        activeMenuItem = null
                    },
                    onClearTestData = {
                        packages.clear()
                        fixedRouteNumbers.clear()
                        selectedPackageCode = ""
                        persistPackages()
                        activeMenuItem = null
                    }
                )
            } else {
                AccountMenuDetailScreen(
                    item = item,
                    isLoggedIn = isLoggedIn,
                    onBack = { activeMenuItem = null },
                    onLoginToggle = onLoginToggle
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
                onLoginToggle = onLoginToggle,
                onGoToBackup = {
                    isSupportHubOpen = false
                    homePanel = HomePanel.Backup
                },
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
            val displayedPackages = if (selectedSummaryFilter == null) visiblePackages else summaryPackages
            PackageListScreen(
                packages = displayedPackages,
                routeNumberMap = gpsRouteNumberMap,
                selectedCode = selectedPackage?.trackingCode,
                onBack = { homePanel = null },
                onSelectPackage = onPackageSelected,
                onEditPackage = { code ->
                    homePanel = null
                    editingPackageCode = code
                },
                onCompleteAll = {
                    for (i in packages.indices) {
                        if (packages[i].status != DeliveryStatus.Completed) {
                            packages[i] = packages[i].copy(status = DeliveryStatus.Completed)
                        }
                    }
                    persistPackages()
                }
            )
            return@MaterialTheme
        }

        if (homePanel == HomePanel.Courses) {
            RegularCourseScreen(
                courses = regularCourseList,
                isLoggedIn = isLoggedIn,
                isFreeExpired = isFreeExpired,
                onRequireLogin = {
                    homePanel = null
                    activeMenuItem = AccountMenuItem.Account
                },
                onShowPaywall = { showPaywallDialog = true },
                onBack = { homePanel = null },
                onLoadCourse = { course ->
                    val otherCoursePrefixes = packages
                        .map { it.trackingCode }
                        .filter { it.startsWith("COURSE-") && !it.startsWith("COURSE-${course.code}-") }
                    packages.removeAll { it.trackingCode in otherCoursePrefixes }
                    otherCoursePrefixes.forEach { fixedRouteNumbers.remove(it) }
                    val coursePackages = createPackagesFromCourse(course, packages.size)
                    packages.addAll(coursePackages)
                    coursePackages.forEachIndexed { index, packageItem ->
                        fixedRouteNumbers[packageItem.trackingCode] = index + 1
                    }
                    selectedPackageCode = packages.lastOrNull()?.trackingCode ?: selectedPackageCode
                    persistPackages()
                    homePanel = null
                },
                onClearCourseFromMap = { course ->
                    val prefix = "COURSE-${course.code}-"
                    val removedCodes = packages.filter { it.trackingCode.startsWith(prefix) }.map { it.trackingCode }
                    packages.removeAll { it.trackingCode.startsWith(prefix) }
                    removedCodes.forEach { fixedRouteNumbers.remove(it) }
                    if (selectedPackageCode in removedCodes) {
                        selectedPackageCode = packages.lastOrNull()?.trackingCode ?: ""
                    }
                    persistPackages()
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
                onUpdateCourseAddress = { courseCode, addressIndex, recipient, address, timeWindow, memo ->
                    coroutineScope.launch {
                        val updated = createCourseAddressFromInput(recipient, address, timeWindow, memo)
                        val index = regularCourseList.indexOfFirst { it.code == courseCode }
                        if (index >= 0) {
                            val course = regularCourseList[index]
                            if (addressIndex in course.addresses.indices) {
                                val newAddresses = course.addresses.toMutableList()
                                newAddresses[addressIndex] = updated
                                regularCourseList[index] = course.copy(addresses = newAddresses)
                                persistRegularCourses()
                            }
                        }
                    }
                },
                onDeleteCourseAddress = { courseCode, addressIndex ->
                    val index = regularCourseList.indexOfFirst { it.code == courseCode }
                    if (index >= 0) {
                        val course = regularCourseList[index]
                        if (addressIndex in course.addresses.indices) {
                            val newAddresses = course.addresses.toMutableList().also { it.removeAt(addressIndex) }
                            regularCourseList[index] = course.copy(addresses = newAddresses)
                            persistRegularCourses()
                        }
                    }
                }
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
                onBack = { isRegisteringPackage = false },
                onOpenMap = { addr -> context.startActivity(openMapIntent(addr)) },
                onOpenNavigation = { addr -> context.startActivity(openNavigationIntent(addr)) },
                onRegister = { results ->
                    coroutineScope.launch {
                        var lastAddedCode: String? = null
                        results.forEach { result ->
                            val newPackage = createDeliveryPackageFromRegistration(result, packages.size)
                            packages.add(newPackage)
                            lastAddedCode = newPackage.trackingCode
                        }
                        lastAddedCode?.let { selectedPackageCode = it }
                        persistPackages()
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
                    onEditPackage = { code -> editingPackageCode = code },
                    currentLocation = currentLocation,
                    fusedLocationClient = fusedLocationClient,
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
                    onVoiceNextStop = {
                        nextStopVoiceHint = null
                        nextStopVoiceLauncher.launch(createVoiceNextStopIntent())
                    },
                    voiceNextStopHint = nextStopVoiceHint,
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
                        routeNumberMap = gpsRouteNumberMap,
                        courses = regularCourseList,
                        selectedCode = selectedPackage?.trackingCode,
                        backupText = backupText,
                        importText = importText,
                        onClose = { homePanel = null },
                        onSelectPackage = onPackageSelected,
                        onEditPackage = { code ->
                            homePanel = null
                            editingPackageCode = code
                        },
                        onExport = { backupText = exportPackages(packages) },
                        onClearBackup = { backupText = "" },
                        onImportTextChange = { importText = it },
                        onImport = {
                            val importedPackages = importPackages(importText, packages.size)
                            packages.addAll(importedPackages)
                            selectedPackageCode = packages.lastOrNull()?.trackingCode ?: selectedPackageCode
                            persistPackages()
                            importText = ""
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
                            val otherCoursePrefixes = packages
                                .map { it.trackingCode }
                                .filter { it.startsWith("COURSE-") && !it.startsWith("COURSE-${course.code}-") }
                            packages.removeAll { it.trackingCode in otherCoursePrefixes }
                            otherCoursePrefixes.forEach { fixedRouteNumbers.remove(it) }
                            val coursePackages = createPackagesFromCourse(course, packages.size)
                            packages.addAll(coursePackages)
                            coursePackages.forEachIndexed { index, packageItem ->
                                fixedRouteNumbers[packageItem.trackingCode] = index + 1
                            }
                            selectedPackageCode = packages.lastOrNull()?.trackingCode ?: selectedPackageCode
                            persistPackages()
                            homePanel = null
                        },
                        onClearCourseFromMap = { course ->
                            val prefix = "COURSE-${course.code}-"
                            val removedCodes = packages.filter { it.trackingCode.startsWith(prefix) }.map { it.trackingCode }
                            packages.removeAll { it.trackingCode.startsWith(prefix) }
                            removedCodes.forEach { fixedRouteNumbers.remove(it) }
                            if (selectedPackageCode in removedCodes) {
                                selectedPackageCode = packages.lastOrNull()?.trackingCode ?: ""
                            }
                            persistPackages()
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
    packages: List<DeliveryPackage>,
    originLat: Double = 35.681236,
    originLng: Double = 139.767125
) {
    val currentCodes = packages.map { it.trackingCode }.toSet()
    fixedRouteNumbers.keys.retainAll(currentCodes)

    val unnumberedPackages = packages.filterNot { fixedRouteNumbers.containsKey(it.trackingCode) }
    if (unnumberedPackages.isEmpty()) return

    val nextRouteNumber = (fixedRouteNumbers.values.maxOrNull() ?: 0) + 1
    calculateNearestRoute(unnumberedPackages, originLat, originLng).forEachIndexed { index, routeStop ->
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
    onVoiceNextStop: () -> Unit,
    voiceNextStopHint: String?,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onVoiceNextStop, modifier = Modifier.height(36.dp)) {
                Text(stringResource(R.string.voice))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onToggleMapControls, modifier = Modifier.height(36.dp)) {
                Text(if (showMapControls) stringResource(R.string.hide_summary) else stringResource(R.string.show_summary))
            }
        }
        voiceNextStopHint?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xEEFFFFFF)), shape = RoundedCornerShape(10.dp)) {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color(0xFFE53935),
                    style = MaterialTheme.typography.labelSmall
                )
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
    routeNumberMap: Map<String, Int> = emptyMap(),
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
    onClearBackup: () -> Unit,
    onAddCourseAddress: (String, String, String, TimeWindow, String) -> Unit,
    onLoadCourse: (RegularCourse) -> Unit,
    onClearCourseFromMap: (RegularCourse) -> Unit,
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
                HomePanel.Packages -> PackageList(packages, routeNumberMap, selectedCode, onSelectPackage, onEditPackage)
                HomePanel.Courses -> RegularCoursePanel(courses, onLoadCourse, onClearCourseFromMap, onAddCourseAddress)
                HomePanel.Backup -> AddressBackupPanel(backupText, importText, onExport, onImportTextChange, onImport, onClearBackup, onClose)
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
    onEditPackage: (String) -> Unit,
    currentLocation: LatLng? = null,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null,
    onNavigate: (DeliveryPackage) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveOrigin = currentLocation ?: origin
    val routeStops = remember(packages, effectiveOrigin) {
        calculateNearestRoute(packages, effectiveOrigin.latitude, effectiveOrigin.longitude)
    }
    val selectedPackage = routeStops.firstOrNull { it.deliveryPackage.trackingCode == selectedCode }?.deliveryPackage
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(effectiveOrigin, 12.5f)
    }
    val context = LocalContext.current
    var mapLoaded by remember { mutableStateOf(false) }
    var mapBearing by remember { mutableStateOf(0f) }
    var drivingRoutePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val activeRouteDestination = selectedPackage ?: routeStops.firstOrNull()?.deliveryPackage
    var initialCameraMoved by remember { mutableStateOf(false) }
    LaunchedEffect(mapLoaded, currentLocation) {
        if (!mapLoaded || initialCameraMoved) return@LaunchedEffect
        val target = currentLocation ?: return@LaunchedEffect
        initialCameraMoved = true
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 14f))
    }
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
                fetchDrivingRoutePointsCached(
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
            properties = MapProperties(
                isMyLocationEnabled = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ),
            uiSettings = MapUiSettings(
                compassEnabled = true,
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                tiltGesturesEnabled = true,
                rotationGesturesEnabled = true,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true
            ),
            onMapLoaded = { mapLoaded = true }
        ) {
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
                val routeNumber = stop.routeNumber
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(item.latitude, item.longitude)),
                    title = "$routeNumber. ${item.recipient}",
                    snippet = "${item.deliveryTimeWindow.label} / ${item.address}",
                    icon = deliveryMarkerIcon(routeNumber, item),
                    anchor = Offset(0.5f, 1f),
                    onClick = {
                        onSelect(item.trackingCode)
                        onEditPackage(item.trackingCode)
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
                if (!mapLoaded) return@FloatingMapButton
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val client = fusedLocationClient
                if ((hasFine || hasCoarse) && client != null) {
                    val priority = if (hasFine)
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
                    else
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    client.getCurrentLocation(priority, null).addOnSuccessListener { loc ->
                        val target = loc?.let { LatLng(it.latitude, it.longitude) } ?: currentLocation
                        if (target != null) {
                            coroutineScope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 15f))
                            }
                        }
                    }
                } else {
                    currentLocation?.let { location ->
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(location, 15f))
                        }
                    }
                }
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
            AccountMenuPanel(
                isLoggedIn = isLoggedIn,
                onSelect = onSelect,
                onLoginToggle = onLoginToggle
            )
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
    billingClient: BillingClient,
    onBack: () -> Unit,
    onLoginToggle: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit,
    onSeedTestData: () -> Unit = {},
    onClearTestData: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    var isEditing by remember(userProfile.isRegistered) { mutableStateOf(!userProfile.isRegistered) }
    var driverName by remember { mutableStateOf(userProfile.driverName) }
    var base by remember { mutableStateOf(userProfile.base) }
    var contact by remember { mutableStateOf(userProfile.contact) }
    var email by remember { mutableStateOf(userProfile.email) }
    var subscribeMessage by remember { mutableStateOf("") }

    val isPremium = userProfile.plan == MembershipPlan.Premium
    val isFreeExpired = !isPremium && userProfile.isFreeTrialExpired()
    val trialRemainingDays = if (!isPremium && !isFreeExpired && userProfile.registeredAt > 0L) {
        val trialEndMillis = userProfile.registeredAt + FREE_TRIAL_DAYS * 24L * 60L * 60L * 1000L
        kotlin.math.ceil((trialEndMillis - System.currentTimeMillis()) / (24.0 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    } else 0

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
                            onSaveProfile(userProfile.copy(
                                driverName = driverName,
                                base = base,
                                contact = contact,
                                email = email,
                                isRegistered = true,
                                registeredAt = if (userProfile.registeredAt > 0L) userProfile.registeredAt else System.currentTimeMillis()
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
                                if (isFreeExpired) {
                                    stringResource(R.string.free_trial_expired)
                                } else {
                                    stringResource(R.string.free_trial_active_title, trialRemainingDays)
                                },
                                color = if (isFreeExpired) Color(0xFFE53935) else BrandBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Text(stringResource(R.string.trial_after_register), color = MutedText, style = MaterialTheme.typography.bodySmall)
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
                if (com.delivery.navigator.BuildConfig.DEBUG && userProfile.isRegistered) {
                    Text(stringResource(R.string.debug_section_title), color = MutedText, style = MaterialTheme.typography.labelSmall)
                    if (isPremium) {
                        OutlinedButton(
                            onClick = { onSaveProfile(userProfile.copy(plan = MembershipPlan.Free)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.debug_expire_trial_button)) }
                    } else {
                        OutlinedButton(
                            onClick = { onSaveProfile(userProfile.copy(plan = MembershipPlan.Premium)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.debug_reset_trial_button)) }
                    }
                    OutlinedButton(
                        onClick = onSeedTestData,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("実機テスト用ダミーデータ200件を投入") }
                    OutlinedButton(
                        onClick = onClearTestData,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935))
                    ) { Text("テストデータを全件クリア") }
                }
            }
        }
    }
}

private data class FaqItem(val question: String, val answer: String)

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
    val faqItems = listOf(
        FaqItem(stringResource(R.string.faq_q_01), stringResource(R.string.faq_a_01)),
        FaqItem(stringResource(R.string.faq_q_02), stringResource(R.string.faq_a_02)),
        FaqItem(stringResource(R.string.faq_q_03), stringResource(R.string.faq_a_03)),
        FaqItem(stringResource(R.string.faq_q_04), stringResource(R.string.faq_a_04)),
        FaqItem(stringResource(R.string.faq_q_05), stringResource(R.string.faq_a_05)),
        FaqItem(stringResource(R.string.faq_q_06), stringResource(R.string.faq_a_06)),
        FaqItem(stringResource(R.string.faq_q_07), stringResource(R.string.faq_a_07)),
        FaqItem(stringResource(R.string.faq_q_08), stringResource(R.string.faq_a_08)),
        FaqItem(stringResource(R.string.faq_q_09), stringResource(R.string.faq_a_09)),
        FaqItem(stringResource(R.string.faq_q_10), stringResource(R.string.faq_a_10)),
        FaqItem(stringResource(R.string.faq_q_11), stringResource(R.string.faq_a_11)),
        FaqItem(stringResource(R.string.faq_q_12), stringResource(R.string.faq_a_12)),
        FaqItem(stringResource(R.string.faq_q_13), stringResource(R.string.faq_a_13)),
        FaqItem(stringResource(R.string.faq_q_14), stringResource(R.string.faq_a_14)),
        FaqItem(stringResource(R.string.faq_q_15), stringResource(R.string.faq_a_15)),
        FaqItem(stringResource(R.string.faq_q_16), stringResource(R.string.faq_a_16)),
        FaqItem(stringResource(R.string.faq_q_17), stringResource(R.string.faq_a_17))
    )
    val categories = listOf(
        stringResource(R.string.faq_cat_basic) to faqItems.subList(0, 5),
        stringResource(R.string.faq_cat_ocr)   to faqItems.subList(5, 7),
        stringResource(R.string.faq_cat_notif) to faqItems.subList(7, 9),
        stringResource(R.string.faq_cat_sub)   to faqItems.subList(9, 12),
        stringResource(R.string.faq_cat_data)  to faqItems.subList(12, 14),
        stringResource(R.string.faq_cat_other) to faqItems.subList(14, 17)
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WhiteCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.faq_header_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.faq_header_desc),
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
                    stringResource(R.string.faq_footer_title),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.faq_footer_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

private fun standardPinMarkerIcon(label: String, color: Int): com.google.android.gms.maps.model.BitmapDescriptor {
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    val width = (30f * density).toInt()
    val height = (44f * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val cx = width / 2f
    val r = width / 2f
    // ピン本体
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawCircle(cx, r, r, bodyPaint)
    val tailPath = android.graphics.Path().apply {
        moveTo(cx - r * 0.45f, r * 1.3f)
        lineTo(cx + r * 0.45f, r * 1.3f)
        lineTo(cx, height.toFloat())
        close()
    }
    canvas.drawPath(tailPath, bodyPaint)
    // 白い内円
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
    canvas.drawCircle(cx, r, r * 0.52f, innerPaint)
    // ルート番号または配達状態
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = r * if (label.length >= 2) 0.72f else 0.83f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textY = r - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, cx, textY, textPaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ヘッダー
        WhiteCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.terms_header_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.terms_header_date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Text(
                    stringResource(R.string.terms_header_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF333333)
                )
            }
        }

        // 第1条 サービス概要
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s01_title),
                stringResource(R.string.terms_s01_body)
            )
        }

        // 第2条 利用資格
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s02_title),
                stringResource(R.string.terms_s02_body)
            )
        }

        // 第3条 サブスクリプション・課金
        WhiteCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TermsSection(
                    stringResource(R.string.terms_s03_title),
                    stringResource(R.string.terms_s03_body)
                )
            }
        }

        // 第4条 収集する情報とその利用目的
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s04_title),
                stringResource(R.string.terms_s04_body)
            )
        }

        // 第5条 第三者サービス
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s05_title),
                stringResource(R.string.terms_s05_body)
            )
        }

        // 第6条 データの保管・セキュリティ
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s06_title),
                stringResource(R.string.terms_s06_body)
            )
        }

        // 第7条 禁止事項
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s07_title),
                stringResource(R.string.terms_s07_body)
            )
        }

        // 第8条 免責事項
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s08_title),
                stringResource(R.string.terms_s08_body)
            )
        }

        // 第9条 規約の変更
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s09_title),
                stringResource(R.string.terms_s09_body)
            )
        }

        // 第10条 準拠法・管轄
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_s10_title),
                stringResource(R.string.terms_s10_body)
            )
        }

        // お問い合わせ
        WhiteCard {
            TermsSection(
                stringResource(R.string.terms_contact_title),
                stringResource(R.string.terms_contact_body)
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
private fun RegularCourseScreen(
    courses: List<RegularCourse>,
    isLoggedIn: Boolean,
    isFreeExpired: Boolean,
    onRequireLogin: () -> Unit,
    onShowPaywall: () -> Unit,
    onBack: () -> Unit,
    onLoadCourse: (RegularCourse) -> Unit,
    onClearCourseFromMap: (RegularCourse) -> Unit,
    onAddCourseAddress: (String, String, String, TimeWindow, String) -> Unit,
    onUpdateCourseAddress: (String, Int, String, String, TimeWindow, String) -> Unit,
    onDeleteCourseAddress: (String, Int) -> Unit
) {
    val context = LocalContext.current
    var selectedCourseCode by remember { mutableStateOf(courses.firstOrNull()?.code ?: "A") }
    val selectedCourse = courses.firstOrNull { it.code == selectedCourseCode } ?: courses.firstOrNull()
    var newRecipient by remember { mutableStateOf("") }
    var newAddress by remember { mutableStateOf("") }
    var newTimeWindow by remember { mutableStateOf(TimeWindow.Unspecified) }
    var newMemo by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<AddressCandidate?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var postalSearchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var ocrImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraOcrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            ocrImageUri?.let { uri ->
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                recognizeAddressFromBitmap(
                    bitmap = bitmap,
                    onSuccess = { candidate = it },
                    onFailure = {
                        candidate = AddressCandidate("OCR", "", "住所を読み取れませんでした", "要確認")
                    }
                )
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = java.io.File(context.cacheDir, "ocr/ocr_${System.currentTimeMillis()}.jpg")
                .also { it.parentFile?.mkdirs() }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            ocrImageUri = uri
            cameraOcrLauncher.launch(uri)
        } else {
            candidate = AddressCandidate("カメラ", "", "カメラ権限が許可されていません", "権限確認")
        }
    }
    val voiceInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) candidate = createAddressCandidateFromText("音声入力", spoken)
    }
    LaunchedEffect(candidate) {
        val c = candidate ?: return@LaunchedEffect
        val validAddress = c.address.isNotBlank()
            && !c.address.contains("読み取れませんでした")
            && !c.address.contains("権限が許可")
            && !c.address.contains("取得できませんでした")
        if (validAddress) {
            newAddress = c.address
            if (c.postalCode.isNotBlank()) postalCode = c.postalCode
            if (c.recipientName.isNotBlank()) newRecipient = c.recipientName
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
                Text(stringResource(R.string.panel_courses), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(48.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        when {
                            !isLoggedIn -> onRequireLogin()
                            isFreeExpired -> onShowPaywall()
                            else -> onLoadCourse(course)
                        }
                    },
                    enabled = course.addresses.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.load_course_button, course.code))
                }
                OutlinedButton(
                    onClick = { onClearCourseFromMap(course) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.clear_course_from_map_button))
                }
            }
            course.addresses.forEachIndexed { index, item ->
                CourseAddressItem(
                    item = item,
                    onUpdate = { recipient, address, timeWindow, memo ->
                        onUpdateCourseAddress(course.code, index, recipient, address, timeWindow, memo)
                    },
                    onDelete = { onDeleteCourseAddress(course.code, index) }
                )
            }
        }
    }

    WhiteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.add_course_address_title), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            val file = java.io.File(context.cacheDir, "ocr/ocr_${System.currentTimeMillis()}.jpg")
                                .also { it.parentFile?.mkdirs() }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            ocrImageUri = uri
                            cameraOcrLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.height(36.dp)
                ) { Text(stringResource(R.string.camera)) }
                Button(
                    onClick = { voiceInputLauncher.launch(createVoiceAddressIntent()) },
                    modifier = Modifier.height(36.dp)
                ) { Text(stringResource(R.string.voice)) }
            }
        }
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
                        if (isValid) newAddress = result.address
                    }
                },
                enabled = postalCode.length == 7
            ) { Text(stringResource(R.string.search)) }
        }
        candidate?.let { c ->
            if (c.address.isNotBlank()) {
                TextBox("${c.sourceLabel} / ${c.confidenceLabel}\n${c.address}")
            }
        }
        LabeledField(stringResource(R.string.manual_input_label), stringResource(R.string.manual_placeholder), newAddress) { newAddress = it }
        LabeledField(stringResource(R.string.recipient_field_label), stringResource(R.string.recipient_name_placeholder), newRecipient) { newRecipient = it }
        Text(stringResource(R.string.time_window_title), fontWeight = FontWeight.Bold)
        TimeWindowFilters(selected = newTimeWindow) { newTimeWindow = it }
        LabeledField(stringResource(R.string.memo_field_label), stringResource(R.string.memo_placeholder), newMemo) { newMemo = it }
        Button(
            onClick = {
                when {
                    !isLoggedIn -> onRequireLogin()
                    isFreeExpired -> onShowPaywall()
                    else -> {
                        onAddCourseAddress(selectedCourseCode, newRecipient, newAddress, newTimeWindow, newMemo)
                        newRecipient = ""
                        newAddress = ""
                        newTimeWindow = TimeWindow.Unspecified
                        newMemo = ""
                        postalCode = ""
                        candidate = null
                    }
                }
            },
            enabled = newAddress.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_to_course_button, selectedCourseCode))
        }
    }
            }
        }
    }
}

@Composable
private fun CourseAddressItem(
    item: CourseAddress,
    onUpdate: (String, String, TimeWindow, String) -> Unit,
    onDelete: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var recipient by remember(item) { mutableStateOf(item.recipient) }
    var address by remember(item) { mutableStateOf(item.address) }
    var timeWindow by remember(item) { mutableStateOf(item.timeWindow) }
    var memo by remember(item) { mutableStateOf(item.memo) }

    if (isEditing) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF6F7F9))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LabeledField(stringResource(R.string.recipient_field_label), stringResource(R.string.recipient_name_placeholder), recipient) { recipient = it }
            LabeledField(stringResource(R.string.manual_input_label), stringResource(R.string.manual_placeholder), address) { address = it }
            Text(stringResource(R.string.time_window_title), fontWeight = FontWeight.Bold)
            TimeWindowFilters(selected = timeWindow) { timeWindow = it }
            LabeledField(stringResource(R.string.memo_field_label), stringResource(R.string.memo_placeholder), memo) { memo = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onUpdate(recipient, address, timeWindow, memo)
                        isEditing = false
                    },
                    enabled = address.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.save)) }
                TextButton(onClick = { isEditing = false }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    } else {
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
                Text(item.recipient, fontWeight = FontWeight.Bold)
                Text(item.address, style = MaterialTheme.typography.bodySmall)
                Text("${item.timeWindow.label} / ${item.memo}", style = MaterialTheme.typography.labelSmall, color = MutedText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { isEditing = true }) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = Color(0xFFE53935)) }
            }
        }
    }
}

@Composable
private fun RegularCoursePanel(
    courses: List<RegularCourse>,
    onLoadCourse: (RegularCourse) -> Unit,
    onClearCourseFromMap: (RegularCourse) -> Unit,
    onAddCourseAddress: (String, String, String, TimeWindow, String) -> Unit
) {
    val context = LocalContext.current
    var selectedCourseCode by remember { mutableStateOf(courses.firstOrNull()?.code ?: "A") }
    val selectedCourse = courses.firstOrNull { it.code == selectedCourseCode } ?: courses.firstOrNull()
    var newRecipient by remember { mutableStateOf("") }
    var newAddress by remember { mutableStateOf("") }
    var newTimeWindow by remember { mutableStateOf(TimeWindow.Unspecified) }
    var newMemo by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<AddressCandidate?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var postalSearchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var ocrImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraOcrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            ocrImageUri?.let { uri ->
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                recognizeAddressFromBitmap(
                    bitmap = bitmap,
                    onSuccess = { candidate = it },
                    onFailure = {
                        candidate = AddressCandidate("OCR", "", "住所を読み取れませんでした", "要確認")
                    }
                )
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = java.io.File(context.cacheDir, "ocr/ocr_${System.currentTimeMillis()}.jpg")
                .also { it.parentFile?.mkdirs() }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            ocrImageUri = uri
            cameraOcrLauncher.launch(uri)
        } else {
            candidate = AddressCandidate("カメラ", "", "カメラ権限が許可されていません", "権限確認")
        }
    }
    val voiceInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) candidate = createAddressCandidateFromText("音声入力", spoken)
    }
    LaunchedEffect(candidate) {
        val c = candidate ?: return@LaunchedEffect
        val validAddress = c.address.isNotBlank()
            && !c.address.contains("読み取れませんでした")
            && !c.address.contains("権限が許可")
            && !c.address.contains("取得できませんでした")
        if (validAddress) {
            newAddress = c.address
            if (c.postalCode.isNotBlank()) postalCode = c.postalCode
            if (c.recipientName.isNotBlank()) newRecipient = c.recipientName
        }
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onLoadCourse(course) },
                    enabled = course.addresses.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.load_course_button, course.code))
                }
                OutlinedButton(
                    onClick = { onClearCourseFromMap(course) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.clear_course_from_map_button))
                }
            }
            course.addresses.takeLast(5).forEach { item ->
                TextBox("${item.recipient}\n${item.address}\n${item.timeWindow.label} / ${item.memo}")
            }
        }
    }

    WhiteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.add_course_address_title), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            val file = java.io.File(context.cacheDir, "ocr/ocr_${System.currentTimeMillis()}.jpg")
                                .also { it.parentFile?.mkdirs() }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            ocrImageUri = uri
                            cameraOcrLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.height(36.dp)
                ) { Text(stringResource(R.string.camera)) }
                Button(
                    onClick = { voiceInputLauncher.launch(createVoiceAddressIntent()) },
                    modifier = Modifier.height(36.dp)
                ) { Text(stringResource(R.string.voice)) }
            }
        }
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
                        if (isValid) newAddress = result.address
                    }
                },
                enabled = postalCode.length == 7
            ) { Text(stringResource(R.string.search)) }
        }
        candidate?.let { c ->
            if (c.address.isNotBlank()) {
                TextBox("${c.sourceLabel} / ${c.confidenceLabel}\n${c.address}")
            }
        }
        LabeledField(stringResource(R.string.manual_input_label), stringResource(R.string.manual_placeholder), newAddress) { newAddress = it }
        LabeledField(stringResource(R.string.recipient_field_label), stringResource(R.string.recipient_name_placeholder), newRecipient) { newRecipient = it }
        Text(stringResource(R.string.time_window_title), fontWeight = FontWeight.Bold)
        TimeWindowFilters(selected = newTimeWindow) { newTimeWindow = it }
        LabeledField(stringResource(R.string.memo_field_label), stringResource(R.string.memo_placeholder), newMemo) { newMemo = it }
        Button(
            onClick = {
                onAddCourseAddress(selectedCourseCode, newRecipient, newAddress, newTimeWindow, newMemo)
                newRecipient = ""
                newAddress = ""
                newTimeWindow = TimeWindow.Unspecified
                newMemo = ""
                postalCode = ""
                candidate = null
            },
            enabled = newAddress.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_to_course_button, selectedCourseCode))
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
    onClearBackup: () -> Unit = {},
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
                        onClearBackup()
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
                        onClearBackup()
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
    val routeStops = calculateNearestRoute(packages, origin.latitude, origin.longitude)
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
                        val routeNumber = stop.routeNumber
                        Marker(
                            state = rememberUpdatedMarkerState(position = LatLng(item.latitude, item.longitude)),
                            title = "$routeNumber. ${item.recipient}",
                            snippet = "${item.deliveryTimeWindow.label} / ${item.address}",
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
                Text(stringResource(R.string.next_candidate, it.recipient, it.deliveryTimeWindow.label), color = MutedText)
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
        Text(stringResource(R.string.package_time_cod_locker, deliveryPackage.deliveryTimeWindow.label, yesNo(deliveryPackage.cod), yesNo(deliveryPackage.hasLocker)))
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
    routeNumberMap: Map<String, Int> = emptyMap(),
    selectedCode: String?,
    onBack: () -> Unit,
    onSelectPackage: (String) -> Unit,
    onEditPackage: (String) -> Unit,
    onCompleteAll: () -> Unit = {}
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
                    .padding(16.dp)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PackageList(packages, routeNumberMap, selectedCode, onSelectPackage, onEditPackage)
            }
        }
        Button(
            onClick = onCompleteAll,
            enabled = packages.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
        ) { Text(stringResource(R.string.complete_all_button), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PackageList(
    packages: List<DeliveryPackage>,
    routeNumberMap: Map<String, Int> = emptyMap(),
    selectedCode: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        packages.forEach { item ->
            val routeNum = routeNumberMap[item.trackingCode]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item.trackingCode) },
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
                    if (routeNum != null) {
                        Text(
                            "$routeNum",
                            fontWeight = FontWeight.Bold,
                            color = BrandBlue,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.widthIn(min = 24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(item.status.statusColor())
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.trackingCode}  ${item.recipient}", fontWeight = FontWeight.Bold)
                        Text(item.address, color = MutedText)
                    }
                    StatusPill(item.status)
                    TextButton(
                        onClick = { onEdit(item.trackingCode) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text(stringResource(R.string.edit), style = MaterialTheme.typography.labelSmall) }
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
    var statusVoiceHint by remember { mutableStateOf<String?>(null) }
    var recipient by remember { mutableStateOf(deliveryPackage.recipient) }
    var memo by remember { mutableStateOf(deliveryPackage.memo) }
    var selectedTimeWindow by remember { mutableStateOf(deliveryPackage.deliveryTimeWindow) }
    var hasLocker by remember { mutableStateOf(deliveryPackage.hasLocker) }
    var nameplate by remember { mutableStateOf(deliveryPackage.nameplate) }
    var packageMemo by remember { mutableStateOf(deliveryPackage.packageMemo) }
    var trackingCode by remember { mutableStateOf(deliveryPackage.trackingCode) }
    var phoneNumber by remember { mutableStateOf(deliveryPackage.phoneNumber) }
    var selectedShape by remember { mutableStateOf(deliveryPackage.shape) }

    val statusVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        val matched = parseSpokenDeliveryStatus(spoken)
        if (matched != null) {
            statusVoiceHint = null
            selectedStatus = matched
            onSave(
                deliveryPackage.copy(
                    recipient = recipient,
                    memo = memo,
                    status = matched,
                    timeWindow = selectedTimeWindow.toTimeWindow(),
                    deliveryTimeWindow = selectedTimeWindow,
                    hasLocker = hasLocker,
                    nameplate = nameplate,
                    packageMemo = packageMemo,
                    trackingCode = trackingCode,
                    phoneNumber = phoneNumber,
                    shape = selectedShape
                )
            )
        } else if (spoken.isNotBlank()) {
            statusVoiceHint = context.getString(R.string.voice_status_unrecognized, spoken)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                statusVoiceHint = null
                                statusVoiceLauncher.launch(createVoiceStatusIntent())
                            },
                            modifier = Modifier.height(36.dp)
                        ) { Text(stringResource(R.string.voice)) }
                        Text(stringResource(R.string.recipient_info_title), fontWeight = FontWeight.Bold)
                    }
                    InfoRow(stringResource(R.string.address_label), deliveryPackage.address)
                    LabeledField(stringResource(R.string.recipient_field_label), stringResource(R.string.recipient_name_placeholder), recipient) { recipient = it }
                    statusVoiceHint?.let {
                        Text(it, color = Color(0xFFE53935), style = MaterialTheme.typography.bodySmall)
                    }
                }
                WhiteCard {
                    Text(stringResource(R.string.time_window_title), fontWeight = FontWeight.Bold)
                    SegmentedRow(RegistrationTimeWindow.entries, selectedTimeWindow, { it.label }) { selectedTimeWindow = it }
                }
                WhiteCard {
                    Text(stringResource(R.string.slip_info_title), fontWeight = FontWeight.Bold)
                    LabeledField(stringResource(R.string.slip_number_label), stringResource(R.string.slip_number_placeholder), trackingCode) { trackingCode = it }
                    LabeledField(stringResource(R.string.phone_number_label), stringResource(R.string.phone_number_placeholder), phoneNumber) { phoneNumber = it }
                }
                WhiteCard {
                    Text(stringResource(R.string.package_shape_title), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PackageShape.entries.forEach { shape ->
                            val selected = selectedShape == shape
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) BrandBlue else Color(0xFFF6F7F9))
                                    .clickable { selectedShape = shape }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(shape.symbol, color = if (selected) Color.White else Color(0xFF445064), fontWeight = FontWeight.Bold)
                                Text(shape.label, color = if (selected) Color.White else Color(0xFF445064), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                WhiteCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hasLocker = !hasLocker },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.has_locker_label), fontWeight = FontWeight.Bold)
                        Checkbox(checked = hasLocker, onCheckedChange = { hasLocker = it })
                    }
                    LabeledField(stringResource(R.string.nameplate_label), stringResource(R.string.nameplate_placeholder), nameplate) { nameplate = it }
                    LabeledField(stringResource(R.string.delivery_memo_label), stringResource(R.string.delivery_memo_placeholder), memo) { memo = it }
                    LabeledField(stringResource(R.string.package_memo_label), stringResource(R.string.package_memo_placeholder), packageMemo) { packageMemo = it }
                }
                WhiteCard {
                    Text(stringResource(R.string.delivery_status_title), fontWeight = FontWeight.Bold)
                    SegmentedRow(DeliveryStatus.entries, selectedStatus, { it.label }) { selectedStatus = it }
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
                        timeWindow = selectedTimeWindow.toTimeWindow(),
                        deliveryTimeWindow = selectedTimeWindow,
                        hasLocker = hasLocker,
                        nameplate = nameplate,
                        packageMemo = packageMemo,
                        trackingCode = trackingCode,
                        phoneNumber = phoneNumber,
                        shape = selectedShape
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

private data class PackageRegistrationDetail(
    val hasLocker: Boolean,
    val nameplate: String,
    val deliveryMemo: String,
    val packageMemo: String,
    val status: DeliveryStatus,
    val trackingNumber: String,
    val recipientName: String,
    val phoneNumber: String,
    val shape: PackageShape
)

@Composable
private fun PackageRegistrationScreen(
    initialAddress: String,
    onBack: () -> Unit,
    onOpenMap: (String) -> Unit,
    onOpenNavigation: (String) -> Unit,
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
    var selectedTimeWindow by remember { mutableStateOf(RegistrationTimeWindow.None) }
    var hasLocker by remember { mutableStateOf(false) }
    var nameplate by remember { mutableStateOf("") }
    var deliveryMemo by remember { mutableStateOf("") }
    var packageMemo by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(DeliveryStatus.Pending) }
    var trackingNumber by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedShape by remember { mutableStateOf(PackageShape.SmallBox) }
    val addressDetails = remember { mutableStateMapOf<String, PackageRegistrationDetail>() }

    var ocrImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraOcrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            ocrImageUri?.let { uri ->
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                recognizeAddressFromBitmap(
                    bitmap = bitmap,
                    onSuccess = { candidate = it },
                    onFailure = {
                        candidate = AddressCandidate("OCR", "", "住所を読み取れませんでした", "要確認")
                    }
                )
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = java.io.File(context.cacheDir, "ocr/ocr_${System.currentTimeMillis()}.jpg")
                .also { it.parentFile?.mkdirs() }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            ocrImageUri = uri
            cameraOcrLauncher.launch(uri)
        } else {
            candidate = AddressCandidate("カメラ", "", "カメラ権限が許可されていません", "権限確認")
        }
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
    LaunchedEffect(candidate) {
        val c = candidate ?: return@LaunchedEffect
        val validAddress = c.address.isNotBlank()
            && !c.address.contains("読み取れませんでした")
            && !c.address.contains("権限が許可")
            && !c.address.contains("取得できませんでした")
        if (validAddress) {
            manualInput = c.address
            if (c.postalCode.isNotBlank()) postalCode = c.postalCode
            if (c.recipientName.isNotBlank()) recipientName = c.recipientName
        }
    }
    LaunchedEffect(addresses.size) {
        addresses.forEach { addr ->
            if (!geocodedLocations.containsKey(addr) && addr.isNotBlank()) {
                scope.launch {
                    val (lat, lng) = geocodeAddressPublic(addr)
                    if ((lat != 0.0 || lng != 0.0) && addr in addresses) {
                        geocodedLocations[addr] = LatLng(lat, lng)
                    }
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
                                        ) {
                                            val file = java.io.File(context.cacheDir, "ocr/ocr_${System.currentTimeMillis()}.jpg")
                                                .also { it.parentFile?.mkdirs() }
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context, "${context.packageName}.fileprovider", file
                                            )
                                            ocrImageUri = uri
                                            cameraOcrLauncher.launch(uri)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    modifier = Modifier.height(36.dp)
                                ) { Text(stringResource(R.string.camera)) }
                                Button(
                                    onClick = { voiceInputLauncher.launch(createVoiceAddressIntent()) },
                                    modifier = Modifier.height(36.dp)
                                ) { Text(stringResource(R.string.voice)) }
                            }
                        }
                        // 1. 郵便番号検索
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
                        // 2. 直接入力
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.manual_input_label)) },
                            placeholder = { Text(stringResource(R.string.manual_placeholder)) }
                        )
                        // 3. お届け先名
                        OutlinedTextField(
                            value = recipientName,
                            onValueChange = { recipientName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.recipient_field_label)) },
                            placeholder = { Text(stringResource(R.string.recipient_name_placeholder)) }
                        )
                        Button(
                            onClick = {
                                if (manualInput.isNotBlank()) {
                                    addresses.add(manualInput)
                                    addressDetails[manualInput] = PackageRegistrationDetail(
                                        hasLocker = hasLocker,
                                        nameplate = nameplate,
                                        deliveryMemo = deliveryMemo,
                                        packageMemo = packageMemo,
                                        status = selectedStatus,
                                        trackingNumber = trackingNumber,
                                        recipientName = recipientName,
                                        phoneNumber = phoneNumber,
                                        shape = selectedShape
                                    )
                                    manualInput = ""
                                    hasLocker = false
                                    nameplate = ""
                                    deliveryMemo = ""
                                    packageMemo = ""
                                    selectedStatus = DeliveryStatus.Pending
                                    trackingNumber = ""
                                    recipientName = ""
                                    phoneNumber = ""
                                    selectedShape = PackageShape.SmallBox
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualInput.isNotBlank()
                        ) {
                            Text(stringResource(R.string.add_manual_button))
                        }
                    }
                }

                WhiteCard {
                    Text(stringResource(R.string.time_window_title), fontWeight = FontWeight.Bold)
                    SegmentedRow(
                        RegistrationTimeWindow.entries,
                        selectedTimeWindow,
                        { it.label }
                    ) { selectedTimeWindow = it }
                }

                if (addresses.isNotEmpty()) {
                    RegistrationMapPreview(
                        addresses = addresses.toList(),
                        geocodedLocations = geocodedLocations,
                        pinOverrides = pinOverrides,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }

                WhiteCard {
                    Text(stringResource(R.string.slip_info_title), fontWeight = FontWeight.Bold)
                    LabeledField(stringResource(R.string.slip_number_label), stringResource(R.string.slip_number_placeholder), trackingNumber) { trackingNumber = it }
                    LabeledField(stringResource(R.string.phone_number_label), stringResource(R.string.phone_number_placeholder), phoneNumber) { phoneNumber = it }
                }

                WhiteCard {
                    Text(stringResource(R.string.package_shape_title), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PackageShape.entries.forEach { shape ->
                            val selected = selectedShape == shape
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) BrandBlue else Color(0xFFF6F7F9))
                                    .clickable { selectedShape = shape }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(shape.symbol, color = if (selected) Color.White else Color(0xFF445064), fontWeight = FontWeight.Bold)
                                Text(shape.label, color = if (selected) Color.White else Color(0xFF445064), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                WhiteCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hasLocker = !hasLocker },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.has_locker_label), fontWeight = FontWeight.Bold)
                        Checkbox(checked = hasLocker, onCheckedChange = { hasLocker = it })
                    }
                    LabeledField(stringResource(R.string.nameplate_label), stringResource(R.string.nameplate_placeholder), nameplate) { nameplate = it }
                    LabeledField(stringResource(R.string.delivery_memo_label), stringResource(R.string.delivery_memo_placeholder), deliveryMemo) { deliveryMemo = it }
                    LabeledField(stringResource(R.string.package_memo_label), stringResource(R.string.package_memo_placeholder), packageMemo) { packageMemo = it }
                }

                WhiteCard {
                    Text(stringResource(R.string.delivery_status_title), fontWeight = FontWeight.Bold)
                    SegmentedRow(DeliveryStatus.entries, selectedStatus, { it.label }) { selectedStatus = it }
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(68.dp))
        }
        Button(
            onClick = {
                onRegister(
                    addresses.map { addr ->
                        val latLng = pinOverrides[addr] ?: geocodedLocations[addr]
                        val detail = addressDetails[addr]
                        PackageRegistrationResult(
                            address = addr,
                            hasLocker = detail?.hasLocker ?: false,
                            nameplate = detail?.nameplate.orEmpty(),
                            deliveryMemo = detail?.deliveryMemo.orEmpty(),
                            packageMemo = detail?.packageMemo.orEmpty(),
                            trackingNumber = detail?.trackingNumber.orEmpty(),
                            recipientName = detail?.recipientName.orEmpty(),
                            phoneNumber = detail?.phoneNumber.orEmpty(),
                            status = detail?.status ?: DeliveryStatus.Pending,
                            timeWindow = selectedTimeWindow,
                            shape = detail?.shape ?: PackageShape.SmallBox,
                            colorType = PackageColorType.Kraft,
                            size = PackageSizeOption.Medium,
                            latitude = latLng?.latitude ?: 0.0,
                            longitude = latLng?.longitude ?: 0.0
                        )
                    }
                )
                addresses.forEach { addr ->
                    geocodedLocations.remove(addr)
                    pinOverrides.remove(addr)
                    addressDetails.remove(addr)
                }
                addresses.clear()
                postalCode = ""
                candidate = null
                manualInput = ""
                selectedTimeWindow = RegistrationTimeWindow.None
                hasLocker = false
                nameplate = ""
                deliveryMemo = ""
                packageMemo = ""
                selectedStatus = DeliveryStatus.Pending
                trackingNumber = ""
                recipientName = ""
                phoneNumber = ""
                selectedShape = PackageShape.SmallBox
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
    LaunchedEffect(validPins.map { it.second to it.third }) {
        when {
            validPins.size == 1 -> {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(validPins.first().third, 14f))
            }
            validPins.size > 1 -> {
                val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                validPins.forEach { boundsBuilder.include(it.third) }
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
            }
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
                key(index, addr) {
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

private fun deliveryMarkerIcon(routeNumber: Int, deliveryPackage: DeliveryPackage) = when {
    deliveryPackage.timeWindow == TimeWindow.Unspecified -> {
        standardPinMarkerIcon(routeNumber.toString(), android.graphics.Color.BLACK)
    }
    deliveryPackage.status == DeliveryStatus.Absent -> {
        standardPinMarkerIcon("不", AbsentMarkerArgb)
    }
    deliveryPackage.status == DeliveryStatus.Redelivery -> {
        standardPinMarkerIcon("再", RedeliveryMarkerArgb)
    }
    else -> standardPinMarkerIcon(routeNumber.toString(), deliveryPackage.timeWindow.markerArgb())
}

private fun TimeWindow.markerArgb(): Int = when (this) {
    TimeWindow.Morning -> android.graphics.Color.rgb(47, 128, 237)
    TimeWindow.Afternoon -> android.graphics.Color.rgb(224, 138, 30)
    TimeWindow.Evening -> android.graphics.Color.rgb(122, 75, 216)
    TimeWindow.Unspecified,
    TimeWindow.All -> android.graphics.Color.BLACK
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
    val recipientName = extractLikelyName(sourceText)

    val fallback = sourceText
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("""\d{3}[-\s]?\d{4}"""), "")
        .trim()
    return AddressCandidate(
        sourceLabel = sourceLabel,
        postalCode = postalCode,
        address = address.ifBlank { fallback },
        confidenceLabel = if (address.isNotBlank()) "住所候補" else "OCR全文",
        recipientName = recipientName
    )
}

private fun extractLikelyName(sourceText: String): String {
    val lines = sourceText
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()

    val honorificSuffixes = listOf("様", "殿", "御中")
    lines.firstOrNull { line -> honorificSuffixes.any { line.endsWith(it) } }?.let { line ->
        var name = line
        honorificSuffixes.forEach { name = name.removeSuffix(it) }
        name = name.trim().trim('　', ' ')
        if (name.isNotBlank()) return name
    }

    val namePattern = Regex("""^[ぁ-んァ-ヶ一-龠]{1,4}[\s　]+[ぁ-んァ-ヶ一-龠]{1,6}$""")
    lines.firstOrNull { namePattern.matches(it) }?.let { return it.trim() }

    return ""
}

private fun extractLikelyAddress(sourceText: String): String {
    val prefectures = listOf(
        "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
        "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県",
        "新潟県", "富山県", "石川県", "福井県", "山梨県", "長野県", "岐阜県",
        "静岡県", "愛知県", "三重県", "滋賀県", "京都府", "大阪府", "兵庫県",
        "奈良県", "和歌山県", "鳥取県", "島根県", "岡山県", "広島県", "山口県",
        "徳島県", "香川県", "愛媛県", "高知県", "福岡県", "佐賀県", "長崎県",
        "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"
    )
    val lines = sourceText
        .lineSequence()
        .map { it.trim().replace("〒", "").replace(" ", "").replace("　", "") }
        .filter { it.isNotBlank() }
        .toList()
    val addressIndex = lines.indexOfFirst { line ->
        prefectures.any { line.contains(it) }
    }
    if (addressIndex < 0) return ""

    return lines
        .drop(addressIndex)
        .take(3)
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

private fun createVoiceStatusIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "配達状況を話してください（完了・不在・再配達など）")
    }
}

private fun parseSpokenDeliveryStatus(spoken: String): DeliveryStatus? {
    val text = spoken.trim()
    if (text.isBlank()) return null
    return when {
        text.contains("持ち戻り") || text.contains("持帰り") || text.contains("会社へ") -> DeliveryStatus.ReturnToCompany
        text.contains("再配達") || text.contains("再配") -> DeliveryStatus.Redelivery
        text.contains("不在") -> DeliveryStatus.Absent
        text.contains("配達中") || text.contains("配達開始") -> DeliveryStatus.InProgress
        text.contains("完了") || text.contains("配達済") || text.contains("届けた") || text.contains("配り終") -> DeliveryStatus.Completed
        text.contains("未配達") || text.contains("未配") -> DeliveryStatus.Pending
        else -> null
    }
}

private fun createVoiceNextStopIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "「次の配達先」と話してください")
    }
}

private fun isNextDeliveryCommand(spoken: String): Boolean {
    val text = spoken.trim()
    if (text.isBlank()) return false
    return text.contains("次") && (text.contains("配達") || text.contains("届け先") || text.contains("先"))
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
