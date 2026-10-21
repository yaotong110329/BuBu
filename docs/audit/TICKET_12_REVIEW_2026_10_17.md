# Ticket 12 review — 2026-10-17

固定 audit ref：e12379551c1cf9b48fb17ddc18f424ad31504700；本次 worktree 未建立 commit。以開始前 ticket-12-before/ 快照隔離已接受01–11及16，再審 ticket-12-scoped.diff：3份 production、5份新案例／fixture、1份取消同步 helper，共9份來源。Spec來源為本票 issue、Spec.md、CONTEXT.md及既有ADR；沒有把其他票的變更算入本票。

## Standards

獨立代理 ticket11_final_standards：硬規違規0。第一輪提出兩項判斷性 Duplicated Code：stage／restore 的取消清理，以及六處新案例的取消同步樣板；另提出兩個新方法縮排不齊。

Review階段已抽出 cleanupUndeliveredFiles，保留各呼叫端的檔案歸屬、NonCancellable + IO、suppressed error及原始錯誤。六處樣板抽至 RestoreCancellationGate.kt，僅接受公開 RestoreRepository／候選檔與 gate；故障位置及所有公開結果斷言仍各自保留。restoreManagedBytes與readSnapshot縮排對齊。

複審：硬規違規0、可行動smells0；未發現此次整理引入新問題。

## Spec

獨立代理 ticket11_final_spec：0項findings，整理後複審維持0。取消補償採NonCancellable；commit結果不明時先查實際附件引用，查詢失敗保留檔案，避免破壞完整新快照；舊檔清理工作與替換交易一起寫入既有queue。測試使用真正SQLite FULL、部分寫入／ENOSPC、取消及再次補償失敗，透過公開archive與附件bytes比較。

另核對stageAttachments的回傳：現有唯一caller由withCandidateFile(IO)進入，補償繼承相同IO；其內withContext(IO)沒有dispatcher變更，未找到回傳map因切換dispatcher丟失的可達路徑，故不為未來假設caller擴大重構。

空間預檢屬餘裕估算而非保留容量；4×incoming JSON、2×DB／工作空間及16MiB固定餘裕是heuristic，不能宣稱保證所有archive或並行磁碟佔用。主線驗證文件明載限制。

兩軸最終未解：Standards0；Spec0。代理均唯讀、未執行Gradle／ADB；此review不能代替主線最終build、JVM、裝置、lint證據。

## 最後 lint 讀值修正複核

首次最終lint出現一項新增UsableSpace提示（0 error／127 warnings）。constructor預設讀值改為StatFs(path).availableBytes，仍只計當下freeblocks，不把StorageManager可回收快取當已分配容量；未加Suppress、分配空間或改變注入seam／預算。兩軸唯讀複核均維持0未解；主線另重跑完整閘門。
