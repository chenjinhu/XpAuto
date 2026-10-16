'use strict';
const vscode = require('vscode');
const attribute = value => String(value).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
function inspectorHtml(webview, extensionUri, nonce) {
    const resource = name => attribute(webview.asWebviewUri(vscode.Uri.joinPath(extensionUri, 'media', name)));
    return `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src ${attribute(webview.cspSource)} 'unsafe-inline'; script-src 'nonce-${nonce}';">
<link rel="stylesheet" href="${resource('inspector.css')}"><title>XpAuto 节点分析</title></head><body>
<header><div class="toolbar">
<button id="capture" class="primary">获取节点</button><button id="save" disabled>保存节点&截图</button><button id="load">载入节点&截图</button>
<label class="single-tab" title="新快照替换当前记录，保留其他历史"><input id="single-tab" type="checkbox"> 单 Tab 模式</label>
<span id="coordinates">在截图上移动鼠标查看坐标与颜色</span></div>
<div class="query"><input id="xpath" aria-label="XPath 查询" placeholder="XPath，例如 //node[@text='确定']" maxlength="2000"><button id="test-xpath">测试 XPath</button><button id="clear-xpath">清除标记</button></div></header>
<div id="notice" role="status">连接已开启运行环境的手机，点击“获取节点”；也可以载入已保存的快照。</div>
<main><aside class="history-panel"><h2>抓取历史 <small id="history-count">0 / 10</small></h2><input id="history-filter" aria-label="搜索抓取历史" placeholder="搜索包名、时间或设备"><div id="history"></div><p class="hint">历史暂存于当前窗口会话中。需要保留时请保存快照。</p></aside>
<section class="screen-panel"><div class="section-bar"><strong id="screen-title">屏幕预览</strong><select id="zoom" aria-label="截图缩放"><option value="fit">适应窗口</option><option value="0.5">50%</option><option value="1">100%</option><option value="2">200%</option></select></div>
<div id="viewport"><div id="empty-screen">截图与节点会同时出现在这里<br><small>点击截图可定位节点，支持检查重叠节点</small></div><div id="stage" hidden><img id="screen" alt="已抓取的手机屏幕"><div id="boxes"></div></div></div>
<div class="screen-footer"><label>命中节点 <select id="hits" aria-label="选择重叠节点"><option>点击截图后选择</option></select></label><span id="capture-info"></span></div></section>
<section class="analysis-panel"><div class="section-bar"><strong>节点树</strong><button id="parent" title="选择父节点">↑ 父节点</button><button id="child" title="选择第一个子节点">↓ 子节点</button></div>
<input id="filter" aria-label="筛选节点" placeholder="筛选文本、ID、类名、包名或描述">
<div id="tree" role="tree" aria-label="UI 节点树"></div>
<div class="section-bar"><strong>节点属性</strong><span class="hint">勾选生成条件 · 双击值复制 · 右键更多</span></div>
<div id="properties"><div class="empty">选择一个节点查看属性</div></div>
<div class="code-bar"><select id="code-kind" aria-label="代码生成方式"><option value="single">单节点代码</option><option value="multiple">多节点代码</option><option value="selector">选择器代码</option></select><button id="copy-code" disabled>复制代码</button><span id="match-count"></span></div>
<textarea id="code" aria-label="生成的节点代码" readonly spellcheck="false" placeholder="选中节点后自动生成 XpAuto 代码"></textarea></section></main>
<div id="context-menu" role="menu" hidden><button data-copy="value">复制值</button><button data-copy="selector">复制选择器代码</button><button data-copy="single">复制单节点代码</button><button data-copy="multiple">复制多节点代码</button><button data-copy="absolute">复制绝对路径 XPath</button><button data-copy="relative">复制相对路径 XPath</button></div>
<script nonce="${nonce}" src="${resource('inspector-model.js')}"></script><script nonce="${nonce}" src="${resource('inspector.js')}"></script></body></html>`;
}
module.exports = { inspectorHtml };
