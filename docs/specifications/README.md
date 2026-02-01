# 4D@HOME Android 詳細仕様書

**プロジェクト名**: 4D@HOME Android  
**バージョン**: 1.0.0  
**作成日**: 2025年1月30日

---

## 📚 仕様書一覧

本プロジェクトの詳細仕様書は、技術スタック別に以下のドキュメントで構成されています。

| No. | ドキュメント | 説明 |
|-----|------------|------|
| 00 | [システム概要](00_SYSTEM_OVERVIEW.md) | プロジェクト全体像、技術スタック、ディレクトリ構造 |
| 01 | [Androidアーキテクチャ](01_ANDROID_ARCHITECTURE.md) | Android側のレイヤー構造、DI設計、コンポーネント解説 |
| 02 | [BLE通信仕様](02_BLE_COMMUNICATION.md) | Bluetooth Low Energy通信プロトコル詳細 |
| 03 | [タイムラインフォーマット](03_TIMELINE_FORMAT.md) | エフェクトタイムラインJSON仕様 |
| 04 | [ESP32 EffectStation](04_ESP32_EFFECT_STATION.md) | 環境エフェクト制御ファームウェア詳細 |
| 05 | [ESP32 ActionDrive](05_ESP32_ACTION_DRIVE.md) | 振動モーター制御ファームウェア詳細 |
| 06 | [システム連携](06_SYSTEM_INTEGRATION.md) | Android-ESP32間の連携フロー、シーケンス図 |
| 07 | [UIコンポーネント](07_UI_COMPONENTS.md) | Jetpack Compose UI設計、画面構成 |
| 08 | [設定・データ永続化](08_SETTINGS_DATA.md) | DataStore設定、コンテンツ管理 |

---

## 🎯 本仕様書の目的

このドキュメント群は、**4D@HOME Androidプロジェクトの完全な再現**を可能にすることを目的としています。

### 対象読者

- 本プロジェクトを引き継ぐ開発者
- 類似システムを構築する技術者
- 仕組みを理解したい関係者

### 網羅範囲

- ✅ Androidアプリケーション（Kotlin/Jetpack Compose）
- ✅ ESP32ファームウェア（Arduino/PlatformIO）
- ✅ BLE通信プロトコル
- ✅ タイムラインデータフォーマット
- ✅ システム連携フロー
- ✅ UI/UXデザイン
- ✅ データ永続化

---

## 🔧 技術スタック要約

### Android

| 技術 | バージョン | 用途 |
|------|----------|------|
| Kotlin | 2.0.21 | 開発言語 |
| Jetpack Compose BOM | 2024.12.01 | UI Framework |
| Material 3 | (BOM依存) | デザインシステム |
| Hilt | 2.53.1 | 依存性注入 |
| Media3 ExoPlayer | 1.5.1 | 動画再生 |
| Navigation Compose | 2.8.5 | ナビゲーション |
| DataStore | 1.1.2 | 設定保存 |
| kotlinx.serialization | 1.7.3 | JSONパース |

### ESP32

| 技術 | バージョン | 用途 |
|------|----------|------|
| PlatformIO | - | ビルドシステム |
| espressif32 | (platform) | ESP32サポート |
| Arduino Framework | - | 開発フレームワーク |
| Adafruit NeoPixel | 1.12.0 | LED制御 |
| ESP32 BLE | (標準) | Bluetooth通信 |

---

## 📁 ドキュメント間の関係

```
┌─────────────────────────────────────────────────────────────────┐
│                    00_SYSTEM_OVERVIEW                            │
│                    (システム全体像)                               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ 01_ANDROID_   │   │ 02_BLE_       │   │ 04_ESP32_     │
│ ARCHITECTURE  │   │ COMMUNICATION │   │ EFFECT_STATION│
│               │   │               │   │               │
│ 07_UI_        │   │ 03_TIMELINE_  │   │ 05_ESP32_     │
│ COMPONENTS    │   │ FORMAT        │   │ ACTION_DRIVE  │
│               │   │               │   │               │
│ 08_SETTINGS_  │   │               │   │               │
│ DATA          │   │               │   │               │
└───────┬───────┘   └───────┬───────┘   └───────┬───────┘
        │                   │                   │
        └───────────────────┼───────────────────┘
                            │
                            ▼
                ┌───────────────────────┐
                │ 06_SYSTEM_INTEGRATION │
                │ (連携フロー)           │
                └───────────────────────┘
```

---

## 🚀 クイックスタート

### 新規開発者向け読み順

1. **[00_SYSTEM_OVERVIEW](00_SYSTEM_OVERVIEW.md)** - まずプロジェクト全体を把握
2. **[01_ANDROID_ARCHITECTURE](01_ANDROID_ARCHITECTURE.md)** - Androidアプリの構造理解
3. **[02_BLE_COMMUNICATION](02_BLE_COMMUNICATION.md)** - BLE通信の仕組み理解
4. **[06_SYSTEM_INTEGRATION](06_SYSTEM_INTEGRATION.md)** - 全体の連携フロー確認

### ESP32開発者向け

1. **[00_SYSTEM_OVERVIEW](00_SYSTEM_OVERVIEW.md)** - システム概要
2. **[04_ESP32_EFFECT_STATION](04_ESP32_EFFECT_STATION.md)** - EffectStation詳細
3. **[05_ESP32_ACTION_DRIVE](05_ESP32_ACTION_DRIVE.md)** - ActionDrive詳細
4. **[02_BLE_COMMUNICATION](02_BLE_COMMUNICATION.md)** - コマンド形式確認

### コンテンツ制作者向け

1. **[03_TIMELINE_FORMAT](03_TIMELINE_FORMAT.md)** - タイムラインJSONの書き方
2. **[08_SETTINGS_DATA](08_SETTINGS_DATA.md)** - コンテンツ登録方法

---

## 📝 更新履歴

| 日付 | バージョン | 変更内容 |
|------|----------|---------|
| 2025/02/01 | 1.1.0 | 全仕様書にmermaid図を追加、README更新 |
| 2025/02/01 | 1.0.1 | 実装との整合性確認・仕様書更新（00, 01, 05, 07） |
| 2025/01/30 | 1.0.0 | 初版作成 |

---

## 📞 お問い合わせ

本仕様書に関するご質問・ご指摘は、プロジェクトリポジトリのIssueにてお願いします。
