/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8271003
 * @summary CLASSPATH env variable setting should not be truncated in a hs err log.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run driver ClassPathEnvVar
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.internal.misc.Unsafe;

public class ClassPathEnvVar {
    private static final String pathSep = File.pathSeparator;
    private static final String sep = File.separator;
    private static final String cp_env = "CLASSPATH";
    private static final String end_path = "end-path";

    private static class Crasher {
        public static void main(String[] args) {
            Unsafe.getUnsafe().putInt(0L, 0);
        }
    }

    public static void main(String[] args) throws Exception {
        OutputAnalyzer output = runCrasher("-XX:-CreateCoredumpOnCrash").shouldContain("CreateCoredumpOnCrash turned off, no core file dumped")
                                             .shouldNotHaveExitValue(0);

        checkErrorLog(output);

    }
    private static OutputAnalyzer runCrasher(String option) throws Exception {
        ProcessBuilder pb = 
            ProcessTools.createJavaProcessBuilder(
            "-Xmx128m", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED", option, Crasher.class.getName());

        // Obtain the CLASSPATH setting and expand it to more than 2000 chars.
        Map<String, String> envMap = pb.environment();
        Set<String> keys = envMap.keySet();
        String cp = envMap.get(cp_env);
        if (cp == null) {
            cp = "this" + sep + "is" + sep + "dummy" + sep + "path";
        }
        while (cp.length() < 2000) {
            cp += pathSep + cp;
        }
        cp += pathSep + end_path;
        envMap.put(cp_env, cp);

        return new OutputAnalyzer(pb.start());
    }

    private static void checkErrorLog(OutputAnalyzer output) throws Exception {
        String hs_err_file = output.firstMatch("# *(\\S*hs_err_pid\\d+\\.log)", 1);
        System.out.println("    hs_err_file " + hs_err_file);
        File f = new File(hs_err_file);
        String absPath = f.getAbsolutePath();
        if (!f.exists()) {
            throw new RuntimeException("hs err log missing at " + absPath);
        }

        String cp_line = null;
        try (
            // Locate the line begins with "CLASSPATH".
            FileInputStream fis = new FileInputStream(f);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(cp_env)) {
                    cp_line = line;
                    break;
                }
            }
        }

        if (cp_line == null) {
            throw new RuntimeException("CLASSPATH setting not found in hs err log: " + absPath); 
        }

        // Check if the CLASSPATH line has been truncated.
        if (!cp_line.endsWith(end_path)) {
            throw new RuntimeException("CLASSPATH was truncated in the hs err log: " + absPath);
        }
    }
}
