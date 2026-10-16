// JSON 仅用于跨桥接传递普通数据，SQL 参数值通过 Android ContentValues 绑定。
const sqlite = {
    connectOrCreateDb: name => __xpautoSqlite.connectOrCreateDb(String(name)),
    createTable: (table, columns) => __xpautoSqlite.createTableJson(table, JSON.stringify(columns)),
    insert: (table, values) => __xpautoSqlite.insertJson(table, JSON.stringify(values)),
    update: (table, values, where = '') => __xpautoSqlite.updateJson(table, JSON.stringify(values), where),
    query: sql => JSON.parse(__xpautoSqlite.queryJson(sql)),
    delete: sql => __xpautoSqlite.delete(sql), execSql: sql => __xpautoSqlite.execSql(sql),
    dropTable: table => __xpautoSqlite.dropTable(table), dropDatabase: () => __xpautoSqlite.dropDatabase(),
    close: () => __xpautoSqlite.closeDb(), getErrorMsg: () => __xpautoSqlite.getErrorMsg(),
};
