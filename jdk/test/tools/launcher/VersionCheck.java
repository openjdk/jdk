/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6545058 6611182
 * @summary validate and test -version, -fullversion, and internal, as well as
 *          sanity checks if a tool can be launched.
 * @compile VersionCheck.java
 * @run main VersionCheck
 */

import java.io.File;
import java.io.FileFilter;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VersionCheck extends TestHelper {

    // tools that do not accept -J-option
    static final String[] BLACKLIST_JOPTION = {
        "controlpanel",
        "jabswitch",
        "java-rmi",
        "java-rmi.cgi",
        "java",
        "javaw",
        "javaws",
        "jcontrol",
        "jmc",
        "jvisualvm",
        "packager",
        "unpack200",
        "wsimport"
    };

    // tools that do not accept -version
    static final String[] BLACKLIST_VERSION = {
        "appletviewer",
        "controlpanel",
        "extcheck",
        "jar",
        "jarsigner",
        "java-rmi",
        "java-rmi.cgi",
        "javadoc",
        "javaws",
        "jcmd",
        "jconsole",
        "jcontrol",
        "jdeps",
        "jinfo",
        "jmap",
        "jmc",
        "jps",
        "jrunscript",
        "jjs",
        "jsadebugd",
        "jstack",
        "jstat",
        "jstatd",
        "jvisualvm",
        "keytool",
        "kinit",
        "klist",
        "ktab",
        "native2ascii",
        "orbd",
        "pack200",
        "packager",
        "policytool",
        "rmic",
        "rmid",
        "rmiregistry",
        "schemagen", // returns error code 127
        "serialver",
        "servertool",
        "tnameserv",
        "unpack200",
        "wsgen",
        "wsimport",
        "xjc"
    };

    // expected reference strings
    static String refVersion;
    static String refFullVersion;

    static String getVersion(String... argv) {
        TestHelper.TestResult tr = doExec(argv);
        StringBuilder out = new StringBuilder();
        // remove the HotSpot line
        for (String x : tr.testOutput) {
            if (!x.matches(".*Client.*VM.*|.*Server.*VM.*")) {
                out = out.append(x + "\n");
            }
        }
        return out.toString();
    }

    /*
     * this tests if the tool can take a version string and returns
     * a 0 exit code, it is not possible to validate the contents
     * of the -version output as they are inconsistent.
     */
    static boolean testToolVersion() {
        TestResult tr = null;
        TestHelper.testExitValue = 0;
        for (File f : new File(JAVA_BIN).listFiles(new ToolFilter(BLACKLIST_VERSION))) {
            String x = f.getAbsolutePath();
            System.out.println("Testing (-version): " + x);
            tr = doExec(x, "-version");
            tr.checkPositive();
        }
        return TestHelper.testExitValue == 0;
    }

    static boolean compareJVersionStrings() {
        int failcount = 0;
        for (File f : new File(JAVA_BIN).listFiles(new ToolFilter(BLACKLIST_JOPTION))) {
            String x = f.getAbsolutePath();
            System.out.println("Testing (-J-version): " + x);
            String testStr;

            testStr = getVersion(x, "-J-version");
            if (refVersion.compareTo(testStr) != 0) {
                failcount++;
                System.out.println("Error: " + x +
                                   " fails -J-version comparison");
                System.out.println("Expected:");
                System.out.print(refVersion);
                System.out.println("Actual:");
                System.out.print(testStr);
            }

            testStr = getVersion(x, "-J-fullversion");
            if (refFullVersion.compareTo(testStr) != 0) {
                failcount++;
                System.out.println("Error: " + x +
                                   " fails -J-fullversion comparison");
                System.out.println("Expected:");
                System.out.print(refFullVersion);
                System.out.println("Actual:");
                System.out.print(testStr);
            }
        }
        System.out.println("Version Test: " + failcount);
        return failcount == 0;
    }

    static boolean compareInternalStrings() {
        int failcount = 0;
        String bStr = refVersion.substring(refVersion.lastIndexOf("build") +
                                           "build".length() + 1,
                                           refVersion.lastIndexOf(")"));

        String[] vStr = bStr.split("\\.|-|_");
        String jdkMajor = vStr[0];
        String jdkMinor = vStr[1];
        String jdkMicro = vStr[2];
        String jdkBuild = vStr[vStr.length - 1];

        String expectedDotVersion = "dotversion:" + jdkMajor + "." + jdkMinor;
        String expectedFullVersion = "fullversion:" + bStr;

        Map<String, String> envMap = new HashMap<>();
        envMap.put(TestHelper.JLDEBUG_KEY, "true");
        TestHelper.TestResult tr = doExec(envMap, javaCmd, "-version");
        List<String> alist = new ArrayList<>();
        alist.addAll(tr.testOutput);
        for (String x : tr.testOutput) {
            alist.add(x.trim());
        }
        if (!alist.contains(expectedDotVersion)) {
            System.out.println("Error: could not find " + expectedDotVersion);
            failcount++;
        }

        if (!alist.contains(expectedFullVersion)) {
            System.out.println("Error: could not find " + expectedFullVersion);
            failcount++;
        }
        System.out.println("Internal Strings Test: " + failcount);
        return failcount == 0;
    }

    // Initialize
    static void init() {
        refVersion = getVersion(javaCmd, "-version");
        refFullVersion = getVersion(javaCmd, "-fullversion");
    }

    public static void main(String[] args) {
        init();
        if (compareJVersionStrings() &&
                compareInternalStrings() &&
                testToolVersion()) {
            System.out.println("All Version string comparisons: PASS");
        } else {
            throw new AssertionError("Some tests failed");
        }
    }

    static class ToolFilter implements FileFilter {
        final Iterable<String> exclude ;
        protected ToolFilter(String... exclude) {
            List<String> tlist = new ArrayList<>();
            this.exclude = tlist;
            for (String x : exclude) {
                String str = x + ((isWindows) ? EXE_FILE_EXT : "");
                tlist.add(str.toLowerCase());
            }
        }
        @Override
        public boolean accept(File pathname) {
            if (!pathname.isFile() || !pathname.canExecute()) {
                return false;
            }
            String name = pathname.getName().toLowerCase();
            if (isWindows && !name.endsWith(EXE_FILE_EXT)) {
                return false;
            }
            for (String x : exclude) {
                if (name.endsWith(x)) {
                    return false;
                }
            }
            return true;
        }
    }
}
