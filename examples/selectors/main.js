// 先切换到需要自动化的页面；所有等待单位都是毫秒，按“停止”可取消等待。
const submit = text("确定").enabled(true).visible(true);
const button = submit.getOneNodeInfo(1500);
if (button) {
    logi("找到节点：{}", button.info());
    // 确认目标后再执行：button.clickEx();
    button.recycle();
} else {
    logw("当前页面没有可用的“确定”按钮");
}

// 同一个选择器可重复查询；各个选择器的条件互不影响。
logd("按钮是否仍存在：{}", submit.exists());
const inputs = editable(true).getNodeInfo(0);
for (const input of inputs) {
    logd("输入框：{}", input.info());
    input.recycle();
}

// 先定位容器，再查找其后代。directChild() 仅限直接子节点。
const checkboxes = clz("android.widget.ScrollView").child()
    .clz("android.widget.CheckBox").checked(false).getNodeInfo(0);
logi("未勾选的复选框：{}", checkboxes.length);
for (const node of checkboxes) node.recycle();

// 也可以直接粘贴节点分析器复制的 XPath。
const matches = xpath("//node[@clickable='true' and @enabled='true']").getNodeInfo(0);
logd("可点击节点数量：{}", matches.length);
for (const node of matches) node.recycle();
