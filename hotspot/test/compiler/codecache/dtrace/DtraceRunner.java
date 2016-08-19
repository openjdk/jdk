/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package compiler.codecache.dtrace;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DtraceRunner {

    private static final String DTRACE_DEFAULT_PATH = "/usr/sbin/dtrace";
    private static final String DTRACE_PATH_PROPERTY
            = "com.oracle.test.dtrace.path";
    private static final String OUTPUT_FILE_DTRACE_OPTION = "o";
    private static final String RUN_COMMAND_DTRACE_OPTION = "c";
    private static final String RUN_SCRIPT_DTRACE_OPTION = "s";
    private static final String ALLOW_ZERO_PROBE_DESCRIPTION_DTRACE_OPTION = "Z";
    private static final String DTRACE_OPTION_PREFIX = "-";
    public static final String PERMIT_DESTRUCTIVE_ACTIONS_DTRACE_OPTION = "w";
    public static final String DTRACE_OUT_LOG = "dtrace.out";

    private final String dtraceExecutable;

    public DtraceRunner() {
        dtraceExecutable = getDtracePath();
    }

    private List<String> getLaunchCmd(String java, String javaOpts,
            String execClass, String testArgs, String dtraceScript,
            String dtraceAddOpts) {
        Asserts.assertTrue(!java.matches("\\s"), "Current dtrace implementation"
                + " can't handle whitespaces in application path");
        List<String> result = new ArrayList<>();
        result.add(dtraceExecutable);
        result.add(DTRACE_OPTION_PREFIX + System.getProperty("sun.arch.data.model"));
        result.add(DTRACE_OPTION_PREFIX
                + ALLOW_ZERO_PROBE_DESCRIPTION_DTRACE_OPTION
                + ((dtraceAddOpts == null) ? "" : dtraceAddOpts)
                + RUN_SCRIPT_DTRACE_OPTION); // run_script should be last one
        result.add(dtraceScript);
        result.add(DTRACE_OPTION_PREFIX + OUTPUT_FILE_DTRACE_OPTION);
        result.add(DTRACE_OUT_LOG);
        result.add(DTRACE_OPTION_PREFIX + RUN_COMMAND_DTRACE_OPTION);
        result.add(java + " " + javaOpts + " " + execClass + " " + testArgs);
        return result;
    }

    private void backupLogFile(File file) {
        if (file.exists()) {
            file.renameTo(new File(file.getPath() + ".bak"));
        }
    }

    public void runDtrace(String java, String javaOpts, String execClass,
            String testArgs, String dtraceScript, String dtraceAddOpts,
            DtraceResultsAnalyzer analyzer) {
        backupLogFile(new File(DTRACE_OUT_LOG));
        ProcessBuilder pbuilder = new ProcessBuilder(
                getLaunchCmd(java, javaOpts, execClass, testArgs,
                        dtraceScript, dtraceAddOpts));
        OutputAnalyzer oa;
        try {
            oa = new OutputAnalyzer(pbuilder.start());
        } catch (IOException e) {
            throw new Error("TESTBUG: Can't start process", e);
        }
        analyzer.analyze(oa, DTRACE_OUT_LOG);
    }

    public static boolean dtraceAvailable() {
        String path = getDtracePath();
        if (path == null) {
            return false;
        }
        // now we'll launch dtrace to trace itself just to be sure it works
        // and have all additional previleges set
        ProcessBuilder pbuilder = new ProcessBuilder(path, path);
        try {
            OutputAnalyzer oa = new OutputAnalyzer(pbuilder.start());
            if (oa.getExitValue() != 0) {
                return false;
            }
        } catch (IOException e) {
            throw new Error("Couldn't launch dtrace", e);
        }
        return true;
    }

    private static String getDtracePath() {
        String propPath = System.getProperty(DTRACE_PATH_PROPERTY);
        if (propPath != null && new File(propPath).exists()) {
            return propPath;
        } else if (new File(DTRACE_DEFAULT_PATH).exists()) {
            return DTRACE_DEFAULT_PATH;
        }
        return null;
    }
}
