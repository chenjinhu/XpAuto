// 普通授权截图不需要 Root。首次调用在手机上确认系统的屏幕共享提示。
function main() {
    if (!image.requestScreenCapture(30000, 1)) {
        loge('截图授权失败：' + image.getErrorMsg());
        return;
    }
    try {
        // 自动等待首帧，无需额外 sleep；截图对象应及时回收。
        const screen = image.captureFullScreen();
        if (!screen) {
            loge('截图失败：' + image.getErrorMsg());
            return;
        }
        try {
            logi('截图：%s × %s', image.getWidth(screen), image.getHeight(screen));
            if (image.saveTo(screen, 'screen.png')) {
                logi('截图已保存：' + image.getSaveDir() + '/screen.png');
            } else {
                loge('保存失败：' + image.getErrorMsg());
            }
        } finally {
            image.recycle(screen);
        }
        // 区域参数是起点、终点，这里保存 200×300 像素。
        if (!image.captureToFile(3, 0, 0, 200, 300, 'region.png')) {
            loge(image.getErrorMsg());
        }
    } finally {
        image.releaseScreenCapture();
    }
}
main();
