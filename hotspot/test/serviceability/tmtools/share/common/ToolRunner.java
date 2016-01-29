/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * This class starts a process specified by the passed command line waits till
 * the process completes and returns the process exit code and stdout and stderr
 * output as ToolResults
 */
class ToolRunner {

    private final List<String> cmdArgs = new LinkedList<>();

    ToolRunner(String cmdLine) {
        StringTokenizer st = new StringTokenizer(cmdLine);
        while (st.hasMoreTokens()) {
            cmdArgs.add(st.nextToken());
        }
    }

    /**
     * Starts the process, waits for the process completion and returns the
     * results
     *
     * @return process results
     * @throws Exception if anything goes wrong
     */
    ToolResults runToCompletion() throws Exception {

        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
        OutputAnalyzer oa = ProcessTools.executeProcess(pb);

        return new ToolResults(oa.getExitValue(),
                stringToList(oa.getStdout()),
                stringToList(oa.getStderr()));

    }

    private static List<String> stringToList(String s) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(s));
        List<String> strings = new ArrayList<>();
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            strings.add(line);
        }
        reader.close();
        return strings;
    }
}
