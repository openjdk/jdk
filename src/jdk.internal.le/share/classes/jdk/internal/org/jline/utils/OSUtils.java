/*
 * Copyright (c) 2002-2016, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.io.File;

public class OSUtils {

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

    @Deprecated
    public static final boolean IS_MINGW = IS_WINDOWS
            && System.getenv("MSYSTEM") != null
            && System.getenv("MSYSTEM").startsWith("MINGW");

    public static final boolean IS_MSYSTEM = IS_WINDOWS
            && System.getenv("MSYSTEM") != null
            && (System.getenv("MSYSTEM").startsWith("MINGW")
                    || System.getenv("MSYSTEM").equals("MSYS"));

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
        String path = System.getenv("PATH");
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
