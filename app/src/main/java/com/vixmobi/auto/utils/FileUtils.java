package com.vixmobi.auto.utils;


import static com.vixmobi.auto.Constant.context;
import static com.vixmobi.auto.Constant.tag;
import static com.vixmobi.auto.node.AccUtils.printLogMsg;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    private static final String TAG = tag;

    /**
     * 文件或者文件夹是否存在
     * @param filePath 路径
     * @return 布尔型 true 代表成功，false代表失败
     */
    public static boolean exists(String filePath) {
        return new File(filePath).exists();
    }

    /**
     * 将文件读取为字符串
     * @param filePath 文件的路径
     * @return
     */
    public static String readFile(String filePath) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(Paths.get(filePath)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {}
        return content.toString();
    }

    /**
     * 删除文件某一行或者根据包含条件删除
     * @param filePath 文件路径
     * @param line 行数，如果是-1 代表这个条件不生效
     * @param contains 包含某个字符串就删除，如果为null代表这个条件不生效
     * @return
     */
    public static boolean deleteLine(String filePath, int line, String contains) {
        List<String> lines = readAllLines(filePath);
        boolean allLines = line == -1;
        String writeStr = "";
        for (int i = 0; i < lines.size(); i++) {
            if (!allLines && i == line){
                // 跳过要删除的行
                continue;
            }else if (contains != null){
                String lineStr = lines.get(i);
                if (lineStr.contains(contains)){
                    continue;
                }

            }
            writeStr += lines.get(i) + "\n";

        }

        return deleteFile(filePath) && writeFile(filePath, writeStr);

    }


    /**
     * 读取一行数据，如果行号不对，返回的是空
     * @param filePath 路径
     * @param line 行号
     * @return 字符串 返回一行字符串
     */
    public static String readLine(String filePath, int line) {
        List<String> lines = readAllLines(filePath);
        if (line < 0 || line >= lines.size()){
            return "";
        }
        return lines.get(line);

    }

    /**
     * 从文本中逐行读取
     * @param filePath
     * @return
     */
    public static List<String> readAllLines (String filePath) {

        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    /**
     * 写内容到文件中
     *
     * @param fileName 文件的完整路径
     * @param content 要写入的内容
     * @return 写入成功返回true，否则返回false
     */
    public static boolean writeFile(String content,String fileName) {
        // 使用try-with-resources来自动管理资源，自动关闭流
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(fileName),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            bufferedWriter.write(content);
            bufferedWriter.flush();
            bufferedWriter.close();

            return true; // 成功写入
        } catch (IOException e) {
            printLogMsg("writeFile: IOException: " + e.getMessage());
            return false; // 发生异常，返回false
        }
    }

    /**
     * 追加内容到文件中，并返回操作是否成功
     *
     * @param filePath 文件的路径
     * @param content 要追加的内容
     * @return 操作成功返回true，否则返回false
     */
    public static boolean appendFile(String filePath, String content) {
        // 使用try-with-resources来自动管理资源，自动关闭流
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(filePath),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bufferedWriter.write(content);
            return true; // 文件写入成功
        } catch (IOException e) {
            printLogMsg("appendFile: IOException: " + e.getMessage());
            return false; // 文件写入失败
        }
    }

    /**
     * 写入一行到文件中,追加模式. 在文件尾增加 line + "\n"
     * @param line 行数据
     * @param filePath 文件或者文件路径
     * @return  布尔型 true代表成功 false代表失败
     */
    public static boolean appendLine( String line,String filePath) {
        return appendFile(filePath, line + "\n");
    }

    /**
     * 删除对应文件
     * @param filePath
     * @return
     */
    public static boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * 删除所有文件或者文件夹
     * @param path 文件或者文件路径
     * @return 影响的文件数量
     */
    public static int deleteAllFile(String path){
        File file = new File(path);
        if (!file.exists()){
            return 0;
        }
        if (file.isFile()){
            // 是文件
            return deleteFile(path) ? 1 : 0;
        }else {
            // 是文件夹
            int count = 0;
            for (File f : file.listFiles()) {
                count += deleteAllFile(f.getPath());
            }
            return count;
        }

    }

    /**
     * 创建文件夹
     * @param fileDirectory
     */
    public static void mkdirs(String fileDirectory) {
        File file = new File(fileDirectory);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    public static boolean copy(String src, String dst){
        try {
            Files.copy(Paths.get(src), Paths.get(dst));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 移动文件
     * @param sourceFilePath
     * @param targetFolderPath
     * @return
     */
    public static boolean moveFile(String sourceFilePath, String targetFolderPath) {
        File sourceFile = new File(sourceFilePath);
        File targetFolder = new File(targetFolderPath);

        if (sourceFile.exists() && sourceFile.isFile() && targetFolder.exists() && targetFolder.isDirectory()) {
            boolean isMoved = sourceFile.renameTo(new File(targetFolder, sourceFile.getName()));
            if (isMoved) {
                // 移动成功
                printLogMsg("移动成功");
                return true;
            }
        }
        // 移动失败
        printLogMsg("移动失败");
        return false;
    }

    /**
     * 查看某一目录所有文件
     * @param folderPath
     * @return
     */
    public static List<String> listDir(String folderPath) {
        List<String> list = new ArrayList<>();
        File folder = new File(folderPath);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    String filePath = file.getPath();
                    // 其他操作
                    printLogMsg(filePath);
                    list.add(filePath);
                }
            }
        }
        return list;
    }

    /**
     * 重命名文件
     * @param sourceFilePath
     * @param targetFilePath
     * @return
     */
    public static boolean renameFile(String sourceFilePath, String targetFilePath) {
        // 创建源文件对象和目标文件对象
        File sourceFile = new File(sourceFilePath);
        File targetFile = new File(targetFilePath);

        // 使用renameTo()方法重命名文件
        if (sourceFile.renameTo(targetFile)) {
            printLogMsg("文件重命名成功");
            return true;
        }
        printLogMsg("文件重命名失败");
        return false;
    }

    /**
     * 保存内容到TXT文件中
     */
    public static boolean writeToTxt(String fileName, String content) {
        FileOutputStream fileOutputStream;
        BufferedWriter bufferedWriter;
        File file = new File(fileName);
        try {
            file.createNewFile();
            fileOutputStream = new FileOutputStream(file);
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream));
            bufferedWriter.write(content);
            bufferedWriter.close();
        } catch (IOException e) {
            printLogMsg("writeToTxt: IOException: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * 从给定的Uri中获取文件名。
     *
     * @param context 应用程序上下文，用于访问ContentResolver。
     * @param uri     文件的Uri，可以是content类型的Uri。
     * @return 文件名，如果无法获取则返回null。
     */
    public static String getUriFileName(Context context, Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    // 注意：不同设备上的列名可能不同，通常情况下是 "_display_name"
                    int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (columnIndex != -1) {
                        fileName = cursor.getString(columnIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return fileName;
    }

    /**
     * 从给定的Uri读取文件内容。
     *
     * @param context 应用程序上下文，用于访问ContentResolver。
     * @param uri     文件的Uri。
     * @return 文件内容的字符串表示。如果发生IO异常，则返回null。
     */
    public static String readUriFile(Context context, Uri uri) {
        StringBuilder content = new StringBuilder();
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                    content.append('\n');
                }
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return content.toString();
    }

    /**
     * 将文本写入给定Uri的文件中。如果文件已存在，可以选择覆盖或追加。
     *
     * @param context    应用程序上下文，用于访问ContentResolver。
     * @param uri        文件的Uri。
     * @param content    要写入的文本内容。
     * @param append     如果为true，则在文件末尾追加文本；如果为false，则覆盖文件内容。
     * @return           成功写入返回true，否则返回false。
     */
    public static boolean writeUriFile(Context context, Uri uri, String content, boolean append) {
        try {
            // 根据append参数决定是覆盖还是追加模式
            OutputStream outputStream = context.getContentResolver().openOutputStream(uri, append ? "wa" : "w");
            if (outputStream != null) {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                writer.write(content);
                writer.flush();
                writer.close();
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 创建文件或文件夹  文件本身已存在返回false
     * @param path 文件路径
     * @return 布尔型 true 代表创建成功
     */
    public static boolean create(String path){
        File file = new File(path);
        if (file.exists()){
            // 文件已存在
            return false;
        }
        // 创建当前路径是文件夹还是文件
        Path pPath = Paths.get(path);
        if (path.endsWith(File.separator)){
            // 是文件夹
            file.mkdirs();
        }else{
            // 是文件
            try {
                return file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    /**
     * 从APK的assets文件夹中读取数据为字符串
     * @param path assets文件夹中的文件路径，例如 data/a.txt
     * @return 字符串
     */
    public static String readAssets(String path){
        try {
            InputStream inputStream = context.getAssets().open(path);
            byte[] bArr = new byte[inputStream.available()];
            inputStream.read(bArr);
            inputStream.close();
            String str = new String(bArr, "UTF-8");
            return str;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }


}