// XpAuto 实际运行 API 的编辑器类型；只用于提示，不参与手机脚本执行。
/** 创建独立空选择器，可匹配所有节点
 * @xpauto
 */
declare function selector(): XpAutoSelector;
/** 文本完全匹配
 * @xpauto
 */
declare function text(value: string): XpAutoSelector;
/** 文本包含
 * @xpauto
 */
declare function textContains(value: string): XpAutoSelector;
/** 文本以指定内容开头
 * @xpauto
 */
declare function textStartsWith(value: string): XpAutoSelector;
/** 文本以指定内容结尾
 * @xpauto
 */
declare function textEndsWith(value: string): XpAutoSelector;
/** 文本匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
declare function textMatch(value: string): XpAutoSelector;
/** 完整资源 ID完全匹配
 * @xpauto
 */
declare function id(value: string): XpAutoSelector;
/** 完整资源 ID包含
 * @xpauto
 */
declare function idContains(value: string): XpAutoSelector;
/** 完整资源 ID以指定内容开头
 * @xpauto
 */
declare function idStartsWith(value: string): XpAutoSelector;
/** 完整资源 ID以指定内容结尾
 * @xpauto
 */
declare function idEndsWith(value: string): XpAutoSelector;
/** 完整资源 ID匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
declare function idMatch(value: string): XpAutoSelector;
/** 内容描述完全匹配
 * @xpauto
 */
declare function desc(value: string): XpAutoSelector;
/** 内容描述包含
 * @xpauto
 */
declare function descContains(value: string): XpAutoSelector;
/** 内容描述以指定内容开头
 * @xpauto
 */
declare function descStartsWith(value: string): XpAutoSelector;
/** 内容描述以指定内容结尾
 * @xpauto
 */
declare function descEndsWith(value: string): XpAutoSelector;
/** 内容描述匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
declare function descMatch(value: string): XpAutoSelector;
/** 完整类名完全匹配
 * @xpauto
 */
declare function clz(value: string): XpAutoSelector;
/** 完整类名包含
 * @xpauto
 */
declare function clzContains(value: string): XpAutoSelector;
/** 完整类名以指定内容开头
 * @xpauto
 */
declare function clzStartsWith(value: string): XpAutoSelector;
/** 完整类名以指定内容结尾
 * @xpauto
 */
declare function clzEndsWith(value: string): XpAutoSelector;
/** 完整类名匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
declare function clzMatch(value: string): XpAutoSelector;
/** 包名完全匹配
 * @xpauto
 */
declare function pkg(value: string): XpAutoSelector;
/** 包名包含
 * @xpauto
 */
declare function pkgContains(value: string): XpAutoSelector;
/** 包名以指定内容开头
 * @xpauto
 */
declare function pkgStartsWith(value: string): XpAutoSelector;
/** 包名以指定内容结尾
 * @xpauto
 */
declare function pkgEndsWith(value: string): XpAutoSelector;
/** 包名匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
declare function pkgMatch(value: string): XpAutoSelector;
/** 筛选是否对用户可见
 * @xpauto
 */
declare function visible(value: boolean): XpAutoSelector;
/** 筛选是否可勾选
 * @xpauto
 */
declare function checkable(value: boolean): XpAutoSelector;
/** 筛选是否已勾选
 * @xpauto
 */
declare function checked(value: boolean): XpAutoSelector;
/** 筛选是否可点击
 * @xpauto
 */
declare function clickable(value: boolean): XpAutoSelector;
/** 筛选是否可长按
 * @xpauto
 */
declare function longClickable(value: boolean): XpAutoSelector;
/** 筛选是否可滚动
 * @xpauto
 */
declare function scrollable(value: boolean): XpAutoSelector;
/** 筛选是否可获取焦点
 * @xpauto
 */
declare function focusable(value: boolean): XpAutoSelector;
/** 筛选是否已启用
 * @xpauto
 */
declare function enabled(value: boolean): XpAutoSelector;
/** 筛选是否已获取焦点
 * @xpauto
 */
declare function focused(value: boolean): XpAutoSelector;
/** 筛选是否已选择
 * @xpauto
 */
declare function selected(value: boolean): XpAutoSelector;
/** 筛选是否可编辑
 * @xpauto
 */
declare function editable(value: boolean): XpAutoSelector;
/** 筛选是否多行
 * @xpauto
 */
declare function multiLine(value: boolean): XpAutoSelector;
/** 筛选是否密码输入框
 * @xpauto
 */
declare function password(value: boolean): XpAutoSelector;
/** 筛选从窗口根节点起的深度，根为 0
 * @xpauto
 */
declare function depth(value: number): XpAutoSelector;
/** 筛选在父节点中的下标，从 0 开始
 * @xpauto
 */
declare function index(value: number): XpAutoSelector;
/** 筛选绘制顺序
 * @xpauto
 */
declare function drawingOrder(value: number): XpAutoSelector;
/** 筛选直接子节点数量
 * @xpauto
 */
declare function childCount(value: number): XpAutoSelector;
/** 筛选列表项行号，从 0 开始
 * @xpauto
 */
declare function row(value: number): XpAutoSelector;
/** 筛选列表项列号，从 0 开始
 * @xpauto
 */
declare function column(value: number): XpAutoSelector;
/** 筛选列表项占用行数
 * @xpauto
 */
declare function rowSpan(value: number): XpAutoSelector;
/** 筛选列表项占用列数
 * @xpauto
 */
declare function columnSpan(value: number): XpAutoSelector;
/** 筛选列表容器行数
 * @xpauto
 */
declare function rowCount(value: number): XpAutoSelector;
/** 筛选列表容器列数
 * @xpauto
 */
declare function columnCount(value: number): XpAutoSelector;
/** 屏幕边界完全相等
 * @xpauto
 */
declare function bounds(left: number, top: number, right: number, bottom: number): XpAutoSelector;
/** 节点边界完全位于矩形内
 * @xpauto
 */
declare function boundsInside(left: number, top: number, right: number, bottom: number): XpAutoSelector;
/** 节点边界完全包含矩形
 * @xpauto
 */
declare function boundsContains(left: number, top: number, right: number, bottom: number): XpAutoSelector;
/** 节点边界完全位于当前屏幕内
 * @xpauto
 */
declare function boundsInScreen(): XpAutoSelector;
/** XPath 1.0 查询，可直接使用节点分析器复制的路径
 * @xpauto
 */
declare function xpath(expression: string): XpAutoSelector;
/** 点击节点
 * @xpauto
 */
declare function click(selector: XpAutoSelector | XpAutoNode): boolean;
/** 点击屏幕坐标，需要无障碍权限
 * @xpauto
 */
declare function clickPoint(x: number, y: number): boolean;
/** 执行滑动，duration 为毫秒
 * @xpauto
 */
declare function swipe(x1: number, y1: number, x2: number, y2: number, duration: number): boolean;
/** 调试日志，支持 {} 占位符和多个参数
 * @xpauto
 */
declare function logd(message?: unknown, ...args: unknown[]): void;
/** 信息日志，支持 {} 占位符和多个参数
 * @xpauto
 */
declare function logi(message?: unknown, ...args: unknown[]): void;
/** 警告日志，支持 {} 占位符和多个参数
 * @xpauto
 */
declare function logw(message?: unknown, ...args: unknown[]): void;
/** 错误日志，支持 {} 占位符和多个参数
 * @xpauto
 */
declare function loge(message?: unknown, ...args: unknown[]): void;
/** 设置脚本日志最低等级及 Toast 开关；非法等级返回 false；每次运行默认 debug/true
 * @xpauto
 */
declare function setLogLevel(level: "debug" | "info" | "warn" | "error" | "off", displayToast?: boolean): boolean;
interface XpAutoToastOptions {
/** 屏幕 X 坐标，像素，自动限制在可见区域
 * @xpauto
 */
    x?: number;
/** 屏幕 Y 坐标，像素，自动限制在可见区域
 * @xpauto
 */
    y?: number;
/** 显示时长，1～60000 毫秒，默认 2000
 * @xpauto
 */
    duration?: number;
/** 文字颜色，例如 #FFFFFF 或 #AAFFFFFF
 * @xpauto
 */
    textColor?: string;
/** 宽度，像素；-1 适应可用屏宽，-2 自适应内容
 * @xpauto
 */
    width?: number;
/** 高度，像素；-1 适应可用屏高，-2 自适应内容
 * @xpauto
 */
    height?: number;
/** 是否允许拖动；默认 false，提示不拦截下层点击
 * @xpauto
 */
    draggable?: boolean;
}
/** 显示提示；extra 在有悬浮窗权限时生效，否则回退为系统短 Toast
 * @xpauto
 */
declare function toast(message: unknown, extra?: XpAutoToastOptions | null): void;
/** 显示系统短 Toast，实际时长由 Android 决定
 * @xpauto
 */
declare function toast1(message: unknown): void;
/** 显示系统长 Toast，实际时长由 Android 决定
 * @xpauto
 */
declare function toast2(message: unknown): void;
/** 兼容旧脚本，等同 logd；新代码使用 logd/logi/logw/loge
 * @deprecated 使用 logd
 * @xpauto
 */
declare function print(message?: unknown, ...args: unknown[]): void;
/** 等待指定毫秒数，可被停止操作中断
 * @xpauto
 */
declare function sleep(milliseconds: number): void;
/** 读取现有脚本共享缓存
 * @xpauto
 */
declare function getHashMapBuffer(): any;
/** 返回上一页
 * @xpauto
 */
declare function back(): boolean;
/** 返回系统桌面
 * @xpauto
 */
declare function home(): boolean;
/** 获取前台应用窗口的包名，需要无障碍服务；没有可读取窗口时返回空字符串
 * @xpauto
 */
declare function getCurrentRunningPkg(): string;
/** 获取当前窗口的 Activity 完整类名，需要无障碍服务；尚未收到有效事件或无法校验时返回 null
 * @xpauto
 */
declare function getRunningActivity(): string | null;
/** 执行系统电源操作
 * @xpauto
 */
declare function power(): void;
/** 执行菜单操作
 * @xpauto
 */
declare function menu(): boolean;
/** 打开通知栏
 * @xpauto
 */
declare function openNotification(): void;
/** 打开快捷设置
 * @xpauto
 */
declare function openQuickSettings(): boolean;
/** 打开最近任务
 * @xpauto
 */
declare function recentApps(): boolean;
/** 显示旧脚本日志面板
 * @xpauto
 */
declare function showLog(): void;
/** 隐藏旧脚本日志面板
 * @xpauto
 */
declare function hideLog(): void;
/** 全屏显示旧脚本日志面板
 * @xpauto
 */
declare function fullScreenLog(): void;
/** 清除手机首页和日志悬浮窗的最早若干行，默认 -1 清空全部；不清除 VS Code 已收到的输出
 * @xpauto
 */
declare function clearLog(lines?: number): void;

interface XpAutoSelector {
/** 输出完整筛选条件，不查询界面，如 text("搜索").enabled(true)
 * @xpauto
 */
    toString(): string;
/** 供 JSON.stringify 序列化选择器条件；嵌入对象和数组时也可使用
 * @xpauto
 */
    toJSON(key?: string): string;
/** 文本完全匹配
 * @xpauto
 */
    text(value: string): XpAutoSelector;
/** 文本包含
 * @xpauto
 */
    textContains(value: string): XpAutoSelector;
/** 文本以指定内容开头
 * @xpauto
 */
    textStartsWith(value: string): XpAutoSelector;
/** 文本以指定内容结尾
 * @xpauto
 */
    textEndsWith(value: string): XpAutoSelector;
/** 文本匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
    textMatch(value: string): XpAutoSelector;
/** 完整资源 ID完全匹配
 * @xpauto
 */
    id(value: string): XpAutoSelector;
/** 完整资源 ID包含
 * @xpauto
 */
    idContains(value: string): XpAutoSelector;
/** 完整资源 ID以指定内容开头
 * @xpauto
 */
    idStartsWith(value: string): XpAutoSelector;
/** 完整资源 ID以指定内容结尾
 * @xpauto
 */
    idEndsWith(value: string): XpAutoSelector;
/** 完整资源 ID匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
    idMatch(value: string): XpAutoSelector;
/** 内容描述完全匹配
 * @xpauto
 */
    desc(value: string): XpAutoSelector;
/** 内容描述包含
 * @xpauto
 */
    descContains(value: string): XpAutoSelector;
/** 内容描述以指定内容开头
 * @xpauto
 */
    descStartsWith(value: string): XpAutoSelector;
/** 内容描述以指定内容结尾
 * @xpauto
 */
    descEndsWith(value: string): XpAutoSelector;
/** 内容描述匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
    descMatch(value: string): XpAutoSelector;
/** 完整类名完全匹配
 * @xpauto
 */
    clz(value: string): XpAutoSelector;
/** 完整类名包含
 * @xpauto
 */
    clzContains(value: string): XpAutoSelector;
/** 完整类名以指定内容开头
 * @xpauto
 */
    clzStartsWith(value: string): XpAutoSelector;
/** 完整类名以指定内容结尾
 * @xpauto
 */
    clzEndsWith(value: string): XpAutoSelector;
/** 完整类名匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
    clzMatch(value: string): XpAutoSelector;
/** 包名完全匹配
 * @xpauto
 */
    pkg(value: string): XpAutoSelector;
/** 包名包含
 * @xpauto
 */
    pkgContains(value: string): XpAutoSelector;
/** 包名以指定内容开头
 * @xpauto
 */
    pkgStartsWith(value: string): XpAutoSelector;
/** 包名以指定内容结尾
 * @xpauto
 */
    pkgEndsWith(value: string): XpAutoSelector;
/** 包名匹配 Java 正则，按整个字符串匹配
 * @xpauto
 */
    pkgMatch(value: string): XpAutoSelector;
/** 筛选是否对用户可见
 * @xpauto
 */
    visible(value: boolean): XpAutoSelector;
/** 筛选是否可勾选
 * @xpauto
 */
    checkable(value: boolean): XpAutoSelector;
/** 筛选是否已勾选
 * @xpauto
 */
    checked(value: boolean): XpAutoSelector;
/** 筛选是否可点击
 * @xpauto
 */
    clickable(value: boolean): XpAutoSelector;
/** 筛选是否可长按
 * @xpauto
 */
    longClickable(value: boolean): XpAutoSelector;
/** 筛选是否可滚动
 * @xpauto
 */
    scrollable(value: boolean): XpAutoSelector;
/** 筛选是否可获取焦点
 * @xpauto
 */
    focusable(value: boolean): XpAutoSelector;
/** 筛选是否已启用
 * @xpauto
 */
    enabled(value: boolean): XpAutoSelector;
/** 筛选是否已获取焦点
 * @xpauto
 */
    focused(value: boolean): XpAutoSelector;
/** 筛选是否已选择
 * @xpauto
 */
    selected(value: boolean): XpAutoSelector;
/** 筛选是否可编辑
 * @xpauto
 */
    editable(value: boolean): XpAutoSelector;
/** 筛选是否多行
 * @xpauto
 */
    multiLine(value: boolean): XpAutoSelector;
/** 筛选是否密码输入框
 * @xpauto
 */
    password(value: boolean): XpAutoSelector;
/** 筛选从窗口根节点起的深度，根为 0
 * @xpauto
 */
    depth(value: number): XpAutoSelector;
/** 筛选在父节点中的下标，从 0 开始
 * @xpauto
 */
    index(value: number): XpAutoSelector;
/** 筛选绘制顺序
 * @xpauto
 */
    drawingOrder(value: number): XpAutoSelector;
/** 筛选直接子节点数量
 * @xpauto
 */
    childCount(value: number): XpAutoSelector;
/** 筛选列表项行号，从 0 开始
 * @xpauto
 */
    row(value: number): XpAutoSelector;
/** 筛选列表项列号，从 0 开始
 * @xpauto
 */
    column(value: number): XpAutoSelector;
/** 筛选列表项占用行数
 * @xpauto
 */
    rowSpan(value: number): XpAutoSelector;
/** 筛选列表项占用列数
 * @xpauto
 */
    columnSpan(value: number): XpAutoSelector;
/** 筛选列表容器行数
 * @xpauto
 */
    rowCount(value: number): XpAutoSelector;
/** 筛选列表容器列数
 * @xpauto
 */
    columnCount(value: number): XpAutoSelector;
/** 屏幕边界完全相等
 * @xpauto
 */
    bounds(left: number, top: number, right: number, bottom: number): XpAutoSelector;
/** 节点边界完全位于矩形内
 * @xpauto
 */
    boundsInside(left: number, top: number, right: number, bottom: number): XpAutoSelector;
/** 节点边界完全包含矩形
 * @xpauto
 */
    boundsContains(left: number, top: number, right: number, bottom: number): XpAutoSelector;
/** 节点边界完全位于当前屏幕内
 * @xpauto
 */
    boundsInScreen(): XpAutoSelector;
/** XPath 1.0 查询，可直接使用节点分析器复制的路径
 * @xpauto
 */
    xpath(expression: string): XpAutoSelector;
/** pkg 的兼容别名
 * @xpauto
 */
    packageName(value: string): XpAutoSelector;
/** pkgContains 的兼容别名
 * @xpauto
 */
    packageNameContains(value: string): XpAutoSelector;
/** pkgStartsWith 的兼容别名
 * @xpauto
 */
    packageNameStartsWith(value: string): XpAutoSelector;
/** pkgEndsWith 的兼容别名
 * @xpauto
 */
    packageNameEndsWith(value: string): XpAutoSelector;
/** pkgMatch 的兼容别名
 * @xpauto
 */
    packageNameMatch(value: string): XpAutoSelector;
/** clz 的兼容别名
 * @xpauto
 */
    className(value: string): XpAutoSelector;
/** clzContains 的兼容别名
 * @xpauto
 */
    classNameContains(value: string): XpAutoSelector;
/** clzStartsWith 的兼容别名
 * @xpauto
 */
    classNameStartsWith(value: string): XpAutoSelector;
/** clzEndsWith 的兼容别名
 * @xpauto
 */
    classNameEndsWith(value: string): XpAutoSelector;
/** clzMatch 的兼容别名
 * @xpauto
 */
    classNameMatch(value: string): XpAutoSelector;
/** 在已匹配节点的所有后代中追加条件，不包含节点自身
 * @xpauto
 */
    child(): XpAutoSelector;
/** 只在已匹配节点的直接子节点中追加条件
 * @xpauto
 */
    directChild(): XpAutoSelector;
/** 等待第一个节点；timeout 为毫秒，默认 0；找不到返回 null，选择器可重复查询
 * @xpauto
 */
    getOneNodeInfo(timeout?: number): XpAutoNode | null;
/** 等待全部匹配节点；timeout 为毫秒，默认 0；找到后返回本轮结果，未找到返回空数组
 * @xpauto
 */
    getNodeInfo(timeout?: number): XpAutoNode[];
/** 旧等待接口；省略 timeout 则持续等待，可停止；找不到时返回 exists() 为 false 的空节点
 * @xpauto
 */
    untilFindOne(timeout?: number): XpAutoNode;
/** 立即查找全部节点，兼容集合的 size()/get(i) 用法
 * @xpauto
 */
    find(): XpAutoCollection;
/** 查询是否有结果，不消耗选择器条件
 * @xpauto
 */
    exists(): boolean;
/** 持续等待结果，可通过停止脚本取消
 * @xpauto
 */
    waitFor(): void;
}

interface XpAutoNode {
/** 是否持有节点；需要检查页面是否仍有该节点时使用 isValid
 * @xpauto
 */
    exists(): boolean;
/** 刷新属性，节点已离开界面时返回 false
 * @xpauto
 */
    refresh(): boolean;
/** 刷新并判断节点是否仍有效
 * @xpauto
 */
    isValid(): boolean;
/** 主动释放节点；释放后不能继续操作
 * @xpauto
 */
    recycle(): void;
/** 获取中心坐标，不释放节点
 * @xpauto
 */
    getPoint(): number[] | null;
/** 无障碍点击当前节点，失败时尝试最近的可点击父节点
 * @xpauto
 */
    click(): boolean;
/** 只对当前节点执行无障碍点击
 * @xpauto
 */
    clickEx(): boolean;
/** 对当前节点执行无障碍长按
 * @xpauto
 */
    longClickEx(): boolean;
/** 手势点击可见区域中心，返回系统执行结果
 * @xpauto
 */
    clickCenter(): boolean;
/** 手势点击可见区域中的随机点
 * @xpauto
 */
    clickPoint(): boolean;
/** 在可见区域中心按住指定毫秒数
 * @xpauto
 */
    clickExactPoint(milliseconds: number): boolean;
/** 兼容无障碍长按
 * @xpauto
 */
    longClick(): boolean;
/** 在节点可见区域中心执行指定时长的长按
 * @xpauto
 */
    longClick(milliseconds: number): boolean;
/** 使用无障碍设置输入框全文，等同 setText
 * @xpauto
 */
    inputText(text: string): boolean;
/** 设置输入框全文
 * @xpauto
 */
    setText(text: string): boolean;
/** 清空输入框文字
 * @xpauto
 */
    clearText(): boolean;
/** 设置文字选区，end 不包含在选区内
 * @xpauto
 */
    setSelection(start: number, end: number): boolean;
/** 获取直接子节点数量
 * @xpauto
 */
    childCount(): number;
/** 按从 0 开始的下标获取直接子节点
 * @xpauto
 */
    child(index: number): XpAutoNode | null;
/** 获取父节点，根节点返回 null
 * @xpauto
 */
    parent(): XpAutoNode | null;
/** 获取全部直接子节点，不递归展开
 * @xpauto
 */
    allChildren(): XpAutoNode[];
/** 获取直接子节点的兼容集合
 * @xpauto
 */
    children(): XpAutoCollection;
/** 所有兄弟节点，排除自身，保持原顺序
 * @xpauto
 */
    siblings(): XpAutoNode[];
/** 位于当前节点之前的兄弟节点
 * @xpauto
 */
    previousSiblings(): XpAutoNode[];
/** 位于当前节点之后的兄弟节点
 * @xpauto
 */
    nextSiblings(): XpAutoNode[];
/** 在当前节点的后代中查询，不包含自身；等待毫秒数默认 0
 * @xpauto
 */
    getOneNodeInfo(selector: XpAutoSelector, timeout?: number): XpAutoNode | null;
/** 在当前节点的后代中查询全部结果
 * @xpauto
 */
    getNodeInfo(selector: XpAutoSelector, timeout?: number): XpAutoNode[];
/** 在当前节点的后代中立即查询第一个结果
 * @xpauto
 */
    findOne(selector: XpAutoSelector): XpAutoNode | null;
/** 在当前节点的后代中立即查询全部结果
 * @xpauto
 */
    find(selector: XpAutoSelector): XpAutoCollection;
/** 查找后代中包含指定文本或描述的节点
 * @xpauto
 */
    findByText(value: string): XpAutoCollection;
/** 读取屏幕边界
 * @xpauto
 */
    bounds(): XpAutoRect | null;
/** 读取相对父节点的边界
 * @xpauto
 */
    boundsInParent(): XpAutoRect | null;
/** 读取屏幕内的可见边界
 * @xpauto
 */
    visibleBounds(): XpAutoRect | null;
/** 读取可 JSON 序列化的属性快照
 * @xpauto
 */
    info(): XpAutoNodeInfo | null;
/** JSON.stringify 使用的节点快照
 * @xpauto
 */
    toJSON(): XpAutoNodeInfo | null;
/** 读取文本
 * @xpauto
 */
    text(): string;
/** 读取完整资源 ID
 * @xpauto
 */
    id(): string;
/** 读取内容描述
 * @xpauto
 */
    desc(): string;
/** 读取完整类名
 * @xpauto
 */
    clz(): string;
/** 读取包名
 * @xpauto
 */
    pkg(): string;
/** 读取类名，兼容 clz()
 * @xpauto
 */
    className(): string;
/** 读取包名，兼容 pkg()
 * @xpauto
 */
    packageName(): string;
/** 读取是否对用户可见
 * @xpauto
 */
    visible(): boolean;
/** 读取是否可勾选
 * @xpauto
 */
    checkable(): boolean;
/** 读取是否已勾选
 * @xpauto
 */
    checked(): boolean;
/** 读取是否可点击
 * @xpauto
 */
    clickable(): boolean;
/** 读取是否可长按
 * @xpauto
 */
    longClickable(): boolean;
/** 读取是否可滚动
 * @xpauto
 */
    scrollable(): boolean;
/** 读取是否可获取焦点
 * @xpauto
 */
    focusable(): boolean;
/** 读取是否已启用
 * @xpauto
 */
    enabled(): boolean;
/** 读取是否已获取焦点
 * @xpauto
 */
    focused(): boolean;
/** 读取是否已选择
 * @xpauto
 */
    selected(): boolean;
/** 读取是否可编辑
 * @xpauto
 */
    editable(): boolean;
/** 读取是否多行
 * @xpauto
 */
    multiLine(): boolean;
/** 读取是否密码输入框
 * @xpauto
 */
    password(): boolean;
/** 读取从窗口根节点起的深度，根为 0；未提供的列表元数据为 -1
 * @xpauto
 */
    depth(): number;
/** 读取在父节点中的下标，从 0 开始；未提供的列表元数据为 -1
 * @xpauto
 */
    index(): number;
/** 读取绘制顺序；未提供的列表元数据为 -1
 * @xpauto
 */
    drawingOrder(): number;
/** 读取列表项行号，从 0 开始；未提供的列表元数据为 -1
 * @xpauto
 */
    row(): number;
/** 读取列表项列号，从 0 开始；未提供的列表元数据为 -1
 * @xpauto
 */
    column(): number;
/** 读取列表项占用行数；未提供的列表元数据为 -1
 * @xpauto
 */
    rowSpan(): number;
/** 读取列表项占用列数；未提供的列表元数据为 -1
 * @xpauto
 */
    columnSpan(): number;
/** 读取列表容器行数；未提供的列表元数据为 -1
 * @xpauto
 */
    rowCount(): number;
/** 读取列表容器列数；未提供的列表元数据为 -1
 * @xpauto
 */
    columnCount(): number;
/** 复制选区
 * @xpauto
 */
    copy(): boolean;
/** 剪切选区
 * @xpauto
 */
    cut(): boolean;
/** 粘贴
 * @xpauto
 */
    paste(): boolean;
/** 选择节点
 * @xpauto
 */
    select(): boolean;
/** 折叠节点
 * @xpauto
 */
    collapse(): boolean;
/** 展开节点
 * @xpauto
 */
    expand(): boolean;
/** 滚动显示节点
 * @xpauto
 */
    show(): boolean;
/** 向前滚动
 * @xpauto
 */
    scrollForward(): boolean;
/** 向后滚动
 * @xpauto
 */
    scrollBackward(): boolean;
/** 向上滚动
 * @xpauto
 */
    scrollUp(): boolean;
/** 向下滚动
 * @xpauto
 */
    scrollDown(): boolean;
/** 向左滚动
 * @xpauto
 */
    scrollLeft(): boolean;
/** 向右滚动
 * @xpauto
 */
    scrollRight(): boolean;
/** 输出节点信息，建议改用 logd(node.info())
 * @xpauto
 */
    printInfo(): void;
/** 打印后代节点文字
 * @xpauto
 */
    printText(): void;
}

interface XpAutoNodeInfo {
/** 文本
 * @xpauto
 */
    text: string;
/** 完整资源 ID
 * @xpauto
 */
    id: string;
/** 内容描述
 * @xpauto
 */
    desc: string;
/** 完整类名
 * @xpauto
 */
    clz: string;
/** 包名
 * @xpauto
 */
    pkg: string;
/** 对用户可见
 * @xpauto
 */
    visible: boolean;
/** 可勾选
 * @xpauto
 */
    checkable: boolean;
/** 已勾选
 * @xpauto
 */
    checked: boolean;
/** 可点击
 * @xpauto
 */
    clickable: boolean;
/** 可长按
 * @xpauto
 */
    longClickable: boolean;
/** 可滚动
 * @xpauto
 */
    scrollable: boolean;
/** 可获取焦点
 * @xpauto
 */
    focusable: boolean;
/** 已启用
 * @xpauto
 */
    enabled: boolean;
/** 已获取焦点
 * @xpauto
 */
    focused: boolean;
/** 已选择
 * @xpauto
 */
    selected: boolean;
/** 可编辑
 * @xpauto
 */
    editable: boolean;
/** 多行
 * @xpauto
 */
    multiLine: boolean;
/** 密码输入框
 * @xpauto
 */
    password: boolean;
/** 从窗口根节点起的深度，根为 0
 * @xpauto
 */
    depth: number;
/** 在父节点中的下标，从 0 开始
 * @xpauto
 */
    index: number;
/** 绘制顺序
 * @xpauto
 */
    drawingOrder: number;
/** 直接子节点数量
 * @xpauto
 */
    childCount: number;
/** 列表项行号，从 0 开始
 * @xpauto
 */
    row: number;
/** 列表项列号，从 0 开始
 * @xpauto
 */
    column: number;
/** 列表项占用行数
 * @xpauto
 */
    rowSpan: number;
/** 列表项占用列数
 * @xpauto
 */
    columnSpan: number;
/** 列表容器行数
 * @xpauto
 */
    rowCount: number;
/** 列表容器列数
 * @xpauto
 */
    columnCount: number;
/** 所属窗口 ID
 * @xpauto
 */
    windowId: number;
/** 屏幕边界
 * @xpauto
 */
    bounds: XpAutoRect;
/** 可见边界
 * @xpauto
 */
    visibleBounds: XpAutoRect;
}

interface XpAutoCollection {
/** 获取集合长度
 * @xpauto
 */
    size(): number;
/** 按从 0 开始的下标读取副本；越界返回空节点
 * @xpauto
 */
    get(index: number): XpAutoNode;
/** 转换为可按下标访问的节点数组
 * @xpauto
 */
    toArray(): XpAutoNode[];
/** 主动释放集合中的节点
 * @xpauto
 */
    recycle(): void;
/** 打印集合节点
 * @xpauto
 */
    foreachPrint(): void;
}

interface XpAutoRect {
/** 屏幕边界坐标
 * @xpauto
 */
    left: number;
/** 屏幕边界坐标
 * @xpauto
 */
    top: number;
/** 屏幕边界坐标
 * @xpauto
 */
    right: number;
/** 屏幕边界坐标
 * @xpauto
 */
    bottom: number;
}

interface XpAutoShell {
/** 添加自定义 su 可执行文件路径
 * @xpauto
 */
    addSuBin(path: string): boolean;
/** 请求 Root 授权并验证实际 UID 为 0
 * @xpauto
 */
    su(timeoutMs?: number): boolean;
/** 执行 Shell 命令并返回标准输出；错误和退出码见 getLastResult()
 * @xpauto
 */
    execCommand(command: string, root?: boolean, timeoutMs?: number): string;
/** 使用 Root 执行命令
 * @xpauto
 */
    ROOT(command: string, timeoutMs?: number): string;
/** 使用 Root 执行命令，与 ROOT 相同
 * @xpauto
 */
    sudo(command: string, timeoutMs?: number): string;
/** 使用 Root 安装手机上的 APK
 * @xpauto
 */
    installApp(apkPath: string): boolean;
/** 使用 Root 卸载应用
 * @xpauto
 */
    uninstallApp(packageName: string): boolean;
/** 使用 Root 停止应用
 * @xpauto
 */
    stopApp(packageName: string): boolean;
/** 读取退出码、标准输出、错误和超时状态
 * @xpauto
 */
    getLastResult(): XpAutoShellResult;
}

interface XpAutoShellResult {
/** 进程退出码
 * @xpauto
 */
    code: number;
/** 标准输出
 * @xpauto
 */
    result: string;
/** 标准错误
 * @xpauto
 */
    error: string;
/** 是否超时
 * @xpauto
 */
    timedOut: boolean;
/** 是否取消
 * @xpauto
 */
    cancelled: boolean;
/** 是否达到输出容量限制
 * @xpauto
 */
    truncated: boolean;
}
/** Shell 与 Root 操作
 * @xpauto
 */
declare const shell: XpAutoShell;

/**
 * 读取 Android 系统属性，默认普通权限；不存在、为空或执行失败时返回 defaultValue（默认空字符串）。
 * 保留属性值中的空白。root 默认 false，timeoutMs 默认 60000；失败详情见 shell.getLastResult()。
 * @xpauto
 */
declare function getprop(name: string, defaultValue?: string, root?: boolean, timeoutMs?: number): string;
/**
 * 写入 Android 系统属性，返回命令是否成功；value 转为字符串，空字符串表示清空值。
 * root 默认 true，复用 shell.addSuBin()；timeoutMs 默认 60000。只读属性或系统策略可能拒绝写入。
 * @xpauto
 */
declare function setprop(name: string, value: string | number | boolean, root?: boolean, timeoutMs?: number): boolean;

interface XpAutoHttpOptions {
/** 请求地址，支持 HTTP/HTTPS
 * @xpauto
 */
    url: string;
/** 请求方法，默认 GET
 * @xpauto
 */
    method?: string;
/** 总请求超时，毫秒，默认 30000
 * @xpauto
 */
    timeout?: number;
/** 请求头
 * @xpauto
 */
    header?: Record<string, string | number>;
/** 手动发送的 Cookie，不会跨请求保存
 * @xpauto
 */
    cookie?: Record<string, string | number>;
/** GET 查询参数或 POST 表单字段
 * @xpauto
 */
    data?: Record<string, unknown> | string;
/** 原始请求体，JSON 请先序列化
 * @xpauto
 */
    requestBody?: string;
/** 上传文件，filePath 是手机路径
 * @xpauto
 */
    file?: { key: string; filePath: string; fileName?: string; contentType?: string }[];
/** HTTP 代理地址；false 表示直连
 * @xpauto
 */
    proxy?: { host: string; port: number } | false;
/** 自动跳转，默认 true
 * @xpauto
 */
    followRedirects?: boolean;
/** User-Agent 请求头
 * @xpauto
 */
    userAgent?: string;
/** Referer 请求头
 * @xpauto
 */
    referrer?: string;
/** 将任意类型解码成文本，默认 true
 * @xpauto
 */
    ignoreContentType?: boolean;
/** 返回 HTTP 4xx/5xx 的内容，默认 false
 * @xpauto
 */
    ignoreHttpErrors?: boolean;
/** 正文上限，默认 8 MiB，最大 32 MiB；超限返回 null
 * @xpauto
 */
    maxBodySize?: number;
/** 强制响应编码，默认使用响应头或 UTF-8
 * @xpauto
 */
    responseCharset?: string;
}
interface XpAutoHttpResponse {
/** 响应头，保留服务端字段名
 * @xpauto
 */
    header: Record<string, string>;
/** 本次响应的 Cookie
 * @xpauto
 */
    cookie: Record<string, string>;
/** HTTP 状态码
 * @xpauto
 */
    statusCode: number;
/** HTTP 状态说明
 * @xpauto
 */
    statusMessage: string;
/** 解码字符集
 * @xpauto
 */
    charset: string;
/** 响应 Content-Type
 * @xpauto
 */
    contentType: string;
/** 响应正文
 * @xpauto
 */
    body: string;
}
interface XpAutoWebSocket {
/** 同步连接，超时单位毫秒，默认 10000
 * @xpauto
 */
    connect(timeout?: number): boolean;
/** 关闭连接，停止心跳和自动重连
 * @xpauto
 */
    close(): void;
/** 重置后可再次连接
 * @xpauto
 */
    reset(): boolean;
/** 是否已连接
 * @xpauto
 */
    isConnected(): boolean;
/** 是否关闭
 * @xpauto
 */
    isClosed(): boolean;
/** 发送文本，单条最大 1 MiB
 * @xpauto
 */
    sendText(text: string): boolean;
/** 发送字节数组，单条最大 1 MiB
 * @xpauto
 */
    sendBinary(bytes: Uint8Array | number[]): boolean;
/** 连接打开回调，在 connect、sleep、pollEvents 时派发
 * @xpauto
 */
    onOpen(callback: ((socket: XpAutoWebSocket, code: number, message: string) => void) | null): void;
/** 文本回调
 * @xpauto
 */
    onText(callback: ((socket: XpAutoWebSocket, text: string) => void) | null): void;
/** 二进制回调，返回 Uint8Array
 * @xpauto
 */
    onBinary(callback: ((socket: XpAutoWebSocket, bytes: Uint8Array) => void) | null): void;
/** 连接关闭回调
 * @xpauto
 */
    onClose(callback: ((socket: XpAutoWebSocket, code: number, reason: string) => void) | null): void;
/** 错误回调
 * @xpauto
 */
    onError(callback: ((socket: XpAutoWebSocket, message: string) => void) | null): void;
/** 断线后自动重连；在 sleep/pollEvents 中尝试，至少间隔 3 秒
 * @xpauto
 */
    setAutoReconnect(enabled: boolean): void;
/** 应用心跳，周期至少 50 毫秒；回调返回 null 时不发送
 * @xpauto
 */
    startHeartBeat(binary: (() => Uint8Array | number[] | null) | null, text: (() => string | null) | null, period: number, cancelOld?: boolean): void;
/** 停止所有应用心跳
 * @xpauto
 */
    stopHeartBeat(): void;
/** type=1 调用超时，秒；连接前设置
 * @xpauto
 */
    setCallTimeout(seconds: number): void;
/** type=1 读超时，秒；0 不限；连接前设置
 * @xpauto
 */
    setReadTimeout(seconds: number): void;
/** type=1 写超时，秒；连接前设置
 * @xpauto
 */
    setWriteTimeout(seconds: number): void;
/** type=1 Ping 间隔，秒；0 关闭；连接前设置
 * @xpauto
 */
    setPingInterval(seconds: number): void;
/** type=2 存活检测，秒；0 关闭
 * @xpauto
 */
    setConnectionLostTimeout(seconds: number): void;
}
interface XpAutoHttp {
/** 万能请求，失败返回 null 并写日志，停止脚本会取消请求
 * @xpauto
 */
    request(options: XpAutoHttpOptions): XpAutoHttpResponse | null;
/** 万能请求别名，返回普通 JS 对象
 * @xpauto
 */
    requestEx(options: XpAutoHttpOptions): XpAutoHttpResponse | null;
/** GET 正文，默认超时 30000 毫秒
 * @xpauto
 */
    httpGetDefault(url: string, timeout?: number, headers?: Record<string, string | number> | null): string | null;
/** GET，自动编码查询参数
 * @xpauto
 */
    httpGet(url: string, params?: Record<string, unknown> | string | null, timeout?: number, headers?: Record<string, string | number> | null): string | null;
/** POST 表单，files 使用手机文件路径
 * @xpauto
 */
    httpPost(url: string, params?: Record<string, unknown> | string | null, files?: Record<string, string> | null, timeout?: number, headers?: Record<string, string | number> | null): string | null;
/** 标准 multipart POST，参数与 httpPost 相同
 * @xpauto
 */
    httpPostEx(url: string, params?: Record<string, unknown> | string | null, files?: Record<string, string> | null, timeout?: number, headers?: Record<string, string | number> | null): string | null;
/** 发送 JSON 对象或 JSON 字符串
 * @xpauto
 */
    postJSON(url: string, json: unknown, timeout?: number, headers?: Record<string, string | number> | null): string | null;
/** 下载到手机，支持有验证标记的断点续传，完整后替换目标
 * @xpauto
 */
    downloadFile(url: string, path: string, timeout?: number, headers?: Record<string, string | number> | null): boolean;
/** 下载文件，默认超时 30 秒
 * @xpauto
 */
    downloadFileDefault(url: string, path: string, headers?: Record<string, string | number> | null): boolean;
/** 创建 WebSocket，1=OkHttp，2=Java-WebSocket
 * @xpauto
 */
    newWebsocket(url: string, headers?: Record<string, string | number> | null, type?: 1 | 2): XpAutoWebSocket;
/** 派发网络事件、心跳和重连；sleep 自动调用
 * @xpauto
 */
    pollEvents(): void;
/** 兼容原有 GET，失败返回 null
 * @xpauto
 */
    get(url: string): string | null;
/** 兼容原有 POST JSON
 * @xpauto
 */
    post(url: string, json: unknown): string | null;
}
/** HTTP、下载和 WebSocket 工具
 * @xpauto
 */
declare const http: XpAutoHttp;

interface XpAutoUtils {
/** 按包名打开应用
 * @xpauto
 */
    openApp(packageName: string): boolean;
/** 按应用名打开应用
 * @xpauto
 */
    openAppByName(appName: string): boolean;
/** 生成启动应用的命令
 * @xpauto
 */
    getStartAppCmd(packageName: string): string;
}
/** utils 工具对象
 * @xpauto
 */
declare const utils: XpAutoUtils;

interface XpAutoFile {
/** 判断文件存在
 * @xpauto
 */
    exists(path: string): boolean;
/** 读取文本文件
 * @xpauto
 */
    readFile(path: string): string;
/** 写入文本，参数先内容后路径
 * @xpauto
 */
    writeFile(content: string, fileName: string): boolean;
/** 写入文本，参数先路径后内容
 * @xpauto
 */
    writeToTxt(fileName: string, content: string): boolean;
/** 追加文本
 * @xpauto
 */
    appendFile(filePath: string, content: string): boolean;
/** 追加一行文本
 * @xpauto
 */
    appendLine(line: string, filePath: string): boolean;
/** 删除指定文件
 * @xpauto
 */
    deleteFile(path: string): boolean;
/** 创建目录
 * @xpauto
 */
    mkdirs(path: string): void;
/** 复制文件
 * @xpauto
 */
    copy(source: string, destination: string): boolean;
/** 移动文件
 * @xpauto
 */
    moveFile(source: string, destinationFolder: string): boolean;
/** 重命名文件
 * @xpauto
 */
    renameFile(source: string, destination: string): boolean;
/** 创建文件
 * @xpauto
 */
    create(path: string): boolean;
/** 读取 APK 内置资源
 * @xpauto
 */
    readAssets(path: string): string;
}
/** file 工具对象
 * @xpauto
 */
declare const file: XpAutoFile;

/** 当前脚本的悬浮窗；独立于应用悬浮球和日志窗。
 * @xpauto
 */
declare const floaty: XpAutoFloaty;
interface XpAutoFloaty {
    /** 打开悬浮窗授权页并等待；默认 10000ms，0 表示打开后不等待，最大 120000ms。
     * @xpauto
     */
    requestFloatViewPermission(timeoutMs?: number): boolean;
    /** 显示 XML 内容或手机上的 XML 文件；坐标为像素，同标签替换旧窗，失败返回 null。
     * @xpauto
     */
    showFloatXml(tag: string | number, xml: string, x: number, y: number): XpAutoFloatView | null;
    /** 显示当前会话的视图句柄或 Java 侧提供的原生 View；视图不能仍属于其他窗口。
     * @xpauto
     */
    showFloatView(tag: string | number, view: XpAutoFloatView, x: number, y: number): XpAutoFloatView | null;
    /** 更新窗口 X 坐标，单位像素。
     * @xpauto
     */
    updateX(tag: string | number, x: number): boolean;
    /** 更新窗口 Y 坐标，单位像素。
     * @xpauto
     */
    updateY(tag: string | number, y: number): boolean;
    /** 更新窗口宽高；像素值，-1 为填满屏幕，-2 为按内容测量。
     * @xpauto
     */
    updateSize(tag: string | number, width: number, height: number): boolean;
    /** 关闭指定窗口；不存在时返回 false。
     * @xpauto
     */
    close(tag: string | number): boolean;
    /** 设置是否接收触摸；false 时窗口透明度调整为 0.7。
     * @xpauto
     */
    touchable(tag: string | number, touchable: boolean): boolean;
    /** 设置能否获得输入焦点；输入框需先启用此选项。
     * @xpauto
     */
    focusable(tag: string | number, focusable: boolean): boolean;
    /** 读取窗口 X 坐标，失败返回 -1。
     * @xpauto
     */
    getX(tag: string | number): number;
    /** 读取窗口 Y 坐标，失败返回 -1。
     * @xpauto
     */
    getY(tag: string | number): number;
    /** 读取窗口测量宽度（像素），失败返回 -1。
     * @xpauto
     */
    getWidth(tag: string | number): number;
    /** 读取窗口测量高度（像素），失败返回 -1。
     * @xpauto
     */
    getHeight(tag: string | number): number;
}

/** 原生视图的脚本句柄；以下操作自动切换到 Android 主线程。 */
interface XpAutoFloatView {
    /** 根据 XML tag 或 id 查找控件。
     * @xpauto
     */
    findViewWithTag(tag: string): XpAutoFloatView | null;
    /** 获取视图标签。
     * @xpauto
     */
    getTag(): string | null;
    /** 设置视图标签。
     * @xpauto
     */
    setTag(tag: string): void;
    /** 获取文本或输入框内容。
     * @xpauto
     */
    getText(): string;
    /** 更新文本，在主线程同步完成。
     * @xpauto
     */
    setText(text: string): void;
    /** 设置文字颜色，支持 #RRGGBB、#AARRGGBB 或 Android 整数颜色值。
     * @xpauto
     */
    setTextColor(color: string | number): void;
    /** 设置文字大小，单位 sp。
     * @xpauto
     */
    setTextSize(sp: number): void;
    /** 设置背景颜色。
     * @xpauto
     */
    setBackgroundColor(color: string | number): void;
    /** 启用或禁用控件。
     * @xpauto
     */
    setEnabled(enabled: boolean): void;
    /** 读取启用状态。
     * @xpauto
     */
    isEnabled(): boolean;
    /** 设置可见性：0 显示、4 隐藏但占位、8 隐藏且不占位。
     * @xpauto
     */
    setVisibility(visibility: 0 | 4 | 8): void;
    /** 读取可见性。
     * @xpauto
     */
    getVisibility(): number;
    /** 请求焦点；窗口需先调用 focusable(tag, true)。
     * @xpauto
     */
    requestFocus(): boolean;
    /** 取消当前控件焦点。
     * @xpauto
     */
    clearFocus(): void;
    /** 设置 CheckBox / Switch 的勾选状态。
     * @xpauto
     */
    setChecked(checked: boolean): void;
    /** 读取 CheckBox / Switch 的勾选状态。
     * @xpauto
     */
    isChecked(): boolean;
    /** 读取实际布局宽度，首帧前可能为 0；窗口尺寸优先使用 floaty.getWidth(tag)。
     * @xpauto
     */
    getWidth(): number;
    /** 读取实际布局高度，首帧前可能为 0。
     * @xpauto
     */
    getHeight(): number;
    /** 按 Android MeasureSpec 测量；measure(0, 0) 为不约束测量。
     * @xpauto
     */
    measure(widthSpec: number, heightSpec: number): void;
    /** 读取测量宽度。
     * @xpauto
     */
    getMeasuredWidth(): number;
    /** 读取测量高度。
     * @xpauto
     */
    getMeasuredHeight(): number;
    /** 读取直接子控件数量。
     * @xpauto
     */
    getChildCount(): number;
    /** 获取下标从 0 开始的子控件。
     * @xpauto
     */
    getChildAt(index: number): XpAutoFloatView | null;
}

// 线程、工具、数据库与快捷操作（所有时间单位均为毫秒）。
type XpAutoThreadId = number;
type XpAutoTarget = XpAutoSelector | XpAutoNode;
/** 延迟执行回调，保留闭包；sleep 时允许其他线程运行
 * @xpauto
 */
declare function setTimeout(callback: () => void, timeout?: number): XpAutoThreadId;
/** 取消延迟任务
 * @xpauto
 */
declare function cancelTimeout(threadId: XpAutoThreadId): boolean;
/** 按固定间隔重复回调，上一轮完成后再等待 interval
 * @xpauto
 */
declare function setInterval(callback: () => void, interval: number): XpAutoThreadId;
/** 取消周期任务
 * @xpauto
 */
declare function cancelInterval(threadId: XpAutoThreadId): boolean;
/** 每 50ms 检查条件，严格返回 true 时成功
 * @xpauto
 */
declare function execSync(condition: () => unknown, timeout: number): boolean;
/** 闭区间内随机安全整数
 * @xpauto
 */
declare function random(min: number, max: number): number;
/** 直接执行节点点击动作
 * @xpauto
 */
declare function clickEx(target: XpAutoTarget): boolean;
/** 直接执行节点长按动作
 * @xpauto
 */
declare function longClickEx(target: XpAutoTarget): boolean;
/** 随机匹配节点内随机坐标点击
 * @xpauto
 */
declare function clickRandom(target: XpAutoSelector): boolean;
/** 随机匹配节点执行点击动作
 * @xpauto
 */
declare function clickRandomEx(target: XpAutoSelector): boolean;
/** 区域内随机坐标点击
 * @xpauto
 */
declare function clickRandomRect(rect: XpAutoRect | number[]): boolean;
/** 点击区域中心
 * @xpauto
 */
declare function clickCenter(rect: XpAutoRect | number[]): boolean;
/** 点击文本完全匹配的节点
 * @xpauto
 */
declare function clickText(text: string): boolean;
/** 长按节点中心 600ms
 * @xpauto
 */
declare function longClick(target: XpAutoTarget): boolean;
/** 长按坐标 600ms
 * @xpauto
 */
declare function longClickPoint(x: number, y: number): boolean;
/** 按住坐标指定时长 1～60000ms
 * @xpauto
 */
declare function press(x: number, y: number, delay: number): boolean;
/** 最多三指按事件延迟执行，默认超时 10000ms
 * @xpauto
 */
declare function multiTouch(touch1: XpAutoTouchEvent[], touch2?: XpAutoTouchEvent[] | null, touch3?: XpAutoTouchEvent[] | null, timeout?: number): boolean;
/** 释放当前续接的触点
 * @xpauto
 */
declare function resetAllTouchState(): boolean;
/** 无指针向前滚动
 * @xpauto
 */
declare function scrollForward(target: XpAutoTarget): boolean;
/** 无指针向后滚动
 * @xpauto
 */
declare function scrollBackward(target: XpAutoTarget): boolean;
/** 从节点中心滑动，另支持旧五坐标参数形式
 * @xpauto
 */
declare function swipe(target: XpAutoTarget, endX: number, endY: number, duration: number): boolean;
/** 坐标滑动，等待系统手势完成
 * @xpauto
 */
declare function swipeToPoint(startX: number, startY: number, endX: number, endY: number, duration: number): boolean;
/** 根据节点支持的滚动动作判断边界；缺失或非滚动节点返回 false
 * @xpauto
 */
declare function isScrollEnd(direction: "UP" | "DOWN" | "LEFT" | "RIGHT", target: XpAutoTarget): boolean;
/** 先按住 600ms，再拖动 duration 毫秒
 * @xpauto
 */
declare function drag(startX: number, startY: number, endX: number, endY: number, duration: number): boolean;
/** 拖动一个节点到另一个节点中心
 * @xpauto
 */
declare function dragTo(target: XpAutoTarget, destination: XpAutoTarget, duration: number): boolean;
/** 拖动节点到坐标
 * @xpauto
 */
declare function dragToPoint(target: XpAutoTarget, endX: number, endY: number, duration: number): boolean;
/** 通过节点设置文本，无需输入法
 * @xpauto
 */
declare function inputText(target: XpAutoTarget, content: string): boolean;
/** 写入剪贴板并执行节点粘贴
 * @xpauto
 */
declare function pasteText(target: XpAutoTarget, content: string): boolean;
/** 清空节点文本
 * @xpauto
 */
declare function clearTextField(target: XpAutoTarget): boolean;
/** 获取输入焦点
 * @xpauto
 */
declare function setFocus(target: XpAutoTarget): boolean;
/** 系统当前是否选择 XpAuto 脚本输入法
 * @xpauto
 */
declare function currentIsOurIme(): boolean;
/** XpAuto 输入法视图是否显示
 * @xpauto
 */
declare function imeInputViewShown(): boolean;
/** 由 XpAuto 输入法提交文本；null 使用当前焦点
 * @xpauto
 */
declare function imeInputText(target: XpAutoTarget | null, content: string): boolean;
/** 输入法发送 KeyEvent 或 1000～1005 编辑动作
 * @xpauto
 */
declare function imeInputKeyCode(target: XpAutoTarget | null, code: number): boolean;
/** 最近无障碍事件的 Unix 毫秒时间
 * @xpauto
 */
declare function lastNodeEventTime(): number;
/** 当前是否有匹配节点
 * @xpauto
 */
declare function has(target: XpAutoSelector): boolean;
/** 等待 Activity 完整类名出现
 * @xpauto
 */
declare function waitExistActivity(activity: string, timeout: number): boolean;
/** 等待匹配节点出现
 * @xpauto
 */
declare function waitExistNode(target: XpAutoSelector, timeout: number): boolean;
/** 读取所有匹配节点文本
 * @xpauto
 */
declare function getText(target: XpAutoSelector): string[];
/** 获取节点数组；调用者负责 recycle
 * @xpauto
 */
declare function getNodeInfo(target: XpAutoSelector, timeout?: number): XpAutoNode[];
/** 获取首个节点；未找到返回 null
 * @xpauto
 */
declare function getOneNodeInfo(target: XpAutoSelector, timeout?: number): XpAutoNode | null;
/** 读取匹配节点的指定属性
 * @xpauto
 */
declare function getNodeAttrs(target: XpAutoSelector, attribute: keyof XpAutoNodeInfo | "className" | "packageName"): unknown[];
/** 设置抓取模式、可见性和遍历顺序
 * @xpauto
 */
declare function setFetchNodeMode(mode: 1 | 2, fetchInvisible?: boolean, fetchNotImportant?: boolean, algorithm?: "nsf" | "bsf" | "dsf"): boolean;
/** 设置节点深度上限 0～80 和静态遍历方式
 * @xpauto
 */
declare function setNodeDumpParam(data: {dumpMethod?: 1 | 2 | 3; maxDepth?: number}): boolean;
/** 逗号分隔屏蔽属性，空串恢复
 * @xpauto
 */
declare function setBlockNodeAttr(attributes: string): boolean;
/** 增加无障碍抓取标志，0 刷新节点
 * @xpauto
 */
declare function addNodeFlag(flag: number): boolean;
/** 移除抓取标志，0 刷新节点
 * @xpauto
 */
declare function removeNodeFlag(flag: number): boolean;
/** 输出节点 XML
 * @xpauto
 */
declare function dumpXml(): string;
/** 冻结当前节点树，直到 releaseNode 或脚本结束
 * @xpauto
 */
declare function lockNode(): boolean;
/** 释放冻结快照
 * @xpauto
 */
declare function releaseNode(): void;
/** 请求 Manifest 已声明的普通动态权限；默认超时 30000ms
 * @xpauto
 */
declare function requestRuntimePermission(permissions: string[], timeout?: number): boolean;
/** 显式通过 Root 设置 PROJECT_MEDIA app-op，不创建截图令牌
 * @xpauto
 */
declare function tryGetProjectionPermission(): boolean;
/** 首次通过 Root 授予 WRITE_SECURE_SETTINGS 并启用服务，后续调用无需 Root
 * @xpauto
 */
declare function tryGetAccStartupPermission(): boolean;
/** 单指续接：Down
 * @xpauto
 */
declare function touchDown(x: number, y: number): boolean;
/** 无障碍模式忽略 pointerId，多指使用 multiTouch
 * @xpauto
 */
declare function touchDownEx(x: number, y: number, pointerId?: number): boolean;
/** 单指续接：Move
 * @xpauto
 */
declare function touchMove(x: number, y: number): boolean;
/** 无障碍模式忽略 pointerId，多指使用 multiTouch
 * @xpauto
 */
declare function touchMoveEx(x: number, y: number, pointerId?: number): boolean;
/** 单指续接：Up
 * @xpauto
 */
declare function touchUp(x: number, y: number): boolean;
/** 无障碍模式忽略 pointerId，多指使用 multiTouch
 * @xpauto
 */
declare function touchUpEx(x: number, y: number, pointerId?: number): boolean;
interface XpAutoTouchEvent { action: 0 | 1 | 2; x: number; y: number; pointer?: number; delay?: number; }
interface XpAutoThread {
/** 启动共享闭包线程，最多同时 32 个
 * @xpauto
 */
    execAsync(callback: () => void): XpAutoThreadId;
/** 轮询条件直到 true 或超时
 * @xpauto
 */
    execSync(condition: () => unknown, timeout: number): boolean;
/** 协作取消线程；sleep 和阻塞等待会响应
 * @xpauto
 */
    cancelThread(id: XpAutoThreadId): boolean;
/** 已取消、已结束或不存在均返回 true
 * @xpauto
 */
    isCancelled(id: XpAutoThreadId): boolean;
/** 取消所有子线程，保留主脚本
 * @xpauto
 */
    stopAll(): void;
}
/** 线程管理
 * @xpauto
 */
declare const thread: XpAutoThread;
interface XpAutoSqlite {
/** 连接或创建数据库，相对路径位于应用 databases 目录
 * @xpauto
 */
    connectOrCreateDb(path: string): boolean;
/** 创建自增 id 和 TEXT 字段，复杂结构使用 execSql
 * @xpauto
 */
    createTable(table: string, columns: string[]): boolean;
/** 通过参数绑定插入一行
 * @xpauto
 */
    insert(table: string, values: Record<string, unknown>): boolean;
/** 更新匹配行，省略条件更新所有行
 * @xpauto
 */
    update(table: string, values: Record<string, unknown>, whereSql?: string): boolean;
/** 查询返回普通 JS 数组，失败返回 null
 * @xpauto
 */
    query(sql: string): Record<string, unknown>[] | null;
/** 执行删除 SQL
 * @xpauto
 */
    delete(sql: string): boolean;
/** 执行建表、修改或事务 SQL
 * @xpauto
 */
    execSql(sql: string): boolean;
/** 删除表
 * @xpauto
 */
    dropTable(table: string): boolean;
/** 关闭并删除当前数据库
 * @xpauto
 */
    dropDatabase(): boolean;
/** 关闭连接
 * @xpauto
 */
    close(): boolean;
/** 最近操作错误，成功后清除
 * @xpauto
 */
    getErrorMsg(): string | null;
}
/** SQLite 数据库
 * @xpauto
 */
declare const sqlite: XpAutoSqlite;
interface XpAutoEncodeDecoder {
/** AES 加密到 Base64，密钥规则见 utils-api.md
 * @xpauto
 */
    aesEncrypt(data: string, password: string): string | null;
/** AES 从 Base64 解密，密钥规则见 utils-api.md
 * @xpauto
 */
    aesDecrypt(data: string, password: string): string | null;
/** DES 加密到 Base64，密钥规则见 utils-api.md
 * @xpauto
 */
    desEncrypt(data: string, password: string): string | null;
/** DES 从 Base64 解密，密钥规则见 utils-api.md
 * @xpauto
 */
    desDecrypt(data: string, password: string): string | null;
/** DES3 加密到 Base64，密钥规则见 utils-api.md
 * @xpauto
 */
    des3Encrypt(data: string, password: string): string | null;
/** DES3 从 Base64 解密，密钥规则见 utils-api.md
 * @xpauto
 */
    des3Decrypt(data: string, password: string): string | null;
/** 当前线程最近加解密错误
 * @xpauto
 */
    getErrorMsg(): string | null;
}
/** 对称加解密
 * @xpauto
 */
declare const encodeDecoder: XpAutoEncodeDecoder;
interface XpAutoActivityOptions { action?: string; uri?: string; type?: string; pkg?: string; className?: string; flag?: number; [extra: string]: unknown; }
interface XpAutoBitmap {
/** 图像宽度
 * @xpauto
 */
    getWidth(): number;
/** 图像高度
 * @xpauto
 */
    getHeight(): number;
/** 释放图像内存
 * @xpauto
 */
    recycle(): void;
}
interface XpAutoUtils {
/** 按 Intent 参数启动 Activity
 * @xpauto
 */
    openActivity(options: XpAutoActivityOptions): boolean;
/** 生成经过 shell 转义的启动命令
 * @xpauto
 */
    getStartActivityCmd(options: XpAutoActivityOptions): string;
/** 按 Android action 打开界面
 * @xpauto
 */
    openIntentAction(action: string): boolean;
/** 扫描图片到图库，文件应位于共享媒体目录
 * @xpauto
 */
    insertImageToAlbum(path: string): boolean;
/** 扫描视频到图库
 * @xpauto
 */
    insertVideoToAlbum(path: string): boolean;
/** 压缩文件或目录，可选 AES 密码
 * @xpauto
 */
    zip(zipFile: string, password: string | null, files: string[]): boolean;
/** 解压 UTF-8 ZIP
 * @xpauto
 */
    unzip(zipFile: string, password: string | null, destDir: string): boolean;
/** 指定 ZIP 文件名编码，例如 GBK
 * @xpauto
 */
    unzipWithEncode(zipFile: string, password: string | null, destDir: string, fileNameEncode: string): boolean;
/** 读取 ZIP 内 UTF-8 文本，最多 16MB
 * @xpauto
 */
    readFileInZip(zipFile: string, password: string | null, entry: string): string | null;
/** 生成二维码 Bitmap，尺寸 32～4096
 * @xpauto
 */
    createQRCode(content: string, width: number, height: number, logo?: XpAutoBitmap | null): XpAutoBitmap | null;
/** 识别二维码
 * @xpauto
 */
    decodeQRCode(bitmap: XpAutoBitmap): string | null;
/** 保存 Bitmap 为 PNG
 * @xpauto
 */
    saveBitmap(bitmap: XpAutoBitmap, path: string): boolean;
/** 读取整型配置，缺失返回 0
 * @xpauto
 */
    readConfigInt(json: Record<string, unknown>, key: string): number;
/** 读取字符串配置，缺失返回空串
 * @xpauto
 */
    readJSONString(json: Record<string, unknown>, key: string): string;
/** 是否为 null 或 undefined
 * @xpauto
 */
    isObjectNull(value: unknown): boolean;
/** 是否非 null 且非 undefined
 * @xpauto
 */
    isObjectNotNull(value: unknown): boolean;
/** 严格等于 true
 * @xpauto
 */
    isTrue(value: unknown): boolean;
/** 按 0～100 百分比随机返回 true
 * @xpauto
 */
    getRatio(ratio: number): boolean;
/** 闭区间随机安全整数
 * @xpauto
 */
    getRangeInt(min: number, max: number): number;
/** 读取文件的 MD5
 * @xpauto
 */
    fileMd5(path: string): string | null;
/** UTF-8 字符串的 MD5
 * @xpauto
 */
    dataMd5(data: unknown): string | null;
/** 1～15 位随机整数
 * @xpauto
 */
    randomInt(length: number): number;
/** 随机大小写英文字母和数字
 * @xpauto
 */
    randomCharNumber(length: number): string;
/** 读取 APK 包名
 * @xpauto
 */
    getApkPkgName(path: string): string;
/** 检查可见的已安装应用
 * @xpauto
 */
    isAppExist(packageName: string): boolean;
/** 读取版本号，未找到为 0
 * @xpauto
 */
    getAppVersionCode(packageName: string): number;
/** 读取版本名，未找到为空串
 * @xpauto
 */
    getAppVersionName(packageName: string): string;
/** 写入剪贴板
 * @xpauto
 */
    setClipboardText(text: string): boolean;
/** 读取剪贴板，受 Android 前台/默认输入法限制
 * @xpauto
 */
    getClipboardText(): string;
/** 异步播放本地音频
 * @xpauto
 */
    playMp3(path: string, loop?: boolean): boolean;
/** 等待音频结束，可取消；循环播放需 stopMp3
 * @xpauto
 */
    playMp3WaitEnd(path: string, loop?: boolean): boolean;
/** 停止并释放播放器
 * @xpauto
 */
    stopMp3(): boolean;
}
interface XpAutoNode {
/** 获取输入焦点
 * @xpauto
 */
    setFocus(): boolean;
/** 检查节点是否暴露指定系统动作
 * @xpauto
 */
    hasAction(action: number): boolean;
}


/** 截图对象，可独立回收；脚本结束时自动回收。
 * @xpauto
 */
interface XpAutoImage extends XpAutoBitmap {
/** 是否已经回收
 * @xpauto
 */
    isRecycled(): boolean;
/** 可读的图片尺寸与回收状态
 * @xpauto
 */
    toString(): string;
}
interface XpAutoBitmap {
/** 是否已经回收
 * @xpauto
 */
    isRecycled(): boolean;
}
interface XpAutoImageApi {
/** 申请屏幕共享。timeout 默认 10000ms；0/1 使用系统授权，2 代理模式暂不支持并返回 false。系统弹窗需用户确认。
 * @xpauto
 */
    requestScreenCapture(timeout?: number, type?: 0 | 1 | 2): boolean;
/** 释放投影和前台通知；再次截图需要重新申请，不回收已经返回的图片。
 * @xpauto
 */
    releaseScreenCapture(): void;
/** 区域截图，坐标为起点和终点；缺省、负数或空区域表示全屏。retryNumber 默认 3，范围 0～20。
 * @xpauto
 */
    captureScreen(retryNumber?: number, x?: number, y?: number, ex?: number, ey?: number): XpAutoImage | null;
/** 截取全屏；失败返回 null，通过 getErrorMsg 查看原因。
 * @xpauto
 */
    captureFullScreen(): XpAutoImage | null;
/** 全屏截图扩展；当前没有代理模式，与 captureFullScreen 相同。
 * @xpauto
 */
    captureFullScreenEx(): XpAutoImage | null;
/** 没有代理服务时回退到已授权的全屏截图。
 * @xpauto
 */
    captureScreenshot(): XpAutoImage | null;
/** 没有代理服务时回退到已授权的全屏截图。
 * @xpauto
 */
    captureScreenSurface(): XpAutoImage | null;
/** 返回 Android Bitmap。坐标为起点/终点；format 和 q 只校验兼容参数，授权模式直接返回无损像素。
 * @xpauto
 */
    captureScreenBitmap(format?: 'png' | 'jpg' | 'jpeg' | 'webp', x?: number, y?: number, ex?: number, ey?: number, q?: number): XpAutoBitmap | null;
/** 全屏截图并返回独立的 Android Bitmap。
 * @xpauto
 */
    captureScreenBitmapEx(): XpAutoBitmap | null;
/** 截取区域并保存为 PNG，内部自动回收临时图片；路径可为应用可写绝对路径，或相对于 getSaveDir 的路径。
 * @xpauto
 */
    captureToFile(retryNumber: number, x: number, y: number, ex: number, ey: number, path: string): boolean;
/** XpAuto 便利重载：截取全屏并保存 PNG。
 * @xpauto
 */
    captureToFile(path: string): boolean;
/** 使用 Root 执行系统 screencap，不依赖 MediaProjection；root 默认为 true，false 因无代理服务而失败。
 * @xpauto
 */
    screencapImage(root?: boolean): XpAutoImage | null;
/** Root 截图并返回 Bitmap，兼容 shell.addSuBin 自定义 su 路径。
 * @xpauto
 */
    screencapBitmap(root?: boolean): XpAutoBitmap | null;
/** 获取图片宽度，失败返回 0。
 * @xpauto
 */
    getWidth(img: XpAutoImage | XpAutoBitmap): number;
/** 获取图片高度，失败返回 0。
 * @xpauto
 */
    getHeight(img: XpAutoImage | XpAutoBitmap): number;
/** 读取手机图片，相对路径位于 getSaveDir 下。
 * @xpauto
 */
    readImage(path: string): XpAutoImage | null;
/** 读取手机图片为 Bitmap。
 * @xpauto
 */
    readBitmap(path: string): XpAutoBitmap | null;
/** 保存为 PNG；原文件存在时原子覆盖。
 * @xpauto
 */
    saveTo(img: XpAutoImage | XpAutoBitmap, path: string): boolean;
/** 编码保存图片；质量 1～100，PNG 忽略质量。
 * @xpauto
 */
    saveBitmap(bitmap: XpAutoBitmap, format: 'png' | 'jpg' | 'jpeg' | 'webp', q: number, path: string): boolean;
/** 图片编码为无换行 Base64，默认 PNG、质量 100。
 * @xpauto
 */
    toBase64Format(img: XpAutoImage, format?: 'png' | 'jpg' | 'jpeg' | 'webp', q?: number): string | null;
/** Bitmap 编码为无换行 Base64。
 * @xpauto
 */
    bitmapBase64(bitmap: XpAutoBitmap, format?: 'png' | 'jpg' | 'jpeg' | 'webp', q?: number): string | null;
/** 裁剪图片，ex/ey 是终点坐标，返回独立图片。
 * @xpauto
 */
    clip(img: XpAutoImage, x: number, y: number, ex: number, ey: number): XpAutoImage | null;
/** 裁剪 Bitmap，w/h 是宽高，返回独立图片。
 * @xpauto
 */
    clipBitmap(bitmap: XpAutoBitmap, x: number, y: number, w: number, h: number): XpAutoBitmap | null;
/** 转换并复制为 Bitmap，两份图片可独立回收。
 * @xpauto
 */
    imageToBitmap(img: XpAutoImage): XpAutoBitmap | null;
/** 转换并复制为图片对象，两份图片可独立回收。
 * @xpauto
 */
    bitmapToImage(bitmap: XpAutoBitmap): XpAutoImage | null;
/** 获取像素的有符号 32 位 ARGB 值。
 * @xpauto
 */
    pixel(img: XpAutoImage | XpAutoBitmap, x: number, y: number): number;
/** 获取图片像素的 ARGB 值。
 * @xpauto
 */
    pixelInImage(img: XpAutoImage, x: number, y: number): number;
/** 获取 Bitmap 像素的 ARGB 值。
 * @xpauto
 */
    getPixelBitmap(bitmap: XpAutoBitmap, x: number, y: number): number;
/** null 或已经回收的图片返回 true。
 * @xpauto
 */
    isRecycled(img: XpAutoImage | XpAutoBitmap | null): boolean;
/** 回收图片；可以重复调用。
 * @xpauto
 */
    recycle(img: XpAutoImage | XpAutoBitmap | null): void;
/** 回收本会话由 image 模块创建的全部图片。
 * @xpauto
 */
    recycleAllImage(): boolean;
/** 获取当前脚本线程最近一次图片操作的错误；成功操作清空错误。
 * @xpauto
 */
    getErrorMsg(): string;
/** XpAuto 扩展：图片相对路径的基准目录，位于应用私有 files/images 中。
 * @xpauto
 */
    getSaveDir(): string;
}
/** 截图与图片存取接口，普通截图需要先申请系统屏幕共享。
 * @xpauto
 */
declare const image: XpAutoImageApi;
