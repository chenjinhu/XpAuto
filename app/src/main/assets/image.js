// 截图参数在 JS 层统一校验，Java 层负责系统授权、像素处理和资源生命周期。
var image = (() => {
    const api = __xpautoImage;
    function integer(value, name, min, max) {
        if (!Number.isInteger(value) || value < min || value > max) throw new RangeError(name + ' 参数超出范围');
        return value;
    }
    const coordinate = value => integer(value, '坐标', -2147483648, 2147483647);
    function capture(retry = 3, x = -1, y = -1, ex = -1, ey = -1) {
        return api.captureBitmap(integer(retry, '重试次数', 0, 20), coordinate(x), coordinate(y), coordinate(ex), coordinate(ey));
    }
    function quality(value) { return integer(value, '图片质量', 1, 100); }
    function filePath(value) {
        if (typeof value !== 'string' || !value.trim()) throw new TypeError('图片路径必须是非空字符串');
        return value;
    }
    function format(value) {
        if (!['png', 'jpg', 'jpeg', 'webp'].includes(String(value).toLowerCase())) throw new TypeError('图片格式仅支持 png、jpg、webp');
        return String(value);
    }
    const result = {
        requestScreenCapture(timeout = 10000, type = 0) {
            return api.requestScreenCapture(integer(timeout, '授权超时', 1, 300000), integer(type, '截图模式', 0, 2));
        },
        releaseScreenCapture() { api.releaseScreenCapture(); },
        captureScreen(...args) { return api.wrap(capture(...args)); },
        captureFullScreen() { return api.wrap(capture()); },
        captureFullScreenEx() { return result.captureFullScreen(); },
        // 没有代理服务时按参考接口的约定回退到已授权的全屏截图。
        captureScreenshot() { return result.captureFullScreen(); },
        captureScreenSurface() { return result.captureFullScreen(); },
        captureScreenBitmap(encoding = 'png', x = -1, y = -1, ex = -1, ey = -1, q = 100) {
            format(encoding); quality(q);
            return capture(3, x, y, ex, ey);
        },
        captureScreenBitmapEx() { return capture(); },
        captureToFile(retry, x, y, ex, ey, path) {
            // 便利重载：image.captureToFile('screen.png')。
            if (arguments.length === 1 && typeof retry === 'string') { path = retry; retry = 3; x = y = ex = ey = -1; }
            if (typeof path !== 'string' || !path.trim()) throw new TypeError('请指定截图保存路径');
            const bitmap = capture(retry, x, y, ex, ey);
            if (!bitmap) return false;
            try { return api.saveBitmap(bitmap, 'png', 100, path); }
            finally { api.recycle(bitmap); }
        },
        screencapImage(root = true) { return api.wrap(result.screencapBitmap(root)); },
        screencapBitmap(root = true) {
            if (typeof root !== 'boolean') throw new TypeError('root 必须是布尔值');
            return api.screencapBitmap(root);
        },
        getWidth(img) { return api.getWidth(img); },
        getHeight(img) { return api.getHeight(img); },
        readImage(path) { return api.wrap(api.readBitmap(filePath(path))); },
        readBitmap(path) { return api.readBitmap(filePath(path)); },
        saveTo(img, path) { return api.saveBitmap(img, 'png', 100, filePath(path)); },
        saveBitmap(bitmap, encoding, q, path) { return api.saveBitmap(bitmap, format(encoding), quality(q), filePath(path)); },
        toBase64Format(img, encoding = 'png', q = 100) { return api.bitmapBase64(img, format(encoding), quality(q)); },
        bitmapBase64(bitmap, encoding = 'png', q = 100) { return result.toBase64Format(bitmap, encoding, q); },
        imageToBitmap(img) { return api.copyBitmap(img); },
        bitmapToImage(bitmap) { return api.wrap(api.copyBitmap(bitmap)); },
        clip(img, x, y, ex, ey) { coordinate(ex); coordinate(ey); return api.wrap(result.clipBitmap(img, x, y, ex - x, ey - y)); },
        clipBitmap(bitmap, x, y, w, h) { return api.clipBitmap(bitmap, integer(x, 'x', 0, 2147483647), integer(y, 'y', 0, 2147483647), integer(w, '宽度', 1, 2147483647), integer(h, '高度', 1, 2147483647)); },
        pixel(img, x, y) { return api.pixel(img, coordinate(x), coordinate(y)); },
        pixelInImage(img, x, y) { return result.pixel(img, x, y); },
        getPixelBitmap(bitmap, x, y) { return result.pixel(bitmap, x, y); },
        isRecycled(img) { return api.isRecycled(img); },
        recycle(img) { api.recycle(img); },
        recycleAllImage() { return api.recycleAllImage(); },
        getErrorMsg() { return String(api.getErrorMsg()); },
        getSaveDir() { return String(api.getSaveDir()); }
    };
    return result;
})();
