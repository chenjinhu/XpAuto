# SQLite 数据库

参考 [EasyClick SQLite 函数](https://ieasyclick.com/docs/funcs/sqlite-api)。每个脚本会话维护一条连接；结束或断开连接自动关闭数据库，不删除文件。

| 函数 | 说明 |
| --- | --- |
| `sqlite.connectOrCreateDb(path)` | 创建或打开数据库，返回 boolean；相对路径保存在应用 databases 目录，绝对路径需要本应用有读写权限 |
| `sqlite.createTable(table, columns)` | 创建自增整数主键 `id`，其他列为 TEXT；已有表保留 |
| `sqlite.insert(table, values)` | 插入普通 JS 对象，值通过 ContentValues 绑定 |
| `sqlite.update(table, values, whereSql="")` | 更新；省略或留空条件表示全部记录 |
| `sqlite.query(sql)` | 返回普通 JS 对象数组，空结果为 `[]`，失败为 null |
| `sqlite.delete(sql)` / `sqlite.execSql(sql)` | 执行 SQL，返回 boolean |
| `sqlite.dropTable(table)` | 删除指定表 |
| `sqlite.dropDatabase()` | 关闭并删除当前数据库及 SQLite 附属文件 |
| `sqlite.close()` | 主动关闭连接，返回 true |
| `sqlite.getErrorMsg()` | 最近错误或 null；下一次成功操作清除错误 |

```js
if (!sqlite.connectOrCreateDb("demo.db")) throw Error(sqlite.getErrorMsg());
try {
    sqlite.createTable("users", ["name", "note"]);
    sqlite.insert("users", {name: "小明", note: "包含 ' 引号的文本"});
    const rows = sqlite.query("SELECT id, name FROM users ORDER BY id DESC LIMIT 10");
    if (rows === null) throw Error(sqlite.getErrorMsg());
    logd(rows);
} finally {
    sqlite.close();
}
```

需要 INTEGER、REAL、索引、外键或事务时使用 `execSql` 自行建表。事务可使用 `BEGIN` / `COMMIT` / `ROLLBACK`。`createTable` 的字段参数是字段名，不是 SQL 片段。

查询保留 SQLite 的 null、数值和文本；BLOB 返回 0～255 的字节数组。JavaScript 数值超出安全整数范围时应在 SQL 中 `CAST(value AS TEXT)`。单次最多返回 100000 行 / 16MB 序列化文本，大数据使用 LIMIT 分页；SQL 条件本身不会自动转义，外部文本不要直接拼接为条件。

开发实现：`storage/SqliteApi.java` 封装 Android SQLiteDatabase，`assets/sqlite.js` 负责普通 JS 对象与 JSON 的边界。所有游标用 try-with-resources 关闭；连接操作串行化，脚本停止时会退出查询并释放连接。
