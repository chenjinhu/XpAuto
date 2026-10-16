# 节点选择器与节点操作

本模块参考 [EasyClick 选择器与节点 API](https://ieasyclick.com/docs/funcs/global/selector-node)，基于 Android 无障碍服务实现。需要先在手机开启 XpAuto 无障碍服务；不需要 Root。新增及调整的代码使用中文注释。

## 查询写法

```javascript
const submit = text("确定").enabled(true).visible(true);
const node = submit.getOneNodeInfo(1500);
if (node) {
    logi("节点信息：{}", node.info());
    node.clickEx();
    node.recycle();
}
logd("仍然存在：{}", submit.exists()); // 条件不会被查询清空
```

每次调用全局入口都产生独立选择器。链式条件采用 AND 关系，不同变量不会互相污染；同一对象追加条件会修改自身，`child()` 与 `directChild()` 创建独立的级联副本。重复追加同一属性也按 AND 处理，例如 `text("甲").text("乙")` 没有结果。

## 打印选择器

选择器输出完整的条件链，`print`、四级日志、字符串拼接和模板字符串都可以直接使用：

```javascript
const search = text("搜索");
print(search);                          // [DEBUG] text("搜索")
logi("选择器：{}", search);              // [INFO] 选择器：text("搜索")
logd(textMatch(".*Play.*").visible(true)); // [DEBUG] textMatch(".*Play.*").visible(true)
logd("条件=" + search);                 // [DEBUG] 条件=text("搜索")
logd({ target: search });                // [DEBUG] {"target":"text(\"搜索\")"}
```

`toString()` 返回可读的条件链；`toJSON()` 返回同样的字符串，所以 `JSON.stringify(search)` 是合法 JSON 字符串，嵌入数组或对象时也不会丢失条件。空选择器输出 `selector()`；级联保留 `child()` / `directChild()`；字符串中的引号、换行和正则反斜杠会转义。兼容别名统一输出 `pkg` / `clz`，`boundsInScreen()` 输出创建条件时的实际 `boundsInside` 矩形，每段 XPath 输出在该段普通条件之前。

打印只读取已有条件，不访问无障碍服务、不查找节点、不等待，也不改变选择器。因此 `text("搜索")` 即使没有找到节点，仍然是一个真值对象；`if (!search)` 不能判断节点不存在。需要判断是否找到时使用 `search.exists()` 或查询结果：

```javascript
const node = text("搜索").getOneNodeInfo(1000);
if (node) {
    click(node);
    node.recycle();
} else {
    logw("未找到搜索节点");
}
```

## 筛选条件

下表中的方法都可作为全局入口或链式方法；`selector()` 创建不带条件的选择器。

| 分类 | 方法 | 语义 |
| --- | --- | --- |
| 文本属性 | `text`、`id`、`desc`、`clz`、`pkg` | 完全相等；空字符串也是有效条件 |
| 包含匹配 | 上述名称加 `Contains` | 如 `textContains("设置")` |
| 前后缀 | 上述名称加 `StartsWith` / `EndsWith` | 如 `idEndsWith("/submit")` |
| 正则 | 上述名称加 `Match` | Java 正则全文匹配，如 `textMatch(".*设置.*")` |
| 布尔状态 | `visible`、`checkable`、`checked`、`clickable`、`longClickable`、`scrollable`、`focusable`、`enabled`、`focused`、`selected`、`editable`、`multiLine`、`password` | 参数必须表示所需的 true/false 状态 |
| 层级与位置 | `depth`、`index`、`drawingOrder`、`childCount` | 非负整数；根深度、兄弟下标从 0 起 |
| 列表项 | `row`、`column`、`rowSpan`、`columnSpan` | 依赖应用提供的 CollectionItemInfo |
| 列表容器 | `rowCount`、`columnCount` | 依赖应用提供的 CollectionInfo |
| 边界 | `bounds(l,t,r,b)` | 与节点屏幕矩形完全相同 |
| 矩形内 | `boundsInside(l,t,r,b)` | 节点完整位于给定矩形内 |
| 包含矩形 | `boundsContains(l,t,r,b)` | 节点矩形完整包住给定矩形 |
| 屏幕内 | `boundsInScreen()` | 完整位于当前屏幕范围内 |
| XPath | `xpath(expression)` | XPath 1.0 返回节点集合 |

`id` 使用完整资源名，`clz` 使用完整类名。例如 `id("com.example:id/submit")`、`clz("android.widget.Button")`。需要模糊匹配时显式使用 Contains 等方法。`packageName*` 是 `pkg*` 的链式兼容别名；`className*` 是 `clz*` 的链式兼容别名。

列表属性不等于布局坐标：控件没有提供列表元数据时，读取值为 -1，不会被 `row(0)` 等条件匹配。`checked` 是勾选状态，`selected` 是选择状态，二者分别读取。

## 结果与等待

| 方法 | 结果 |
| --- | --- |
| `getOneNodeInfo(timeout=0)` | 首个节点，未找到为 null |
| `getNodeInfo(timeout=0)` | 节点数组，未找到为 []，支持 length、下标、for...of 和 map |
| `find()` | 立即查询，返回兼容 `UiCollection`，使用 size()/get(i) |
| `exists()` | 立即查询是否有结果，不消耗条件 |
| `untilFindOne(timeout?)` | 兼容旧接口，超时返回 exists() 为 false 的空节点；省略 timeout 持续等待 |
| `waitFor()` | 持续等待出现，可停止脚本取消 |

时间单位都是毫秒。0 表示只查一轮，正数最多等待到出现结果；多节点查询在首次发现结果时返回该轮结果，不等待后续所有动态节点加载完成。查询默认搜索系统暴露的全部无障碍窗口；普通查询优先活动窗口，窗口内采用深度优先顺序。等待期间每约 50 ms 重试，Android Binder 单次调用耗时可能使实际返回稍晚于 timeout。

停止脚本或停止调试控制台中的等待会中断查询，不再执行等待之后的语句。普通查询找到首项即可结束；每轮设有 5000 节点、80 层、4 秒的上限，超出时抛出清晰错误而非返回不完整结果。

## 级联与局部范围

```javascript
// 容器中任意层级的后代，不包含容器自身。
const checks = clz("android.widget.ScrollView").child()
    .clz("android.widget.CheckBox").checked(false).getNodeInfo(500);

// 严格限定直接子节点。
const direct = desc("工具栏").directChild().clickable(true).getNodeInfo();

const panel = desc("设置面板").getOneNodeInfo();
if (panel) {
    const save = panel.getOneNodeInfo(text("保存"), 500);
    const inputs = panel.getNodeInfo(editable(true), 0);
}
```

级联最多 32 段；每段可以组合多个属性。节点上的 `getOneNodeInfo(selector, timeout)` / `getNodeInfo(selector, timeout)` 只查该节点后代，不会越过范围，也不会匹配自身。`findOne(selector)` 与 `find(selector)` 保留为立即查询的便利接口。

## XPath 与节点分析器

```javascript
xpath("//node[@text='确定' and @enabled='true']").getOneNodeInfo(500);
xpath("//node[contains(@text,'商品')]").getNodeInfo();
xpath("//node[@text='标题']/following-sibling::node").getNodeInfo();
```

内存树结构为 `/hierarchy/window[@id='窗口ID']/node/...`，节点属性包括 id/text/desc/clz/pkg、布尔状态、层级、行列信息与 bounds。布尔属性是字符串 `true` 或 `false`，因此使用 `@checked='true'`；`@checked` 单独出现仅检查属性是否存在。

插件复制的绝对/相对 XPath 可以直接传给全局 `xpath()`。`contains`、`starts-with`、`concat`、位置谓词和父子/兄弟轴使用标准 XPath 1.0。表达式必须返回节点集合，长度上限 8192 字符。XPath 可继续追加普通条件或用于级联。

绝对路径中的窗口 ID 和节点位置可能随界面重建变化，页面变更后应重新抓取。节点分析器的 XPath 测试仍在已保存快照上运行，脚本查询读取手机实时树。局部查询的 XPath 只看到该节点及其后代构成的树，最终结果排除范围根节点。

## 节点操作

| 方法 | 行为 |
| --- | --- |
| `parent()`、`child(index)` | 父/直接子节点；不存在或越界返回 null |
| `allChildren()` | 全部直接子节点数组，不递归展开 |
| `children()` | 直接子节点的兼容集合 |
| `siblings()` | 全部兄弟节点，排除自身 |
| `previousSiblings()` / `nextSiblings()` | 前/后兄弟节点，按原下标顺序 |
| `clickEx()` / `longClickEx()` | 当前节点的无障碍点击/长按 |
| `clickCenter()` | 手势点击可见矩形中心 |
| `clickPoint()` | 手势点击可见矩形内随机点 |
| `click()` | 保留旧行为：无障碍点击，失败时找最近的可点击父节点 |
| `longClick()` | 保留旧的无障碍长按行为 |
| `longClick(ms)` / `clickExactPoint(ms)` | 中心点按住指定时长，1～60000 ms |
| `inputText(text)` / `setText(text)` | 无障碍设置输入框全文 |
| `clearText()` | 将输入框文字设为空 |
| `refresh()` / `isValid()` | 刷新并返回节点是否仍有效 |
| `exists()` | 是否持有句柄；不访问系统验证页面是否变化 |
| `scrollForward()` / `scrollBackward()` | 按 Android 语义向前/向后滚动 |
| `scrollUp/Down/Left/Right()` | 对应方向的无障碍滚动动作 |
| `show()` | 请求系统滚动到节点 |
| `copy/cut/paste()`、`setSelection(start,end)` | 编辑文本选区操作 |
| `select/collapse/expand()` | 对应的无障碍动作 |
| `recycle()` | 主动释放句柄；之后不能继续操作 |

动作返回布尔结果，界面最终效果取决于目标应用提供的无障碍支持。手势方法等待系统完成回调；可见边界是节点边界与屏幕的交集，其他窗口遮挡仍会影响实际点击。

调用读取、点击等方法不会自动回收或将句柄替换成父节点。长循环中可主动 recycle；集合 recycle 不影响通过 get(i)/toArray() 已取得的副本。

## 节点属性与兼容说明

保留现有 `node.text()`、`id()`、`desc()`、`className()`、`bounds()` 等方法。新增 `clz()`、`pkg()`、各状态方法、depth/index、行列方法及 `info()`。`node.info().text` 是普通属性快照；`JSON.stringify(node)` 和 `logd("{}", node)` 可输出节点信息。与参考文档的直接 `node.text` 字段写法不同，XpAuto 使用方法读取，避免破坏现有脚本。

0.4.0 修正了旧实现的三处语义：

1. text/id/desc/clz/packageName 从隐式包含改为精确匹配；旧脚本需要模糊匹配时使用 Contains。
2. `getOneNodeInfo(number)` 的数字是等待毫秒数；按下标取节点改为 `find().get(index)` 或 `getNodeInfo()[index]`。
3. 无参 `getOneNodeInfo()` 没有结果返回 null；应先判断节点是否存在，再调用方法。`untilFindOne` 仍提供旧空节点兼容。

参考页中的 IME 专用输入接口需要独立输入法服务，当前版本不提供 `imeInputText/imeInputKeyCode`；本次输入使用 Android 无障碍 ACTION_SET_TEXT。

## 开发与验证

- `SelectorCondition`：纯 Java 条件匹配，正则预编译。
- `UiSelector`：链式条件与查询入口；`NodeQuery`：遍历、等待、取消和节点副本管理。
- `UiSelector.toString/toJSON`：只格式化条件；`SelectorCondition` 负责参数转义，`NodeXPath` 保留原表达式。Javet 的 `JSON.stringify` 会传属性名，因此 Java 保留无参和字符串参数两个 `toJSON` 重载。
- `NodeAttributes`：统一属性读取；`NodeXPath`：编译与执行内存 XPath。
- `UiObject` / `UiCollection`：节点与集合操作，不隐式释放返回值。
- `base.js`：独立的全局选择器入口。
- `types/xpauto.d.ts`：中文补全与参数提示；节点分析器同步开放新增状态和层级条件。
- debug 专用 `SelectorFixtureActivity`：可重复的查询、列表元数据、输入、点击和滚动验证页面，不进入 release 包。

示例：[examples/selectors/main.js](../examples/selectors/main.js)。测试命令：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
node --test vscode-extension/test/*.test.js
XPAUTO_ADB=/path/to/adb node scripts/verify-selectors.cjs
XPAUTO_ADB=/path/to/adb node scripts/verify-selector-output.cjs
XPAUTO_ADB=/path/to/adb node scripts/verify-vscode-debug.mjs
```

新增原生能力需要更新一次 APK；日常 F5 仍只连接设备和同步脚本。
