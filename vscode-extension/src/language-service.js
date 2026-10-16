'use strict';
const ts = require('typescript');
const fs = require('node:fs');
const path = require('node:path');
const display = parts => ts.displayPartsToString(parts || []);
const isSdk = item => item?.tags?.some(tag => tag.name === 'xpauto');

/** 使用真正的 JS 类型推断处理链式调用和变量，不靠正则猜测光标前的表达式。 */
class SdkLanguageService {
    constructor(typesPath) {
        this.typesPath = typesPath;
        this.typesText = fs.readFileSync(typesPath, 'utf8');
        this.names = new Set();
        const declarations = ts.createSourceFile(typesPath, this.typesText, ts.ScriptTarget.Latest);
        const collect = node => { if (node.name && ts.isIdentifier(node.name)) this.names.add(node.name.text); ts.forEachChild(node, collect); };
        collect(declarations);
        const options = { allowJs: true, checkJs: false, target: ts.ScriptTarget.ES2020,
            module: ts.ModuleKind.CommonJS, moduleDetection: ts.ModuleDetectionKind.Force,
            strictNullChecks: true, lib: ['lib.es2020.d.ts'] };
        this.service = ts.createLanguageService({
            getCompilationSettings: () => options,
            getScriptFileNames: () => [this.typesPath, ...(this.file ? [this.file] : [])],
            getScriptVersion: name => name === this.file ? String(this.version) : '1',
            getScriptSnapshot: name => {
                const source = name === this.file ? this.source : name === this.typesPath ? this.typesText : ts.sys.readFile(name);
                return source === undefined ? undefined : ts.ScriptSnapshot.fromString(source);
            },
            getCurrentDirectory: () => path.dirname(this.file || typesPath),
            getDefaultLibFileName: config => ts.getDefaultLibFilePath(config),
            fileExists: ts.sys.fileExists, readFile: ts.sys.readFile, readDirectory: ts.sys.readDirectory,
        });
    }
    setDocument(file, source) {
        // 不同未保存文件可能拥有相同的文档版本，使用内容变化计数避免复用旧结果。
        if (this.file !== file || this.source !== source) this.version = (this.version || 0) + 1;
        this.file = file; this.source = source;
    }
    completions(offset) {
        const result = this.service.getCompletionsAtPosition(this.file, offset, {});
        return (result?.entries || []).filter(entry => this.names.has(entry.name)).map(entry => {
            const detail = this.service.getCompletionEntryDetails(this.file, offset, entry.name, {}, entry.source, {}, entry.data);
            return isSdk(detail) ? { name: entry.name, kind: entry.kind, span: entry.replacementSpan,
                signature: display(detail.displayParts), documentation: display(detail.documentation) } : null;
        }).filter(Boolean);
    }
    hover(offset) {
        const info = this.service.getQuickInfoAtPosition(this.file, offset);
        return isSdk(info) ? { signature: display(info.displayParts), documentation: display(info.documentation), span: info.textSpan } : undefined;
    }
    signatures(offset) {
        const result = this.service.getSignatureHelpItems(this.file, offset, undefined);
        if (!result) return undefined;
        const items = result.items.filter(isSdk);
        if (!items.length) return undefined;
        return { active: Math.max(0, items.indexOf(result.items[result.selectedItemIndex])), argument: result.argumentIndex,
            items: items.map(item => ({ prefix: display(item.prefixDisplayParts), suffix: display(item.suffixDisplayParts),
                separator: display(item.separatorDisplayParts), documentation: display(item.documentation),
                parameters: item.parameters.map(parameter => ({ label: display(parameter.displayParts), documentation: display(parameter.documentation) })) })) };
    }
    dispose() { this.service.dispose(); }
}
module.exports = { SdkLanguageService };
