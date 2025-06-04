/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8307478
 * @summary Test Instrumentation tracing is enabled with a system property
 * @library /test/lib
 * @run shell MakeJAR3.sh TraceUsageAgent 'Agent-Class: TraceUsageAgent' 'Can-Retransform-Classes: true'
 * @run junit TraceUsageTest
 */

import com.sun.tools.attach.VirtualMachine;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceUsageTest {
    private static final String JAVA_AGENT = "TraceUsageAgent.jar";

    // Instrumentation methods to test
    private static final String[] INSTRUMENTATION_METHODS = {
            "addTransformer",
            "retransformClasses",
            "redefineModule"
    };

    /**
     * If launched with the argument "attach" then it loads the java agent into the
     * current VM with the given options.
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("attach")) {
            String options = args[1];
            long pid = ProcessHandle.current().pid();
            VirtualMachine vm = VirtualMachine.attach(""+pid);
            try {
                vm.loadAgent(JAVA_AGENT, options);
            } finally {
                vm.detach();
            }
        }
    }

    /**
     * Test agent started on the command line with -javaagent.
     */
    @Test
    void testPremain() throws Exception {
        OutputAnalyzer outputAnalyzer = execute(
                "-javaagent:" + JAVA_AGENT + "=" + String.join(",", INSTRUMENTATION_METHODS),
                "-Djdk.instrument.traceUsage=true",
                "TraceUsageTest"
        );
        for (String mn : INSTRUMENTATION_METHODS) {
            String expected = "Instrumentation." + mn + " has been called by TraceUsageAgent";
            outputAnalyzer.shouldContain(expected);
        }
        outputAnalyzer.shouldContain("at TraceUsageAgent.premain");
    }

    /**
     * Test agent loaded into a running VM with the attach mechanism.
     */
    @Test
    void testAgentmain() throws Exception {
        OutputAnalyzer outputAnalyzer = execute(
                "-Djdk.attach.allowAttachSelf=true",
                "-Djdk.instrument.traceUsage=true",
                "TraceUsageTest",
                "attach",
                String.join(",", INSTRUMENTATION_METHODS)
        );
        for (String mn : INSTRUMENTATION_METHODS) {
            String expected = "Instrumentation." + mn + " has been called by TraceUsageAgent";
            outputAnalyzer.shouldContain(expected);
        }
        outputAnalyzer.shouldContain("at TraceUsageAgent.agentmain");
    }

    private OutputAnalyzer execute(String... command) throws Exception {
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(command)
                .outputTo(System.out)
                .errorTo(System.out);
        assertEquals(0, outputAnalyzer.getExitValue());
        return outputAnalyzer;
    }
}
