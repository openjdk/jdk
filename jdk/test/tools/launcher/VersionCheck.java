/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @summary validate and test -version, -fullversion, and internal
 * @compile VersionCheck.java
 * @run main VersionCheck
 */

import java.lang.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class VersionCheck {

    private static String javaBin;

    // A known set of programs we know for sure will behave correctly.
    private static String[] programs = new String[]{
        "appletviewer",
        "extcheck",
        "idlj",
        "jar",
        "jarsigner",
        "javac",
        "javadoc",
        "javah",
        "javap",
        "jconsole",
        "jdb",
        "jhat",
        "jinfo",
        "jmap",
        "jps",
        "jstack",
        "jstat",
        "jstatd",
        "keytool",
        "native2ascii",
        "orbd",
        "pack200",
        "policytool",
        "rmic",
        "rmid",
        "rmiregistry",
        "schemagen",
        "serialver",
        "servertool",
        "tnameserv",
        "wsgen",
        "wsimport",
        "xjc"
    };

    // expected reference strings
    static String refVersion;
    static String refFullVersion;

    private static List<String> getProcessStreamAsList(boolean javaDebug,
                                                       String... argv) {
        List<String> out = new ArrayList<String>();
        List<String> javaCmds = new ArrayList<String>();

        String prog = javaBin + File.separator + argv[0];
        if (System.getProperty("os.name").startsWith("Windows")) {
            prog = prog.concat(".exe");
        }
        javaCmds.add(prog);
        for (int i = 1; i < argv.length; i++) {
            javaCmds.add(argv[i]);
        }

        ProcessBuilder pb = new ProcessBuilder(javaCmds);
        Map<String, String> env = pb.environment();
        if (javaDebug) {
            env.put("_JAVA_LAUNCHER_DEBUG", "true");
        }
        try {
            Process p = pb.start();
            BufferedReader r = (javaDebug) ?
                new BufferedReader(new InputStreamReader(p.getInputStream())) :
                new BufferedReader(new InputStreamReader(p.getErrorStream())) ;

            String s = r.readLine();
            while (s != null) {
                out.add(s.trim());
                s = r.readLine();
            }
            p.waitFor();
            p.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex.getMessage());
        }
        return out;
    }

    static String getVersion(String... argv) {
        List<String> alist = getProcessStreamAsList(false, argv);
        if (alist.size() == 0) {
            throw new AssertionError("unexpected process returned null");
        }
        StringBuilder out = new StringBuilder();
        // remove the HotSpot line
        for (String x : alist) {
            if (!x.contains("HotSpot")) {
                out = out.append(x + "\n");
            }
        }
        return out.toString();
    }

    static boolean compareVersionStrings() {
        int failcount = 0;
        for (String x : programs) {
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

        List<String> alist = getProcessStreamAsList(true, "java", "-version");

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
        String javaHome = System.getProperty("java.home");
        if (javaHome.endsWith("jre")) {
            javaHome = new File(javaHome).getParent();
        }
        javaBin = javaHome + File.separator + "bin";
        refVersion = getVersion("java", "-version");
        refFullVersion = getVersion("java", "-fullversion");
    }

    public static void main(String[] args) {
        init();
        if (compareVersionStrings() && compareInternalStrings()) {
            System.out.println("All Version string comparisons: PASS");
        } else {
            throw new AssertionError("Some tests failed");
        }
    }
}
