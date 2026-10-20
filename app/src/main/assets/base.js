//-------------------- pre set -------------------//
const __xpautoTask = new engines();
const getHashMapBuffer = () => {
    let buffer = __xpautoTask._getHashMapBuffer();
    if(buffer != null) {
        return buffer;
    }
    return null;
}
// -------------------- 全局快捷事件-------------------//
// 每个入口创建自己的选择器，避免多个变量的筛选条件互相影响。
const selector = () => __xpautoNodeCompat.wrap(__xpautoTask.newSelector());
const text = value => selector().text(value);
const textContains = value => selector().textContains(value);
const textStartsWith = value => selector().textStartsWith(value);
const textEndsWith = value => selector().textEndsWith(value);
const textMatch = value => selector().textMatch(value);
const id = value => selector().id(value);
const idContains = value => selector().idContains(value);
const idStartsWith = value => selector().idStartsWith(value);
const idEndsWith = value => selector().idEndsWith(value);
const idMatch = value => selector().idMatch(value);
const desc = value => selector().desc(value);
const descContains = value => selector().descContains(value);
const descStartsWith = value => selector().descStartsWith(value);
const descEndsWith = value => selector().descEndsWith(value);
const descMatch = value => selector().descMatch(value);
const clz = value => selector().clz(value);
const clzContains = value => selector().clzContains(value);
const clzStartsWith = value => selector().clzStartsWith(value);
const clzEndsWith = value => selector().clzEndsWith(value);
const clzMatch = value => selector().clzMatch(value);
const pkg = value => selector().pkg(value);
const pkgContains = value => selector().pkgContains(value);
const pkgStartsWith = value => selector().pkgStartsWith(value);
const pkgEndsWith = value => selector().pkgEndsWith(value);
const pkgMatch = value => selector().pkgMatch(value);
const visible = value => selector().visible(value);
const checked = value => selector().checked(value);
const checkable = value => selector().checkable(value);
const clickable = value => selector().clickable(value);
const longClickable = value => selector().longClickable(value);
const scrollable = value => selector().scrollable(value);
const focusable = value => selector().focusable(value);
const enabled = value => selector().enabled(value);
const focused = value => selector().focused(value);
const selected = value => selector().selected(value);
const editable = value => selector().editable(value);
const multiLine = value => selector().multiLine(value);
const password = value => selector().password(value);
const depth = value => selector().depth(value);
const index = value => selector().index(value);
const drawingOrder = value => selector().drawingOrder(value);
const childCount = value => selector().childCount(value);
const row = value => selector().row(value);
const column = value => selector().column(value);
const rowSpan = value => selector().rowSpan(value);
const columnSpan = value => selector().columnSpan(value);
const rowCount = value => selector().rowCount(value);
const columnCount = value => selector().columnCount(value);
const xpath = value => selector().xpath(value);
const bounds = (left, top, right, bottom) => selector().bounds(left, top, right, bottom);
const boundsInside = (left, top, right, bottom) => selector().boundsInside(left, top, right, bottom);
const boundsContains = (left, top, right, bottom) => selector().boundsContains(left, top, right, bottom);
const boundsInScreen = () => selector().boundsInScreen();

//  ---- 点击事件 ----//
const click = (node) => __xpautoTask._clickNode(__xpautoUnwrapNode(node));
const clickPoint = (x, y) => __xpautoGesture.press(x, y, 80);
//  ---- 滑动事件 ----//
const swipe = (...args) => args.length === 4 ? __xpautoMoveNode(args[0],args[1],args[2],args[3],false) : swipeToPoint(...args);

// -------------------- 日志输出类 -------------------//
// 旧脚本继续可用，新代码统一使用 logd/logi/logw/loge。
const print = logd;
const showLog = () => showLogWindow();
const hideLog = () => closeLogWindow();
const fullScreenLog = () => __xpautoTask._fullScreenLog();
// -------------------- 按键模拟 -------------------// 参考autojs
const back = () => __xpautoTask._back();
const home = () => __xpautoTask._home();
const power = () => __xpautoTask._power();
const menu = () => __xpautoTask._menu();
const openNotification = () => __xpautoTask._openNotification();
const openQuickSettings = () => __xpautoTask._openQuickSettings();
const recentApps       = () => __xpautoTask._recentApps();
// 包名读取应用窗口，Activity 使用经过校验的窗口事件；都不要求 Root。
const getCurrentRunningPkg = () => __xpautoTask._getCurrentRunningPkg();
const getRunningActivity = () => __xpautoTask._getRunningActivity();
// -------------------- 时间类 -------------------//
const sleep = (ms) => __xpautoNetworkSleep(ms);

// -------------------- 网络请求类 -------------------//

// -------------------- 工具类 -------------------//

//-------------------- pre end -------------------//
