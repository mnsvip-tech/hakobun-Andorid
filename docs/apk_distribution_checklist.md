# HAKOBUN APK作成・実機配布チェックリスト

## 1. 事前準備

- Android Studio または JDK 17 をインストールし、`JAVA_HOME` を設定する。
- `local.properties` に `sdk.dir` と `MAPS_API_KEY` を設定する。
- 実機の開発者向けオプションでUSBデバッグを有効化する。

## 2. デバッグAPK

```bat
gradlew.bat assembleDebug
```

生成物:

```text
app\build\outputs\apk\debug\app-debug.apk
```

実機インストール:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 3. リリースAPK

Android Studio の `Build > Generate Signed Bundle / APK` から署名付きAPKを生成する。

確認項目:

- `versionCode` と `versionName` を更新済み
- `MAPS_API_KEY` の制限がAndroidアプリ用に設定済み
- カメラ権限の利用目的がアプリ内説明と一致
- OCRモデル同梱によりAPKサイズが増える点を確認
- 実機で荷物登録・保存・OCR・音声入力・地図ピンを確認

## 4. Google Playポリシー観点

- 保存データは端末内SharedPreferencesに限定し、外部送信しない。
- カメラ権限はOCR撮影時のみ要求する。
- 位置情報権限は現在の実装では有効化していないため、不要なら削除を検討する。
- `targetSdk` は `36` に設定済み。
