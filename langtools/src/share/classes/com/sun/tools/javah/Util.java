/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


package com.sun.tools.javah;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ResourceBundle;
import java.text.MessageFormat;
import java.util.MissingResourceException;

/**
 * Messages, verbose and error handling support.
 *
 * For errors, the failure modes are:
 *      error -- User did something wrong
 *      bug   -- Bug has occurred in javah
 *      fatal -- We can't even find resources, so bail fast, don't localize
 *
 */
public class Util {

    /*
     * Help for verbosity.
     */
    public static boolean verbose = false;

    public static void log(String s) {
        System.out.println(s);
    }


    /*
     * Help for loading localized messages.
     */
    private static ResourceBundle m;

    private static void initMessages() {
        try {
            m=ResourceBundle.getBundle("com.sun.tools.javah.resources.l10n");
        } catch (MissingResourceException mre) {
            fatal("Error loading resources.  Please file a bug report.", mre);
        }
    }

    public static String getText(String key) {
        return getText(key, null, null);
    }

    private static String getText(String key, String a1, String a2){
        if (m == null)
            initMessages();
        try {
            return MessageFormat.format(m.getString(key),
                                        new Object[] { a1, a2 });
        } catch (MissingResourceException e) {
            fatal("Key " + key + " not found in resources.", e);
        }
        return null; /* dead code */
    }

    /*
     * Usage message.
     */
    public static void usage(int exitValue) {
        if (exitValue == 0) {
            System.out.println(getText("usage"));
        } else {
            System.err.println(getText("usage"));
        }
        System.exit(exitValue);
    }

    public static void version() {
        System.out.println(getText("javah.version",
                                   System.getProperty("java.version"), null));
        System.exit(0);
    }

    /*
     * Failure modes.
     */
    public static void bug(String key) {
        bug(key, null);
    }

    public static void bug(String key, Exception e) {
        if (e != null)
            e.printStackTrace();
        System.err.println(getText(key));
        System.err.println(getText("bug.report"));
        System.exit(11);
    }

    public static void error(String key) {
        error(key, null);
    }

    public static void error(String key, String a1) {
        error(key, a1, null);
    }

    public static void error(String key, String a1, String a2) {
        error(key, a1, a2, false);
    }

    public static void error(String key, String a1, String a2,
                             boolean showUsage) {
        System.err.println("Error: " + getText(key, a1, a2));
        if (showUsage)
            usage(15);
        System.exit(15);
    }


    private static void fatal(String msg) {
        fatal(msg, null);
    }

    private static void fatal(String msg, Exception e) {
        if (e != null) {
            e.printStackTrace();
        }
        System.err.println(msg);
        System.exit(10);
    }

    /*
     * Support for platform specific things in javah, such as pragma
     * directives, exported symbols etc.
     */
    static private ResourceBundle platform = null;

    /*
     * Set when platform has been initialized.
     */
    static private boolean platformInit = false;

    static String getPlatformString(String key) {
        if (!platformInit) {
            initPlatform();
            platformInit = true;
        }
        if (platform == null)
            return null;
        try {
            return platform.getString(key);
        } catch (MissingResourceException mre) {
            return null;
        }
    }

    private static void initPlatform() {
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows")) {
            os = "win32";
        } else if (os.indexOf("Linux") >= 0) {
            os = "Linux";
        }
        String arch = System.getProperty("os.arch");
        String resname = "com.sun.tools.javah.resources." + os + "_" + arch;
        try {
            platform=ResourceBundle.getBundle(resname);
        } catch (MissingResourceException mre) {
            // fatal("Error loading resources.  Please file a bug report.", mre);
        }
    }
}
