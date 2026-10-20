'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

// 默认只同步入口目录的源码；资源由用户显式指定，避免把工作区中的无关文件传入手机。
async function collectProject(program, source, options = {}) {
    const root = path.resolve(options.projectRoot || path.dirname(program));
    const files = Object.create(null);
    let total = 0;
    const relative = file => {
        const name = path.relative(root, file).split(path.sep).join('/');
        if (!name || name === '..' || name.startsWith('../') || path.isAbsolute(name) || name.includes('\\'))
            throw new Error('项目文件必须位于 projectRoot 内：' + file);
        return name;
    };
    const add = async (file, content) => {
        const name = relative(file);
        if (Object.hasOwn(files, name)) return;
        const bytes = content === undefined ? await fs.readFile(file) : Buffer.from(content, 'utf8');
        if (bytes.length > 16 * 1024 * 1024 || total + bytes.length > 32 * 1024 * 1024)
            throw new Error('项目单文件最多 16 MiB，总计最多 32 MiB。');
        if (Object.keys(files).length >= 512) throw new Error('项目最多同步 512 个文件。');
        total += bytes.length;
        files[name] = bytes.toString('base64');
    };
    const ignored = new Set(['node_modules', 'build', 'dist', 'out', 'coverage']);
    const walk = async (file, resources) => {
        const stat = await fs.lstat(file);
        if (stat.isSymbolicLink()) throw new Error('项目同步不支持符号链接：' + file);
        if (stat.isDirectory()) {
            for (const item of await fs.readdir(file, {withFileTypes: true})) {
                if (item.name.startsWith('.') || (!resources && ignored.has(item.name))) continue;
                await walk(path.join(file, item.name), resources);
            }
        } else if (stat.isFile() && (resources || /\.(js|cjs|json)$/i.test(file))) {
            if (stat.size > 16 * 1024 * 1024) throw new Error('项目单文件最多 16 MiB：' + file);
            await add(file);
        }
    };
    const entry = relative(path.resolve(program));
    // 拒绝入口或项目根目录通过符号链接间接指向外部。
    const canonicalRoot = await fs.realpath(root);
    if (await fs.realpath(program) !== path.resolve(canonicalRoot, entry))
        throw new Error('projectRoot 和入口请使用实际路径，不支持符号链接。');
    await add(program, source);
    await walk(root, false);
    if (options.resources !== undefined && !Array.isArray(options.resources)) throw new Error('resources 必须为路径数组。');
    for (const resource of options.resources || []) {
        if (typeof resource !== 'string' || !resource || path.isAbsolute(resource)) throw new Error('resources 必须为项目内相对路径。');
        const file = path.resolve(root, resource);
        relative(file);
        // 检查每一层真实路径，拒绝资源父目录中的符号链接。
        if (await fs.realpath(file) !== path.resolve(canonicalRoot, relative(file))) throw new Error('资源路径不支持符号链接：' + resource);
        await walk(file, true);
    }
    const nodeMode = options.nodeMode || 'xpauto';
    if (!['xpauto', 'easyclick'].includes(nodeMode)) throw new Error('nodeMode 必须为 xpauto 或 easyclick。');
    return {entry, sourceRoot: pathToFileURL(root + path.sep).href, nodeMode, files};
}
module.exports = {collectProject};
