# 01 — CSV 報表 ZIP 匯出

**What to build:** 使用者可在離線裝置選擇車輛與日期範圍，將可由 Excel 正確閱讀的 CSV 報表 ZIP 儲存到自行選擇的位置；匯出只供閱讀與分析，不提供反向匯入，也不包含附件實體檔案。

**Blocked by:** None — can start immediately.

**Status:** completed

- [x] 產生包含車輛、加油、服務工單／項目、支出、里程修正、提醒、附件清單與繁體中文說明的 ZIP，並以匯出內有效的易讀參照連結資料。
- [x] CSV 符合 UTF-8 BOM、CRLF、RFC 4180 quoting、固定英文欄位、本地日期時間與臺幣／公升格式要求，且防止試算表公式注入。
- [x] 使用 App 私有暫存區完成並驗證 ZIP 後才寫入使用者目的地；取消或失敗時清理暫存與未完成輸出。
- [x] 以 unit、Repository 與 UI 測試驗證 Excel 相容文字、escaping、篩選、排序、資料隱私與不可逆匯出契約。
