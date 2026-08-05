package com.delivery.navigator.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.security.MessageDigest

/**
 * 無料トライアルの再取得(アンインストール→再インストールや「ローカルデータを削除」による
 * registeredAtリセット)を防ぐためのローカルガード。
 * ANDROID_IDのハッシュをファイル名にした空マーカーを、アプリのプライベート領域ではなく
 * 共有ストレージ(Downloads配下の隠しフォルダ)に残すことで、アプリ削除後も
 * 同一端末での初回登録日時を復元できるようにする。
 * API 28以下(スコープドストレージ未対応)や書き込み失敗時は追加パーミッションを要求せず
 * ベストエフォートで従来通りの挙動にフォールバックする。
 */
object TrialGuard {
    private const val MARKER_PREFIX = "hakobun_trial_"
    private val RELATIVE_DIR = "${Environment.DIRECTORY_DOWNLOADS}/.hakobun"

    /** 新規登録時の起算日時を決定する。過去にこの端末で登録済みならその日時を返す。 */
    fun resolveTrialStartMillis(context: Context, newStartMillis: Long): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return newStartMillis
        return runCatching {
            val markerName = MARKER_PREFIX + deviceHash(context) + ".marker"
            readMarkerTimestamp(context, markerName) ?: run {
                writeMarker(context, markerName, newStartMillis)
                newStartMillis
            }
        }.getOrDefault(newStartMillis)
    }

    private fun deviceHash(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun readMarkerTimestamp(context: Context, markerName: String): Long? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf("$RELATIVE_DIR/", markerName)
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                val uri = ContentUris.withAppendedId(collection, id)
                resolver.openInputStream(uri)?.use { input ->
                    return input.bufferedReader().readText().trim().toLongOrNull()
                }
            }
        }
        return null
    }

    private fun writeMarker(context: Context, markerName: String, timestamp: Long) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, markerName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_DIR)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { output ->
            output.write(timestamp.toString().toByteArray())
        }
    }
}
