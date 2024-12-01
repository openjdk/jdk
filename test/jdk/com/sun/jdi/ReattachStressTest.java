/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import jdk.test.lib.process.ProcessTools;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

/**
 * @test
 * @bug 8338708
 * @summary Stress test for reattaching to a debuggee
 * @library /test/lib
 * @modules jdk.jdi
 * @run driver ProcessAttachTest
 */

class ReattachStressTestTarg {
    public static void main(String args[]) throws Exception {
        System.out.println("Debuggee started");
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
    }
}

public class ReattachStressTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Test 1: Debuggee start with suspend=n");
        runTest("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n");

        System.out.println("Test 2: Debuggee start with suspend=y");
        runTest("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y");
    }

    private static void runTest(String jdwpArg) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                jdwpArg,
                "ReattachStressTestTarg");
        Process p = null;
        try {
            p = pb.start();

            // Read the first character of output to make sure we've waited until the
            // debuggee is ready. This will be the debug agent's "Listening..." message.
            InputStream is = p.getInputStream();
            is.read();

            // Attach a debugger
            tryDebug(p.pid(), is);
        } finally {
            p.destroyForcibly();
        }
    }

    private static void tryDebug(long pid, InputStream is) throws IOException,
            IllegalConnectorArgumentsException {
        // Get the ProcessAttachingConnector, which can attach using the pid of the debuggee.
        AttachingConnector ac = Bootstrap.virtualMachineManager().attachingConnectors()
                .stream()
                .filter(c -> c.name().equals("com.sun.jdi.ProcessAttach"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unable to locate ProcessAttachingConnector"));

        // Set the connector's "pid" argument to the pid of the debuggee.
        Map<String, Connector.Argument> args = ac.defaultArguments();
        Connector.StringArgument arg = (Connector.StringArgument)args.get("pid");
        arg.setValue("" + pid);

        // Loop that will repeatedly attach and detach from the same debuggee.
        for (int i = 0; i < 500; i++) {
            System.out.println(i + ": Debugger is attaching to: " + pid + " ...");

            // Attach to the debuggee.
            VirtualMachine vm = ac.attach(args);

            // Drain remaining "Listening..." output.  Otherwise too much
            // output will buffer up and the debuggee may block until it is cleared.
            while (is.available() > 0) {
                is.read();
            }

            // We've attached. Do some things that will send JDWP commands.
            System.out.println("Attached!");
            System.out.println("JVM name: " + vm.name());
            System.out.println("Num threads: " + vm.allThreads().size());

            // We're all done with this debugger connection.
            vm.dispose();

            // Wait for first char of next "Listening..." output.
            is.read();
        }
        System.out.println("Debugger done.");
    }
}
