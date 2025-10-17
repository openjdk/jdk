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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test TestEarlyDynamicLoad
 * @summary Test that dynamic attach fails gracefully when the JVM is not in live phase.
 * @requires vm.jvmti
 * @library /test/lib
 * @run junit TestEarlyDynamicLoad
 */
public class TestEarlyDynamicLoad {
    private static Process child;

    @BeforeAll
    static void startAndWaitChild() throws Exception {
        child = ProcessTools.createTestJavaProcessBuilder(
                "-XX:+StartAttachListener",
                "-agentpath:" + Utils.TEST_NATIVE_PATH + File.separator + System.mapLibraryName("EarlyDynamicLoad"),
                "-version").start();

        // Wait the process to enter VMStartCallback
        child.getInputStream().read();
    }

    @AfterAll
    static void stopChild() throws Exception {
        child.destroy();
        child.waitFor();
    }

    @Test
    public void virtualMachine() throws Exception {
        try {
            VirtualMachine vm = VirtualMachine.attach(child.pid() + "");
            vm.loadAgent("some.jar");
            vm.detach();
            throw new AssertionError("Should have failed with AgentLoadException");
        } catch(AgentLoadException exception) {
            if (!exception.getMessage().contains("Dynamic agent loading is only permitted in the live phase")) {
                throw new AssertionError("Unexpected error message", exception);
            }
        }
    }

    @Test
    public void jcmd() throws Exception {
        JDKToolLauncher jcmd = JDKToolLauncher.createUsingTestJDK("jcmd");
        jcmd.addToolArg(child.pid() + "");
        jcmd.addToolArg("JVMTI.agent_load");
        jcmd.addToolArg("some.jar");

        ProcessBuilder pb = new ProcessBuilder(jcmd.getCommand());
        OutputAnalyzer out = new OutputAnalyzer(pb.start());

        out.shouldHaveExitValue(0);
        out.stdoutShouldContain("Dynamic agent loading is only permitted in the live phase");
    }
}
