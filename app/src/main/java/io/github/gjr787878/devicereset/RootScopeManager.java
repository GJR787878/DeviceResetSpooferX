package io.github.gjr787878.devicereset;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Root 作用域直写管理器。
 *
 * <p>在已授予 root 的设备上，无需打开 LSPosed/Vector 管理器、也无需在通知上点“批准”，
 * 直接把目标应用写入框架的作用域数据库 {@code modules_config.db}，并自动重启 lspd 守护进程
 * 使其立即重新读取配置，再强制停止目标应用 —— 实现“勾上即生效、零点击”。</p>
 *
 * <p>框架在守护进程启动时读取配置（见 Beetroot《LSPosed 配置与作用域管理指南》），
 * 因此写完库后必须重启守护进程。守护进程重启命令复刻自 LSPosed 工程的 reRunDaemon：
 * {@code killall lspd} 后运行模块目录的 {@code service.sh}（其内部 unshare 拉起 daemon）。</p>
 *
 * <p>不依赖设备上是否存在 sqlite3：采用“root 拷出到 /data/local/tmp → 应用内 SQLite 修改 →
 * root 拷回（覆盖同名文件以保留属主/权限/SELinux 上下文）”的方式，自包含、可预期。</p>
 */
public final class RootScopeManager {

    private static final String TAG = "RootScopeManager";
    private static final String MODULE_PKG = "io.github.gjr787878.devicereset";
    private static final int USER_ID = 0;
    private static final String STAGE = "/data/local/tmp/drs_scope.db";

    private final Context context;
    private Boolean rootCached = null;
    private String dbPathCached = null;
    private String moduleDirCached = null;

    public RootScopeManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // ============================== root shell ==============================

    /** 执行一段 root 脚本，返回合并的输出（stdout+stderr）。exitCode 通过 out[0] 无法返回，故用 Result。 */
    public static final class Result {
        public final int exitCode;
        public final String output;
        Result(int exitCode, String output) { this.exitCode = exitCode; this.output = output; }
        public boolean ok() { return exitCode == 0; }
    }

    /** 以 root 运行脚本（多行），返回退出码与输出。 */
    public static Result runRoot(String script) {
        Process p = null;
        StringBuilder out = new StringBuilder();
        try {
            p = Runtime.getRuntime().exec(new String[]{"su"});
            OutputStream os = p.getOutputStream();
            os.write((script + "\nexit\n").getBytes());
            os.flush();
            os.close();
            Thread tOut = stream(p, out, false);
            Thread tErr = stream(p, out, true);
            int code = p.waitFor();
            if (tOut != null) tOut.join(1500);
            if (tErr != null) tErr.join(1500);
            return new Result(code, out.toString().trim());
        } catch (Throwable t) {
            Log.e(TAG, "runRoot failed", t);
            return new Result(-1, String.valueOf(t.getMessage()));
        } finally {
            if (p != null) try { p.destroy(); } catch (Throwable ignored) {}
        }
    }

    private static Thread stream(final Process p, final StringBuilder sb, final boolean err) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    err ? p.getErrorStream() : p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (sb) { sb.append(line).append('\n'); }
                }
            } catch (Throwable ignored) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** root 是否可用（结果缓存）。 */
    public boolean isRootAvailable() {
        if (rootCached != null) return rootCached;
        Result r = runRoot("id");
        rootCached = r.ok() && r.output.contains("uid=0");
        return rootCached;
    }

    // ============================== 路径探测 ==============================

    /** 定位 modules_config.db（结果缓存）。 */
    public String findDbPath() {
        if (dbPathCached != null) return dbPathCached;
        String script =
                "for p in \\\n" +
                "  /data/adb/lspd/config/modules_config.db \\\n" +
                "  /data/adb/modules/zygisk_lsposed/config/modules_config.db \\\n" +
                "  /data/adb/modules/*lsposed*/config/modules_config.db \\\n" +
                "  /data/adb/modules/*lspd*/config/modules_config.db \\\n" +
                "  /data/adb/modules/*vector*/config/modules_config.db; do\n" +
                "  [ -f \"$p\" ] && echo \"$p\" && break\n" +
                "done";
        Result r = runRoot(script);
        if (r.ok() && !r.output.isEmpty()) {
            String p = r.output.split("\\n")[0].trim();
            if (!p.isEmpty() && p.contains("modules_config.db")) {
                dbPathCached = p;
                return p;
            }
        }
        return null;
    }

    /** 定位框架 magisk 模块目录（含 daemon 与 service.sh），用于重启守护进程。 */
    public String findModuleDir() {
        if (moduleDirCached != null) return moduleDirCached;
        String script =
                "for d in /data/adb/modules/*/ /data/adb/modules_update/*/; do\n" +
                "  if [ -f \"$d/daemon\" ] && [ -f \"$d/service.sh\" ]; then\n" +
                "    case \"$d\" in *lsposed*|*lspd*|*vector*) echo \"$d\"; break;; esac\n" +
                "  fi\n" +
                "done\n" +
                "# 兜底：任意含 daemon+service.sh 的目录\n" +
                "if [ ! -d \"$d\" ]; then\n" +
                "  for d in /data/adb/modules/*/; do\n" +
                "    [ -f \"$d/daemon\" ] && [ -f \"$d/service.sh\" ] && { echo \"$d\"; break; }\n" +
                "  done\n" +
                "fi";
        Result r = runRoot(script);
        if (r.ok() && !r.output.isEmpty()) {
            String d = r.output.split("\\n")[0].trim();
            if (d.endsWith("/")) { moduleDirCached = d; return d; }
        }
        return null;
    }

    // ============================== 读取作用域 ==============================

    /** 读取本模块当前作用域包名集合（root 拷出后用应用内 SQLite 查询）。失败返回 null。 */
    public Set<String> readScope() {
        String db = findDbPath();
        if (db == null) return null;
        String copy =
                "rm -f " + STAGE + " " + STAGE + "-wal " + STAGE + "-shm\n" +
                "cp '" + db + "' " + STAGE + " 2>/dev/null\n" +
                "[ -f '" + db + "-wal' ] && cp '" + db + "-wal' " + STAGE + "-wal 2>/dev/null\n" +
                "[ -f '" + db + "-shm' ] && cp '" + db + "-shm' " + STAGE + "-shm 2>/dev/null\n" +
                "chmod 666 " + STAGE + " 2>/dev/null\n" +
                "[ -f " + STAGE + "-wal ] && chmod 666 " + STAGE + "-wal\n" +
                "echo COPIED";
        Result r = runRoot(copy);
        if (!r.output.contains("COPIED")) return null;
        Set<String> scope = new LinkedHashSet<>();
        SQLiteDatabase sql = null;
        try {
            File f = new File(STAGE);
            if (!f.exists()) return null;
            sql = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE);
            Long mid = getModuleMid(sql);
            if (mid == null) return scope;
            try (Cursor c = sql.rawQuery(
                    "SELECT app_pkg_name FROM scope WHERE mid=? AND user_id=?",
                    new String[]{String.valueOf(mid), String.valueOf(USER_ID)})) {
                while (c.moveToNext()) scope.add(c.getString(0));
            }
        } catch (Throwable t) {
            Log.e(TAG, "readScope", t);
            return null;
        } finally {
            if (sql != null) try { sql.close(); } catch (Throwable ignored) {}
            runRoot("rm -f " + STAGE + " " + STAGE + "-wal " + STAGE + "-shm");
        }
        return scope;
    }

    private Long getModuleMid(SQLiteDatabase sql) {
        try (Cursor c = sql.rawQuery(
                "SELECT mid FROM modules WHERE module_pkg_name=?",
                new String[]{MODULE_PKG})) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return null;
    }

    // ============================== 写入作用域（全量同步） ==============================

    /**
     * 把本模块作用域全量同步为 {@code targetPkgs}，并确保模块已启用；随后重启守护进程。
     *
     * @return 成功与否
     */
    public boolean syncScope(Set<String> targetPkgs) {
        String db = findDbPath();
        if (db == null) { Log.e(TAG, "db not found"); return false; }

        // 1) 先停守护进程，避免并发写 / WAL 竞争
        killDaemon();

        // 2) 拷出（含 wal/shm）
        String copyOut =
                "rm -f " + STAGE + " " + STAGE + "-wal " + STAGE + "-shm\n" +
                "cp '" + db + "' " + STAGE + "\n" +
                "[ -f '" + db + "-wal' ] && cp '" + db + "-wal' " + STAGE + "-wal\n" +
                "[ -f '" + db + "-shm' ] && cp '" + db + "-shm' " + STAGE + "-shm\n" +
                "chmod 666 " + STAGE + "\n" +
                "[ -f " + STAGE + "-wal ] && chmod 666 " + STAGE + "-wal\n" +
                "echo OUT_OK";
        if (!runRoot(copyOut).output.contains("OUT_OK")) { restartDaemon(); return false; }

        // 3) 应用内 SQLite 修改
        boolean edited;
        SQLiteDatabase sql = null;
        try {
            File f = new File(STAGE);
            sql = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE);
            sql.beginTransaction();
            try {
                Long mid = getModuleMid(sql);
                if (mid == null) {
                    // 模块记录缺失（从未在管理器启用）：补一条 enabled 记录
                    String apk = context.getPackageManager()
                            .getApplicationInfo(MODULE_PKG, 0).sourceDir;
                    sql.execSQL(
                            "INSERT INTO modules (module_pkg_name, apk_path, enabled, auto_include) " +
                            "VALUES (?,?,1,0)",
                            new Object[]{MODULE_PKG, apk});
                    mid = getModuleMid(sql);
                } else {
                    sql.execSQL("UPDATE modules SET enabled=1 WHERE mid=?",
                            new Object[]{mid});
                }
                if (mid == null) throw new IllegalStateException("no mid");
                // 全量替换主用户(0)作用域
                sql.execSQL("DELETE FROM scope WHERE mid=? AND user_id=?",
                        new Object[]{mid, USER_ID});
                for (String pkg : targetPkgs) {
                    if (pkg == null || pkg.isEmpty()) continue;
                    sql.execSQL(
                            "INSERT INTO scope (mid, app_pkg_name, user_id) VALUES (?,?,?)",
                            new Object[]{mid, pkg, USER_ID});
                }
                sql.setTransactionSuccessful();
                edited = true;
            } finally {
                sql.endTransaction();
            }
        } catch (Throwable t) {
            Log.e(TAG, "syncScope edit", t);
            edited = false;
        } finally {
            if (sql != null) try { sql.close(); } catch (Throwable ignored) {}
        }

        // 4) 拷回（覆盖同名文件，保留属主/权限/SELinux 上下文）；清掉旧 wal/shm
        String copyBack =
                "cp " + STAGE + " '" + db + "'\n" +
                "rm -f '" + db + "-wal' '" + db + "-shm'\n" +
                "chmod 660 '" + db + "' 2>/dev/null\n" +
                "rm -f " + STAGE + " " + STAGE + "-wal " + STAGE + "-shm\n" +
                "echo BACK_OK";
        Result back = runRoot(copyBack);

        // 5) 无论成败都重启守护进程
        boolean restarted = restartDaemon();
        return edited && back.output.contains("BACK_OK") && restarted;
    }

    // ============================== 守护进程 / 目标应用 ==============================

    /** 停止 lspd 守护进程（轮询确认退出，最多约 3 秒）。 */
    public void killDaemon() {
        runRoot(
                "killall lspd 2>/dev/null\n" +
                "i=0\n" +
                "while pidof lspd >/dev/null 2>&1; do\n" +
                "  i=$((i+1)); [ $i -ge 15 ] && break\n" +
                "  sleep 0.2\n" +
                "done\n" +
                "echo KILLED");
    }

    /**
     * 重启 lspd 守护进程：运行框架模块目录的 service.sh（复刻 reRunDaemon）。
     * 其内部 {@code unshare ... sh -c "$MODDIR/daemon --from-service ...&"} 拉起守护进程。
     */
    public boolean restartDaemon() {
        String dir = findModuleDir();
        if (dir == null) { Log.e(TAG, "module dir not found"); return false; }
        String sh = dir + "service.sh";
        String busybox = "/data/adb/magisk/busybox";
        String shell = "export PATH=/data/adb/magisk:/system/bin:/system/xbin:$PATH; " +
                "[ -x " + busybox + " ] && BB=\"" + busybox + "\" || BB=sh; " +
                "export ASH_STANDALONE=1; " +
                "$BB sh '" + sh + "' --system-server-max-retry=-1; " +
                "i=0; " +
                "until pidof lspd >/dev/null 2>&1; do " +
                "  i=$((i+1)); [ $i -ge 40 ] && break; " +
                "  sleep 0.2; " +
                "done; " +
                "(pidof lspd >/dev/null && echo DAEMON_UP) || echo DAEMON_DOWN";
        Result r = runRoot(shell);
        boolean up = r.output.contains("DAEMON_UP");
        Log.i(TAG, "restartDaemon: " + r.output);
        return up;
    }

    /** 强制停止目标应用（root），下次打开即为注入后的全新进程。 */
    public boolean forceStop(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        Result r = runRoot("am force-stop '" + pkg + "'; echo FS_DONE");
        return r.output.contains("FS_DONE");
    }
}
