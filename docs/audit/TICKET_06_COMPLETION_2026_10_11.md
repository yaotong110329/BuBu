# Ticket 06 — 系統回收後恢復表單草稿

完成日期：2026-10-11。01–06 完成；07–16 共 10 張待完成，CB-12 真實 Google 帳號驗收獨立 pending。

## 實作與變更檔案

- Vehicle／Fuel／Service 的 FormDraft、FormState、FormViewModel、FormScreen 與 Labels：保留新增／編輯的原始欄位、未知時間、加油輸入來源、里程理由、工單項目顺序／穩定鍵與未完成編輯器。BuBuNavHost 的八個表單 factory 路徑使用 framework SavedStateHandle；不保存 busy／單次 effect、DAO entity、圖片 bytes 或 token。
- core/state/FormDraftPersistence、domain/repository/FormDraftRepository、data/repository/PrivateFormDraftRepository、AppContainer：31 KiB UTF-8 JSON 內嵌上限，長稿私有 atomic publish；handle 僅保留 UUID、摘要、版本與旗標。字串預算 regression ≤32 KiB，未宣稱完整 Android Parcel 大小已量測。owner／SHA／revision 驗證允許背景工作已完整發布的新版本，較舊／缺檔／損壞檔案阻擋恢復與正式儲存。取消只清自有 JSON／中斷 .part，不留每次長稿按鍵的完整副本。
- Vehicle／Fuel／Service domain repositories、Offline repositories：以既有 UUID 作穩定建立識別，重播已提交草稿不再次建立正式紀錄；Vehicle／Fuel 提交後偏好故障以公開識別核對提交結果。沒有資料庫 migration。
- PrivateAttachmentStore／OfflineServiceRepository：私有附件 owner 可恢復、啟動 reconciliation 保護舊草稿，取消／來源失效清理只刪私有副本，保留來源與正式引用。來源失效會先取消並等待匯入；IO 完成、caller 尚未交付時取消仍清掉此次副本。清理失敗保留主例外及紀錄。
- strings.xml：草稿保存中／保存失敗／恢復失敗資源文案，使用 Material 3 theme。
- 測試：三種公開 FormViewModel＋真實私有檔案的 JVM 回歸；三種 Offline repository、ServiceFormViewModelIntegration 的 Room／圖片回歸；正常導航／外部操作旋轉 11 項保持通過。TASKS 與 issue 06 已更新。

## 驗證

證據均在 `outputs/release-1.1.1-audit/`。

| 檢查 | 結果 | 證據 |
| --- | --- | --- |
| 真正 OS 背景回收 | 三種新增表單均先取得實際 red，再取得 green；不是 force-stop 或 rotation 替代 | ticket-06-process-recreation-red.json 及前輪各表單 red／green JSON/XML |
| 長稿背景快照競態 | A handle 保存、延遲附件發布 B、原 scope 終止、用 A 重建：原長備註／標題與 B 附件保留，取消清理且無正式工單；actual red→green | ticket-06-background-completion-{red,green}.log |
| 其他 actual red→green | 已提交草稿重播、里程理由、post-commit 偏好故障、owner .part、來源失效匯入與 IO 交付取消 | ticket-06-*.log；保留失敗，不冒稱 fixture 編譯失敗為產品 red |
| 首輪完整品質檢查 | JVM 203／裝置 99，均 0 failures／errors／skipped | ticket-06-build-unit-green.log、ticket-06-connected-final.log 與 preserved summary |
| 最終 App／測試 APK、完整 JVM | 成功，204／0／0／0；包含新背景完成回歸 | ticket-06-snapshot-final-gates.log（4m34s）、ticket-06-snapshot-jvm-summary-final.json |
| 最終相關裝置回歸 | API35 AVD 39／0／0／0（3m21s），六個 repository／form／rotation 測試類別 | ticket-06-snapshot-connected-final.log／xml／summary |
| 最終 lint | 0 errors／fatal，126 既有 warnings；6 個新增 VisibleForTests 已移除 | ticket-06-snapshot-lint-summary-final.json、ticket-06-post-connected-lint-final.log（3s，分析 up-to-date） |
| whitespace／scope | git diff --check 通過，既有 CRLF 提醒；沒有未使用的新增 helper 或版本變更 | ticket-06-final-diff-check.log |

## 最新 APK 的原任務恢復

僅專用 BuBu_API_35／emulator-5556、隔離 ADB 5041。背景後切到其他系統畫面，確認 cached、`am kill` 後 pidof 為空，再恢復原 Activity；每次 `stopped=false`、原 Task 186／ActivityRecord 450d773 均相同。

| 表單 | PID 前→後 | 保留欄位 | 證據 |
| --- | --- | --- | --- |
| 車輛 | 8537→8820 | SNAPSHOT06-CAR／SNAPSHOT06-BRAND | ticket-06-snapshot-final-vehicle-green.json 及 before／after XML |
| 加油 | 8820→9313 | 1234／135／5／27 | ticket-06-snapshot-final-fuel-green.json 及 before／after XML |
| 服務 | 9313→9763 | 未完成機油 editor：450／SNAPSHOT06-ITEM；關閉 editor 後確認原里程 2345 | ticket-06-snapshot-final-service-editor-green.json 及 editor／base XML |

車輛在 OS 草稿驗證後才儲存為模擬器 fixture，供後兩表單使用；加油與服務未建立正式紀錄，驗證後明確取消，首頁最新里程仍 0。最新 APK SHA-256：`e37e9e552325662815a310c64c5518f22864a7f66383420a4fb35aa6cf3a46ef`。

初版最終 harness 曾以裸 component 初始啟動、MAIN／LAUNCHER 返回，產生第二個 MainActivity 而顯示首頁。Back 返回原 Activity 後資料仍在，此輸出不是產品 red。保留 invalid-launch-harness 與 task dump，修正為 reorder-to-front＋原 task／ActivityRecord 核對，未隱藏這次流程錯誤。其餘無觀察到的按鈕操作均由 fresh XML 安全拒絕後重讀，未對猜測座標點擊。

## Standards

復審 0 個未處理確認問題。分層、framework handle、資源、取消傳播與私有 owner 清理符合本票，未新增架構違規／確認 smell。

## Spec

最後 1 個 P2（背景完成覆寫長稿）已實際重現並修正，公開回歸 green 後復審解除；0 個未處理確認問題。新增／編輯來源識別與保存後去重有公開 VM／Room 回歸，未擴張至雲端草稿或新增權限。

Standards：0；Spec：0 未處理確認問題。兩位 reviewer 僅唯讀，測試由主代理執行。

## 實際版本與限制

App1.1.1（code3）、DB15、backup format1；compile／target／min SDK37／37／26；AGP9.3.1、Gradle9.6.1、Kotlin2.4.10、KSP2.3.10、Room2.8.4、Compose BOM2026.06.01／Material3 1.4.0、Activity Compose1.13.0、Navigation2.9.8、Lifecycle2.11.0、DataStore1.2.1、Work2.11.2、Coroutines1.11.0、Serialization1.8.1、Credentials1.6.0、GoogleID1.2.0、PlayAuth21.6.0、CoreKtx1.19.0、JBR25.0.2。沒有 dependency／schema／backup format／簽署／權限變更。

- 正常 sandbox helper／apply_patch 的 Windows reparse 問題仍存在；僅經核准修改工作區檔案。一次 TASKS 可能截斷後續內容的自動審查拒絕未執行，已改成精確取代原段落並核對前後內容相同，沒有未解權限阻擋。
- 離線 Gradle／已驗證官方 local artifact init；不下载或升級依賴。其他專案的 emulator-5554／預設 ADB server 未停止或修改；隔離 server 只註冊本票 AVD，Gradle 裝置 guard 全程通過。
- 最新三種新增表單與未完成工單 editor 有真正 cached process termination；編輯、長稿與附件競態以 controlled public VM／真實檔案／Room 驗證，未聲稱各組都經 OS kill 或真實低記憶體硬體測試。
- 未操作實體手機、實際 Drive、真實 ENOSPC／大附件 heap。CB-12、後續票的故障／效能驗證維持獨立追蹤。專用測試 AVD 保留供下一票驗證。
