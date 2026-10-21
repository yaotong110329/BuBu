# Ticket 09 結案 — 2026-10-14

帳號／過期／stale identifier驗證與切換清單、選取、完成訊息一致性完成；同帳號大小寫／時間更新保留狀態。build、完整JVM217及相關裝置13全綠；lint0 errors／126 warnings，雙軸0未解問題。未操作手機或實際Drive；CB-12真實帳號獨立pending。

## 範圍與變更檔案

固定點e12379551c1cf9b48fb17ddc18f424ad31504700；ticket-09-before及final-scoped.diff排除已接受01–08。沒有commit、PR或發布。

- CloudBackupViewModel.kt：拒絕跨帳號Upload／Load／Download／Delete；過期及授權前後stale ID攔截；切換清除A清單與完成訊息；先同步新connection再載入B清單，避免observer清除新結果；審查整併selectionError。
- CloudBackup.kt、SettingsScreen.kt、strings.xml：AccountMismatch及資源化提示。
- BuBuNavHost.kt：帳號身分變更清除雲端清單／刪除確認，同帳號更新保留，維持08導航。
- CloudBackupViewModelTest.kt：15個公開VM案例；ExternalOperationsRotationTest.kt：新增真實Compose選取清理與同帳號正向案例；CloudAccountConnectionIntegrationTest.kt：真實Repository/DataStore的帳號／時間。
- TASKS.md、本票issue、audit文件：結案與證據。

## 品質閘門

| 驗證 | 實際結果 | outputs/release-1.1.1-audit/證據 |
|---|---|---|
| assembleDebug／完整JVM／Android test APK | 最終production4m04s成功，217/0/0/0；後續僅fixture定位，build20s成功、JVM up-to-date | ticket-09-final-build-unit.log、ticket-09-unit-final/、ticket-09-dialog-selector-build.log |
| 相關connectedDebugAndroidTest | 13/0/0/0、1m36s；ExternalOperationsRotationTest12＋DataStore1 | ticket-09-connected-dialog-scoped-final.log、ticket-09-connected-final.xml/counts.json |
| lintDebug | 3m45s成功；0 Error／0 Fatal／126 Warning | ticket-09-lint-final.log/xml/counts.json |
| 範圍／空白檢查 | git diff --check exit0，限定diff及source/APK hashes保存 | ticket-09-final-scoped.diff、ticket-09-final-source-apk-sha256.json |

跨帳號上傳、過期後錯選帳號、切換清單載入失敗、授權前後stale ID、observer變更、成功切換清單競態及舊完成訊息均保存actual red→green。取消／一般授權失敗、單帳號解除／重連通過。DataStore從A時間42→NeedsAuthorization→B，確認B不繼承時間。

## Standards

獨立審查0硬違規／0未解Fowler smells。公開VM／Repository／Compose邊界及外部Drive回應fixtures符合既定seams；共享選取驗證，單module／分層／資源合規，沒有新依賴、schema、permissions或token持久化。dialog定位增量審查通過。

## Spec

獨立審查0未解問題；符合09帳號／清單狀態要求，窄Drive scopes／appDataFolder不變，無scope creep。Reviewer未執行Gradle／ADB；閘門由主代理實際完成。完整報告TICKET_09_REVIEW_2026_10_14.md。

## 環境與限制

只操作專用BuBu_Ticket08_API35／emulator-5558／私有ADB5042；Android15/API35、google_apis/x86_64 rev9，核對launcher／qemu路徑／AVD／serial／boot才安裝。userdata保留。

首次AVD未註冊私有ADB、storage測試程序在斷言前終止；同source warm retry1項通過。首輪完整13/1為背景與dialog「刪除」同名造成fixture定位歧義，未到帳號斷言；限定公開dialog語意後完整13全綠。失敗證據保留，不把單獨retry當整組通過。真實Google多帳號CB-12仍pending。

## 實際版本

本輪核對libs.versions.toml及build配置：App1.1.1/code3、DB15、backup format1；SDK37/37/26，AGP9.3.1、Gradle9.6.1、Kotlin2.4.10、KSP2.3.10、JBR25.0.2／Java bytecode11；Compose BOM2026.06.01／Material3 1.4.0、Room2.8.4、DataStore1.2.1、Navigation2.9.8、Lifecycle2.11.0、Coroutines1.11.0、Serialization1.8.1；Credentials1.6.0、GoogleID1.2.0、PlayAuth21.6.0；JUnit4.13.2、AndroidxJUnit1.3.0、Espresso3.7.0。其餘版本沿用08完整清單；本票無升級。

01–09完成，10–16共7張待完成；CB-12獨立pending。
