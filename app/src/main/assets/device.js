// 设备信息使用当前屏幕与系统实际值，不缓存旋转后的宽高。
const device = Object.freeze({
    getScreenWidth: () => __xpautoDevice.getScreenWidth(),
    getScreenHeight: () => __xpautoDevice.getScreenHeight(),
    getDensity: () => __xpautoDevice.getDensity(),
    getDensityDpi: () => __xpautoDevice.getDensityDpi(),
    getBrand: () => __xpautoDevice.getBrand(),
    getModel: () => __xpautoDevice.getModel(),
    getManufacturer: () => __xpautoDevice.getManufacturer(),
    getSdkInt: () => __xpautoDevice.getSdkInt(),
    getOSVersion: () => __xpautoDevice.getOSVersion(),
    getAndroidId: () => __xpautoDevice.getAndroidId(),
    getBattery: () => __xpautoDevice.getBattery(),
    isCharging: () => __xpautoDevice.isCharging(),
    isScreenOn: () => __xpautoDevice.isScreenOn(),
    getBrightness: () => __xpautoDevice.getBrightness(),
    getIpAddress: () => __xpautoDevice.getIpAddress(),
});
utils.getInstalledApps = (includeSystem = false) => {
    if (typeof includeSystem !== 'boolean') throw new TypeError('includeSystem 必须为布尔值');
    return JSON.parse(__xpautoDevice.installedAppsJson(includeSystem));
};
utils.getInstalledPkgName = (includeSystem = false) => utils.getInstalledApps(includeSystem).map(app => app.packageName);
utils.getInstalledAppName = (includeSystem = false) => utils.getInstalledApps(includeSystem).map(app => app.appName);
