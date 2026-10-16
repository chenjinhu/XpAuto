package com.vixmobi.auto.floaty;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

/** 原生 View 的脚本句柄：常用方法同步返回，实际读取和修改统一在主线程执行。 */
public final class FloatyView {
    final FloatyApi owner;
    final View view;
    FloatyView(FloatyApi owner, View view) { this.owner = owner; this.view = view; }
    public FloatyView findViewWithTag(String tag) {
        return owner.accessView(() -> { View found = view.findViewWithTag(tag); return found == null ? null : new FloatyView(owner, found); });
    }
    public String getTag() { return owner.accessView(() -> view.getTag() == null ? null : view.getTag().toString()); }
    public void setTag(String tag) { owner.accessView(() -> { view.setTag(tag); return null; }); }
    public String getText() { return owner.accessView(() -> textView().getText().toString()); }
    public void setText(String text) { owner.accessView(() -> { textView().setText(text); return null; }); }
    public void setTextColor(String color) { owner.accessView(() -> { textView().setTextColor(Color.parseColor(color)); return null; }); }
    public void setTextColor(int color) { owner.accessView(() -> { textView().setTextColor(color); return null; }); }
    public void setTextSize(float sp) {
        owner.accessView(() -> { if (!Float.isFinite(sp) || sp < 0 || sp > 1000) throw new IllegalArgumentException("字号必须为 0 到 1000 sp"); textView().setTextSize(sp); return null; });
    }
    public void setBackgroundColor(String color) { owner.accessView(() -> { view.setBackgroundColor(Color.parseColor(color)); return null; }); }
    public void setBackgroundColor(int color) { owner.accessView(() -> { view.setBackgroundColor(color); return null; }); }
    public void setEnabled(boolean enabled) { owner.accessView(() -> { view.setEnabled(enabled); return null; }); }
    public boolean isEnabled() { return owner.accessView(view::isEnabled); }
    public void setVisibility(int visibility) {
        owner.accessView(() -> {
            if (visibility != View.VISIBLE && visibility != View.INVISIBLE && visibility != View.GONE) throw new IllegalArgumentException("visibility 必须为 0 / 4 / 8");
            view.setVisibility(visibility); return null;
        });
    }
    public int getVisibility() { return owner.accessView(view::getVisibility); }
    public boolean requestFocus() { return owner.accessView(view::requestFocus); }
    public void clearFocus() { owner.accessView(() -> { view.clearFocus(); return null; }); }
    public void setChecked(boolean checked) { owner.accessView(() -> { compoundButton().setChecked(checked); return null; }); }
    public boolean isChecked() { return owner.accessView(() -> compoundButton().isChecked()); }
    public int getWidth() { return owner.accessView(view::getWidth); }
    public int getHeight() { return owner.accessView(view::getHeight); }
    public void measure(int widthSpec, int heightSpec) { owner.accessView(() -> { view.measure(widthSpec, heightSpec); return null; }); }
    public int getMeasuredWidth() { return owner.accessView(view::getMeasuredWidth); }
    public int getMeasuredHeight() { return owner.accessView(view::getMeasuredHeight); }
    public int getChildCount() { return owner.accessView(() -> view instanceof ViewGroup ? ((ViewGroup) view).getChildCount() : 0); }
    public FloatyView getChildAt(int index) {
        return owner.accessView(() -> {
            View child = view instanceof ViewGroup ? ((ViewGroup) view).getChildAt(index) : null;
            return child == null ? null : new FloatyView(owner, child);
        });
    }
    private TextView textView() {
        if (!(view instanceof TextView)) throw new IllegalStateException("该控件不支持文本操作");
        return (TextView) view;
    }
    private CompoundButton compoundButton() {
        if (!(view instanceof CompoundButton)) throw new IllegalStateException("该控件不支持勾选操作");
        return (CompoundButton) view;
    }
}
