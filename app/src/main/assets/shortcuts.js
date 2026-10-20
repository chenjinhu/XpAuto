// 选择器操作临时获取节点并及时回收；传入现有节点时由调用者管理生命周期。
const __xpautoWithNode = (target, action) => {
    target = __xpautoUnwrapNode(target);
    if (target == null) return false;
    const owns = typeof target.exists === 'function' && typeof target.info === 'function';
    const node = owns ? target : target.getOneNodeInfo(0);
    if (!node) return false;
    try { return action(node); } finally { if (!owns) node.recycle(); }
};
const __xpautoRect = value => {
    if (!value) throw new TypeError('区域不能为空');
    const r = Array.isArray(value) ? {left:value[0],top:value[1],right:value[2],bottom:value[3]} : value;
    const values = [r.left,r.top,r.right,r.bottom];
    if (values.some(v => !Number.isFinite(v)) || r.right <= r.left || r.bottom <= r.top) throw new RangeError('区域必须有正宽高');
    return r;
};
const clickEx = target => __xpautoWithNode(target, node => node.clickEx());
const longClickEx = target => __xpautoWithNode(target, node => node.longClickEx());
const __xpautoRandomClick = (target, direct) => {
    const nodes = __xpautoUnwrapNode(target).getNodeInfo(0);
    if (!nodes.length) return false;
    try { const node = nodes[random(0,nodes.length-1)]; return direct ? node.clickEx() : clickRandomRect(node.bounds()); }
    finally { for (const node of nodes) node.recycle(); }
};
const clickRandom = target => __xpautoRandomClick(target,false);
const clickRandomEx = target => __xpautoRandomClick(target,true);
const clickRandomRect = area => { const r=__xpautoRect(area); return clickPoint(random(Math.ceil(r.left),Math.ceil(r.right)-1),random(Math.ceil(r.top),Math.ceil(r.bottom)-1)); };
const clickCenter = area => { const r=__xpautoRect(area); return clickPoint((r.left+r.right)/2,(r.top+r.bottom)/2); };
const clickText = value => click(text(value));
const longClick = target => __xpautoWithNode(target,node => { const r=node.bounds();return longClickPoint((r.left+r.right)/2,(r.top+r.bottom)/2); });
const longClickPoint = (x,y) => press(x,y,600);
const press = (x,y,delay) => __xpautoGesture.press(x,y,delay);
const multiTouch = (touch1,touch2,touch3,timeout=10000) => __xpautoGesture.multiTouchJson(JSON.stringify([touch1||[],touch2||[],touch3||[]]),timeout);
const touchDown = (x,y) => __xpautoGesture.touch(0,x,y);
const touchMove = (x,y) => __xpautoGesture.touch(2,x,y);
const touchUp = (x,y) => __xpautoGesture.touch(1,x,y);
// 与参考文档的无障碍模式一致，Ex 的 pointerId 不参与单点续接；多指请使用 multiTouch。
const touchDownEx = (x,y,pointerId=1) => touchDown(x,y);
const touchMoveEx = (x,y,pointerId=1) => touchMove(x,y);
const touchUpEx = (x,y,pointerId=1) => touchUp(x,y);
const resetAllTouchState = () => __xpautoGesture.resetAllTouchState();
const scrollForward = target => __xpautoWithNode(target,node=>node.scrollForward());
const scrollBackward = target => __xpautoWithNode(target,node=>node.scrollBackward());
const swipeToPoint = (x1,y1,x2,y2,duration) => __xpautoGesture.swipe(x1,y1,x2,y2,duration);
const __xpautoMoveNode = (target,x,y,duration,dragging) => __xpautoWithNode(target,node=>{ const r=node.bounds();return (dragging?drag:swipeToPoint)((r.left+r.right)/2,(r.top+r.bottom)/2,x,y,duration); });
const isScrollEnd = (direction,target) => {
    const actions={UP:16908344,DOWN:16908346,LEFT:16908345,RIGHT:16908347};
    direction=String(direction).toUpperCase();if(!actions[direction])throw new TypeError('滚动方向为 UP/DOWN/LEFT/RIGHT');
    return __xpautoWithNode(target,node=> {
        if(!node.scrollable())return false;
        const positive=direction==='DOWN'||direction==='RIGHT';
        return !node.hasAction(actions[direction]) && !node.hasAction(positive?4096:8192);
    });
};
const drag = (x1,y1,x2,y2,duration) => __xpautoGesture.drag(x1,y1,x2,y2,duration);
const dragTo = (target,destination,duration) => __xpautoWithNode(destination,node=>{const r=node.bounds();return dragToPoint(target,(r.left+r.right)/2,(r.top+r.bottom)/2,duration);});
const dragToPoint = (target,x,y,duration) => __xpautoMoveNode(target,x,y,duration,true);
const inputText = (target,content) => __xpautoWithNode(target,node=>node.inputText(String(content)));
const clearTextField = target => __xpautoWithNode(target,node=>node.clearText());
const pasteText = (target,content) => utils.setClipboardText(content) && __xpautoWithNode(target,node=>node.paste());
const setFocus = target => __xpautoWithNode(target,node=>node.focused() || node.setFocus());
const currentIsOurIme = () => __xpautoInput.current();
const imeInputViewShown = () => __xpautoInput.shown();
const imeInputText = (target,content) => (target==null || setFocus(target)) && __xpautoInput.text(String(content));
const imeInputKeyCode = (target,code) => (target==null || setFocus(target)) && __xpautoInput.key(code);
const lastNodeEventTime = () => Number(__xpautoNodes.lastNodeEventTime());
const has = target => target != null && target.exists();
const waitExistActivity = (activity,timeout) => execSync(()=>getRunningActivity()===activity,timeout);
const waitExistNode = (target,timeout) => execSync(()=>has(target),timeout);
const getNodeInfo = (target,timeout=0) => target.getNodeInfo(timeout);
const getOneNodeInfo = (target,timeout=0) => target.getOneNodeInfo(timeout);
const getNodeAttrs = (target,attribute) => {
    const aliases={className:'clz',packageName:'pkg'};attribute=aliases[attribute]||attribute;
    const nodes=getNodeInfo(target);
    try { return Array.from(nodes,node=>{const info=node.info();if(!Object.prototype.hasOwnProperty.call(info,attribute))throw new TypeError('未知或已屏蔽的节点属性：'+attribute);return info[attribute];}); }
    finally { for(const node of nodes)node.recycle(); }
};
const getText = target => getNodeAttrs(target,'text');
const setFetchNodeMode = (mode,fetchInvisible=false,fetchNotImportant=true,algorithm='nsf') => __xpautoNodes.setFetchNodeMode(mode,fetchInvisible,fetchNotImportant,algorithm);
const setNodeDumpParam = data => __xpautoNodes.setNodeDumpParamJson(JSON.stringify(data));
const setBlockNodeAttr = attributes => __xpautoNodes.setBlockNodeAttr(attributes);
const addNodeFlag = flag => __xpautoNodes.addNodeFlag(flag);
const removeNodeFlag = flag => __xpautoNodes.removeNodeFlag(flag);
const dumpXml = () => __xpautoNodes.dumpXml();
const lockNode = () => __xpautoNodes.lockNode();
const releaseNode = () => __xpautoNodes.releaseNode();
const requestRuntimePermission = (permissions,timeout=30000) => __xpautoPermissions.requestRuntimePermissionJson(JSON.stringify(permissions),timeout);
const tryGetProjectionPermission = () => __xpautoPermissions.tryGetProjectionPermission();
const tryGetAccStartupPermission = () => __xpautoPermissions.tryGetAccStartupPermission();
