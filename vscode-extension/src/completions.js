'use strict';
const vscode = require('vscode');
const path = require('node:path');
const { SdkLanguageService } = require('./language-service');

/** 提示在本地完成，不连接手机，也不改写用户的 JS 或 jsconfig。 */
function registerCompletions(context) {
    let language;
    const prepare = document => {
        language ??= new SdkLanguageService(path.join(context.extensionPath, 'types/xpauto.d.ts'));
        const file = document.uri.scheme === 'file' ? document.fileName : path.join(context.extensionPath, 'types/scratch.js');
        language.setDocument(file, document.getText(), document.version);
        return language;
    };
    const docs = value => new vscode.MarkdownString().appendCodeblock(value.signature, 'typescript')
        .appendText('\n' + value.documentation);
    const range = (document, span) => new vscode.Range(document.positionAt(span.start), document.positionAt(span.start + span.length));
    const selector = [{ language: 'javascript', scheme: 'file' }, { language: 'javascript', scheme: 'untitled' }];
    context.subscriptions.push({ dispose: () => language?.dispose() },
        vscode.languages.registerCompletionItemProvider(selector, {
            provideCompletionItems(document, position, token) {
                if (token.isCancellationRequested) return [];
                return prepare(document).completions(document.offsetAt(position)).map(value => {
                    const kind = /method|function/.test(value.kind) ? vscode.CompletionItemKind.Method : vscode.CompletionItemKind.Property;
                    const item = new vscode.CompletionItem(value.name, kind);
                    item.detail = value.signature;
                    item.documentation = new vscode.MarkdownString().appendText(value.documentation);
                    item.sortText = '0-' + value.name;
                    if (value.span) item.range = range(document, value.span);
                    return item;
                });
            },
        }, '.'),
        vscode.languages.registerHoverProvider(selector, {
            provideHover(document, position) {
                const value = prepare(document).hover(document.offsetAt(position));
                if (value) return new vscode.Hover(docs(value), range(document, value.span));
            },
        }),
        vscode.languages.registerSignatureHelpProvider(selector, {
            provideSignatureHelp(document, position) {
                const value = prepare(document).signatures(document.offsetAt(position));
                if (!value) return undefined;
                const help = new vscode.SignatureHelp();
                help.activeSignature = value.active; help.activeParameter = value.argument;
                help.signatures = value.items.map(item => {
                    const label = item.prefix + item.parameters.map(p => p.label).join(item.separator) + item.suffix;
                    const signature = new vscode.SignatureInformation(label, item.documentation);
                    signature.parameters = item.parameters.map(p => new vscode.ParameterInformation(p.label, p.documentation));
                    return signature;
                });
                return help;
            },
        }, '(', ','));
}
module.exports = { registerCompletions };
