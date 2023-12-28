/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertTrue;

public class UpcallTestHelper extends NativeTestHelper {

    public OutputAnalyzer runInNewProcess(Class<?> target, boolean useSpec, String... programArgs) throws IOException, InterruptedException {
        return runInNewProcess(target, useSpec, List.of(), List.of(programArgs));
    }

    public OutputAnalyzer runInNewProcess(Class<?> target, boolean useSpec, List<String> vmArgs, List<String> programArgs) throws IOException, InterruptedException {
        assert !target.isArray();

        List<String> command = new ArrayList<>(List.of(
            "--enable-native-access=ALL-UNNAMED",
            "-Djava.library.path=" + System.getProperty("java.library.path"),
            "-Djdk.internal.foreign.UpcallLinker.USE_SPEC=" + useSpec
        ));
        command.addAll(vmArgs);
        command.add(target.getName());
        command.addAll(programArgs);

        Process process = ProcessTools.createTestJavaProcessBuilder(command).start();

        long timeOut = (long) (Utils.TIMEOUT_FACTOR * 1L);
        boolean completed = process.waitFor(timeOut, TimeUnit.MINUTES);
        assertTrue(completed, "Time out while waiting for process");

        OutputAnalyzer output = new OutputAnalyzer(process);
        output.outputTo(System.out);
        output.errorTo(System.err);

        return output;
    }
}
