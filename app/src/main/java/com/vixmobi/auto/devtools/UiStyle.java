package com.vixmobi.auto.devtools;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.ImageView;

/** 首页与悬浮窗共用的颜色、圆角背景和应用图标，避免两处样式不一致。 */
final class UiStyle {
    static final int BLUE = Color.rgb(40, 100, 235);
    static final int INK = Color.rgb(28, 43, 65);
    static final int MUTED = Color.rgb(113, 126, 146);
    static final int BORDER = Color.rgb(227, 233, 242);
    static final int SURFACE = Color.rgb(245, 247, 251);

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    static GradientDrawable shape(Context context, int color, int radius, boolean stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke) drawable.setStroke(dp(context, 1), BORDER);
        return drawable;
    }
    static RippleDrawable ripple(Context context, int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x182864EB),
                shape(context, color, radius, true), shape(context, Color.WHITE, radius, false));
    }
    static ImageView appIcon(Context context, int size) {
        ImageView icon = new ImageView(context);
        icon.setImageDrawable(context.getApplicationInfo().loadIcon(context.getPackageManager()));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(shape(context, Color.WHITE, size / 2, false));
        icon.setClipToOutline(true);
        return icon;
    }
    private UiStyle() { }
}
