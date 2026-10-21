# Ticket 14 完成記錄 — 2026-10-18

已完成。油耗異常基準從「最高八個有效區段」修正為「最近八個有效區段」，再沿用既有中位數／MAD及偏差門檻。只調整 domain 中取樣與數值排序的順序，沒有改 accumulator、滿箱錨點、原費用／里程／公升或使用者排除判定。

## 變更與實際重現

- app/src/main/java/com/kumo/bubu/domain/model/FuelEconomy.kt：values.toList().takeLast(8).sorted()；註解記錄 map 保留區段終點時序。
- app/src/test/java/com/kumo/bubu/domain/model/FuelEconomyTest.kt：五個回歸，涵蓋八舊30／八近10、同日時間／未知時間／序號／ID穩定排序、不足八段及候選筆不進基準、排除區段與下一錨點、兩車候選隔離。
- app/src/test/java/com/kumo/bubu/feature/fuel/FuelFormViewModelTest.kt：兩個公開事件／狀態回歸，正常10km/L直接儲存；異常30km/L顯示10基準，明確確認後才保存且保留數值，未自動排除歷史。共用具名歷史 fixture。

第一個 regression 的實際 red：期望無警告，卻得到 FuelEconomyOutlier(candidate=10000, baseline=30000)。修正後同一測試 green；其後逐片新增保護測試，沒有把未執行的命令算成 red。

## 驗證

證據根目錄 outputs/release-1.1.1-audit/；所有 Gradle 依序、不重疊，建置期間沒有來源編輯。

| 閘門 | 實際結果 | 證據 |
|---|---|---|
| 最初單一回歸 | 1 test／1 failure，2m12s | ticket-14-recent-window-red.log／xml |
| 最小修正重跑 | 1 test 通過，23s | ticket-14-recent-window-green.log／xml |
| 逐片補充回歸 | 同日、短歷史、排除、兩車與兩個表單案例均通過 | ticket-14-same-day-window、short-history、excluded-history、vehicle-isolation、normal-form-save、warning-confirmation logs |
| Debug／完整 JVM／test APK | 成功2m04s；243 tests，0 failures/errors/skips | ticket-14-final-build-unit.log；ticket-14-final-unit/ |
| 相關 connected | 成功51s；8 tests，0 failures/errors/skips | ticket-14-final-connected.log及同名XML／counts目錄 |
| Lint | 成功5m10s；0 Fatal／0 Error／125 Warning | ticket-14-final-lint.log／xml／counts.json |
| Review／差異 | Standards0硬規／0未解smell；Spec0；git diff --check exit0 | [雙軸記錄](TICKET_14_REVIEW_2026_10_18.md) |

Connected為真實 OfflineFuelRepositoryTest5項及FuelFormScreenTest3項；新增domain／ViewModel回歸在JVM完整集內。Lint警告數與Ticket12最終125相同。三個當下來源及兩份APK另存ticket-14-final-artifacts/，SHA-256及時間存ticket-14-source-apk-sha256.json；ViewModel來源包含先前票已接受變更，審查只涵蓋本票新增方法／helper／import。第一輪審查的一項測試fixture重複已修正，最終完整集在整理後執行。

## 版本與限制

App1.1.1/code3，DB15／backup format1；SDK compile/target/min37/37/26；AGP9.3.1、Gradle9.6.1、Kotlin2.4.10、KSP2.3.10、JBR25.0.2／bytecode11；Room2.8.4、Coroutines1.11.0、Serialization1.8.1、Compose BOM2026.06.01／Material3 1.4.0。版本與依賴均未更新，沒有schema或permission變更。

僅操作專用BuBu_Ticket08_API35、Android15/API35 google_apis x86_64、emulator-5558／隔離ADB5042。今日舊owner已無程序，控制guard先拒絕resume；保留日誌後核對空inventory／port／alias，啟動相同userdata，16:23:53核對boot_completed1與AVD名稱。Lint期間安全pause，結束後resume，沒有wipe。沿用Windows中文路徑命令列override與驗證同檔身分的ASCII測試classpath，不更動產品build設定。

10/17自動核准審查因額度而未執行命令；10/18同一命令重試核准，未繞過。未操作手機／實際Drive，沒有發布新版本。Ticket13、15及獨立CB-12驗收不以本票通過代替。
