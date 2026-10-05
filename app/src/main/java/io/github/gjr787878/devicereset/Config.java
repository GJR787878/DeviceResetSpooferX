package io.github.gjr787878.devicereset;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 模块配置管理。
 * 配置存在模块自己的SharedPreferences中，不在目标APP目录，
 * 所以清除目标APP数据不会影响模块配置。
 */
public class Config {
    private static final String PREFS_NAME = "devicereset_config";
    private static final String KEY_TARGET_PACKAGES = "target_packages";
    /** 已手动设置伪装身份的包名集合（身份主存储，模块通过 XSharedPreferences 读取，UI 无需 Root 读写） */
    private static final String KEY_IDENTITY_PACKAGES = "identity_packages";
    private static final String KEY_IDENTITY_PREFIX = "identity_";
    private static final String KEY_AUTO_RESET = "auto_reset_on_clear";
    private static final String KEY_HOOK_ANDROID_ID = "hook_android_id";
    private static final String KEY_HOOK_AD_ID = "hook_ad_id";
    private static final String KEY_HOOK_IMEI = "hook_imei";
    private static final String KEY_HOOK_BUILD = "hook_build_info";
    private static final String KEY_HOOK_MAC = "hook_mac";
    private static final String KEY_HOOK_GSF = "hook_gsf_id";
    private static final String KEY_HOOK_CARRIER = "hook_carrier";

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取所有目标应用包名。
     * 注意：在Xposed hook进程中调用时，context是目标APP的context，
     * 无法直接读取模块的SharedPreferences。所以目标包名列表通过
     * XSharedPreferences读取，这里提供给配置界面使用。
     */
    public static Set<String> getTargetPackages(Context context) {
        return getPrefs(context).getStringSet(KEY_TARGET_PACKAGES, new HashSet<>());
    }

    public static void setTargetPackages(Context context, Set<String> packages) {
        getPrefs(context).edit().putStringSet(KEY_TARGET_PACKAGES, packages).apply();
        updateTargetsFile(context, packages);
    }

    public static void addTargetPackage(Context context, String packageName) {
        Set<String> packages = new HashSet<>(getTargetPackages(context));
        packages.add(packageName);
        setTargetPackages(context, packages);
    }

    public static void removeTargetPackage(Context context, String packageName) {
        Set<String> packages = new HashSet<>(getTargetPackages(context));
        packages.remove(packageName);
        setTargetPackages(context, packages);
    }

    public static boolean isTargetPackage(Context context, String packageName) {
        return getTargetPackages(context).contains(packageName);
    }

    /**
     * 把目标列表同步到模块私有 files/targets.txt（chmod 666，任何进程可读）。
     * 用途：LSPosed 勾选「系统框架」后模块注入所有进程，MainHook 读此文件
     * 判断目标，实现「应用内选目标即生效」，完全不依赖 LSPosed 作用域同步。
     */
    private static void updateTargetsFile(Context context, Set<String> packages) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String p : packages) sb.append(p).append('\n');
            java.io.File f = new java.io.File(context.getFilesDir(), "targets.txt");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();
            f.setReadable(true, false); // chmod 644，hook 进程可读
        } catch (Throwable ignored) {
        }
        // 关键：目录链也必须放开执行/读权限，否则被注入的目标进程进不了
        // /data/data/模块/files 目录，readTargetsFile 必败 → 全局注入全部跳过。
        // （有 root 才有效；无 root 时 targets 机制不可用，静默跳过不打扰）
        try {
            Process su = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(su.getOutputStream());
            os.writeBytes("chmod 755 /data/data/" + context.getPackageName() + "\n");
            os.writeBytes("chmod 755 /data/data/" + context.getPackageName() + "/files\n");
            os.writeBytes("chmod 666 /data/data/" + context.getPackageName() + "/files/targets.txt\n");
            os.writeBytes("exit\n");
            os.flush();
            su.waitFor();
        } catch (Throwable ignored) {
        }
    }

    // ===== 身份主存储（模块配置，手动触发写入，UI 无需 Root） =====

    /** 已手动设置伪装身份的包名集合 */
    public static Set<String> getIdentityPackages(Context context) {
        return getPrefs(context).getStringSet(KEY_IDENTITY_PACKAGES, new HashSet<>());
    }

    public static boolean isIdentityConfigured(Context context, String packageName) {
        return getIdentityPackages(context).contains(packageName);
    }

    /** 读取某应用的伪装身份 JSON（未设置返回 null） */
    public static String getIdentity(Context context, String packageName) {
        return getPrefs(context).getString(KEY_IDENTITY_PREFIX + packageName, null);
    }

    /** 写入某应用的伪装身份 JSON（手动触发：点随机/保存） */
    public static void setIdentity(Context context, String packageName, String json) {
        Set<String> pkgs = new HashSet<>(getIdentityPackages(context));
        pkgs.add(packageName);
        getPrefs(context).edit()
                .putStringSet(KEY_IDENTITY_PACKAGES, pkgs)
                .putString(KEY_IDENTITY_PREFIX + packageName, json)
                .apply();
    }

    /** 删除某应用的伪装身份（长按删除目标时调用） */
    public static void removeIdentity(Context context, String packageName) {
        Set<String> pkgs = new HashSet<>(getIdentityPackages(context));
        pkgs.remove(packageName);
        getPrefs(context).edit()
                .putStringSet(KEY_IDENTITY_PACKAGES, pkgs)
                .remove(KEY_IDENTITY_PREFIX + packageName)
                .apply();
    }

    // 各开关的getter/setter
    public static boolean isAutoReset(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_RESET, true);
    }

    public static void setAutoReset(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_RESET, value).apply();
    }

    public static boolean isHookAndroidId(Context context) {
        return getPrefs(context).getBoolean(KEY_HOOK_ANDROID_ID, true);
    }

    public static void setHookAndroidId(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_ANDROID_ID, value).apply();
    }

    public static boolean isHookAdId(Context context) {
        return getPrefs(context).getBoolean(KEY_HOOK_AD_ID, true);
    }

    public static void setHookAdId(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_AD_ID, value).apply();
    }

    public static boolean isHookImei(Context context) {
        return getPrefs(context).getBoolean(KEY_HOOK_IMEI, true);
    }

    public static void setHookImei(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_IMEI, value).apply();
    }

    public static boolean isHookBuild(Context context) {
        return getPrefs(context).getBoolean(KEY_HOOK_BUILD, true);
    }

    public static void setHookBuild(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_BUILD, value).apply();
    }

    public static boolean isHookMac(Context context) {
        return getPrefs(context).getBoolean(KEY_HOOK_MAC, true);
    }

    public static void setHookMac(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_MAC, value).apply();
    }

    public static boolean isHookGsf(Context context) {
        return getPrefs(context).getBoolean(KEY_HOOK_GSF, true);
    }

    public static void setHookGsf(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_GSF, value).apply();
    }

    public static boolean isHookCarrier(Context context) {
        return getPrefs(context).getBoolean(KEY_HOOK_CARRIER, true);
    }

    public static void setHookCarrier(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_CARRIER, value).apply();
    }
}
