# BuBu Android App 專案規格

## 1. 專案目標

建立一個 Android 原生 App，名稱暫定為：

# BuBu

用途是記錄與管理臺灣自用燃油汽車、機車的：

- 加油紀錄
- 保養紀錄
- 維修紀錄
- 里程
- 金額
- 油耗
- 每公里成本
- 車輛提醒
- 報表與趨勢圖表
- CSV 報表匯出
- 完整備份與還原

此 App 採「離線優先」設計。

第一版不需要登入、不依賴雲端、不需要伺服器。所有資料預設儲存在使用者手機內。

第一版只支援自用車、液體燃料、新臺幣、公里、公升及 km/L，不支援電動車、營業車、租賃車、其他貨幣或英制單位。

未來可以擴充 NAS、WebDAV、Google Drive 或其他同步方式，但第一版不要實作。主要資料除本機 `Long` 主鍵外，應具有不可變 UUID，為未來跨裝置識別保留穩定身分。

---

# 2. 品牌與介面風格

## App 名稱

BuBu

## 中文名稱

BuBu 車庫

## 英文副標題

Fuel, Service, Reports & Mileage Tracker

## 品牌語氣

簡單、可愛、有一點幽默，但操作介面不能過度卡通化。

可使用以下文案：

- Your car eats money. BuBu keeps the receipts.
- 車車負責花錢，BuBu 負責記帳。
- 記錄每一公里，也記錄花掉的每一塊錢。
- 今天愛車又花了多少錢？

## 視覺方向

- Material 3
- 圓角卡片
- 清楚的大按鈕
- 簡潔圖示
- 手機優先
- 深色模式與淺色模式
- 避免資訊過度密集
- 主要操作可單手完成

首頁最重要的兩個按鈕：

- 新增加油
- 新增保養／維修

---

# 3. 技術架構

## 主要技術

- Kotlin
- Android Studio
- Jetpack Compose
- Material 3
- MVVM
- Repository Pattern
- Room Database
- Kotlin Coroutines
- Kotlin Flow
- Navigation Compose
- DataStore
- WorkManager
- Storage Access Framework
- Gradle Kotlin DSL
- Version Catalog

## 建議最低版本

- minSdk：26
- targetSdk：使用建立專案時可用的穩定版本
- compileSdk：使用建立專案時可用的穩定版本

不要使用已停止維護或實驗性過高的框架。

## Package Name

```text
com.kumo.bubu
```

## 專案模組

第一版使用單一 `app` module，不要過早拆成多個 Gradle module。

專案內部以 feature-first 方式分類。

```text
app/
└── src/main/java/com/kumo/bubu/
    ├── BuBuApplication.kt
    ├── MainActivity.kt
    │
    ├── core/
    │   ├── database/
    │   ├── date/
    │   ├── currency/
    │   ├── units/
    │   ├── csv/
    │   ├── backup/
    │   ├── validation/
    │   ├── ui/
    │   └── util/
    │
    ├── data/
    │   ├── local/
    │   │   ├── entity/
    │   │   ├── dao/
    │   │   ├── relation/
    │   │   └── converter/
    │   ├── mapper/
    │   ├── repository/
    │   └── exporter/
    │
    ├── domain/
    │   ├── model/
    │   ├── repository/
    │   └── usecase/
    │
    ├── feature/
    │   ├── dashboard/
    │   ├── vehicle/
    │   ├── fuel/
    │   ├── service/
    │   ├── history/
    │   ├── reports/
    │   ├── reminder/
    │   ├── dataexport/
    │   └── settings/
    │
    └── navigation/
```

---

# 4. 架構原則

資料流：

```text
Compose UI
    ↓
ViewModel
    ↓
UseCase
    ↓
Repository
    ↓
Room / CSV Export / Backup File
```

## 分層責任

### UI Layer

負責：

- 顯示畫面
- 接收使用者操作
- 顯示 loading、error、empty、success 狀態
- 不直接操作 Room
- 不直接執行 CSV 匯出、備份或還原邏輯

### ViewModel

負責：

- 管理畫面狀態
- 呼叫 UseCase 或 Repository
- 驗證畫面輸入
- 不持有 Activity 或 Context
- 需要 Context 的檔案操作透過 abstraction 處理

### Domain Layer

負責：

- 業務規則
- 油耗計算
- 每公里成本
- 里程驗證
- 保養到期計算
- 備份與還原資料驗證
- 重複紀錄判斷

### Data Layer

負責：

- Room Entity
- DAO
- Repository 實作
- CSV Report Writer
- Backup Writer
- Backup Reader
- Entity 與 Domain Model 映射

---

# 5. 資料模型

所有主要資料使用 `Long` 自動遞增主鍵，並另存建立時產生、永不改變的 UUID。Room 關聯使用 `Long`，備份與未來跨裝置識別保留 UUID。

固定資料規則：

- 金額只使用新臺幣，實付總額保存整數元。
- 油量以毫升保存；每公升單價保存至千分之一元。
- 事件日期保存本地日曆日期，時間選填且不保存時區；未填時間不代表午夜。
- `createdAt`、`updatedAt` 使用 epoch milliseconds，所有可變資料表都必須具備。
- 可刪除的紀錄先進垃圾桶，保存 `deletedAt`，30 天後才永久刪除。

## 5.1 Vehicle

代表一台臺灣自用燃油汽車或機車。第一版不支援電動車、營業車、租賃車或特殊用途車。

核心欄位：

```kotlin
data class Vehicle(
    val id: Long,
    val publicId: String,
    val name: String,
    val vehicleType: VehicleType,
    val motorcycleClass: MotorcycleClass?,
    val brand: String?,
    val model: String?,
    val manufactureYear: Int?,
    val engineDisplacementCc: Int?,
    val licensePlate: String?,
    val powertrainType: PowertrainType?,
    val trackingStartDateEpochDay: Long,
    val trackingStartOdometerKm: Long,
    val currentOdometerKm: Long,
    val note: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
```

`currentOdometerKm` 是可重建快取。未更換里程表時，它等於追蹤起始里程、有效加油紀錄與服務工單里程的最大值；里程表更換後，以各段增加量換算累積里程。沒有加油或服務事件時，不提供獨立更新里程功能。

```kotlin
enum class VehicleType { CAR, MOTORCYCLE }

enum class MotorcycleClass {
    LIGHT,
    ORDINARY_HEAVY,
    LARGE_HEAVY
}

enum class PowertrainType {
    GASOLINE,
    DIESEL,
    HYBRID,
    OTHER_LIQUID_FUEL
}
```

## 5.2 FuelRecord

代表一次加油，不保存加油站或付款方式。

```kotlin
data class FuelRecord(
    val id: Long,
    val publicId: String,
    val vehicleId: Long,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int?,
    val sequenceInDay: Int,
    val odometerKm: Long,
    val fuelVolumeMl: Long,
    val pricePerLiterMilli: Long?,
    val totalCostTwd: Long,
    val isFullTank: Boolean,
    val fuelProduct: FuelProduct?,
    val startsNewConsumptionCycle: Boolean,
    val excludeFromFuelEconomy: Boolean,
    val note: String?,
    val deletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)
```

```kotlin
enum class FuelProduct {
    GASOLINE_92,
    GASOLINE_95,
    GASOLINE_98,
    DIESEL,
    OTHER
}
```

數值範例：4.270 L 保存為 `4270 ml`；31.7 元/L 保存為 `31700`；145 元保存為 `145`。

使用者輸入公升數、每公升單價、實付總額其中兩項時，自動計算第三項。最後明確輸入的兩欄為來源，計算欄位要有清楚標示；保存後以油量與實付總額為權威資料。計算使用 Decimal 或整數換算，不得使用浮點累加。

## 5.3 ServiceRecord 與 ServiceItem

`ServiceRecord` 是一次保養、維修或檢驗工單，不保存店家；稅費與保險不屬於服務工單。

```kotlin
enum class ServiceRecordType { MAINTENANCE, REPAIR, INSPECTION }

enum class PaymentMethod {
    CASH,
    CREDIT_CARD,
    MOBILE_PAYMENT,
    BANK_TRANSFER,
    OTHER
}
```

工單包含車輛、UUID、本地日期、選填時間、同日順序、整數里程、工單類型、標題、選填付款方式、由項目合計得出的工單總金額、備註、垃圾桶狀態與稽核時間。一張工單必須包含至少一個 `ServiceItem`，並可包含多個項目。

工單不設獨立工資、施工費、服務費或手動總額覆寫欄位。工資、施工費、檢查費等都以一般 `ServiceItem` 保存，避免主工單與明細出現兩套金額來源。

服務項目包含：

- 選填的 `serviceTypeId`
- 建立當下的名稱快照
- 大於零的數量與單位；新增時數量預設為 1
- 非負整數新臺幣單價
- 由數量與單價自動得出的非負整數新臺幣小計
- 選填的下次到期里程與日期
- 備註
- `createdAt`、`updatedAt`

數量單位內建：個、組、公升、瓶、條、片、工時、其他。不同單位不可互相加總。數量以既有整數縮放規則保存，計算使用 `Long`、`BigDecimal` 或等價的十進位規則，不得使用 `Double` 累計。

```text
項目小計 = 數量 × 單價
工單總金額 = 所有項目小計總和
```

項目小計與工單總金額都是衍生結果，畫面不得提供手動修改小計或覆寫工單總金額的輸入欄位。

## 5.4 ServiceType

使用者可把任意自訂項目保存為常用服務類型，不能只提供寫死的固定清單。內建類型不可改名或刪除，但可調整順序、隱藏及重新顯示；自訂類型可修改名稱、調整順序、封存及解除封存。任何已有歷史引用的類型都不可永久刪除，歷史項目的名稱快照不受類型後續變動影響。

內建常用項目至少依下列初始順序提供：機油、機油芯、空氣／引擎濾芯、冷氣濾芯、空調乾燥劑包、輪胎全部、輪胎前組、輪胎後組、輪胎對調、燃油／汽油濾芯、正時皮帶／鏈條、冷氣壓縮機皮帶、煞車皮、煞車油、齒輪油、火星塞、電瓶、冷卻液、傳動皮帶、普利珠、離合器、其他。

## 5.5 ExpenseRecord

代表不屬於加油或服務工單的其他支出。它不保存里程，也不影響目前里程。

```kotlin
enum class ExpenseCategory {
    LICENSE_TAX,
    ROAD_MAINTENANCE_FEE,
    INSURANCE,
    PARKING,
    TOLL,
    FINE,
    CAR_CARE,
    OTHER
}
```

支出紀錄包含車輛、UUID、本地日期、選填時間、類別、非負整數實付總額、備註、選填提醒關聯、垃圾桶狀態與稽核時間。第一版不支援負數退款；退款以修改原紀錄並在備註說明。

## 5.6 OdometerCorrection 與 OdometerReplacement

- `OdometerCorrection`：修正既有加油或服務工單誤填的里程，保存來源紀錄、原值、新值、原因及修正時間。
- `OdometerReplacement`：保存更換日期、舊表最後里程、新表起始里程與原因，用偏移量維持累積里程，並中斷當時油耗週期。

## 5.7 VehicleReminder

提醒來源包含服務項目、使用牌照稅、公路使用養護安全管理費、定期驗車、保險或手動提醒。核心資料包含車輛、UUID、來源與來源紀錄、標題、到期里程、到期日期、週期、即將到期門檻、狀態、延後日期、完成時間、規則版本、啟用狀態與稽核時間。到期里程與日期至少一項存在。

Phase 3 的服務項目提醒關聯至少保存來源 `ServiceItem`、完成該提醒的後續 `ServiceRecord` 與完成時間。新增、編輯、補登或刪除工單時，必須依工單本地日期、選填時間及同日順序重建同車輛的服務提醒完成鏈；補登中間紀錄不得讓較晚工單錯誤完成較早一期提醒。

## 5.8 Attachment

附件用於車輛封面、照片、收據、發票及一般文件。保存所屬類型與 ID、UUID、用途、App 私有目錄相對路徑、MIME type、顯示名稱、說明與稽核時間。不得保存暫時性 URI 作為正式路徑。

第一版支援 JPEG、PNG、WebP、PDF；每筆資料最多 10 個附件，單檔上限 20 MB。刪除附件只刪除 BuBu 私有副本，不得碰使用者的來源檔案。

---

# 6. Room Database

必要 DAO：

- `VehicleDao`
- `FuelRecordDao`
- `ServiceRecordDao`
- `ServiceItemDao`
- `ServiceTypeDao`
- `ExpenseRecordDao`
- `VehicleReminderDao`
- `AttachmentDao`
- `OdometerCorrectionDao`
- `OdometerReplacementDao`

DAO 必須提供 Flow 觀察、單筆查詢、新增、更新、移入垃圾桶、還原及永久刪除所需操作。報表查詢要能依車輛與日期範圍聚合，不可把整張表載入記憶體後才計算。

有歷史紀錄的車輛只能封存；只有完全沒有關聯資料的車輛可以直接永久刪除。封存車輛仍可查閱、搜尋、匯出及修改舊紀錄，但不能新增日常紀錄，且暫停提醒通知。

舊版服務工單升級為「總額完全由項目衍生」模型時，必須保留既有實付總額。若舊總額與可正規化的明細及工資不一致，遷移需建立明確的舊工單金額調整項目；不可靜默覆寫既有總額，也不可留下零項目的工單。

---

# 7. Transaction 規則

新增或更新服務工單時，以下資料庫操作必須包在同一個 Room Transaction：

1. 新增或更新 `ServiceRecord`
2. 取得 recordId
3. 新增、更新或刪除所有 `ServiceItem`
4. 完成舊提醒並新增、更新或刪除服務項目產生的下一期提醒資料
5. 新增、更新或刪除附件關聯資料
6. 以追蹤起始里程、有效加油紀錄及有效服務工單的最大里程重算 `Vehicle.currentOdometerKm`

任何一步失敗時必須全部 rollback。

Phase 3 只建立服務項目提醒所需的關聯資料與交易一致性；提醒頁、狀態判定、WorkManager 及系統通知仍屬 Phase 6，不得提前實作。

服務工單移入垃圾桶時，必須一致處理：

- 所有服務項目
- 相關附件關聯
- 該工單產生的下一期提醒
- 被該工單完成的上一期提醒
- 目前里程與衍生結果

還原工單時必須反向恢復以上狀態。附件實體檔案無法直接加入 Room Transaction，新增、更新與刪除都必須採可補償流程：先把檔案放入可恢復狀態，再提交資料庫交易，交易失敗時回復檔案，成功後才完成清理，不能留下資料庫孤兒或遺失檔案。

在 Phase 3 尚未提供垃圾桶操作前，使用者確認刪除服務工單即為永久刪除，必須同步刪除服務項目、相關提醒資料、附件關聯及 BuBu 私有附件檔案，並重算目前里程。日後導入垃圾桶後，移入垃圾桶仍保留附件，只有永久刪除才清除私有檔案。

新增、更新、刪除或還原加油紀錄時，必須從最早受影響日期重算目前里程、滿箱油耗、平均行駛里程、每公里成本及里程提醒，不可默默修改其他原始紀錄。

完整還原必須先在隔離位置完成解壓、校驗與資料遷移，再切換正式資料；失敗時使用還原前復原備份回復。

---

# 8. 業務規則

## 8.1 里程規則

- 里程不可為負數
- 只接受整數公里
- 補登時依日期時間找到前後相鄰有效紀錄；落在兩者之間不警告
- 破壞局部里程順序時顯示前後紀錄，使用者確認並填寫原因後仍可保存
- 補登舊資料不得降低目前里程
- 同日時間未知的多筆紀錄保存使用者確認的順序，但不得虛構時間
- 不允許建立未來日期的已發生紀錄；未來事項使用提醒
- 修改追蹤起始值或刪除最高里程紀錄時，先顯示受影響結果
- 里程錯誤修正保留原值、新值與原因
- 里程表更換保存舊表最後值與新表起始值，以偏移量接續累積里程，並中斷油耗週期

## 8.2 油耗計算

油耗固定使用滿箱法，只使用兩個連續可信的滿箱錨點：

```text
油耗 km/L =（本次滿箱累積里程 - 上次滿箱累積里程）
             ÷（上次滿箱之後至本次滿箱的所有加油公升數）
```

分母包含本次滿箱及中間所有未滿箱油量，但排除上次滿箱本身的油量。第一個滿箱錨點無法產生油耗。

其他規則：

- `是否加滿` 由使用者勾選，並依車輛記住上次設定，不得依油量猜測
- `重新開始油耗週期` 必須同時是滿箱；它不和前一錨點計算，但成為下一區段起點
- 漏記、順序未確認或使用者排除的紀錄不參與油耗，但其支出仍有效
- 距離或累積油量不大於零時不計算，不顯示 0、NaN 或無限值
- 極端但結構有效的數值只警告，不以固定上下限自動排除
- 不同汽油油品可合併公升數；油品支出仍分開統計
- 編輯或刪除錨點後，重算至資料結尾或下一個週期重啟點
- 一段油耗歸屬於終點滿箱紀錄日期，跨月不拆分
- 首頁最近平均油耗為最近三個有效區段的總距離除以總油量

## 8.3 每公里成本

```text
每公里成本 =
指定期間內所有支出
÷
指定期間內行駛里程
```

分子包含日期範圍內的燃料、保養、維修、稅費、保險及其他支出。分母使用期間開始以前最近一筆有效累積里程與期間結束以前最近一筆有效累積里程之差；缺少任一端或差值不大於零時顯示資料不足，不進行推估。

## 8.4 車輛提醒

提醒可以依：

- 里程
- 日期
- 里程或日期先到者

狀態：

```kotlin
enum class ReminderStatus {
    NORMAL,
    DUE_SOON,
    OVERDUE
}
```

預設判定：

- 剩餘 200 km 以內：DUE_SOON
- 剩餘 7 天以內：DUE_SOON
- 超過目標：OVERDUE

單一提醒設定優先於服務類型預設，服務類型預設再優先於全域預設。里程或日期任一條件先達到即採較嚴重狀態。

提醒來源與規則：

- 服務項目：明確到期值優先，未填才使用服務類型預設；完成工單後依實際里程與日期建立下一期
- 使用牌照稅：臺灣自用車預設每年 4 月 1–30 日；不試算金額
- 公路使用養護安全管理費：臺灣自用汽、機車預設每年 7 月 1–31 日；不試算金額
- 定期驗車：自用小客車與大型重型機車未滿 5 年免定檢、5 年以上未滿 10 年每年一次、10 年以上每年兩次；日期以行照指定檢驗月日為準
- 普通重型與輕型機車不套用上述車輛定檢週期
- 保險：不自動建立，但允許手動年度提醒

建立車輛時預設開啟適用的牌照稅與公路養管費提醒，使用者可停用。牌照稅通知日為 3 月 25 日預告、4 月 1 日開徵、4 月 23 日未完成追蹤；公路養管費為 6 月 24 日預告、7 月 1 日開徵、7 月 24 日未完成追蹤。使用者完成後，當年度不再通知。

新增同週期稅費支出時，顯示是否一併完成提醒的勾選，不可因補登舊支出自動完成今年提醒。使用者也可只標記完成而不新增支出。下一期以原到期日加一年，不依實際付款日漂移。

驗車在指定檢驗日前一個月通知可開始辦理、指定日前 7 天再提醒、期限最後 7 天仍未完成時顯示緊急提醒。10 年以上車輛的兩個固定檢驗月日由使用者分別確認，不假設必定相隔六個月。

內建法規提醒保存規則版本與查證日期；App 更新規則時不得覆寫使用者已自訂的日期，只提示可套用新版預設。

里程提醒依最近 90 天、至少兩點且跨度 14 天的有效里程推算通知日；資料不足時擴至 180 天，仍不足則只在 App 內顯示資料不足。日期提醒每天上午 9 點檢查。里程預估只決定通知時間，不改變真正到期里程。

延後通知支援 1、3、7 天或自訂日期，不改變到期條件。相同狀態只通知一次；由 `DUE_SOON` 轉為 `OVERDUE` 可再次通知。

## 8.5 金額與輸入

- 實付總額不得為負，可以為零
- 加油量必須大於 0，最高 999.999 L；超過 200 L 顯示強烈警告
- 加油總額由系統計算時四捨五入至整數新臺幣，使用者可依收據修改
- 服務項目小計由數量乘以單價自動計算，工單總金額為所有項目小計總和；兩者都不可手動覆寫
- 工資、施工費、服務費與檢查費都必須作為一般服務項目，不得在工單建立獨立欄位
- 服務項目與工單加總不得使用 `Double`，並應偵測整數溢位
- 疑似重複紀錄只警告，不自動刪除或合併

## 8.6 垃圾桶

加油、服務及其他支出刪除後進入垃圾桶 30 天。垃圾桶資料不參與里程、油耗、報表或提醒，但包含在完整備份中；期限屆滿才永久刪除資料與 App 私有附件。

---

# 9. 畫面與導航

底部導航建議：

1. 首頁
2. 紀錄
3. 報表
4. 車輛
5. 設定

---

## 9.1 首頁 Dashboard

顯示目前選定車輛。

內容：

- 車輛名稱
- 車輛圖片
- 目前里程
- 本月總花費
- 本月油錢
- 最近平均油耗
- 距離下次保養
- 最近 5 筆紀錄
- 快速新增加油
- 快速新增保養／維修

若沒有車輛：

- 顯示 Empty State
- 顯示「新增第一台車」按鈕

---

## 9.2 車輛列表

顯示：

- 車輛圖片
- 車輛名稱
- 廠牌型號
- 目前里程
- 最近一筆紀錄日期
- 是否封存

操作：

- 新增
- 編輯
- 切換目前車輛
- 封存
- 刪除空白車輛

已有任何關聯資料的車輛只能封存，不能從一般操作永久刪除。封存後仍可查看、搜尋、匯出及修改歷史資料，但需先解除封存才能新增日常紀錄。

---

## 9.3 新增加油

欄位：

- 車輛
- 日期
- 時間（選填）
- 目前里程
- 公升數
- 每公升單價
- 總金額
- 是否加滿
- 油品
- 是否重新開始油耗週期
- 是否排除油耗計算
- 備註
- 收據照片

UX 要求：

- 數字鍵盤
- 自動計算
- 顯示計算結果
- 以勾選欄位輸入是否加滿，並依車輛記住上次設定
- 欄位錯誤顯示在欄位附近
- 儲存成功後返回上一頁
- 防止重複點擊造成重複新增

---

## 9.4 新增保養／維修

頁面採「主工單＋多個服務項目」結構，不使用單一長表單。視覺必須沿用 BuBu 的 Material 3 主題，不複製參考產品的品牌、配色或圖示。

### 主工單基本資料

使用獨立卡片集中顯示：

- 車輛
- 日期與時間
- 目前里程
- 工單總金額
- 標題
- 工單類型

日期與時間預設為新增當下的本地日期與時間，仍以不含時區的本地事件時間保存。里程欄下方以小字顯示「目前最新里程：XXXX 公里」；其值必須是追蹤起始里程、所有有效加油紀錄及所有有效服務工單里程的最大值。補登舊工單不得降低 `Vehicle.currentOdometerKm`。

工單總金額在主卡中顯示為唯讀衍生值，不提供手動覆寫。付款方式保留在既有資料模型供歷史工單讀取，但目前新增／編輯表單不顯示或寫入付款方式。

### 零件／保養項目

區塊標題固定為「零件／保養項目」。已加入的每個項目使用獨立卡片，支援編輯與刪除；只有明確選取項目時才開啟編輯 Sheet。

目前表單的每個項目包含：

- 項目名稱
- 項目金額（即唯讀小計）
- 項目備註

選擇常用項目後，使用 `ModalBottomSheet` 先確認名稱、項目金額與備註；確認且名稱與金額有效後才加入或更新。自訂項目先以 `AlertDialog` 輸入名稱及是否保存為常用類型，接著開啟相同的項目編輯 Sheet。已存在的項目再次選取時開啟相同編輯器，不建立重複項目。新增資料以既有 schema 的固定 `quantityMilli = 1000`、`PIECE` 與 `unitPriceTwd = subtotalTwd = 項目金額` 保存，且不進行資料庫遷移；舊資料的數量、單價、下次保養里程與日期仍保留可讀性，但目前表單不提供這些欄位的編輯。

常用項目依使用者調整的順序顯示未隱藏、未封存的內建及自訂類型；自訂輸入接受任意非空白名稱，並可由使用者決定是否保存成常用類型。

常用類型管理必須支援：自訂類型編輯、排序、封存及解除封存；內建類型排序、隱藏及重新顯示。內建類型不可改名或刪除。

### 附件圖片

區塊標題固定為「附件圖片」。顯示新增照片卡片，並支援多張 JPEG、PNG 或 WebP 圖片；每張圖片可以預覽及刪除。選取後立即複製到 App 私有目錄，資料庫只保存私有相對路徑，不得把暫時 `content URI` 當成正式附件。刪除工單時依 Transaction 與可補償檔案流程同步清除附件資料與 BuBu 私有副本，不得刪除使用者的來源檔案。

### 備註

備註使用獨立卡片，欄位名稱為「備註」，並提供多行文字輸入。

### 儲存操作

頁面底部主要按鈕固定為「儲存工單」，必須清楚可見並套用 `imePadding` 與 `navigationBarsPadding`。快速重複點擊只能送出一次；儲存期間停用重複操作並顯示 loading；成功後返回上一頁；失敗時顯示具體原因，不得只顯示通用錯誤。

支援：

- 新增、編輯與刪除多個項目
- 從常用項目快速選擇及建立自訂常用類型
- 自動計算項目小計與工單總金額
- 新增、預覽與刪除多張附件圖片
- 新增與編輯工單時保持項目、提醒、附件關聯及里程的交易一致性

---

## 9.5 紀錄頁

使用統一時間軸。

篩選：

- 全部
- 加油
- 保養
- 維修
- 稅金
- 公路養管費
- 保險
- 日期範圍
- 金額範圍
- 關鍵字
- 車輛

每筆紀錄顯示：

- 日期
- 類型
- 里程（其他支出可無里程）
- 金額
- 主要內容
- 是否有附件

支援：

- 點擊查看
- 編輯
- 刪除
- 複製成新紀錄

---

## 9.6 報表頁

報表頁提供數字摘要、分類比較與時間趨勢，第一版至少包含：

- 本月總支出
- 本年總支出
- 燃料支出
- 保養支出
- 維修支出
- 稅費支出
- 保險與其他支出
- 平均油耗
- 每公里成本
- 月支出趨勢圖
- 平均油耗趨勢圖
- 保養花費趨勢圖
- 維修花費趨勢圖
- 支出類別占比圖
- 累積里程趨勢圖

所有報表必須支援：

- 選擇車輛
- 選擇日期範圍
- 快速期間：本月、今年、最近 12 個月、全部、自訂
- 點擊圖表資料點查看該期間或該筆來源紀錄

圖表規則：

- 月支出使用按月長條圖；需要比較分類時使用堆疊長條
- 平均油耗使用折線圖並顯示每個有效滿箱區段資料點
- 保養與維修花費使用按月長條圖，可切換分開或合併比較
- 支出類別占比預設使用具文字標籤的橫向長條圖，避免小螢幕圓餅圖難以比較
- 累積里程使用折線圖，里程表更換前後維持連續累積值
- 平均油耗圖表只使用有效滿箱油耗區段，資料點落在區段終點滿箱日期
- 首頁平均油耗與報表摘要使用加權平均：總距離除以總油量，不平均各段 km/L
- 保養花費圖表只包含 `ServiceRecordType.MAINTENANCE`
- 維修花費圖表只包含 `ServiceRecordType.REPAIR`
- 工單以保存的工單總金額計入；該金額等於項目小計總和，報表不可再把項目逐筆重複加總
- 支出類別圖表分開顯示燃料、保養、維修、牌照稅、公路養管費、保險及其他
- 多車選擇時必須清楚標示合計；油耗不可跨車混算，只能分車顯示
- 圖表使用可辨識的顏色、圖例與文字數值，不能只靠顏色傳達資訊

空資料或資料不足時顯示原因，不可顯示 0、NaN、無限值或誤導性折線。報表數字應可由來源紀錄重建，不保存為不可變正式資料。

---

## 9.7 提醒頁

顯示：

- 即將到期
- 已逾期
- 未來提醒
- 已完成提醒
- 稅費提醒
- 定期驗車提醒

使用者可：

- 標記已完成
- 延後提醒
- 編輯條件
- 停用提醒

---

## 9.8 設定頁

內容：

- 外觀
- 深色模式
- 預設車輛
- 車輛提醒門檻
- 臺灣稅費提醒開關與法規查證日期
- CSV 報表匯出
- 完整備份
- 從備份還原
- 每月備份提醒
- 最新復原備份
- 關於 BuBu
- App 版本
- 隱私說明

---

# 10. 匯出設計

## 10.1 CSV 報表匯出

CSV 只供使用者以試算表閱讀與分析，不負責還原資料，也不提供一般 CSV 反向匯入或匿名模式。

匯出 ZIP：

```text
bubu-export-YYYY-MM-DD-HHmmss.zip
├── vehicles.csv
├── fuel_records.csv
├── service_records.csv
├── service_items.csv
├── expense_records.csv
├── odometer_corrections.csv
├── reminders.csv
├── attachments.csv
└── README.txt
```

CSV 要求：

- UTF-8 with BOM
- 逗號分隔、CRLF 換行、符合 RFC 4180 quoting
- 第一列使用固定英文欄位名稱，`README.txt` 提供繁體中文說明
- 日期使用 `YYYY-MM-DD`，時間另欄使用本地 `HH:mm`；時間未知時留白，不附時區
- 新臺幣輸出整數；公升與每公升單價最多三位小數；不加貨幣符號或千分位
- 選填值缺少時留白，真正零值輸出 `0`
- 布林固定使用 `true`、`false`
- 處理逗號、引號、換行及試算表公式注入
- 不輸出資料庫主鍵、私有路徑或 Android URI
- 每次匯出產生只在該 ZIP 有效的 `VEH-001`、`SRV-000001` 等易讀參照，以連結多份 CSV
- `attachments.csv` 只輸出所屬參照、顯示名稱、用途、MIME type、說明與建立日期，不包含附件檔案
- 預設包含使用中及封存車輛，排除垃圾桶；可選車輛與日期範圍
- 紀錄依車輛、日期、時間與使用者確認的同日順序由舊到新排列

匯出流程先在 App 私有暫存區完整建立並驗證 ZIP，再複製到使用者選擇的位置。建立階段可取消；失敗時清理暫存及未完成目的檔案。

---

# 11. 完整備份設計

備份由使用者手動建立，副檔名為 `.bubu`，實際格式為 ZIP。第一版不在背景自動寫檔，可提供每月備份提醒。備份不加密，匯出前必須提醒使用者妥善保管。

```text
bubu-backup-YYYY-MM-DD-HHmmss.bubu
├── manifest.json
├── data.json
└── attachments/
```

備份內容：

- 完整資料庫內容
- 使用中與封存車輛
- 保留期限內的垃圾桶資料及原刪除期限
- 提醒規則、目前狀態、完成歷史與延後狀態
- 資料庫引用的所有附件

不包含外觀、最後頁面、目前選定車輛等 App 偏好，也不包含可重新計算的油耗、每公里成本或報表快取。

`manifest.json` 必須包含格式版本、App 版本、建立時間、各類資料數量、附件總量，以及每個資料檔與附件的相對路徑、大小與 SHA-256。出現未宣告檔案、重複路徑、不安全路徑、遺失附件或摘要不符時拒絕還原。

## 還原規則

還原前：

1. 解析檔案
2. 驗證 ZIP 路徑安全、清單、檔案大小與 SHA-256
3. 驗證 `formatVersion`
4. 檢查解壓、遷移及復原備份所需空間
5. 顯示備份日期、版本、車輛、各類紀錄、提醒、附件及總大小
6. 明確告知目前資料將完全覆蓋並要求確認

第一版只支援完全覆蓋，不支援合併。較舊格式透過逐版遷移；高於目前 App 支援版本的備份拒絕還原並提示升級。

還原前在 App 私有目錄建立復原備份，只保留最新一份，並在設定頁提供匯出與刪除。新復原備份成功建立前不得刪除舊檔。

正式還原先在隔離位置完成解壓、校驗及遷移，再切換資料。切換或重新開啟資料庫失敗時立即回復復原備份；使用者選取的原始 `.bubu` 永遠不得刪除或修改。還原後只有一台使用中車輛時自動選擇，多台時要求選擇，沒有使用中車輛時顯示空畫面。

---

# 12. 權限與隱私

第一版不要要求：

- 位置權限
- 聯絡人權限
- 電話權限
- 管理所有檔案權限
- 帳號權限

照片與檔案選擇使用 Android 系統選擇器。

不使用廣告 SDK。

不使用追蹤 SDK。

不蒐集使用者資料。

不自動上傳任何車牌、照片或維修資料。

---

# 13. 錯誤處理

所有畫面必須有：

- Loading State
- Empty State
- Error State
- Success State

錯誤訊息要提供具體原因。

不要只顯示：

```text
發生錯誤
```

應顯示：

```text
備份還原失敗：附件 receipts/2026-08-01.jpg 的完整性驗證不符。
```

Room、CSV 匯出、備份與還原錯誤必須捕捉，不能讓 App 直接閃退。

---

# 14. 測試要求

## Unit Test

至少測試：

- 油耗計算
- 多次未加滿後的滿箱油耗
- 油耗週期重啟與錨點編輯重算
- 最近三段加權平均油耗
- 每公里成本
- 車輛提醒到期與平均里程預估
- 臺灣使用牌照稅、公路養管費與定期驗車規則
- 金額換算
- 公升與 ml 換算
- CSV escaping
- CSV 公式注入防護
- 本地日期與選填時間解析
- 重複紀錄判斷
- 目前里程重建、補登、修正與里程表更換
- 服務項目數量縮放、小計四捨五入、工單總額與整數溢位
- 報表加總、分類及空資料處理

## DAO Test

至少測試：

- 新增車輛
- 更新車輛
- 新增加油
- 新增、更新與刪除服務工單、項目、提醒及附件關聯
- 新增其他支出
- 工單、提醒及附件關聯一致性與還原
- Transaction rollback
- 刪除關聯資料
- 垃圾桶排除與還原
- Flow 更新

## UI Test

至少測試：

- 建立第一台車
- 新增加油
- 新增與編輯服務工單的主卡片、多項目選擇及附件流程
- 服務工單快速重複儲存、loading、具體錯誤與成功返回
- 鍵盤及系統導覽列顯示時，儲存按鈕仍可操作
- 查看紀錄
- 篩選紀錄
- 查看平均油耗與保養花費圖表
- CSV 報表匯出流程
- 完整備份與還原確認流程
- 表單驗證

---

# 15. 開發階段

## Phase 0：專案初始化

完成：

- 建立 Android Studio 專案
- Kotlin
- Compose
- Material 3
- Navigation
- Room
- DataStore
- WorkManager
- Version Catalog
- 基本 theme
- 深色模式
- 專案目錄
- README
- AGENTS.md
- CHANGELOG.md

驗收：

- 可正常 build
- 可在 Emulator 啟動
- 無 lint error
- 無未使用的示範程式碼

---

## Phase 1：車輛管理

完成：

- Vehicle Entity
- Vehicle DAO
- Vehicle Repository
- 車輛列表
- 新增車輛
- 編輯車輛
- 封存車輛
- 自用汽車／機車與機車級別
- 切換目前車輛
- Empty State

驗收：

- 關閉 App 再開啟後資料仍存在
- 可建立多台車
- 目前車輛可以保存
- 不可建立空白名稱車輛

---

## Phase 2：加油紀錄

完成：

- FuelRecord Entity
- FuelRecord DAO
- 新增加油頁
- 編輯加油
- 刪除加油
- 自動計算
- 里程更新
- 加滿標記
- 最近紀錄

驗收：

- 只輸入公升與單價可計算總額
- 只輸入公升與總額可計算單價
- 不會因快速點擊建立重複紀錄
- 補登舊資料不會降低目前里程

---

## Phase 3：保養與維修

完成：

- ServiceRecord
- ServiceItem
- ServiceType
- ExpenseRecord
- 一對多 Room Relation
- 主工單卡片與多服務項目卡片
- 常用項目選擇與類型管理
- 服務工單多圖片附件
- 服務項目提醒與附件關聯資料
- 新增其他支出頁
- 多項目輸入
- 自動小計與工單總額
- 編輯
- 刪除
- 防止重複儲存、loading、具體錯誤與成功返回

驗收：

- 新增或更新工單、項目、提醒、附件關聯及目前里程必須同時成功
- 失敗時完整 rollback
- 可新增、編輯、排序及封存自訂服務類型；內建類型可排序及隱藏
- 工資與施工費只能作為一般項目，畫面沒有獨立工資或總額覆寫欄位
- 項目小計與工單總金額正確且不使用 `Double` 累計
- 多張圖片保存於 App 私有目錄，刪除工單後不留下附件資料或私有檔案
- 最新里程包含加油與服務工單最大值，補登不降低目前里程

---

## Phase 4：首頁與紀錄

完成：

- Dashboard
- 統一時間軸
- 最近紀錄
- 分類篩選
- 日期範圍
- 搜尋
- 詳情頁

驗收：

- 加油、服務與支出依本地日期、選填時間及同日順序正確排序
- 篩選結果正確
- 空資料畫面正常
- 刪除後立即更新

---

## Phase 5：報表

完成：

- 月支出
- 年支出
- 分類支出
- 油耗
- 每公里成本
- 月支出趨勢圖
- 平均油耗趨勢圖
- 保養與維修花費圖表
- 支出類別占比圖
- 累積里程趨勢圖

驗收：

- 無資料時不顯示 NaN
- 除數為 0 時安全處理
- 日期範圍正確
- 多車資料不互相混用
- 多車油耗不跨車平均
- 圖表資料點可追溯來源紀錄

---

## Phase 6：提醒

完成：

- 里程提醒
- 日期提醒
- 到期狀態
- WorkManager
- 通知權限處理
- 完成與延後
- 使用牌照稅與公路養管費固定月份提醒
- 自用小客車與大型重型機車定期驗車提醒
- 平均里程預估通知

驗收：

- 達到條件時顯示提醒
- 不重複產生相同通知
- 關閉提醒後不再通知

---

## Phase 7：資料匯出、備份與還原

完成：

- CSV 報表 ZIP 匯出
- 完整 `.bubu` 備份
- 備份驗證
- 完全覆蓋還原
- 還原前復原備份

驗收：

- Excel 開啟繁體中文不亂碼
- CSV 可由 Excel 正確閱讀，且不宣稱可反向匯入
- 失敗還原不破壞原資料
- 備份格式版本可驗證
- 附件損壞時拒絕整份還原

---

# 16. UI 元件

建立可重用元件：

```text
BuBuTopAppBar
BuBuBottomNavigation
BuBuPrimaryButton
BuBuOutlinedButton
BuBuNumberField
BuBuCurrencyField
BuBuOdometerField
BuBuDateField
BuBuTimeField
VehicleSelector
EmptyState
ErrorState
LoadingOverlay
RecordCard
SummaryCard
StatCard
ChartCard
ReportFilterBar
ConfirmDeleteDialog
UnsavedChangesDialog
```

---

# 17. 導航路由

建議：

```kotlin
sealed class Route(val route: String) {
    data object Dashboard : Route("dashboard")
    data object History : Route("history")
    data object Reports : Route("reports")
    data object Vehicles : Route("vehicles")
    data object Settings : Route("settings")

    data object AddVehicle : Route("vehicle/add")
    data object EditVehicle : Route("vehicle/{vehicleId}")

    data object AddFuel : Route("fuel/add")
    data object EditFuel : Route("fuel/{fuelId}")

    data object AddService : Route("service/add")
    data object EditService : Route("service/{recordId}")

    data object AddExpense : Route("expense/add")
    data object EditExpense : Route("expense/{recordId}")
}
```

---

# 18. 儲存與附件注意事項

使用者選取附件後，必須複製到 App 私有目錄；不得只保存來源 URI。刪除 BuBu 附件時只刪除私有副本，不得刪除使用者來源檔案。服務工單表單支援多張 JPEG、PNG 或 WebP 圖片，並允許逐張預覽與刪除。

照片存放：

```text
files/attachments/{uuid}.{extension}
```

資料庫只保存相對路徑。

紀錄進入垃圾桶時保留附件；紀錄永久刪除時才刪除對應 App 私有附件檔案。Phase 3 尚未提供垃圾桶操作，因此該階段確認刪除服務工單即同步永久刪除附件關聯與私有副本；檔案與 Room 必須使用可補償流程保持一致。

備份時要包含附件。

---

# 19. 重要禁止事項

Codex 不得：

- 將所有邏輯寫在 MainActivity
- 在 Composable 直接操作 DAO
- 使用 Double 保存金額
- 使用 Double 累加油量
- 將服務類型全部寫死
- 為服務工單建立獨立工資、服務費或施工費欄位
- 允許手動修改服務項目小計或覆寫工單總金額
- 沒有 Transaction 就新增服務工單
- 把暫時 `content URI` 當作正式附件路徑
- 每次畫面重組都重新讀取資料
- 使用 GlobalScope
- 吞掉 Exception
- 使用空 catch
- 使用硬編碼中文字串
- 要求管理所有檔案權限
- 加入 Firebase
- 加入會員系統
- 加入廣告 SDK
- 未經要求加入雲端同步
- 加入電動車、營業車、租賃車、多貨幣或英制單位
- 把 CSV 報表當作完整備份或可逆匯入格式
- 未經要求大幅修改資料結構
- 用假資料取代正式資料流
- 留下不可執行的 TODO 當作完成功能

---

# 20. 程式碼品質要求

- 所有 UI 字串放入 resources
- 所有尺寸與主題依 Material 3 管理
- ViewModel 使用 immutable UI state
- UI event 使用 sealed interface
- Repository 介面放 domain
- Repository 實作放 data
- Room Entity 不直接傳到 UI
- Domain Model 與 Entity 分開
- Mapper 有單元測試
- 所有公開方法命名清楚
- 避免超過 300 行的單一檔案
- 避免超過 80 行的 Composable
- 大型畫面拆成小型元件
- build 必須保持乾淨

---

# 21. Git 規則

分支：

```text
main
develop
feature/*
fix/*
```

Commit 範例：

```text
feat(vehicle): add vehicle creation flow
feat(fuel): add fuel record calculation
fix(odometer): prevent current mileage rollback
test(fuel): add full-tank consumption tests
```

每個 Phase 完成後：

1. 執行 build
2. 執行 unit tests
3. 執行 lint
4. 更新 CHANGELOG
5. 建立 Git commit

---

# 22. 第一個 Codex 任務

請先只執行 Phase 0，不要一次完成所有功能。

## 任務內容

1. 建立 BuBu Android 專案骨架
2. 使用 package `com.kumo.bubu`
3. 使用 Kotlin、Compose、Material 3
4. 設定 Room、DataStore、Navigation、WorkManager
5. 建立上述目錄
6. 建立基本 Theme
7. 建立深色與淺色模式
8. 建立底部導航骨架
9. 建立五個空畫面：
   - Dashboard
   - History
   - Reports
   - Vehicles
   - Settings
10. 建立 README.md
11. 建立 AGENTS.md
12. 建立 CHANGELOG.md
13. 確保專案可以編譯與啟動
14. 不要先實作資料庫 Entity
15. 不要先實作完整功能
16. 完成後回報：
   - 建立的檔案
   - 技術版本
   - build 結果
   - 測試結果
   - 下一步建議

## Phase 0 驗收標準

- App 可啟動
- 底部導航可以切換五個頁面
- 深色模式可正常顯示
- 無 crash
- 無 lint error
- 無明顯警告
- 不包含無關範例頁面
- 不包含 Firebase
- 不包含登入功能
- 不包含雲端功能

---

# 23. 最終產品範圍

第一個正式版本必須具備：

- 多車輛
- 加油紀錄
- 保養紀錄
- 維修紀錄
- 里程
- 金額
- 油耗
- 每公里成本
- 平均油耗、保養花費、維修花費、分類支出及里程圖表
- 車輛提醒
- 臺灣使用牌照稅、公路養管費及定期驗車提醒
- 搜尋與篩選
- CSV 報表匯出
- 完整備份
- 完整還原
- 深色模式
- 無登入
- 無廣告
- 離線可用

此文件為專案的主要規格來源。

若程式碼、README、TODO 與本文件衝突，以本文件為準。
