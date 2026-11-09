// ============ FloatBot 示範腳本 demo_script.js ============
// 執行環境：Rhino (ES5+部分ES6)。全域物件 bot 由 App 提供。
//
// Bridge API 一覽：
//   bot.click(x, y)                    座標點擊
//   bot.swipe(x1, y1, x2, y2, ms)      滑動（ms 必填，建議 300）
//   bot.tapText("文字")                 依畫面文字找控件並點擊，回傳 true/false
//   bot.existsText("文字")              畫面上是否存在該文字
//   bot.sleep(ms)                      等待（可被 ⏸/⏹ 控制）
//   bot.toast("訊息") / bot.log("訊息")
//   bot.screenW() / bot.screenH()      螢幕寬高
//
// 注意：pause/stop 是在每次 bot.* 呼叫時生效，迴圈請保持每次迭代都有 bot 呼叫。

function swipe5(x1, y1, x2, y2, ms) {
    bot.swipe(x1, y1, x2, y2, ms || 300);
}

var W = bot.screenW();
var H = bot.screenH();
bot.toast("示範腳本開始 " + W + "x" + H);
bot.log("示範腳本開始");

// 示範 1：等 2 秒後從螢幕下方滑到上方（模擬捲動）
bot.sleep(2000);
swipe5(W / 2, H * 0.8, W / 2, H * 0.3, 400);

// 示範 2：每秒檢查畫面上有沒有「繼續」按鈕，有就點，最多找 10 次
var found = false;
for (var i = 0; i < 10; i++) {
    bot.sleep(1000);
    if (bot.existsText("繼續")) {
        bot.tapText("繼續");
        found = true;
        break;
    }
}
bot.toast(found ? "已點擊「繼續」" : "沒找到「繼續」");

// 示範 3：簡單迴圈（按 ⏹ 可隨時中斷、⏸ 可暫停）
for (var n = 0; n < 5; n++) {
    bot.sleep(500);
    bot.log("第 " + (n + 1) + " 次迭代");
}

bot.toast("示範腳本結束");
