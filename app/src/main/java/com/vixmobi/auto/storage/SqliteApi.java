package com.vixmobi.auto.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.vixmobi.auto.runtime.ScriptBlocking;
import java.io.File;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

/** 一次脚本会话一条连接；所有语句串行执行，游标用完立即关闭。 */
public final class SqliteApi implements AutoCloseable {
  private final File directory;
  private SQLiteDatabase db;
  private File path;
  private String error;
  private volatile android.os.CancellationSignal activeQuery;

  public void cancel() {
    android.os.CancellationSignal current = activeQuery;
    if (current != null) current.cancel();
  }

  public SqliteApi(File directory) {
    this.directory = directory;
  }

  public synchronized String getErrorMsg() {
    return error;
  }

  private boolean fail(Exception exception) {
    error = exception.getMessage();
    return false;
  }

  private void ready() {
    ScriptBlocking.checkCancelled();
    if (db == null) throw new IllegalStateException("请先连接数据库");
    error = null;
  }

  private static String identifier(String value) {
    if (value == null || value.isEmpty() || value.indexOf('\0') >= 0)
      throw new IllegalArgumentException("表名和字段名不能为空");
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  public synchronized boolean connectOrCreateDb(String name) {
    try {
      ScriptBlocking.checkCancelled();
      if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("数据库路径不能为空");
      File target = new File(name);
      if (!target.isAbsolute()) target = new File(directory, name);
      File parent = target.getCanonicalFile().getParentFile();
      if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("无法创建数据库目录");
      SQLiteDatabase next = SQLiteDatabase.openOrCreateDatabase(target, null);
      close();
      db = next;
      path = target;
      error = null;
      return true;
    } catch (Exception exception) {
      return fail(exception);
    }
  }

  public synchronized boolean createTableJson(String table, String columns) {
    try {
      ready();
      JSONArray names = new JSONArray(columns);
      StringBuilder sql =
          new StringBuilder("CREATE TABLE IF NOT EXISTS ")
              .append(identifier(table))
              .append(" (\"id\" INTEGER PRIMARY KEY AUTOINCREMENT");
      for (int i = 0; i < names.length(); i++) {
        String name = names.getString(i);
        if (!name.equalsIgnoreCase("id")) sql.append(',').append(identifier(name)).append(" TEXT");
      }
      db.execSQL(sql.append(')').toString());
      return true;
    } catch (Exception exception) {
      return fail(exception);
    }
  }

  private ContentValues values(String json) throws Exception {
    JSONObject input = new JSONObject(json);
    ContentValues result = new ContentValues();
    Iterator<String> keys = input.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = input.get(key);
      key = identifier(key);
      if (value == JSONObject.NULL) result.putNull(key);
      else if (value instanceof Boolean) result.put(key, (Boolean) value);
      else if (value instanceof Integer || value instanceof Long)
        result.put(key, ((Number) value).longValue());
      else if (value instanceof Number) result.put(key, ((Number) value).doubleValue());
      else result.put(key, String.valueOf(value));
    }
    return result;
  }

  public synchronized boolean insertJson(String table, String data) {
    try {
      ready();
      db.insertOrThrow(identifier(table), null, values(data));
      return true;
    } catch (Exception exception) {
      return fail(exception);
    }
  }

  public synchronized boolean updateJson(String table, String data, String where) {
    try {
      ready();
      db.update(
          identifier(table),
          values(data),
          where == null || where.trim().isEmpty() ? null : where,
          null);
      return true;
    } catch (Exception exception) {
      return fail(exception);
    }
  }

  public synchronized String queryJson(String sql) {
    try {
      ready();
      JSONArray rows = new JSONArray();
      long characters = 0;
      activeQuery = new android.os.CancellationSignal();
      try (Cursor cursor = db.rawQuery(sql, null, activeQuery)) {
        while (cursor.moveToNext()) {
          ScriptBlocking.checkCancelled();
          if (rows.length() >= 100000)
            throw new IllegalArgumentException("查询结果超过 100000 行，请使用 LIMIT 分页");
          JSONObject row = new JSONObject();
          for (int i = 0; i < cursor.getColumnCount(); i++) {
            Object value;
            switch (cursor.getType(i)) {
              case Cursor.FIELD_TYPE_NULL:
                value = JSONObject.NULL;
                break;
              case Cursor.FIELD_TYPE_INTEGER:
                value = cursor.getLong(i);
                break;
              case Cursor.FIELD_TYPE_FLOAT:
                value = cursor.getDouble(i);
                break;
              case Cursor.FIELD_TYPE_BLOB:
                value = new JSONArray();
                for (byte b : cursor.getBlob(i)) ((JSONArray) value).put(b & 255);
                break;
              default:
                value = cursor.getString(i);
            }
            row.put(cursor.getColumnName(i), value);
          }
          rows.put(row);
          characters += row.toString().length();
          if (characters > 16 * 1024 * 1024)
            throw new IllegalArgumentException("查询文本超过 16MB，请使用 LIMIT 分页");
        }
      }
      return rows.toString();
    } catch (Exception exception) {
      fail(exception);
      return "null";
    } finally {
      activeQuery = null;
    }
  }

  public synchronized boolean execSql(String sql) {
    try {
      ready();
      db.execSQL(sql);
      return true;
    } catch (Exception exception) {
      return fail(exception);
    }
  }

  public boolean delete(String sql) {
    return execSql(sql);
  }

  public boolean dropTable(String table) {
    return execSql("DROP TABLE IF EXISTS " + identifier(table));
  }

  public synchronized boolean dropDatabase() {
    if (path == null) {
      error = "尚未连接数据库";
      return false;
    }
    File target = path;
    close();
    boolean removed = SQLiteDatabase.deleteDatabase(target);
    error = removed ? null : "无法删除数据库";
    return removed;
  }

  public synchronized boolean closeDb() {
    close();
    error = null;
    return true;
  }

  @Override
  public synchronized void close() {
    if (db != null) {
      db.close();
      db = null;
    }
  }
}
