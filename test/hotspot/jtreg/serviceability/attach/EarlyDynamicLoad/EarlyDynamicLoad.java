/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AgentLoadException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;

/*
 * @test EarlyDynamicLoad
 * @summary Test that dynamic attach fails gracefully when the JVM is not in live phase.
 * @requires vm.jvmti
 * @library /test/lib
 * @run junit EarlyDynamicLoad
 */
public class EarlyDynamicLoad {
    private static final String EXPECTED_MESSAGE = "Dynamic agent loading is only permitted in the live phase";

    private static Process child;

    @BeforeAll
    static void startAndWaitChild() throws Exception {
        child = ProcessTools.createTestJavaProcessBuilder(
                        "-XX:+StartAttachListener",
                        "-agentpath:" + Utils.TEST_NATIVE_PATH + File.separator + System.mapLibraryName("EarlyDynamicLoad"),
                        "--version").start();

        // Wait until the process enters VMStartCallback
        try (InputStream is = child.getInputStream()) {
            is.read();
        }
    }

    @AfterAll
    static void stopChild() throws Exception {
        try (OutputStream os = child.getOutputStream()) {
            os.write(0);
        }

        if (!child.waitFor(5, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            throw new AssertionError("Timed out while waiting child process to complete");
        }

        OutputAnalyzer analyzer = new OutputAnalyzer(child);
        analyzer.shouldHaveExitValue(0);
    }

    @Test
    public void virtualMachine() throws Exception {
        try {
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(child.pid()));
            vm.loadAgent("some.jar");
            vm.detach();
            throw new AssertionError("Should have failed with AgentLoadException");
        } catch(AgentLoadException exception) {
            if (!exception.getMessage().contains(EXPECTED_MESSAGE)) {
                throw new AssertionError("Unexpected error message", exception);
            }
        }
    }

    @Test
    public void jcmd() throws Exception {
        PidJcmdExecutor executor = new PidJcmdExecutor(String.valueOf(child.pid()));
        OutputAnalyzer out = executor.execute("JVMTI.agent_load some.jar");

        out.shouldHaveExitValue(0);
        out.stdoutShouldContain(EXPECTED_MESSAGE);
    }
}
