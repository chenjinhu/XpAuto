# 全局快捷操作

参考 [EasyClick 全局快捷函数](https://ieasyclick.com/docs/funcs/global/global-shortcut)，本次覆盖点击、多点触摸、滚动/滑动、拖拽、输入、节点操作、动态权限和随机函数。时间单位均为毫秒。坐标为屏幕像素，操作需要无障碍服务。

## 点击、滑动与拖拽

`click(selectorOrNode)` 保留既有行为：先点击节点，失败时寻找可点击父节点。`clickEx` / `longClickEx` 只执行当前节点对应动作。以下函数返回 boolean：

- `clickPoint(x,y)`：精确坐标，等待手势完成。
- `clickRandom(selector)` / `clickRandomEx(selector)`：随机一个匹配节点，分别使用区域随机点击/节点动作。
- `clickRandomRect(rect)` / `clickCenter(rect)`：区域支持 `{left,top,right,bottom}` 或四元素数组。
- `clickText(text)`：文本完全匹配。
- `longClick(selectorOrNode)` / `longClickPoint(x,y)`：坐标长按 600ms。
- `press(x,y,delay)`：指定按压时长 1～60000ms。
- `scrollForward(selectorOrNode)` / `scrollBackward(selectorOrNode)`：节点滚动动作。
- `swipe(selectorOrNode,endX,endY,duration)`：从节点中心滑动。
- `swipe(startX,startY,endX,endY,duration)`：保留旧签名，同 `swipeToPoint(...)`。
- `isScrollEnd(direction,selectorOrNode)`：方向 UP/DOWN/LEFT/RIGHT，根据节点当前暴露的滚动动作判断；节点缺失/非滚动节点返回 false。应用没有正确暴露滚动动作时无法保证边界判断准确。
- `drag(startX,startY,endX,endY,duration)` / `dragTo(source,destination,duration)` / `dragToPoint(source,endX,endY,duration)`：先按住 600ms，再移动指定时长。

## 多点触摸

`multiTouch(touch1,touch2?,touch3?,timeout=10000)` 最多三指，每个参数为事件数组：

```js
multiTouch(
    [{action:0,x:300,y:800,pointer:1,delay:0},
     {action:2,x:200,y:800,pointer:1,delay:300},
     {action:1,x:200,y:800,pointer:1,delay:50}],
    [{action:0,x:500,y:800,pointer:2,delay:0},
     {action:2,x:600,y:800,pointer:2,delay:300},
     {action:1,x:600,y:800,pointer:2,delay:50}],
    null, 2000
);
```

`action` 为 0 按下、2 移动、1 抬起；`delay` 是该事件前的延迟。数组位置区分手指，pointer 字段用于兼容。无障碍系统以续接手势分段执行，有系统调度误差；零时长移动按至少 1ms 处理。每指最多 1000 个事件，timeout 最多 60000ms。省略最终抬起时自动补齐。

`touchDown(x,y)` → `touchMove(x,y)` → `touchUp(x,y)` 可连续操作单指；`resetAllTouchState()` 释放当前保存触点。`touchDownEx/touchMoveEx/touchUpEx(x,y,pointerId)` 与参考文档的无障碍模式一致，忽略 pointerId，多指使用 multiTouch。新的独立手势会中断前一个触摸序列。

## 输入

`inputText(target,content)`、`clearTextField(target)`、`setFocus(target)` 使用节点动作；`pasteText(target,content)` 先写入剪贴板再执行粘贴。目标为选择器或节点，找不到返回 false。

`imeInputText(targetOrNull,content)` 和 `imeInputKeyCode(targetOrNull,code)` 需要在 Android“语言和输入法”中启用并选中 **XpAuto 脚本输入法**。`null` 使用当前输入焦点；传选择器则先设置焦点。`currentIsOurIme()` 查询当前输入法，`imeInputViewShown()` 查询键盘视图。

特殊文本：`—ec_close_input—`、`—ec_open_input—` 关闭/打开软键盘；`—ec_show_input_view—`、`—ec_hide_input_view—` 显示/隐藏输入法视图。按键支持 Android KeyEvent 常量与 1000 搜索、1001 完成、1002 Go、1003 下一个、1004 上一个、1005 发送。输入法不保存或记录输入内容。

## 节点操作

| 函数 | 说明 |
| --- | --- |
| `lastNodeEventTime()` | 最近无障碍事件 Unix 毫秒时间，尚无事件为 0 |
| `has(selector)` | 是否存在节点 |
| `waitExistActivity(activity,timeout)` | 等待完整 Activity 类名 |
| `waitExistNode(selector,timeout)` | 等待节点出现 |
| `getText(selector)` | 文本数组，无匹配为 [] |
| `getNodeInfo(selector,timeout=0)` | 节点数组 |
| `getOneNodeInfo(selector,timeout=0)` | 首个节点或 null |
| `getNodeAttrs(selector,attribute)` | 属性数组；支持 className/packageName 别名，屏蔽/未知属性会报错 |
| `setFetchNodeMode(mode,fetchInvisible=false,fetchNotImportant=true,algorithm="nsf")` | 1 增强型主动 refresh，2 快速型使用系统缓存；nsf 静态快照、bsf 广度优先、dsf 深度优先 |
| `setNodeDumpParam({dumpMethod:1,maxDepth:50})` | 最大深度 0～80；静态遍历方法：1 递归、2 显式栈、3 队列 |
| `setBlockNodeAttr("text,desc")` | 不读取这些属性；空串恢复，使用被屏蔽属性的选择器不匹配 |
| `addNodeFlag(flag)` / `removeNodeFlag(flag)` | 更新 AccessibilityServiceInfo 标志；0 刷新根节点 |
| `dumpXml()` | 输出当前节点 XML |
| `lockNode()` / `releaseNode()` | 冻结/释放查询快照；冻结期间界面变化不影响查询信息 |

通过 getNodeInfo/getOneNodeInfo 返回的节点由调用者 `recycle()`；快捷方法内部的临时节点自动回收。`lockNode` 冻结的是查询信息，点击仍操作真实界面，旧节点可能失效。单轮上限 5000 节点、80 层、4 秒；脚本结束恢复节点设置和服务 flags。默认保留旧版抓取行为（包含隐藏节点、深度优先），调用 setFetchNodeMode 后按传入设置生效。

## 权限与随机

- `requestRuntimePermission(permissionArray,timeout=30000)`：只请求 Manifest 已声明的普通动态权限。系统弹窗由用户选择；全部已授权立即 true，拒绝、未声明、特殊权限或超时为 false。不能代替悬浮窗、文件管理、无障碍或输入法的设置授权。
- `tryGetProjectionPermission()`：已有授权时直接返回 true，否则显式通过 Root 设置并检查 PROJECT_MEDIA app-op，返回 boolean。它不创建 MediaProjection 截图令牌，也不绕过 Android 14+ 的截图会话/前台服务要求。
- `tryGetAccStartupPermission()`：首次显式使用 Root 授予本应用 WRITE_SECURE_SETTINGS，随后合并启用无障碍服务并保留其他服务。授权保留后再次调用不需要 Root；返回设置是否成功，服务连接异步完成。应用启动时不会擅自重新打开用户手动关闭的无障碍服务。
- `random(min,max)`：包含最大/最小值的随机安全整数，等同 `utils.getRangeInt`。

开发实现：`assets/shortcuts.js` 组合选择器/节点；`node/GestureApi.java` 执行可取消手势；`node/NodeSettings.java` 管理快照和设置；`input/` 提供输入法服务；`permissions/` 承载系统权限弹窗。运行入口统一由 `runtime/ScriptModules.java` 注册与清理。
