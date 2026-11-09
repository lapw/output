# FloatBot — Android 自動化浮動條（構思驗證框架）

Rhino(JS) 腳本引擎 + 無障礙服務懸浮條。啟動後螢幕上浮動條：▶ Play / ⏸ Pause / ⏹ Stop / 📷 截圖 / 📜 選腳本。

## 建置（本機無 Android SDK，需在裝有 Android Studio 的機器開）
1. 用 Android Studio（Koala 以上）開啟此資料夾，會自動抓 Gradle 8.7 + SDK 34。
2. 直接 Run 到實機或模擬器（minSdk 24，Android 7+）。

## 首次使用流程
1. 開 App → 按「前往開啟無障礙服務」→ 找到 FloatBot → 允許。
2. 回 App → 按「啟動懸浮控制條」（服務啟動時浮條自動出現）。
3. 📜 選腳本 → 選 demo_script.js → ▶ 執行。

## 腳本放哪
- 內建：`app/src/main/assets/scripts/`（打包進 APK）
- 熱載入：`adb push 你的.js /sdcard/Android/data/com.example.floatbot/files/scripts/`（優先於 assets，免重裝 APK）

## Bridge API（腳本內的全域物件 bot）
| 函式 | 說明 |
|---|---|
| bot.click(x, y) | 座標點擊 |
| bot.swipe(x1,y1,x2,y2,ms) | 滑動，ms 必填 |
| bot.tapText("文字") | 依畫面文字找控件點擊，回傳 bool |
| bot.existsText("文字") | 畫面是否存在該文字 |
| bot.sleep(ms) | 等待；⏸/⏹ 在每次 bot.* 呼叫時生效 |
| bot.toast / bot.log | 通知 / logcat（tag=FloatBot） |
| bot.screenW() / bot.screenH() | 螢幕尺寸 |

## 設計決策（為什麼這樣搭）
- 腳本引擎 = Rhino 1.7.15：純 Java、~1MB、AutoX.js 同款，embedding 最簡單。
- 浮動條 = TYPE_ACCESSIBILITY_OVERLAY：掛無障礙層，**免**「顯示在其他應用程式上層」權限。
- 截圖 = AccessibilityService.takeScreenshot()（Android 11+）：**免** MediaProjection 彈窗；Android 10 以下要另接（暫未做）。
- 點擊/滑動 = dispatchGesture（Android 7+，免 root）。
- 執行緒 = 單一 executor 執行緒；pause 用 gate（每次 bot.* 呼叫檢查）、stop 用 thread interrupt。

## 下一步候選（改這個框架時的清單）
- [ ] 📜 改為掃描 assets + 外部目錄全部 .js 並顯示清單（已做 assets）
- [ ] 腳本內截圖 API（callback 版 takeScreenshot 已備好，接上即用）
- [ ] 找圖/找色（OpenCV 或純 Bitmap 比對）
- [ ] 主頁顯示執行狀態/日誌頁
- [ ] Android 10 以下 MediaProjection 截圖備援
- [ ] 腳本編輯器頁（先用手機上任何編輯器 + 熱載入目錄即可）
