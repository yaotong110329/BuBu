# Ticket 11 雙軸審查 — 2026-10-17

固定 audit reference：`e12379551c1cf9b48fb17ddc18f424ad31504700`。僅審查 ticket 11 的 Reader、RestoreRepository enum fallback 清理與公開 archive／實際 Room round-trip 測試；已接受的 01–10 依 ticket-11-before 快照隔離。未建立 commit 或 PR。

## Standards

獨立代理 ticket11_final_standards 最終報告：硬規違規 0；可行動 Fowler smells 0。

Reader 使用既有 domain 金額計算與溢位保護，沒有新增推測性框架。測試經公開 Reader／Writer、Repository 匯出與 RestoreViewModel 驗證；SQLite trigger 只用於實際故障 fixture。語意與歷史相容性測試在 review 階段拆分，重用合法資料與正式 archive writer。

新增三條 uniqueness guard 保持現有結構；mapNotNull 保留多筆 NULL。公開 regression 涵蓋拒絕重複，以及同名不同車種／多筆 NULL 的合法例外。

## Spec

獨立代理 ticket11_final_spec 最終報告：0 項未解決 finding。

審查曾發現過度嚴格的車種相等檢查。Migration 8→9 將舊共用類型標為 CAR，但保留機車歷史引用；車輛編輯也允許變更車種後保留既有 policy。公開相容性 regression 取得 actual red→green，修正只保留適用的參照存在與擁有者檢查。

審查另發現兩組預覽漏檢：service type 的 UNIQUE(vehicleType,name)，以及 reminder 非 NULL 的 sourceServiceItemId／automaticKey UNIQUE。公開 regression 已實際失敗後補入 guard；精確比較原始 pair、不正規化名稱，nullable key 排除 NULL，符合既有 DB 契約。

遷移 4→5 先處理超額明細再建立非負調整項，不需要放寬工單金額契約；合法提醒 producer／遷移保證至少一項到期值。format 1 additive defaults 與歷史機車引用皆保留。

兩軸最終未解決 findings：Standards 0；Spec 0。審查代理僅唯讀，未執行 Gradle 或 ADB；建置、JVM、裝置與 lint 閘門需另以主線實測記錄判定，審查通過不代表閘門通過。

## 既有還原測試契約補充複核

舊 OfflineRestoreRepositoryTest 以未知 enum 模擬較晚的替換失敗，期待產生一份復原檔。提前驗證後，實機重跑取得 expected1／was0 的實際失敗；首次冷測試程序另有啟動 ANR，不算此契約的 red，沒有修改 App 來掩蓋它。

測試改為 invalidEnumFailsBeforeRecoveryAndKeepsTheOriginalSnapshot：未知 enum 還原失敗、復原目錄空白，經公開 BackupRepository 匯出與正式 Reader 比對原快照未變，兩份比較 fixture 於 finally 清理。真正交易中途失敗仍由 BackupRoundTripAuditTest 的 SQLite trigger 案例覆蓋。

兩代理再次唯讀複核：Standards 0 硬違規／0 可行動 smells；Spec 0 findings。沒有新增 DAO 斷言或修改 production。裝置測試與 lint 結果仍由主線另外記錄。
