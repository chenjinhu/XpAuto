const utils = (() => {
    const integer = (value, name) => { if (!Number.isSafeInteger(value)) throw new TypeError(name + ' 必须为安全整数'); return value; };
    const getRangeInt = (min, max) => {
        integer(min, 'min'); integer(max, 'max');
        if (min > max || !Number.isSafeInteger(max - min + 1)) throw new RangeError('随机整数范围无效');
        return min + Math.floor(Math.random() * (max - min + 1));
    };
    return {
        openApp: pkg => __xpautoUtils.openApp(pkg), getStartAppCmd: pkg => __xpautoUtils.getStartAppCmd(pkg),
        openAppByName: name => __xpautoUtils.openAppByName(name),
        openActivity: map => __xpautoUtils.openActivityJson(JSON.stringify(map)),
        getStartActivityCmd: map => __xpautoUtils.getStartActivityCmdJson(JSON.stringify(map)),
        openIntentAction: action => __xpautoUtils.openIntentAction(action),
        insertImageToAlbum: path => __xpautoUtils.insertImageToAlbum(path), insertVideoToAlbum: path => __xpautoUtils.insertVideoToAlbum(path),
        zip: (file, password, files) => __xpautoUtils.zipJson(file, password || '', JSON.stringify(files)),
        unzip: (file, password, dest) => __xpautoUtils.unzip(file, password || '', dest),
        unzipWithEncode: (file, password, dest, encoding) => __xpautoUtils.unzipWithEncode(file, password || '', dest, encoding),
        readFileInZip: (file, password, entry) => __xpautoUtils.readFileInZip(file, password || '', entry),
        createQRCode: (content, width, height, logo = null) => __xpautoUtils.createQRCode(content, width, height, logo),
        decodeQRCode: bitmap => __xpautoUtils.decodeQRCode(bitmap), saveBitmap: (bitmap, path) => __xpautoUtils.saveBitmap(bitmap, path),
        readConfigInt: (json, key) => { const n = Number(json == null ? 0 : json[key]); return Number.isFinite(n) ? Math.trunc(n) : 0; },
        readJSONString: (json, key) => json == null || json[key] == null ? '' : String(json[key]),
        isObjectNull: value => value == null, isObjectNotNull: value => value != null, isTrue: value => value === true,
        getRatio: ratio => { if (!Number.isFinite(ratio) || ratio < 0 || ratio > 100) throw new RangeError('概率必须为 0～100'); return Math.random() * 100 < ratio; },
        getRangeInt,
        randomInt: length => { integer(length, 'length'); if (length < 1 || length > 15) throw new RangeError('随机数字长度为 1～15'); return getRangeInt(length === 1 ? 0 : 10 ** (length - 1), 10 ** length - 1); },
        randomCharNumber: length => { integer(length, 'length'); if (length < 0 || length > 1000000) throw new RangeError('字符长度为 0～1000000'); const alphabet = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'; return Array.from({length}, () => alphabet[getRangeInt(0, alphabet.length - 1)]).join(''); },
        fileMd5: path => __xpautoUtils.fileMd5(path), dataMd5: data => __xpautoUtils.dataMd5(String(data)),
        getApkPkgName: path => __xpautoUtils.getApkPkgName(path), isAppExist: pkg => __xpautoUtils.isAppExist(pkg),
        getAppVersionCode: pkg => Number(__xpautoUtils.getAppVersionCode(pkg)), getAppVersionName: pkg => __xpautoUtils.getAppVersionName(pkg),
        setClipboardText: text => __xpautoUtils.setClipboardText(String(text)), getClipboardText: () => __xpautoUtils.getClipboardText(),
        playMp3: (path, loop = false) => __xpautoUtils.playMp3(path, loop), playMp3WaitEnd: (path, loop = false) => __xpautoUtils.playMp3WaitEnd(path, loop), stopMp3: () => __xpautoUtils.stopMp3(),
    };
})();
const random = (min, max) => utils.getRangeInt(min, max);
