# 中油人工加油公告價與歷史價格

查證日期：2026-08-06

本筆記只採台灣中油或政府第一手資料，供 BuBu「新增加油」的建議單價功能使用。此功能應視為建議牌價，不可當成使用者實際付款金額。

## 結論

- **可依日期取得歷史價格。**台灣中油提供 `getCPCMainProdListPrice_Historical` Web Service；傳入 `prodid` 可取得一項汽、柴油的完整歷史零售牌價資料集。`1` 為 92 無鉛、`2` 為 95 無鉛、`3` 為 98 無鉛、`4` 為超級／高級柴油。[中油 Web Service 作業說明](https://vipmbr.cpc.com.tw/CPCSTN/ListPriceWebService.asmx?op=getCPCMainProdListPrice_Historical)
- 建議實作時下載各油品的歷史資料、以「生效日不晚於使用者選取日期的最新一筆」決定建議單價；油價並非每天一筆，日期應落在該筆生效日至下一筆生效日前一天的區間。
- 目前價格可使用中油的 JSON 開放資料端點：[`https://vipmbr.cpc.com.tw/opendata/sixtypeoillistprice`](https://vipmbr.cpc.com.tw/opendata/sixtypeoillistprice)。資料含產品名稱、計價單位、參考牌價與牌價生效日期；其中 92、95、98 無鉛與超級柴油的零售列是一般自用客戶在中油自營站的牌價。[台灣中油公司各項油品牌價資料集](https://data.gov.tw/dataset/166537)
- 官方資料集對此 JSON 標示為**不定期更新**，未提供服務等級承諾；另一份同樣由中油提供的主產品資料集標示每 7 日更新。中油公告可見調整後價格自週一凌晨零時生效。因此不應假設每天或每週一定更新，也應保存「牌價生效日期」而非只保存抓取時間。[各項油品牌價資料集](https://data.gov.tw/dataset/166537)；[中油公告範例](https://www.cpc.com.tw/News_Content.aspx?n=28&s=436)

## 「人工油價」的產品定義

- 中油公開的「參考牌價」是中油自營站的汽、柴油零售牌價；它適合作為本需求中的**人工服務／未套用自助折扣前公告價**。
- 它不是每位使用者的保證實付價。中油官方會員頁明載自助加油可另享每公升 0.8 元折扣，且支付方式、會員或其他活動也可能造成實付價差異。因此 App 應標示為「中油公告人工價（參考）」並允許手動覆寫；不得把它宣稱為收據實付價。[中油會員捷利卡](https://vipmbr.cpc.com.tw/vipjieli/)

## 建議的存取與失敗策略

- 優先以歷史 Web Service 取回該油品資料，解析後快取在本機；首次或快取失效時可更新。四種油品可以分開快取，避免每次表單變更日期都呼叫網路。
- 使用者切換油品或日期時，直接帶入查得的公告價與生效日。**編輯既有紀錄不得自動覆蓋已儲存的實付單價。**
- 無網路、服務錯誤或查無資料時，依已確認需求改帶入「同車輛、同油品」最近一次加油的實付單價；若仍無資料則讓單價空白，使用者手動輸入。
- 請在 UI 顯示來源狀態，例如「中油公告人工價，2026-08-03 生效」或「上次加油單價」。

## 授權與限制

- 「台灣中油公司各項油品牌價」為台灣中油提供的原始資料，標示免費、政府資料開放授權條款第 1 版。該條款允許產品／服務利用與改作，但要求明確標示資料提供機關及授權聲明；因此 App 的油價來源說明應至少標示「資料來源：台灣中油股份有限公司，台灣中油公司各項油品牌價，政府資料開放授權條款第 1 版」。[資料集](https://data.gov.tw/dataset/166537)；[政府資料開放授權條款第 1 版](https://data.gov.tw/license)
- 授權條款同時說明資料提供機關可以停止提供，且不保證資料無錯漏。因此功能必須保留人工輸入與離線 fallback，不能讓加油紀錄因查價失敗而無法儲存。[政府資料開放授權條款第 1 版](https://data.gov.tw/license)
- 公開的歷史服務是舊式 ASMX/SOAP 介面；可用 `https://vipmbr.cpc.com.tw/CPCSTN/ListPriceWebService.asmx/getCPCMainProdListPrice_Historical?prodid={1..4}` 的 HTTP GET，官方作業頁也提供 POST／SOAP 範例。官方未公布 SLA、速率限制或穩定性承諾；實作應設逾時、避免頻繁請求，並以本機快取為主。[中油 Web Service 作業說明](https://vipmbr.cpc.com.tw/CPCSTN/ListPriceWebService.asmx?op=getCPCMainProdListPrice_Historical)
