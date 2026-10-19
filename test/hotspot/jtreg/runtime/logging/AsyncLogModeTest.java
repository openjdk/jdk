/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8392140
 * @summary Test command-line modes for asynchronous logging
 * @requires vm.flagless
 * @library /test/lib
 * @run driver AsyncLogModeTest
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class AsyncLogModeTest {
    // Emitted after the asynchronous logging buffers have been allocated.
    private static final String BUFFER_INITIALIZED = "AsyncLogBuffer estimates memory use:";

    private static OutputAnalyzer execute(List<String> arguments) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                arguments.toArray(new String[0]));
        return new OutputAnalyzer(pb.start());
    }

    private static void checkAsyncMode(boolean enabled, String... modes) throws Exception {
        List<String> arguments = new ArrayList<>();
        Collections.addAll(arguments, modes);
        arguments.add("-Xlog:logging=info");
        arguments.add("-version");

        OutputAnalyzer output = execute(arguments);
        output.shouldHaveExitValue(0);
        if (enabled) {
            output.shouldContain(BUFFER_INITIALIZED);
        } else {
            output.shouldNotContain(BUFFER_INITIALIZED);
        }
    }

    private static void checkInvalidMode() throws Exception {
        OutputAnalyzer output = execute(List.of("-Xlog:async:offfoo", "-version"));
        output.shouldNotHaveExitValue(0);
        output.shouldContain("Invalid -Xlog option '-Xlog:async:offfoo'");
    }

    private static void checkHelp() throws Exception {
        OutputAnalyzer output = execute(List.of("-Xlog:help"));
        output.shouldHaveExitValue(0);
        output.shouldContain("-Xlog:async[:[off|stall|drop]]");
    }

    public static void main(String[] args) throws Exception {
        checkAsyncMode(false, "-Xlog:async:off");
        checkAsyncMode(false, "-Xlog:async", "-Xlog:async:off");
        checkAsyncMode(false, "-Xlog:async:stall", "-Xlog:async:off");
        checkAsyncMode(true, "-Xlog:async:off", "-Xlog:async");
        checkAsyncMode(true, "-Xlog:async:stall");
        checkAsyncMode(true, "-Xlog:async:drop");
        checkInvalidMode();
        checkHelp();
    }
}
