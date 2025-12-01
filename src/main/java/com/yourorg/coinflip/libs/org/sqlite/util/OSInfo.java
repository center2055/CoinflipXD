package com.yourorg.coinflip.libs.org.sqlite.util;

import java.util.HashMap;
import java.util.Locale;

/**
 * Safe replacement for sqlite-jdbc OS detection without executing system commands.
 * Relies only on JVM system properties to determine OS/arch.
 */
public final class OSInfo {

    protected static ProcessRunner processRunner = new ProcessRunner();

    public static final String X86 = "x86";
    public static final String X86_64 = "x86_64";
    public static final String IA64_32 = "ia64_32";
    public static final String IA64 = "ia64";
    public static final String PPC = "ppc";
    public static final String PPC64 = "ppc64";

    private static final HashMap<String, String> archMapping = new HashMap<>();

    static {
        archMapping.put("i386", X86);
        archMapping.put("i486", X86);
        archMapping.put("i586", X86);
        archMapping.put("i686", X86);
        archMapping.put("x86", X86);
        archMapping.put("amd64", X86_64);
        archMapping.put("x86_64", X86_64);
        archMapping.put("ia64", IA64);
        archMapping.put("ia64w", IA64);
        archMapping.put("ia64n", IA64_32);
        archMapping.put("ppc", PPC);
        archMapping.put("ppc64", PPC64);
        archMapping.put("aarch64", "aarch64");
        archMapping.put("arm64", "aarch64");
        archMapping.put("arm", "arm");
    }

    public static void main(String[] args) {
        System.out.println(getNativeLibFolderPathForCurrentOS());
    }

    public static String getNativeLibFolderPathForCurrentOS() {
        String os = translateOSNameToFolderName(getOSName());
        String arch = translateArchNameToFolderName(getArchName());
        return os + "/" + arch;
    }

    public static String getOSName() {
        return System.getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH);
    }

    public static boolean isAndroid() {
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ENGLISH);
        String vm = System.getProperty("java.vm.name", "").toLowerCase(Locale.ENGLISH);
        String vendor = System.getProperty("java.vm.vendor", "").toLowerCase(Locale.ENGLISH);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        return runtime.contains("android") || vm.contains("dalvik") || vendor.contains("android") || os.contains("android");
    }

    public static boolean isAndroidRuntime() {
        return isAndroid();
    }

    public static boolean isAndroidTermux() {
        return false;
    }

    public static boolean isMusl() {
        return false;
    }

    static String getHardwareName() {
        return System.getProperty("os.arch", "unknown");
    }

    static String resolveArmArchType() {
        String arch = getHardwareName().toLowerCase(Locale.ENGLISH);
        if (arch.startsWith("armv8") || arch.startsWith("aarch64") || arch.startsWith("arm64")) {
            return "aarch64";
        }
        if (arch.startsWith("armv7")) {
            return "armv7";
        }
        if (arch.startsWith("armv6")) {
            return "armv6";
        }
        if (arch.startsWith("arm")) {
            return "arm";
        }
        return arch;
    }

    public static String getArchName() {
        String arch = getHardwareName().toLowerCase(Locale.ENGLISH);
        String mapped = archMapping.get(arch);
        if (mapped != null) {
            return mapped;
        }
        if (arch.startsWith("arm")) {
            return resolveArmArchType();
        }
        return arch;
    }

    static String translateOSNameToFolderName(String osName) {
        if (osName.contains("windows")) {
            return "Windows";
        }
        if (osName.contains("mac") || osName.contains("os x") || osName.contains("darwin")) {
            return "Mac";
        }
        if (osName.contains("linux")) {
            return "Linux";
        }
        if (osName.contains("freebsd")) {
            return "FreeBSD";
        }
        if (osName.contains("openbsd")) {
            return "OpenBSD";
        }
        if (osName.contains("sunos") || osName.contains("solaris")) {
            return "SunOS";
        }
        if (osName.contains("aix")) {
            return "AIX";
        }
        return osName.replaceAll("\\s", "_");
    }

    static String translateArchNameToFolderName(String archName) {
        String key = archName.toLowerCase(Locale.ENGLISH);
        String mapped = archMapping.getOrDefault(key, key);
        return mapped.replaceAll("[^A-Za-z0-9_]", "");
    }
}
