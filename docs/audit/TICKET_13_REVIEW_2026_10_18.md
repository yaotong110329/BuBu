# Ticket 13 雙軸審查 — 2026-10-18

依 code-review 技能分別由兩個 agent 審查；固定參考 e12379551c1cf9b48fb17ddc18f424ad31504700，HEAD無新增commit。採已接受的 scoped snapshot/worktree：RestoreSafetyFixture.kt 與 OfflineRestoreRepository.kt 對照 outputs/release-1.1.1-audit/ticket-13-before/；新測試檔完整審查。PrivateAttachmentStore及OfflineBackupRepository與票前副本相同。最終共六個來源，主機 controller 另作執行與歸屬證據。

權威為 AGENTS.md、Spec.md、CONTEXT.md、TASKS.md、所有ADR／skills；規格為 .scratch/release-1-1-1-hardening/issues/13-restore-process-restart-safety.md。沒有把 coroutine cancellation 當成程序終止，也沒有宣稱裝置斷電驗證。

## Standards

首輪0硬規／1判斷性 Duplicated Code：新restart archive比較與fixture方法重複。抽出同package的BackupArchiveAssertions.kt，原fixture入口委派、新probe直接呼叫，沒有廣泛重構先前測試。

最終0硬規／0未解smell。新增暫存回收集中在既有data repository；canonical私有目錄、UUID檔名、一般檔案、24h門檻限制刪除，失敗記log後續重試。跨instance的active集合直接處理實際重現的並行問題，建立前登記、finally刪除後才解除，沒有新框架或未使用抽象。

## Spec

首輪1項P2部分完成：四個資料完整性實驗原本只驗managed attachments，未涵蓋被kill留下的cacheDir/restore/.UUID.bubu副本。先擴充真正kill流程，取得「下一次公開preview仍未回收過期副本」actual red，才加最小期限／歸屬清理；再以獨立並行public restore／第二instance preview取得誤刪使用中副本的actual red，才加active ownership保護。

複審0未解，原P2已解決。Probe記錄真正中斷副本，重開先驗完整內容，再驗超過24h回收、新檔及非管理檔保留；下一次成功讀入來源後才清理，來源若是私有cache URI也先完成複製。四個終止點、先觀察再復原、完整快照／附件及明確手動復原均無新缺口或scope creep。沒有默默重做DB覆蓋或刪除recovery。

審查讀取了actual red→green證據，但不代替最終品質閘門；四次最終重播、完整JVM、普通connected與Lint另記錄實際結果。
