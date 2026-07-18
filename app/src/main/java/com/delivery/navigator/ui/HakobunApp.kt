package com.delivery.navigator.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.delivery.navigator.data.createDeliveryPackageFromRegistration
import com.delivery.navigator.data.createEndOfDayCalendarIntent
import com.delivery.navigator.data.createPackagesFromCourse
import com.delivery.navigator.data.currentRouteOrigin
import com.delivery.navigator.data.exportPackages
import com.delivery.navigator.data.hidesMapPin
import com.delivery.navigator.data.importPackages
import com.delivery.navigator.data.LocalDeliveryStore
import com.delivery.navigator.data.regularCourses
import com.delivery.navigator.data.samplePackages
import com.delivery.navigator.data.searchPostalCode
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
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition

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
    var selectedWindow by remember { mutableStateOf(TimeWindow.All) }
    var selectedMapTimeWindow by remember { mutableStateOf(TimeWindow.All) }
    var selectedPackageCode by remember { mutableStateOf(packages.first().trackingCode) }
    var postalCode by remember { mutableStateOf("") }
    var addressCandidate by remember { mutableStateOf<AddressCandidate?>(null) }
    var isRegisteringPackage by remember { mutableStateOf(false) }
    var isSupportHubOpen by remember { mutableStateOf(false) }
    var activeMenuItem by remember { mutableStateOf<AccountMenuItem?>(null) }
    var isLoggedIn by remember { mutableStateOf(true) }
    var backupText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var selectedSummaryFilter by remember { mutableStateOf<PackageSummaryFilter?>(null) }
    var redeliveryCandidateCode by remember { mutableStateOf<String?>(null) }
    val fixedRouteNumbers = remember { mutableStateMapOf<String, Int>() }
    val persistPackages = { deliveryStore.savePackages(packages) }
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
    LaunchedEffect(packages.map { it.trackingCode }) {
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
        redeliveryCandidate?.let { packageItem ->
            AlertDialog(
                onDismissRequest = { redeliveryCandidateCode = null },
                title = { Text("再配達しますか？") },
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
                    ) { Text("再配達") }
                },
                dismissButton = {
                    TextButton(onClick = { redeliveryCandidateCode = null }) { Text("キャンセル") }
                }
            )
        }

        activeMenuItem?.let { item ->
            AccountMenuDetailScreen(
                item = item,
                isLoggedIn = isLoggedIn,
                onBack = { activeMenuItem = null },
                onLoginToggle = { isLoggedIn = !isLoggedIn }
            )
            return@MaterialTheme
        }

        if (isSupportHubOpen) {
            SupportHubScreen(
                packages = packages,
                isLoggedIn = isLoggedIn,
                onBack = { isSupportHubOpen = false },
                onSelect = { activeMenuItem = it },
                onLoginToggle = { isLoggedIn = !isLoggedIn },
                onFinishDay = { summary -> context.startActivity(createEndOfDayCalendarIntent(summary)) }
            )
            return@MaterialTheme
        }

        if (isRegisteringPackage) {
            val registrationAddress = addressCandidate?.address
                ?: selectedPackage?.address
                ?: "東京都千代田区丸の内1-1-1"
            PackageRegistrationScreen(
                address = registrationAddress,
                onBack = { isRegisteringPackage = false },
                onOpenMap = { context.startActivity(openMapIntent(registrationAddress)) },
                onOpenNavigation = { context.startActivity(openNavigationIntent(registrationAddress)) },
                onRegister = { result ->
                    packages.add(createDeliveryPackageFromRegistration(result, packages.size))
                    selectedPackageCode = packages.last().trackingCode
                    persistPackages()
                    isRegisteringPackage = false
                }
            )
            return@MaterialTheme
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Header(
                        packages = packages,
                        selectedFilter = selectedSummaryFilter,
                        onFilterSelected = { filter ->
                            selectedSummaryFilter = if (selectedSummaryFilter == filter) null else filter
                        },
                        isLoggedIn = isLoggedIn,
                        onOpenMenu = { isSupportHubOpen = true },
                        onRegisterPackage = { isRegisteringPackage = true }
                    )
                }
                if (selectedSummaryFilter != null) {
                    item {
                        SummaryFilteredPackagePanel(
                            filter = selectedSummaryFilter,
                            packages = summaryPackages,
                            selectedCode = selectedPackage?.trackingCode,
                            onSelect = onPackageSelected,
                            onClear = { selectedSummaryFilter = null }
                        )
                    }
                }
                item {
                    RegularCoursePanel(
                        courses = regularCourses(),
                        onLoadCourse = { course ->
                            packages.addAll(createPackagesFromCourse(course, packages.size))
                            selectedPackageCode = packages.lastOrNull()?.trackingCode ?: selectedPackageCode
                            persistPackages()
                        }
                    )
                }
                item {
                    AddressBackupPanel(
                        backupText = backupText,
                        importText = importText,
                        onExport = { backupText = exportPackages(packages) },
                        onImportTextChange = { importText = it },
                        onImport = {
                            val importedPackages = importPackages(importText, packages.size)
                            packages.addAll(importedPackages)
                            selectedPackageCode = packages.lastOrNull()?.trackingCode ?: selectedPackageCode
                            persistPackages()
                        }
                    )
                }
                item {
                    AddressInputPanel(
                        postalCode = postalCode,
                        candidate = addressCandidate,
                        onPostalCodeChange = { postalCode = it.filter(Char::isDigit).take(7) },
                        onCameraScan = {
                            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                cameraOcrLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onVoiceInput = {
                            voiceInputLauncher.launch(createVoiceAddressIntent())
                        },
                        onPostalSearch = { addressCandidate = searchPostalCode(postalCode) },
                        onRegisterPackage = { isRegisteringPackage = true },
                        onClear = {
                            postalCode = ""
                            addressCandidate = null
                        }
                    )
                }
                item { TimeWindowFilters(selectedWindow) { selectedWindow = it } }
                item { MapTimeWindowFilters(selectedMapTimeWindow) { selectedMapTimeWindow = it } }
                item {
                    DeliveryGoogleMap(
                        packages = visibleMapPackages,
                        fixedRouteNumbers = fixedRouteNumbers,
                        selectedCode = selectedPackage?.takeUnless { it.status.hidesMapPin() }?.trackingCode,
                        onSelect = { selectedPackageCode = it },
                        onNavigate = {
                            context.startActivity(
                                openNavigationIntent(
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                    label = it.recipient
                                )
                            )
                        }
                    )
                }
                selectedPackage?.let { packageItem ->
                    item {
                        PackageDetail(
                            deliveryPackage = packageItem,
                            onStatusChange = { status ->
                                val index = packages.indexOfFirst { it.trackingCode == packageItem.trackingCode }
                                if (index >= 0) {
                                    packages[index] = packageItem.copy(status = status)
                                    persistPackages()
                                }
                            }
                        )
                    }
                }
                item {
                    PackageList(
                        packages = visiblePackages,
                        selectedCode = selectedPackage?.trackingCode,
                        onSelect = onPackageSelected
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
                    Text("今日の配達を軽く、正確に", color = MutedText)
                    Text(if (isLoggedIn) "ログイン中" else "未ログイン", color = BrandBlue, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = onOpenMenu,
                    modifier = Modifier.height(40.dp)
                ) { Text("お知らせ") }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onRegisterPackage,
                    modifier = Modifier.height(40.dp)
                ) { Text("荷物登録") }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                "残り",
                remaining.toString(),
                Modifier.weight(1f),
                selected = selectedFilter == PackageSummaryFilter.Remaining,
                onClick = { onFilterSelected(PackageSummaryFilter.Remaining) }
            )
            MetricCard(
                "完了",
                completed.toString(),
                Modifier.weight(1f),
                selected = selectedFilter == PackageSummaryFilter.Completed,
                onClick = { onFilterSelected(PackageSummaryFilter.Completed) }
            )
            MetricCard(
                "不在",
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
                Text("${filter.label}一覧", fontWeight = FontWeight.Bold)
                Text("${packages.size}件を表示中", color = MutedText)
            }
            TextButton(onClick = onClear) { Text("閉じる") }
        }
        if (packages.isEmpty()) {
            Text("該当する荷物はありません。", color = MutedText)
        } else {
            PackageList(
                packages = packages,
                selectedCode = selectedCode,
                onSelect = onSelect
            )
        }
        if (filter == PackageSummaryFilter.Absent) {
            Text("不在の項目をタップすると再配達確認を表示します。", color = MutedText)
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
    onFinishDay: (EndOfDaySummary) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("← 戻る") }
                    Text("お知らせ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(64.dp))
                }
            }
            item {
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
            }
            item {
                EndOfDayPanel(
                    packages = packages,
                    onFinishDay = onFinishDay
                )
            }
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
                Text("アカウント・サポート", fontWeight = FontWeight.Bold)
                Text("運営連絡、設定、問い合わせをまとめて管理", color = MutedText)
            }
            Button(onClick = onLoginToggle) { Text(if (isLoggedIn) "ログアウト" else "ログイン") }
        }
        AccountMenuItem.entries.chunked(2).forEach { rowItems ->
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
                            Text("詳細を見る →", color = BrandPurple, style = MaterialTheme.typography.labelSmall)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← 戻る") }
                Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onLoginToggle) { Text(if (isLoggedIn) "ログアウト" else "ログイン") }
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
                            label = { Text("フィードバック内容") },
                            placeholder = { Text("例）地図ピンをもう少し大きくしたい") },
                            minLines = 4
                        )
                        Button(
                            onClick = { feedbackSubmitted = true },
                            enabled = feedbackText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("運営へ送信") }
                        if (feedbackSubmitted) {
                            Text("送信予定として保存しました。API連携後に運営へ送信します。", color = SuccessGreen)
                        }
                    }
                    AccountMenuItem.Announcements -> {
                        InfoRow("重要", "Google Mapsを主地図として開発中。ZENRIN APIは将来差し替え予定。")
                        InfoRow("更新", "完了・会社へ持ち戻りの荷物は地図ピンから非表示。")
                        InfoRow("案内", "終了処理で1日の件数をカレンダーへ登録。")
                    }
                    AccountMenuItem.Account -> {
                        InfoRow("ユーザー", if (isLoggedIn) "HAKOBUNドライバー" else "ゲスト")
                        InfoRow("所属拠点", "未設定")
                        InfoRow("連絡先", "未設定")
                        InfoRow("ログイン状態", if (isLoggedIn) "ログイン中" else "未ログイン")
                    }
                    AccountMenuItem.Settings -> {
                        InfoRow("地図", "Google Maps表示、時間帯別ピン、コンパス、ズーム")
                        InfoRow("ナビ", "ピンタップ後にGoogleマップナビを起動")
                        InfoRow("通知", "運営お知らせ・時間帯指定アラートを予定")
                    }
                    AccountMenuItem.Help -> {
                        InfoRow("問い合わせ", "help@hakobun.example")
                        InfoRow("FAQ", "住所OCR、郵便番号検索、定期コース、バックアップ")
                        InfoRow("緊急時", "所属会社の運行管理者へ連絡")
                    }
                    AccountMenuItem.Terms -> {
                        Text("本アプリは配送業務の補助を目的とします。実際の交通法規・配送会社の運用ルールを優先してください。")
                        Text("住所・氏名・電話番号などの個人情報は、配送業務に必要な範囲でのみ扱います。")
                        Text("Google Mapsおよび将来のZENRIN API利用時は、各サービスの利用規約に従います。")
                    }
                }
            }
        }
    }
}

@Composable
private fun EndOfDayPanel(packages: List<DeliveryPackage>, onFinishDay: (EndOfDaySummary) -> Unit) {
    val summary = calculateEndOfDaySummary(packages)
    WhiteCard {
        Text("終了処理", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric("配達件数", summary.deliveredStops.toString(), Modifier.weight(1f))
            SummaryMetric("配達個数", summary.deliveredPackages.toString(), Modifier.weight(1f))
            SummaryMetric("持戻り", summary.returnedPackages.toString(), Modifier.weight(1f))
        }
        Button(onClick = { onFinishDay(summary) }, modifier = Modifier.fillMaxWidth()) {
            Text("終了処理してカレンダー入力")
        }
    }
}

@Composable
private fun RegularCoursePanel(courses: List<RegularCourse>, onLoadCourse: (RegularCourse) -> Unit) {
    WhiteCard {
        Text("定期配送コース", fontWeight = FontWeight.Bold)
        Text("A〜Dコースの登録住所を今日の配達へ呼び出します。", color = MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            courses.forEach { course ->
                Button(onClick = { onLoadCourse(course) }, modifier = Modifier.weight(1f)) {
                    Text(course.code)
                }
            }
        }
        Text("例: 本日はAコース、明日はDコースを読み込み", color = MutedText)
    }
}

@Composable
private fun AddressBackupPanel(
    backupText: String,
    importText: String,
    onExport: () -> Unit,
    onImportTextChange: (String) -> Unit,
    onImport: () -> Unit
) {
    WhiteCard {
        Text("住所データ管理", fontWeight = FontWeight.Bold)
        Text("定期コース以外の住所もバックアップ・インポートできます。", color = MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport, modifier = Modifier.weight(1f)) { Text("バックアップ") }
            Button(onClick = onImport, enabled = importText.isNotBlank(), modifier = Modifier.weight(1f)) {
                Text("インポート")
            }
        }
        if (backupText.isNotBlank()) {
            TextBox(backupText)
        }
        OutlinedTextField(
            value = importText,
            onValueChange = onImportTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("インポートデータ") },
            placeholder = { Text("伝票番号,届け先,住所,時間帯,メモ") },
            minLines = 2
        )
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
                Text("住所入力", fontWeight = FontWeight.Bold)
                Text("カメラ・音声・郵便番号から住所候補を作成", color = MutedText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCameraScan) { Text("カメラ") }
                Button(onClick = onVoiceInput) { Text("音声") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = postalCode,
                onValueChange = onPostalCodeChange,
                modifier = Modifier.weight(1f),
                label = { Text("郵便番号") },
                placeholder = { Text("例 1000005") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(onClick = onPostalSearch, enabled = postalCode.length == 7) { Text("検索") }
        }
        candidate?.let {
            val postalPrefix = it.postalCode.takeIf(String::isNotBlank)?.let { code -> "〒$code " }.orEmpty()
            TextBox("${it.sourceLabel} / ${it.confidenceLabel}\n$postalPrefix${it.address}")
            Button(onClick = onRegisterPackage) { Text("荷物登録") }
        }
        if (postalCode.isNotEmpty() || candidate != null) {
            TextButton(onClick = onClear) { Text("入力をクリア") }
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
        Text("地図表示: 時間帯別荷物", fontWeight = FontWeight.Bold)
        Text("時間指定なしは黒ピン、時間指定ありは時間帯色で表示します。", color = MutedText)
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
                    label = { Text(if (window == TimeWindow.All) "全時間帯" else window.label) }
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
    onSelect: (String) -> Unit,
    onNavigate: (DeliveryPackage) -> Unit
) {
    val routeStops = calculateNearestRoute(packages)
    val selectedPackage = routeStops.firstOrNull { it.deliveryPackage.trackingCode == selectedCode }?.deliveryPackage
    val originPair = currentRouteOrigin()
    val origin = LatLng(originPair.first, originPair.second)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(origin, 12.5f)
    }
    var mapBearing by remember { mutableStateOf(0f) }
    LaunchedEffect(selectedPackage?.trackingCode) {
        selectedPackage?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
            )
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("配達マップ", fontWeight = FontWeight.Bold)
                    Text("時間指定荷物を現在地から近い順に採番", color = MutedText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            mapBearing = 0f
                            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(origin, 12.5f))
                        }
                    ) {
                        Text("現在地")
                    }
                    Button(
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
                        Text("回転")
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
                    )
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
                        onClick = {
                            cameraPositionState.move(
                                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("選択ピンへ")
                    }
                    Button(onClick = { onNavigate(it) }, modifier = Modifier.weight(1f)) {
                        Text("ナビ開始")
                    }
                }
            }
            routeStops.firstOrNull()?.deliveryPackage?.let {
                Text("次の候補: ${it.recipient} / ${it.timeWindow.label}", color = MutedText)
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
        Text("荷物 ${deliveryPackage.trackingCode} / ${deliveryPackage.size}サイズ / ${deliveryPackage.colorLabel} / ${deliveryPackage.packageType}")
        Text("時間帯 ${deliveryPackage.timeWindow.label} / 代引き ${yesNo(deliveryPackage.cod)} / 宅配BOX ${yesNo(deliveryPackage.hasLocker)}")
        Text(deliveryPackage.memo, color = MutedText)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onStatusChange(DeliveryStatus.Completed) }) { Text("完了") }
            Button(onClick = { onStatusChange(DeliveryStatus.Absent) }) { Text("不在") }
            Button(onClick = { onStatusChange(DeliveryStatus.ReturnToCompany) }) { Text("持戻り") }
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
            ) { Text("ナビ") }
        }
    }
}

@Composable
private fun PackageList(packages: List<DeliveryPackage>, selectedCode: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        packages.forEach { item ->
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
                    Text(item.timeWindow.label)
                }
            }
        }
    }
}

@Composable
private fun PackageRegistrationScreen(
    address: String,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenNavigation: () -> Unit,
    onRegister: (PackageRegistrationResult) -> Unit
) {
    var hasLocker by remember { mutableStateOf(false) }
    var nameplate by remember { mutableStateOf("") }
    var deliveryMemo by remember { mutableStateOf("") }
    var packageMemo by remember { mutableStateOf("") }
    var trackingNumber by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(DeliveryStatus.InProgress) }
    var selectedTimeWindow by remember { mutableStateOf(RegistrationTimeWindow.None) }
    var selectedShape by remember { mutableStateOf(PackageShape.SmallBox) }
    var selectedType by remember { mutableStateOf(PackageColorType.Kraft) }
    var selectedSize by remember { mutableStateOf(PackageSizeOption.Medium) }
    val hasRequiredInput = address.isNotBlank() && (trackingNumber.isNotBlank() || recipientName.isNotBlank())

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F9))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← 戻る") }
                Text("荷物登録", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                StatusPill(if (hasRequiredInput) DeliveryStatus.InProgress else DeliveryStatus.Pending)
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("届け先", fontWeight = FontWeight.Bold)
                            Text("OCR・音声・郵便番号から引き継いだ住所です", color = MutedText)
                        }
                        Text("必須", color = BrandBlue, fontWeight = FontWeight.Bold)
                    }
                    Text(address, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasLocker, onCheckedChange = { hasLocker = it })
                        Text("建物に宅配BOXあり")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenMap, modifier = Modifier.weight(1f)) { Text("地図") }
                        Button(onClick = onOpenNavigation, modifier = Modifier.weight(1f)) { Text("ナビ") }
                    }
                }
                WhiteCard {
                    Text("現場メモ", fontWeight = FontWeight.Bold)
                    LabeledField("表札", "表札情報を入力できます", nameplate) { nameplate = it }
                    LabeledField("メモ", "届け先メモを入力できます", deliveryMemo) { deliveryMemo = it }
                    LabeledField("荷物メモ", "荷姿の情報等を入力できます", packageMemo) { packageMemo = it }
                }
                WhiteCard {
                    Text("配達状況", fontWeight = FontWeight.Bold)
                    SegmentedRow(DeliveryStatus.entries, selectedStatus, { it.label }) { selectedStatus = it }
                    Text("時間指定", fontWeight = FontWeight.Bold)
                    SegmentedRow(RegistrationTimeWindow.entries, selectedTimeWindow, { it.label }) { selectedTimeWindow = it }
                }
                WhiteCard {
                    Text("伝票情報", fontWeight = FontWeight.Bold)
                    Text("伝票番号または届け先名のどちらかを入力してください。", color = MutedText)
                    LabeledField("伝票番号", "例）0123-4567-8910-1112", trackingNumber) { trackingNumber = it }
                    LabeledField("届け先名", "例）善隣 太郎", recipientName) { recipientName = it }
                    LabeledField("電話番号", "例）09012345678", phoneNumber) { phoneNumber = it }
                }
                WhiteCard {
                    Text("HAKOBUN荷姿", fontWeight = FontWeight.Bold)
                    Text("GODOORとは違う、現場で迷いにくい分類です。", color = MutedText)
                    SegmentedRow(PackageShape.entries, selectedShape, { "${it.symbol} ${it.label}" }) { selectedShape = it }
                    SegmentedRow(PackageColorType.entries, selectedType, { it.label }) { selectedType = it }
                    SegmentedRow(PackageSizeOption.entries, selectedSize, { it.label }) { selectedSize = it }
                }
            }
        }
        Button(
            onClick = {
                onRegister(
                    PackageRegistrationResult(
                        address = address,
                        hasLocker = hasLocker,
                        nameplate = nameplate,
                        deliveryMemo = deliveryMemo,
                        packageMemo = packageMemo,
                        trackingNumber = trackingNumber,
                        recipientName = recipientName,
                        phoneNumber = phoneNumber,
                        status = selectedStatus,
                        timeWindow = selectedTimeWindow,
                        shape = selectedShape,
                        colorType = selectedType,
                        size = selectedSize
                    )
                )
            },
            enabled = hasRequiredInput,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
        ) { Text(if (hasRequiredInput) "登録して保存" else "伝票番号または届け先名を入力", fontWeight = FontWeight.Bold) }
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
        LegendDot(TimeWindow.Unspecified, "なし")
        LegendDot(TimeWindow.Morning, "午前")
        LegendDot(TimeWindow.Afternoon, "午後")
        LegendDot(TimeWindow.Evening, "夜間")
        LegendDot(color = AbsentGray, label = "不在")
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
    val origin = currentRouteOrigin()
    val navigationUri = Uri.Builder()
        .scheme("https")
        .authority("www.google.com")
        .path("maps/dir/")
        .appendQueryParameter("api", "1")
        .appendQueryParameter("origin", "${origin.first},${origin.second}")
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
        .addOnFailureListener {
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
