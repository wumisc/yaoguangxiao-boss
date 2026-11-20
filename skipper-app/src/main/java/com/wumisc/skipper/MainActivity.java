package com.wumisc.skipper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** P0 控制台：开关入口、白名单、命中日志。 */
public class MainActivity extends Activity {

    private TextView statusView;
    private TextView logView;
    private EditText whiteInput;
    private CheckBox whiteOnly;
    private SharedPreferences sp;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        HitLog.init(this);
        setContentView(buildUi());
        ui.post(ticker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(ticker);
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private LinearLayout buildUi() {
        int bg = Color.parseColor("#14110C");
        int gold = Color.parseColor("#E3C071");
        int dim = Color.parseColor("#9C948A");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(20), dp(24), dp(20), dp(16));

        TextView title = new TextView(this);
        title.setText("姚广孝 · 跳过");
        title.setTextColor(gold);
        title.setTextSize(24);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("P0 原型 · 只点「跳过」，不屏蔽、不注入、不联网");
        sub.setTextColor(dim);
        sub.setTextSize(12);
        sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(statusView, lp(-1, -2, 0, 12));

        Button open = new Button(this);
        open.setText("① 打开系统无障碍设置");
        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(open);

        whiteOnly = new CheckBox(this);
        whiteOnly.setText("只对下面列出的应用生效（默认关：除金融/系统外全部生效）");
        whiteOnly.setTextColor(Color.WHITE);
        whiteOnly.setTextSize(13);
        whiteOnly.setChecked(sp.getBoolean("whiteOnly", false));
        whiteOnly.setOnCheckedChangeListener((b, checked) ->
                sp.edit().putBoolean("whiteOnly", checked).apply());
        root.addView(whiteOnly, lp(-1, -2, 0, 6));

        whiteInput = new EditText(this);
        whiteInput.setHint("包名，逗号分隔，如 com.ss.android.ugc.aweme");
        whiteInput.setText(sp.getString("whitelist", ""));
        whiteInput.setTextSize(13);
        whiteInput.setTextColor(Color.WHITE);
        whiteInput.setHintTextColor(dim);
        whiteInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        whiteInput.setSingleLine(false);
        whiteInput.setMinLines(2);
        root.addView(whiteInput, lp(-1, -2, 0, 6));

        Button save = new Button(this);
        save.setText("② 保存白名单");
        save.setOnClickListener(v -> {
            sp.edit().putString("whitelist", whiteInput.getText().toString().trim()).apply();
            statusView.setText("白名单已保存：" + describeWhite());
            refresh();
        });
        root.addView(save);

        TextView logTitle = new TextView(this);
        logTitle.setText("③ 命中日志（最近 40 条，最新的在上）");
        logTitle.setTextColor(gold);
        logTitle.setTextSize(13);
        logTitle.setPadding(0, dp(16), 0, dp(6));
        root.addView(logTitle);

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Color.parseColor("#CFC7BB"));
        logView.setPadding(dp(10), dp(10), dp(10), dp(10));
        logView.setBackgroundColor(Color.parseColor("#1C1813"));
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);

        Button clear = new Button(this);
        clear.setText("清空日志");
        clear.setOnClickListener(v -> { HitLog.clear(); refresh(); });
        row.addView(clear, new LinearLayout.LayoutParams(0, -2, 1f));

        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> refresh());
        row.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(row);

        TextView foot = new TextView(this);
        foot.setText("红线：只会点击含「跳过 / 关闭广告」的控件；不会点广告主体；金融、支付、政务类应用永不动作。");
        foot.setTextColor(dim);
        foot.setTextSize(10);
        foot.setGravity(Gravity.START);
        foot.setPadding(0, dp(10), 0, 0);
        root.addView(foot);

        return root;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private boolean serviceEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) return false;
        ComponentName cn = new ComponentName(this, SkipService.class);
        String target = cn.flattenToString().toLowerCase();
        for (String s : flat.split(":")) {
            if (s.toLowerCase().contains(getPackageName().toLowerCase())
                    && s.toLowerCase().contains("skipservice")) return true;
            if (s.toLowerCase().equals(target)) return true;
        }
        return false;
    }

    private String describeWhite() {
        String t = sp.getString("whitelist", "");
        if (t.trim().isEmpty()) return "（空）";
        return t.split(",").length + " 个包名";
    }

    private void refresh() {
        boolean on = serviceEnabled();
        statusView.setText(on
                ? "状态：无障碍服务已开启 ✅\n当前模式：" + (sp.getBoolean("whiteOnly", false)
                    ? "仅白名单（" + describeWhite() + "）" : "除金融/系统外全部生效")
                : "状态：无障碍服务未开启 ❌\n请点下面的按钮，在「已下载的应用」里找到「姚广孝 · 跳过」并打开");
        statusView.setTextColor(Color.parseColor(on ? "#7BD88F" : "#E07B6A"));
        logView.setText(HitLog.recent(40));
    }
}
