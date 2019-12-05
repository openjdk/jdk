/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.File;
import java.nio.file.Path;

/**
 * Helper class which contains functions to get different system
 * dependent paths used by tests
 */
public class JPackagePath {

    // Return path to test src adjusted to location of caller
    public static String getTestSrcRoot() {
        return JPackageHelper.TEST_SRC_ROOT;
    }

    // Return path to calling test
    public static String getTestSrc() {
        return JPackageHelper.TEST_SRC;
    }

    // Returns path to generate test application
    public static String getApp() {
        return getApp("test");
    }

    public static String getApp(String name) {
        return getAppSL(name, name);
    }

    // Returns path to generate test application icon
    public static String getAppIcon() {
        return getAppIcon("test");
    }

    public static String getAppIcon(String name) {
        if (JPackageHelper.isWindows()) {
            return Path.of("output", name, name + ".ico").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", name + ".app",
                    "Contents", "Resources", name + ".icns").toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", name, "lib", name + ".png").toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to generate secondary launcher of given application
    public static String getAppSL(String sl) {
        return getAppSL("test", sl);
    }

    public static String getAppSL(String app, String sl) {
        if (JPackageHelper.isWindows()) {
            return Path.of("output", app, sl + ".exe").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", app + ".app",
                    "Contents", "MacOS", sl).toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", app, "bin", sl).toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to test application cfg file
    public static String getAppCfg() {
        return getAppCfg("test");
    }

    public static String getAppCfg(String name) {
         if (JPackageHelper.isWindows()) {
            return Path.of("output", name, "app", name + ".cfg").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", name + ".app",
                    "Contents", "app", name + ".cfg").toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", name, "lib", "app", name + ".cfg").toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path including executable to java in image runtime folder
    public static String getRuntimeJava() {
        return getRuntimeJava("test");
    }

    public static String getRuntimeJava(String name) {
        if (JPackageHelper.isWindows()) {
            return Path.of(getRuntimeBin(name), "java.exe").toString();
        }
        return Path.of(getRuntimeBin(name), "java").toString();
    }

    // Returns output file name generate by test application
    public static String getAppOutputFile() {
        return "appOutput.txt";
    }

    // Returns path to bin folder in image runtime
    public static String getRuntimeBin() {
        return getRuntimeBin("test");
    }

    public static String getRuntimeBin(String name) {
        if (JPackageHelper.isWindows()) {
            return Path.of("output", name, "runtime", "bin").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", name + ".app",
                    "Contents", "runtime",
                    "Contents", "Home", "bin").toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", name, "lib", "runtime", "bin").toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    public static String getOSXInstalledApp(String testName) {
        return File.separator + "Applications"
                + File.separator + testName + ".app"
                + File.separator + "Contents"
                + File.separator + "MacOS"
                + File.separator + testName;
    }

    public static String getOSXInstalledApp(String subDir, String testName) {
        return File.separator + "Applications"
                + File.separator + subDir
                + File.separator + testName + ".app"
                + File.separator + "Contents"
                + File.separator + "MacOS"
                + File.separator + testName;
    }

    // Returs path to test license file
    public static String getLicenseFilePath() {
        String path = JPackagePath.getTestSrcRoot()
                + File.separator + "resources"
                + File.separator + "license.txt";

        return path;
    }
}
