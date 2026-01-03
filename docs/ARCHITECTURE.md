# Architecture Overview - Studio Book

## 1. 設計方針

本アプリケーションは、**業務ロジックを中心に据えた設計**を採用しています。  
画面構成や技術要素よりも、予約・料金計算・決済・状態遷移といった
**業務上のルールと整合性**を最優先に設計しました。

- 業務ロジックを Service 層に集約し、画面や外部サービスへの依存を最小化
- ユーザー / ホスト / 管理者の **ロール別導線と操作権限を明確に分離**
- 予約・決済・状態遷移の不整合が起きにくい構造を重視

---

## 2. 全体構成

Browser → Controller → Service → Repository → DB  
＋ Stripe Checkout / Webhook（署名検証あり）

```text
[Browser]
   |
   v
[Controller (Spring MVC)]  <-----  [Stripe Webhook: POST /stripe/webhook]
   |                                   ^
   v                                   |
[Service (Business Logic)]  ----->  [Stripe API / Checkout]
   |
   v
[Repository (Spring Data JPA)]
   |
   v
[(MySQL/MariaDB)]

```

- Web 画面は Thymeleaf による SSR を採用
- 外部サービス（Stripe）は Service 層からのみ利用
- Webhook は専用 Controller で受信し、業務処理は Service 層へ委譲

---

## 3. レイヤ責務と依存関係

### 各レイヤの責務
- Controller
  - 画面遷移、入力受付、Webhook エンドポイント定義
  - 業務判断は行わず Service に委譲
- Service
  - 予約、料金計算、キャンセル、決済確定、集計などの業務ルールの中核
- Repository
  - 永続化処理（Spring Data JPA）
- Entity
  - 予約・料金明細・レビュー等のドメイン状態を保持

### 依存関係

```mathematica

Controller → Service → Repository → Entity

```

上位レイヤが下位レイヤにのみ依存する形とし、
循環依存が発生しない構成としています。

---

## 4. 重要な設計判断
### 4.1 料金計算モデル
料金ルール（曜日・時間帯・割増/割引）をそのまま参照するのではなく、
**予約ごとに料金明細（reservation_charge_items）を保持**する設計としました。
- 過去の予約データが、料金ルール変更の影響を受けない
- 売上集計・明細表示・将来的な請求書発行に対応しやすい
- 業務システムで一般的な「確定値保持」の考え方を採用

### 4.2 予約と決済の整合性
決済完了の判定は、画面遷移結果ではなく
Stripe Webhook を唯一の確定トリガーとしています。
- Webhook 受信時に Stripe-Signature を検証
- checkout.session.completed をトリガーに予約を確定
- 決済と予約状態の不整合が起きにくい構成

Webhook の重複送信を考慮し、
将来的には Event ID / Session ID を用いた冪等制御を追加予定です。

### 4.3 ロール別認可
ユーザー / ホスト / 管理者のロールごとに、
**操作可能な画面・機能を明確に分離**しています。

- URL レベルのアクセス制御（Spring Security）
- Service 層でもロール前提の業務処理を実装
- 画面制御だけに依存しない二重の安全設計

---

## 5. 既知の制約
- 初期データ（seed）を固定せず、環境ごとに投入方法を選択可能な設計
- Heroku デモ環境ではスリープ復帰に時間がかかる場合がある
- 一部処理は同期的に実装しており、非同期化は今後の拡張対象

---

## 6. 今後の拡張を見据えた点
- Flyway 等によるマイグレーション管理
- Stripe Webhook の冪等制御強化
- キャッシュ導入による検索・集計性能改善
