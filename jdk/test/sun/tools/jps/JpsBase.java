/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URL;
import java.util.List;

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;

/**
 * The base class for testing the jps utility.
 * The test sequence is to start jps with different combinations of arguments
 * and verify the output contains proper values.
 */
public final class JpsBase {

    private static final String shortProcessName;
    private static final String fullProcessName;

    /**
     * The jps output should contain processes' names
     * (except when jps is started in quite mode).
     * The expected name of the test process is prepared here.
     */
    static {
        URL url = JpsBase.class.getResource("JpsBase.class");
        boolean isJar = url.getProtocol().equals("jar");

        if (isJar) {
            shortProcessName = JpsBase.class.getSimpleName() + ".jar";
            String urlPath = url.getPath();
            File jar = new File(urlPath.substring(urlPath.indexOf("file:") + 5, urlPath.indexOf("jar!") + 3));
            fullProcessName = jar.getAbsolutePath();
        } else {
            shortProcessName = JpsBase.class.getSimpleName();
            fullProcessName = JpsBase.class.getName();
        }
    }

    public static void main(String[] args) throws Exception {
        long pid = ProcessTools.getProcessId();

        List<List<JpsHelper.JpsArg>> combinations = JpsHelper.JpsArg.generateCombinations();
        for (List<JpsHelper.JpsArg> combination : combinations) {
            OutputAnalyzer output = JpsHelper.jps(JpsHelper.JpsArg.asCmdArray(combination));
            output.shouldHaveExitValue(0);

            boolean isQuiet = false;
            boolean isFull = false;
            String pattern;
            for (JpsHelper.JpsArg jpsArg : combination) {
                switch (jpsArg) {
                case q:
                    // If '-q' is specified output should contain only a list of local VM identifiers:
                    // 30673
                    isQuiet = true;
                    JpsHelper.verifyJpsOutput(output, "^\\d+$");
                    output.shouldContain(Long.toString(pid));
                    break;
                case l:
                    // If '-l' is specified output should contain the full package name for the application's main class
                    // or the full path name to the application's JAR file:
                    // 30673 /tmp/jtreg/jtreg-workdir/scratch/JpsBase.jar ...
                    isFull = true;
                    pattern = "^" + pid + "\\s+" + replaceSpecialChars(fullProcessName) + ".*";
                    output.shouldMatch(pattern);
                    break;
                case m:
                    // If '-m' is specified output should contain the arguments passed to the main method:
                    // 30673 JpsBase monkey ...
                    for (String arg : args) {
                        pattern = "^" + pid + ".*" + replaceSpecialChars(arg) + ".*";
                        output.shouldMatch(pattern);
                    }
                    break;
                case v:
                    // If '-v' is specified output should contain VM arguments:
                    // 30673 JpsBase -Xmx512m -XX:+UseParallelGC -XX:Flags=/tmp/jtreg/jtreg-workdir/scratch/vmflags ...
                    for (String vmArg : JpsHelper.getVmArgs()) {
                        pattern = "^" + pid + ".*" + replaceSpecialChars(vmArg) + ".*";
                        output.shouldMatch(pattern);
                    }
                    break;
                case V:
                    // If '-V' is specified output should contain VM flags:
                    // 30673 JpsBase +DisableExplicitGC ...
                    pattern = "^" + pid + ".*" + replaceSpecialChars(JpsHelper.VM_FLAG) + ".*";
                    output.shouldMatch(pattern);
                    break;
                }

                if (isQuiet) {
                    break;
                }
            }

            if (!isQuiet) {
                // Verify output line by line.
                // Output should only contain lines with pids after the first line with pid.
                JpsHelper.verifyJpsOutput(output, "^\\d+\\s+.*");
                if (!isFull) {
                    pattern = "^" + pid + "\\s+" + replaceSpecialChars(shortProcessName);
                    if (combination.isEmpty()) {
                        // If no arguments are specified output should only contain
                        // pid and process name
                        pattern += "$";
                    } else {
                        pattern += ".*";
                    }
                    output.shouldMatch(pattern);
                }
            }
        }
    }

    private static String replaceSpecialChars(String str) {
        String tmp = str.replace("\\", "\\\\");
        tmp = tmp.replace("+", "\\+");
        tmp = tmp.replace(".", "\\.");
        tmp = tmp.replace("\n", "\\\\n");
        tmp = tmp.replace("\r", "\\\\r");
        return tmp;
    }

}
