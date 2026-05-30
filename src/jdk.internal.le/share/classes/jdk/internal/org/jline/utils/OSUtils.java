/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.io.File;

/**
 * Utility class for operating system detection and OS-specific operations.
 *
 * <p>
 * The OSUtils class provides constants and methods for detecting the current operating
 * system and performing OS-specific operations. It helps JLine components adapt their
 * behavior based on the platform they're running on, which is particularly important
 * for terminal handling where different operating systems have different terminal
 * implementations and capabilities.
 * </p>
 *
 * <p>
 * This class includes constants for detecting common operating systems:
 * </p>
 * <ul>
 *   <li>Windows - Including detection of Cygwin and MSYS environments</li>
 *   <li>Linux</li>
 *   <li>macOS (OSX)</li>
 *   <li>AIX</li>
 *   <li>Other Unix-like systems</li>
 * </ul>
 *
 * <p>
 * It also provides utility methods for working with file paths, environment variables,
 * and other OS-specific features that affect terminal behavior.
 * </p>
 *
 * <p>
 * This class is used throughout JLine to ensure correct behavior across different
 * platforms, particularly for terminal detection, path handling, and native library
 * loading.
 * </p>
 */
public class OSUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private OSUtils() {
        // Utility class
    }

    public static final boolean IS_LINUX =
            System.getProperty("os.name").toLowerCase().contains("linux");

    public static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

    public static final boolean IS_OSX =
            System.getProperty("os.name").toLowerCase().contains("mac");

    public static final boolean IS_AIX =
            System.getProperty("os.name").toLowerCase().contains("aix");

    public static final boolean IS_CYGWIN =
            IS_WINDOWS && System.getenv("PWD") != null && System.getenv("PWD").startsWith("/");

    public static final boolean IS_MSYSTEM = IS_WINDOWS && System.getenv("MSYSTEM") != null;

    public static final boolean IS_WSL = System.getenv("WSL_DISTRO_NAME") != null;

    public static final boolean IS_WSL1 = IS_WSL && System.getenv("WSL_INTEROP") == null;

    public static final boolean IS_WSL2 = IS_WSL && !IS_WSL1;

    public static final boolean IS_CONEMU = IS_WINDOWS && System.getenv("ConEmuPID") != null;

    public static String TTY_COMMAND;
    public static String STTY_COMMAND;
    public static String STTY_F_OPTION;
    public static String INFOCMP_COMMAND;
    public static String TEST_COMMAND;

    private static boolean isExecutable(File f) {
        return f.canExecute() && !f.isDirectory();
    }

    static {
        boolean cygwinOrMsys = OSUtils.IS_CYGWIN || OSUtils.IS_MSYSTEM;
        String suffix = cygwinOrMsys ? ".exe" : "";
        String tty = null;
        String stty = null;
        String sttyfopt = null;
        String infocmp = null;
        String test = null;
        String path = "/usr/bin" + File.pathSeparator + "/bin";//was: System.getenv("PATH");
        if (path != null) {
            String[] paths = path.split(File.pathSeparator);
            for (String p : paths) {
                File ttyFile = new File(p, "tty" + suffix);
                if (tty == null && isExecutable(ttyFile)) {
                    tty = ttyFile.getAbsolutePath();
                }
                File sttyFile = new File(p, "stty" + suffix);
                if (stty == null && isExecutable(sttyFile)) {
                    stty = sttyFile.getAbsolutePath();
                }
                File infocmpFile = new File(p, "infocmp" + suffix);
                if (infocmp == null && isExecutable(infocmpFile)) {
                    infocmp = infocmpFile.getAbsolutePath();
                }
                File testFile = new File(p, "test" + suffix);
                if (test == null && isExecutable(testFile)) {
                    test = testFile.getAbsolutePath();
                }
            }
        }
        if (tty == null) {
            tty = "tty" + suffix;
        }
        if (stty == null) {
            stty = "stty" + suffix;
        }
        if (infocmp == null) {
            infocmp = "infocmp" + suffix;
        }
        if (test == null) {
            test = "test" + suffix;
        }
        sttyfopt = IS_OSX ? "-f" : "-F";
        TTY_COMMAND = tty;
        STTY_COMMAND = stty;
        STTY_F_OPTION = sttyfopt;
        INFOCMP_COMMAND = infocmp;
        TEST_COMMAND = test;
    }
}
