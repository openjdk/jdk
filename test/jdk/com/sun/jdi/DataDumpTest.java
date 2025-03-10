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

import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.ClassPrepareRequest;

/**
 * @test
 * @bug  8332488
 * @summary Unit test for testing debug agent support for JVMTI.data_dump jcmd.
 *
 * @library /test/lib
 * @modules jdk.jdi
 * @run driver DataDumpTest
 */

class DataDumpTestTarg {
    public static void main(String args[]) throws Exception {
        // Write something that can be read by the driver
        System.out.println("Debuggee started");
    }
}

public class DataDumpTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Test 1: Debuggee start with datadump=y");
        runTest("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,datadump=y");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private static void runTest(String jdwpArg) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                jdwpArg,
                // Probably not required by this test, but best to include when using datadump
                "-XX:+StartAttachListener",
                "DataDumpTestTarg");
        Process p = null;
        OutputAnalyzer out = null;
        try {
            p = pb.start();
            InputStream is = p.getInputStream();

            // Read the first character of output to make sure we've waited until the
            // debuggee is ready. This will be the debug agent's "Listening..." message.
            char firstChar = (char)is.read();

            out = new OutputAnalyzer(p);

            // Attach a debugger and do the data dump. The data dump output will appear
            // in the debuggee output.
            attachAndDump(p.pid());

            out.waitFor(); // Wait for the debuggee to exit

            System.out.println("Debuggee output:");
            System.out.println(firstChar + out.getOutput());

            // All these strings are part of the debug agent data dump output.
            out.shouldHaveExitValue(0);
            out.shouldContain("Debuggee started");
            out.shouldContain("Debug Agent Data Dump");
            out.shouldContain("suspendAllCount: 0");
            out.shouldContain("ClassMatch: classPattern(DataDumpTestTarg)");
            out.shouldContain("Handlers for EI_VM_DEATH");
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
        }
    }

    private static void attachAndDump(long pid) throws IOException,
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

        // Attach to the debuggee.
        System.out.println("Debugger is attaching to: " + pid + " ...");
        VirtualMachine vm = ac.attach(args);

        // List all threads as a sanity check.
        System.out.println("Attached! Now listing threads ...");
        vm.allThreads().stream().forEach(System.out::println);

        // Request VM to trigger ClassPrepareRequest when DataDumpTestTarg class is prepared.
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter("DataDumpTestTarg");
        // Don't use SUSPEND_ALL here. That might prevent the data dump because the
        // Signal Dispatcher and Attach Listener threads will be suspended, and they
        // may be needed by the jcmd support.
        classPrepareRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        classPrepareRequest.enable();

        try {
            while (true) { // Exit when we get VMDisconnectedException
                EventSet eventSet = vm.eventQueue().remove();
                if (eventSet == null) {
                    continue;
                }
                for (Event event : eventSet) {
                    System.out.println("Received event: " + event);
                    if (event instanceof ClassPrepareEvent) {
                        ClassPrepareEvent evt = (ClassPrepareEvent) event;
                        ClassType classType = (ClassType) evt.referenceType();

                        // Run JVMTI.data_dump jcmd.
                        OutputAnalyzer out = new PidJcmdExecutor("" + pid).execute("JVMTI.data_dump");
                        out.waitFor();

                        // Verify the output of the jcmd. Note the actual dump is in the debuggee
                        // output, not in the jcmd output, so we don't check it here.
                        System.out.println("JVMTI.data_dump output:");
                        System.out.println(out.getOutput());
                        out.shouldContain("Command executed successfully");
                        out.shouldHaveExitValue(0);
                    }
                }
                eventSet.resume();
            }
        } catch (VMDisconnectedException e) {
            System.out.println("VM is now disconnected.");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
