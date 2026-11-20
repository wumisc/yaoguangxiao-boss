package com.wumisc.skipper;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 命中日志：内存 + 文件各一份，最多 300 条，界面显示最近 40 条。 */
public final class HitLog {

    private static final int MAX = 300;
    private static final List<String> LINES = new ArrayList<>();
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
    private static File file;

    private HitLog() {}

    public static synchronized void init(Context ctx) {
        if (file != null) return;
        file = new File(ctx.getFilesDir(), "hits.log");
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.trim().isEmpty()) LINES.add(l);
            }
        } catch (Exception ignored) {
        }
        trim();
    }

    public static synchronized void add(String pkg, String reason, String result) {
        String line = TS.format(new Date()) + " | " + shortPkg(pkg) + " | " + reason + " | " + result;
        LINES.add(line);
        trim();
        if (file == null) return;
        try (FileWriter w = new FileWriter(file, false)) {
            for (String s : LINES) w.write(s + "\n");
        } catch (Exception ignored) {
        }
    }

    public static synchronized String recent(int n) {
        if (LINES.isEmpty()) return "（还没有命中记录）\n\n装好后先打开一个带开屏广告的 App，再回来看这里。";
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, LINES.size() - n);
        for (int i = LINES.size() - 1; i >= from; i--) sb.append(LINES.get(i)).append('\n');
        return sb.toString();
    }

    public static synchronized void clear() {
        LINES.clear();
        if (file != null) {
            try (FileWriter w = new FileWriter(file, false)) { w.write(""); } catch (Exception ignored) {}
        }
    }

    private static void trim() {
        while (LINES.size() > MAX) LINES.remove(0);
    }

    private static String shortPkg(String pkg) {
        if (pkg == null) return "?";
        return pkg.length() > 28 ? pkg.substring(0, 26) + "…" : pkg;
    }
}
