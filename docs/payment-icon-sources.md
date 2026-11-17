# 支付工具圖示與預設資料

## 素材調查與採用方式

2026-11-17 搜尋 SVG Repo、Wikimedia Commons 與品牌官方網站。未確認有涵蓋這十三種工具（十二張共用／獨立圖示）、可直接使用的 CC0 logo 圖示。

- Commons 的 [Suica logo](https://commons.wikimedia.org/wiki/File:Suica_logo.svg) 與 [ICOCA logo](https://commons.wikimedia.org/wiki/File:ICOCA_logo.svg) 標示為 PD-textlogo（公有領域文字標誌），並非 CC0。
- [Carte suica.jpg](https://commons.wikimedia.org/wiki/File:Carte_suica.jpg) 為 CC0 卡片照片，但不是適合小尺寸 UI 的圖示，未採用。
- 依使用者後續指示，最終改以實際品牌素材進行裁切、影像處理及 Android 資源轉換。沒有使用最初的字型拼貼重繪版本。

下表記錄實際來源；**不將原品牌標誌或格式轉換的結果宣稱為 CC0**。品牌名稱、圖像與商標權利仍屬各權利人。格式轉換不會改變原素材的授權狀態。

| 工具／資源 | 實際來源 | 處理 |
| --- | --- | --- |
| 全支付 `ic_pxpay_plus.png` | [全支付官方圖示](https://marketing.pxpayplus.com/pxplus_marketing_page/pxplus_logo.png) | 裁去外圍空白，抽取原字標與笑臉輪廓 |
| 街口支付 `ic_jkopay.png` | [街口官方 SVG](https://img.jkos.com.tw/official_jkos_image/logo-red-square.svg) | 擷取「街」字與定位符號，移除紅底及外框 |
| LINE Pay `ic_linepay.png` | [LINE Pay 官方 Google Play 頁面](https://play.google.com/store/apps/details?id=com.linepaytw.upay) | 使用官方 app 圖示的 LINE／Pay 字標；[品牌規範](https://developers-pay.line.me/logo-guideline) |
| iPASS MONEY `ic_ipass_money.png` | [一卡通官方 app 圖示](https://www.i-pass.com.tw/CKPackage/images/iPMicon.png) | 抽取 iPASS MONEY 字樣，與實體一卡通分開 |
| 台灣 Pay `ic_taiwanpay.png` | [玉山銀行支付服務](https://www.esunbank.com/zh-tw/personal/credit-card/payment/e_pay)、[圖檔](https://www.esunbank.com/zh-tw/-/media/New-ESUNBANK/Credit-Card/payment/e_pay/taiwanpay-logo.png) | 擷取中央識別圖形並轉成單色輪廓 |
| icash Pay `ic_icash_pay.png` | [icash Pay 官方 Google Play 頁面](https://play.google.com/store/apps/details?id=tw.com.icash.a.icashpay) | 抽取 Google Play app 圖示中央上下排字標，移除底色與外框 |
| 台新 Pay `ic_taishin_pay.png` | [台新銀行官方圖示](https://mkp.taishinbank.com.tw/TsCms/marketing/expose/WM_20250331113039795/images/icon-3.png)、[產品頁](https://mkp.taishinbank.com.tw/s/2022/taishinpay/index.html) | 裁去外圍空白，保留原字標 |
| Suica `ic_suica.png` | [JR 東日本官方 Google Play 頁面](https://play.google.com/store/apps/details?id=com.mobilesuica.msb.android) | 使用官方 Suica app 識別圖形 |
| ICOCA `ic_icoca.png` | [JR 西日本官方 SVG](https://www.jr-odekake.net/icoca/mobileicoca/assets/img/logo_icoca.svg) | 光柵化原始向量，保留原字形並轉成單色輪廓 |
| 悠遊卡 `ic_easycard.png` | [悠遊卡官方 SVG](https://www.easycard.com.tw/styles/images/common/logo.svg) | 擷取原始四色識別圖形 |
| 悠遊付（與悠遊卡共用 `ic_easycard.png`） | 同上，依使用者指定共用 | 保留電子支付分類及既有 ID 10，與電子票證的悠遊卡分開 |
| icash `ic_icash.png` | [官方共用 icash 字標](https://www.icashpay.com.tw/img/brand_icashPay.png) | 擷取 icash 品牌字樣輪廓，與 icash Pay 分開 |
| 一卡通 `ic_ipass.png` | [一卡通官方品牌圖檔](https://static01-ipass.cdn.hinet.net/ipassweb/9TH/img/iPASS_Brand_Logo_Horizonta_Color.png) | 擷取原始三色識別圖形並轉成單色輪廓 |

最終輸出位於 `app/src/main/res/drawable-nodpi`，為**黑色輪廓、透明背景的單色 PNG**，最長邊 384 px。保留實際 logo 的核心字形／符號，移除 app icon 底色、白框、圓角底板及外圍留白；不將整張 app icon 再嵌入現有圖示。全支付保留字標與笑臉，街口保留「街」與定位符號。LINE Pay 裁出中央字標，Suica 保留完整企鵝輪廓與手持裝置，只移除周圍底色與空白；icash Pay 採 Google Play 版本的上下排核心字標。悠遊卡與悠遊付共用相同輪廓。

SVG 以 resvg 光柵化；PNG 以 Pillow 裁切與抽取透明輪廓。`PaymentIconMapper.getPainter` 同時支援品牌 PNG 與既有向量圖示，所有圖示沿用 app 原有的單色染色方式。選擇圖示時帶入品牌主色作為預設色，使用者之後仍可自行改色。選取狀態沿用白色圖示，不使用多色 logo 或額外底板。

[最終圖示預覽](payment-icons-preview.png) 包含核心圖形與 app 原本圓形容器的一般／選取狀態。

## 預設 ID 與版本升級

透過使用者手機的無線 ADB 讀取現有資料，確認標準分類為 ID 1–4、街口 8、LINE Pay 9、悠遊付 10、Apple Pay 11、Google Pay 12、Pi 15、全支付 16、台灣 Pay 17、銀行轉帳 18，且 ICOCA 已存在於 ID 24。

新安裝沿用既有通用預設項目的 ID。19–23 保留，不分配給新預設項目；不把私人信用卡名稱或設定加入預設資料。

| 項目 | 優先 ID | 分類 |
| --- | ---: | --- |
| ICOCA | 24 | 電子票證 |
| 電子票證分類 | 25 | 最上層 |
| iPASS MONEY | 26 | 電子支付 |
| icash Pay | 27 | 電子支付 |
| 台新 Pay | 28 | 電子支付 |
| 悠遊卡 | 29 | 電子票證 |
| icash | 30 | 電子票證 |
| 一卡通 | 31 | 電子票證 |
| Suica 西瓜卡 | 32 | 電子票證 |

資料庫版本 13→14 為一次性資料升級，沒有新增或刪除資料欄位。已有同名／對應圖示的工具沿用原 ID；ICOCA 24 只更新圖示與父分類，保留交易關聯、名稱、顏色、排序與額度設定。優先 ID 若被其他安裝的自訂工具占用，改由 SQLite 配發新 ID，禁止覆寫既有項目。升級不恢復使用者曾刪除的 Samsung Pay、PayPal 等舊選項。
