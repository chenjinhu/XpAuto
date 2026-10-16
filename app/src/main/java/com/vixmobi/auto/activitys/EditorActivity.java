package com.vixmobi.auto.activitys;

import static com.vixmobi.auto.Constant.PATH;
import static com.vixmobi.auto.Constant.tag;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.vixmobi.auto.R;

import java.io.File;

public class EditorActivity extends AppCompatActivity {
    private TextView title, lines;
    private EditText editor;
    private Editor ed;

    private boolean isDark;

    public static final String scripts_path = Environment.getExternalStorageDirectory() + PATH;

    private void isDark() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        isDark = sharedPref.getBoolean("dark_mode", false);
        if (isDark) super.setTheme(R.style.AppThemeDark);
        else super.setTheme(R.style.AppThemeLight);
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        isDark();
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.status_color));
        setContentView(R.layout.editor);
//
        init_editor();

        ed = new Editor(editor, lines, title, this, isDark);
        ed.SCRIPT_PATH = scripts_path;

        read_script();

    }

    private void init_editor() {
        LinearLayout layout = findViewById(R.id.editor_layout);

        // 判断是否为夜间模式
        if (isDark) {
            layout.setBackgroundColor(getResources().getColor(android.R.color.black));
        }else{
            layout.setBackgroundColor(getResources().getColor(android.R.color.white));
        }

        final View header = findViewById(R.id.header);
        // 快捷插入按钮
        final View insert_btns = findViewById(R.id.insert_btns);
        insert_btns.setVisibility(View.GONE);

        lines = findViewById(R.id.lines);
        lines.setVerticalScrollBarEnabled(true);
        lines.setMovementMethod(new ScrollingMovementMethod());
        title = findViewById(R.id.script_title);


        editor = findViewById(R.id.editor);
        editor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (keyboardShown(editor.getRootView())) {
                    // insert_btns.setVisibility(View.VISIBLE);
                    header.setVisibility(View.GONE);
                    //about.setVisibility(View.GONE);
                } else {
                    header.setVisibility(View.VISIBLE);
                    //about.setVisibility(View.VISIBLE);
                    //insert_btns.setVisibility(View.GONE);
                }
            }
        });

    }

    private void read_script() {
        String name = "", path = "";
        final Intent intent = getIntent();
        final String action = intent.getAction();
        final Bundle bundle = intent.getExtras();
        Log.d(tag, "action: " + action);

        if (Intent.ACTION_VIEW.equals(action)) {
            Log.i("HINT", "DATA");

            if (intent.getData() == null) return;
            path = intent.getData().getPath();
            path = path.replace("/root", "");
            File f = new File(path);
            name = f.getName();
        }else if (Intent.ACTION_SEND.equals(action)){
            Log.i("HINT", "SEND");

            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                String txt = bundle.getString(Intent.EXTRA_TEXT);
                if (txt != null) editor.setText(txt);
                path = "";
                name = "";
            } else if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                Log.i("HINT", "SEND");
                Uri uri = (Uri) bundle.get(Intent.EXTRA_STREAM);
                path = getRealPathFromURI(this, uri);
                File f = new File(path);
                name = f.getName();
            }
        } else if (intent.hasExtra("path") && intent.hasExtra("name")) {
            path = intent.getStringExtra("path");
            name = intent.getStringExtra("name");
        }
        if (!path.equals("")) {
            ed.path = path;
            ed.name = name;
            ed.script = new File(path);
        }
        if (ed.script != null) {
            editor.setText(Scripts.read(ed.path));
            title.setText(ed.name);
            ed.count_lines();
        }

    }

    private String getRealPathFromURI(Context context, Uri contentUri) {
        if (contentUri != null && contentUri.getPath().startsWith("/storage"))
            return contentUri.getPath();
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean keyboardShown(View rootView) {
        final int softKeyboardHeight = 100;
        Rect r = new Rect();
        rootView.getWindowVisibleDisplayFrame(r);
        DisplayMetrics dm = rootView.getResources().getDisplayMetrics();
        int heightDiff = rootView.getBottom() - r.bottom;
        return heightDiff > softKeyboardHeight * dm.density;
    }

    // 功能栏 点击事件
    public void edButtons(View v) {
        switch (v.getId()) {
            case R.id.edNew: //save && open new
                ed.AFTER_SAVE = Editor.NEW;
                ed.check_save();
                break;
            case R.id.script_title://show script list
                ed.AFTER_SAVE = Editor.LIS;
                ed.check_save();
                break;
//            case R.id.edShare://save and share file
//                ed.AFTER_SAVE = Editor.SHR;
//                ed.check_save();
//                break;
            case R.id.edSave://save only
                ed.AFTER_SAVE = Editor.SAV;
                if (ed.script != null) ed.save();
                else ed.save_as_dialog();
                break;
            case R.id.edClear:
                editor.setText("");
                break;
            case R.id.edZoomIn:
                ed.zoomIn(true);
                break;
            case R.id.edZoomOut:
                ed.zoomIn(false);
                break;
            case R.id.edDarkMode:
                setDark();
                super.recreate();
                break;
            case R.id.edLines:
                ed.show_lines();
                break;
        }

    }

    public void setDark() {
        isDark = !isDark;
        SharedPreferences.Editor sharedPrefEditor = getPreferences(Context.MODE_PRIVATE).edit();
        sharedPrefEditor.putBoolean("dark_mode", isDark);
        sharedPrefEditor.commit();
    }

}
