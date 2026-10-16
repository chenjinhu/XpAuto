// 使用内联 XML，不需要向手机额外上传布局文件。
function main() {
    if (!floaty.requestFloatViewPermission(10000)) {
        logi("请授予 XpAuto 悬浮窗权限后重试");
        return;
    }
    const tag = "任务进度";
    const view = floaty.showFloatXml(tag, `
        <LinearLayout layout_width="250dp" layout_height="wrap_content"
            orientation="vertical" padding="16dp" background="#E820293B" cornerRadius="16dp">
            <TextView text="XpAuto · 运行进度" textColor="#FFFFFF" textSize="18sp" textStyle="bold"/>
            <TextView tag="status" text="准备开始…" textColor="#C7D5EA" textSize="14sp" layout_marginTop="10dp"/>
            <EditText tag="note" hint="可输入备注" textColor="#FFFFFF" singleLine="true"
                layout_width="match_parent" layout_height="48dp"/>
            <CheckBox tag="verbose" text="显示详细日志" textColor="#FFFFFF"/>
        </LinearLayout>`, 60, 400);
    if (!view) return;
    try {
        floaty.focusable(tag, true);
        const status = view.findViewWithTag("status");
        for (let step = 1; step <= 5; step++) {
            status.setText("已完成 " + step + " / 5");
            logi("窗口尺寸：" + floaty.getWidth(tag) + " × " + floaty.getHeight(tag));
            sleep(1000);
        }
        logi("备注：" + view.findViewWithTag("note").getText());
        logi("详细日志：" + view.findViewWithTag("verbose").isChecked());
        floaty.focusable(tag, false);
        floaty.updateX(tag, 100);
        floaty.updateY(tag, 500);
        sleep(1000);
    } finally {
        // 主动结束、异常或断开调试时，运行环境也会自动清理本次脚本的窗口。
        floaty.close(tag);
    }
}
main();
