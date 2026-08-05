package com.delivery.navigator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.delivery.navigator.R
import com.delivery.navigator.model.MembershipPlan
import com.delivery.navigator.model.UserProfile

// ★プランカード構成は確定済み・ロック中(2026-08-05)★
// 無料トライアル中は「無料/プレミア」の横並び比較カード2枚を必ず表示すること。
// このレイアウトは過去に何度も無断で変更・削除され、その都度ユーザーの指摘で
// 元に戻す作業が発生している(3回目)。ユーザーからの明示的な指示がない限り、
// この構成(isPremium分岐・比較カードRow・Buttonの並び)を変更しないこと。
// 2026-08-06: 他の機能修正の際に誤って巻き込まれて書き換わる事故を防ぐため、
// HakobunApp.ktから本ファイルへ切り出した。この画面を触る用件でない限り、
// このファイルを開く必要はない。
@Composable
internal fun PlanBillingCard(
    userProfile: UserProfile,
    isPremium: Boolean,
    isFreeExpired: Boolean,
    trialRemainingDays: Int,
    freeTrialLabel: String?,
    priceLabel: String?,
    subscriptionProductDetails: ProductDetails?,
    subscribeMessage: String,
    onSubscribe: () -> Unit,
    onManageSubscription: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit,
    onSeedTestData: () -> Unit,
    onClearTestData: () -> Unit
) {
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
                    .background(if (!userProfile.isRegistered || isFreeExpired) Color(0xFFFFEEEE) else Color(0xFFF6F7F9))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when {
                            !userProfile.isRegistered -> stringResource(R.string.guest_not_registered)
                            isFreeExpired -> stringResource(R.string.free_trial_expired)
                            else -> stringResource(R.string.free_trial_active_title, trialRemainingDays)
                        },
                        color = if (!userProfile.isRegistered || isFreeExpired) Color(0xFFE53935) else BrandBlue,
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
                        Text(freeTrialLabel ?: stringResource(R.string.price_loading), color = BrandPurple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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
                        Text(priceLabel ?: stringResource(R.string.price_loading), color = BrandPurple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.premium_features_list), style = MaterialTheme.typography.labelSmall, color = MutedText)
                    }
                }
            }
            if (!userProfile.isRegistered) {
                Text(
                    stringResource(R.string.subscribe_requires_registration),
                    color = Color(0xFFE53935),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = onSubscribe,
                enabled = userProfile.isRegistered && subscriptionProductDetails != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
            ) {
                Text(stringResource(R.string.subscribe_google_pay), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(
                if (freeTrialLabel != null && priceLabel != null) {
                    stringResource(R.string.subscription_terms_dynamic, freeTrialLabel, priceLabel)
                } else {
                    stringResource(R.string.price_loading)
                },
                color = MutedText,
                style = MaterialTheme.typography.labelSmall
            )
        }
        TextButton(
            onClick = onManageSubscription,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.manage_subscription_link)) }
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
