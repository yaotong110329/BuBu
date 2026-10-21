# Ticket 08 結案 — 2026-10-13

通知現在會選取有效的目標車輛，捲到實際列表中的提醒，並以 Material 3 primary 外框與 selected 語意標示。保養摘要會進入既有提醒頁，可建立服務工單；保留原四個底部頁籤。通知返回後重送、冷 Activity 啟動／重建，以及已刪除提醒／封存車輛均完成公開整合驗證。

## 範圍與變更檔案

審查固定點為 Release1.1.1 `e12379551c1cf9b48fb17ddc18f424ad31504700`；本票用 `outputs/release-1.1.1-audit/ticket-08-before/` 比較，排除已接受01–07及部分09。未提交 commit、PR 或發布新版本。

- `app/src/main/java/com/kumo/bubu/MainActivity.kt`：解析摘要入口，成功處理後消耗通知參數與 Intent extras，讓重送可再次處理。
- `app/src/main/java/com/kumo/bubu/navigation/BuBuNavHost.kt`：等待導航圖就緒；摘要重用提醒入口，保存中斷服務表單。只回復相同車輛／保養項目的草稿，儲存下一個中斷前清除上一個導航快照。
- `app/src/main/java/com/kumo/bubu/feature/reminder/RemindersViewModel.kt`：依目前有效提醒解析車輛；無效目標回退有效車輛。
- `app/src/main/java/com/kumo/bubu/feature/reminder/RemindersScreen.kt`：從實際渲染項目計算位置；加上目標標示。
- `app/src/main/java/com/kumo/bubu/core/notification/AndroidMaintenanceSummaryNotificationGateway.kt`：摘要 PendingIntent 明確指定提醒入口。
- `app/src/androidTest/java/com/kumo/bubu/navigation/NotificationEntryIntegrationTest.kt`：12個真實 Worker／系統 PendingIntent／Activity／Room／Repository／Compose 公開整合案例；DAO僅建立與清理可拋棄 fixture。
- `TASKS.md`、`.scratch/release-1-1-1-hardening/issues/08-notification-reminder-target.md`：更新結案狀態。

未增加 schema、備份格式、依賴、權限或產品功能。既有 UI／domain／data 分層維持，Composables 未呼叫 DAO。

## 回歸證據

雙車選取、含多保養區塊的自動捲動、目標標示、返回後重送、摘要入口及冷 Activity 導航 readiness 都取得 actual red→green。刪除與封存目標的現有安全回退通過。審查提出的手動選 A 再重送 B 疑點以原程式實測通過，未為未確認問題增加程式。

摘要從服務表單重送曾建立重複返回入口；改為重用提醒入口後，一次 Back 回首頁。保存中斷表單後也保留未完成的項目備註。審查發現跨項目回復錯誤，B/X→摘要→B/Y 的 requested-Y 斷言 red26.729s→green17.531s；相同項目草稿仍 green16.703s。連續 X→摘要→Y→摘要→Y 的 Y備註斷言 red36.682s→green30.186s，確認新快照取代舊快照，未帶入 X備註。

## 最終品質閘門

| 驗證 | 實際結果 | 保存證據（outputs/release-1.1.1-audit/） |
|---|---|---|
| assembleDebug／完整testDebugUnitTest／assembleDebugAndroidTest | 最新57s成功；完整JVM208/0/0/0，最近完整實跑2m43s，後續僅測試fixture調整所以JVM up-to-date | ticket-08-summary-await-build.log；ticket-08-sequential-draft-fixed-build-unit.log；ticket-08-unit-final-counts.json；ticket-08-unit-final/ |
| 相關connectedDebugAndroidTest | 最終14/0/0/0，2m33s；12通知入口＋2既有摘要整合 | ticket-08-connected-final-publication-await.log；ticket-08-connected-final.xml；ticket-08-connected-final-counts.json |
| lintDebug | 最終成功1m21s，0 Error／0 Fatal／126 Warning，警告數與前一票相同 | ticket-08-lint-final.log；ticket-08-lint-final.xml；ticket-08-lint-final-counts.json |
| diff／範圍查核 | git diff --check通過；六個本票程式檔無行末空白 | ticket-08-final-scoped.diff；ticket-08-final-source-apk-sha256.json |

Gradle最後build、connected、lint分別完成後才進下一階段。直跑instrumentation僅作重現；最終connected閘門使用真正Gradle任務。測試失敗與環境問題均保留，未以單獨retry冒充整組通過。

## Standards

最終增量審查0硬性違規／0可行動 smells。導航保存在導航層，目標使用Repository資料；Material3／字串資源與公開測試 seam符合AGENTS.md及Spec。共享summary與selector助手移除重複，沒有暫時診斷碼。最後摘要等待使用真實NotificationManager、suspending delay與10秒失敗界限，不繞過產品行為。Reviewer未跑Gradle／ADB；品質閘門由主代理實際執行。

## Spec

最終增量審查0未解決產品問題／無scope creep。12案例涵蓋本票入口／定位／重送／失效目標；草稿回復及清理避免通知導航破壞中斷工作，仍限於本票「僅修通知定位所需導航行為」。兩個已確認草稿缺陷取得actual red→green；未確認的原refocus疑點已實測否證。

雙軸完整紀錄：`outputs/release-1.1.1-audit/TICKET_08_REVIEW_2026_10_13.md`。

## 環境與限制

專用BuBu_Ticket08_API35／emulator-5558／隔離ADB5042，Android15/API35、google_apis/x86_64 image rev9。所有APK安裝／fixture操作先檢查launcher、qemu路徑、AVD名稱與serial；未操作手機或實際Drive。先前經使用者允許暫時關閉的BuBu_API_35未抹除資料。本次專用fixture在最終14測試通過後關閉以釋放記憶體，userdata保留。

啟動ANR／SystemUI finalizer崩潰、debug certificate衝突及harness import問題分開保存，不當作產品actual red。整組重跑曾有selector捲動前置失敗，改為先捲到公開通知設定卡，再透過車輛chip的scroll祖先操作；單獨retry不替代整組閘門。另兩項摘要fixture立即讀取通知時尚未找到contentIntent，改為像普通通知一樣等待真實contentIntent；最終14全通過。一次合併connected＋lint造成分析與裝置工作爭用資源，最後改回分開執行。

本票驗證新建Activity與recreation，未宣稱真正process kill或實體手機所有Android版本均驗證；真正還原process故障屬Ticket13。Google真實帳號CB-12獨立pending。

## 技術版本

App1.1.1/code3、Room DB15、backup format1；compileSdk37／targetSdk37／minSdk26。Gradle9.6.1、AGP9.3.1、Kotlin2.4.10、KSP2.3.10、JBR25.0.2、Java bytecode11。Compose BOM2026.06.01／Material3 1.4.0、Navigation2.9.8、Room2.8.4、Activity1.13.0、CoreKtx1.19.0、Lifecycle2.11.0、DataStore1.2.1、Work2.11.2、Coroutines1.11.0、Serialization1.8.1。Credentials1.6.0、GoogleID1.2.0、PlayServicesAuth21.6.0；JUnit4.13.2／AndroidxJUnit1.3.0／Espresso3.7.0。版本由本專案版本表與既有實際編譯環境確認，本票未升級。

目前01–08完成，09進行中、10–16 ready，共8張未結案；CB-12獨立pending。
