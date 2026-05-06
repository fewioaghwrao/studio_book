# スタジオ時間貸し予約アプリ（Studio Book / Java版）

![CI](https://github.com/fewioaghwrao/studio_book/actions/workflows/ci.yml/badge.svg)

スタジオ（ダンススタジオ・レンタルスペース等）の**時間貸し予約**を題材にした、Java / Spring Boot による業務想定Webアプリです。

本リポジトリは、Studio Book の **初期学習・比較実装版**です。  
一般ユーザー・ホスト・管理者の3ロール構成で、検索、予約、決済、営業時間、休館日、料金ルール、レビュー管理など、予約業務アプリに必要な基本機能を実装しています。

現在は、同じ業務テーマを **ASP.NET Core / Next.js 版**へ発展させ、API分離、管理画面、テスト、CI/CD、クラウドデプロイ、README・構成図整備を強化しています。

---

## 現在の位置づけ

このJava版は、以下を目的として作成した初期実装です。

- Spring Boot によるWebアプリ開発の学習
- 予約・決済・ロール管理を含む業務フローの整理
- 一般ユーザー / ホスト / 管理者の権限分離
- Stripe決済とWebhookによる非同期確定フローの実装
- 業務アプリとしての状態管理・料金計算・集計処理の検証

現在の主力成果物は、ASP.NET Core / Next.js 版の Studio Book および Invoice 系アプリです。  
本リポジトリは、Java / Spring Boot による比較実装・学習証跡として公開しています。

---

## 公開デモについて

Java版の公開デモ環境は停止予定です。  
現在は、ソースコード・README・設計資料・画面キャプチャを中心に掲載しています。

公開デモを確認する場合は、ASP.NET Core / Next.js 版をご参照ください。

- ASP.NET Core / Next.js 版リポジトリ：  
  https://github.com/fewioaghwrao/studio-book-dotnet-next

- ASP.NET Core / Next.js 版デモ：  
  https://gray-bush-08e107700.7.azurestaticapps.net/

---

## このアプリで実装したこと

### 3ロール構成

- 一般ユーザー
- スタジオ提供者（ホスト）
- 管理者

それぞれのロールごとに、利用できる画面・操作範囲を分けています。

### 予約業務フロー

一般ユーザーは、スタジオを検索し、詳細確認、予約、Stripe決済までを行えます。

```
検索
  → スタジオ詳細
    → カレンダー予約
      → Stripe決済
        → Webhookによる予約確定
          → 予約履歴に反映
```

### ホスト業務フロー

ホストは、自分が管理するスタジオの予約・休館日・料金・売上・レビューを管理できます。

```
スタジオ管理
  → 営業時間・休館日設定
    → 予約一覧確認
      → 売上確認
        → レビュー管理
```

### 管理者業務フロー

管理者は、ユーザー、スタジオ、予約、売上、システム設定、監査ログを確認できます。

```
ユーザー管理
  → スタジオ管理
    → 予約・売上確認
      → システム設定
        → 監査ログ確認
```

---

## 主な機能

### 共通

- 会員登録
- ログイン / ログアウト
- パスワードリセット
- プロフィール編集
- ロール別メニュー表示
- 利用規約・プライバシーポリシー画面
- レスポンシブ対応

### 一般ユーザー機能

- スタジオ検索
- スタジオ詳細表示
- 予約カレンダー表示
- 営業時間・休館日・既存予約を考慮した予約チェック
- 料金計算
- Stripe Checkout による決済
- 予約履歴表示
- レビュー投稿・編集・削除

### ホスト機能

- スタジオ管理
- 料金ルール管理
- 営業時間設定
- 休館日設定
- 予約一覧・予約詳細確認
- 売上・統計表示
- レビュー管理
- レビュー返信

### 管理者機能

- ユーザー管理
- スタジオ管理
- 予約管理
- 売上集計
- システム設定
- 監査ログ確認

---

## 技術スタック

| 分類 | 技術 |
|------|------|
| Backend | Java 17 / Spring Boot 3 |
| Web | Spring MVC / Thymeleaf |
| Security | Spring Security |
| ORM | Spring Data JPA / Hibernate |
| DB | MySQL / MariaDB |
| Frontend | HTML / CSS / Bootstrap / JavaScript |
| Calendar | FullCalendar |
| Chart | Chart.js |
| Payment | Stripe Checkout / Stripe Webhook |
| Test | JUnit5 / Spring Test / MockMvc / Mockito |
| CI | GitHub Actions |
| Deploy | Heroku（検証用） |

---

## アーキテクチャ概要

詳細な設計方針は、以下にまとめています。

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

本システムは、Spring Boot を中心に、Thymeleaf によるサーバーサイドレンダリングで実装しています。

```
[Browser]
   |
   v
[Controller / Spring MVC]
   |
   v
[Service / Business Logic]
   |
   v
[Repository / Spring Data JPA]
   |
   v
[MySQL / MariaDB]
```

Stripe決済は、Checkout と Webhook を組み合わせています。

```
[User]
  → [Reservation]
    → [Stripe Checkout]
      → [Stripe Webhook]
        → [Reservation Status Update]
```

### パッケージ責務

| パッケージ | 責務 |
|-----------|------|
| `controller` | 画面遷移、入力受付、エンドポイント定義、Webhook受信 |
| `service` | 予約、料金計算、キャンセル、決済確定、売上集計などの業務ロジック |
| `repository` | Spring Data JPA によるDBアクセス |
| `entity` | 予約、料金明細、レビュー、ユーザー、ロールなどのドメインモデル |
| `security` | ログイン、認証、認可、ロール別アクセス制御 |

---

## 設計上のポイント

### ロール別の権限制御

一般ユーザー、ホスト、管理者で操作できる機能を分離しています。

- 一般ユーザー：検索、予約、決済、レビュー
- ホスト：自分のスタジオ、予約、売上、レビュー管理
- 管理者：全体管理、システム設定、監査ログ確認

### 予約・料金計算

営業時間、休館日、料金ルール、既存予約を考慮し、予約可否と金額を計算する構成にしています。

予約ごとの料金明細を保持することで、後から確認・集計しやすい形を意識しています。

### Stripe決済・Webhook

Stripe Checkout による決済フローを実装しています。  
決済完了は画面遷移だけに依存せず、Webhook をトリガーとして予約ステータスを確定させる構成です。

Webhookでは、Stripe-Signature による署名検証を行います。

**Stripe決済フロー：**

```
一般ユーザーでログイン
  → スタジオ詳細
    → 日時選択
      → 予約
        → Stripe Checkout
          → 決済完了
            → Webhook受信
              → 予約ステータス更新
```

**受信イベント例：**

- `checkout.session.completed`
- `payment_intent.succeeded`
- `charge.succeeded`
- `charge.updated`

> ※ Stripe は Test Mode を前提とした実装です。

---

## CI / 品質管理

本リポジトリでは、GitHub Actions によるCIを導入しています。

**実行内容：**

- Mavenビルド
- テスト実行（`mvn -B test`）

**CI設計方針：**

本プロジェクトは、既存コードベースに対してCIを整備する想定で、まずは以下を重視しています。

- 常にビルド可能な状態を保つ
- 外部依存を伴わないテストを優先
- Controller / Service の主要な正常系・異常系を確認
- DB、メール、決済APIなどの外部依存は段階的に分離

ワークフロー定義：[`.github/workflows/ci.yml`](.github/workflows/ci.yml)

---

## 画面イメージ

<div align="center">
<table>
  <tr>
    <td align="center" width="260"><strong>トップページ</strong></td>
    <td align="center" width="260"><strong>スタジオ詳細</strong></td>
    <td align="center" width="260"><strong>予約カレンダー</strong></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/top.png" width="240"></td>
    <td align="center"><img src="docs/screenshots/room_detail.png" width="240"></td>
    <td align="center"><img src="docs/screenshots/room_calendar.png" width="240"></td>
  </tr>
  <tr>
    <td align="center" width="260"><strong>ホストダッシュボード</strong></td>
    <td align="center" width="260"><strong>管理者ダッシュボード</strong></td>
    <td align="center" width="260"></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/host_dashboard.png" width="240"></td>
    <td align="center"><img src="docs/screenshots/admin_dashboard.png" width="240"></td>
    <td align="center">&nbsp;</td>
  </tr>
</table>
</div>

---

## ドメインモデルと状態遷移

### ER図

ER図は [`docs/diagrams/ERD.drawio.png`](docs/diagrams/ERD.drawio.png) を参照してください。

**主なテーブル：**

- `users`
- `roles`
- `rooms`
- `business_hours`
- `closures`
- `price_rules`
- `reservations`
- `reservation_charge_items`
- `reviews`
- `admin_settings`
- `audit_logs`

### 状態遷移図

- 一般ユーザー：[`docs/diagrams/state_user.drawio.png`](docs/diagrams/state_user.drawio.png)
- ホスト：[`docs/diagrams/state_host.drawio.png`](docs/diagrams/state_host.drawio.png)
- 管理者：[`docs/diagrams/state_admin.drawio.png`](docs/diagrams/state_admin.drawio.png)

---

## ローカル開発環境の構築手順

### 前提

- Java 17
- Maven
- MySQL / MariaDB
- Git

### 1. リポジトリのクローン

```bash
git clone https://github.com/fewioaghwrao/studio_book.git
cd studio_book
```

### 2. DB作成

```sql
CREATE DATABASE studio_book_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 3. アプリケーション設定

`src/main/resources/application-example.properties` をコピーして、`application.properties` を作成します。

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/studio_book_dev?serverTimezone=Asia/Tokyo
spring.datasource.username=your_user
spring.datasource.password=your_password

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

stripe.secret-key=sk_test_xxx
stripe.publishable-key=pk_test_xxx
stripe.webhook-secret=whsec_xxx

spring.mail.host=smtp.mailgun.org
spring.mail.port=587
spring.mail.username=postmaster@example.com
spring.mail.password=password
```

### 4. 初期データ投入

本リポジトリでは、DBスキーマはJPAにより自動生成されます。

必要に応じて、以下のいずれかで動作確認用データを作成します。

- アプリ起動後に画面操作でユーザーを作成
- 開発者が手動SQLで最小データを投入

### 5. アプリケーション起動

```bash
./mvnw spring-boot:run
```

ブラウザで以下にアクセスします。

```
http://localhost:8080
```

---

## テスト

### テスト方針

- Controller層は MockMvc によるWeb層テスト
- Service層は業務ロジックの単体テスト
- Stripe Webhook は署名検証や異常系を確認
- 正常系・異常系を分けて検証

### 実行方法

```bash
./mvnw test
```

---

## ディレクトリ構成

```
src
├─ main
│   ├─ java
│   │   └─ com.example.studio_book
│   │       ├─ controller
│   │       ├─ service
│   │       ├─ repository
│   │       ├─ entity
│   │       ├─ dto
│   │       └─ security
│   └─ resources
│       ├─ templates
│       ├─ static
│       └─ application.properties
└─ test
    └─ java
        └─ com.example.studio_book
            └─ controller

docs
├─ diagrams
│   ├─ ERD.drawio.png
│   ├─ state_user.drawio.png
│   ├─ state_host.drawio.png
│   └─ state_admin.drawio.png
└─ screenshots
    ├─ top.png
    ├─ room_detail.png
    ├─ room_calendar.png
    ├─ host_dashboard.png
    └─ admin_dashboard.png
```

---

## ASP.NET Core / Next.js 版への発展

このJava版で整理した予約・決済・ロール管理の業務構成をもとに、現在は ASP.NET Core / Next.js 版へ発展させています。

**ASP.NET Core / Next.js 版では、以下を強化しています：**

- フロントエンドとバックエンドの分離（ASP.NET Core Web API / Next.js / TypeScript）
- JWT認証
- ホスト・管理者向け管理画面
- 売上管理
- CSV / PDF出力
- AI検索
- 監査ログ
- GitHub Actions
- Azure Static Web Apps / Heroku デプロイ
- README、ER図、構成図、認証フロー、予約・決済フローの整備

ASP.NET Core / Next.js 版リポジトリ：  
https://github.com/fewioaghwrao/studio-book-dotnet-next

---

## 補足

本リポジトリは、個人ポートフォリオ用途の学習・比較実装です。  
商用利用や再配布を目的としたものではありません。

---

## ライセンス

このアプリケーションは個人ポートフォリオ用途を想定しています。  
企業での利用や再配布を行う場合は、必要に応じてライセンス文やクレジット表記を追加してください。