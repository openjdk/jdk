/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4531526
 * @summary Test that more than one debuggee cannot bind to same port
 *          at the same time.
 * @library /lib/testlibrary
 *
 * @build jdk.testlibrary.ProcessTools jdk.testlibrary.JDKToolLauncher jdk.testlibrary.Utils
 * @build VMConnection ExclusiveBind HelloWorld
 * @run main ExclusiveBind
 */
import java.net.ServerSocket;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.AttachingConnector;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.Utils;

public class ExclusiveBind {
    /*
     * Find a connector by name
     */
    private static Connector findConnector(String name) {
        List connectors = Bootstrap.virtualMachineManager().allConnectors();
        Iterator iter = connectors.iterator();
        while (iter.hasNext()) {
            Connector connector = (Connector)iter.next();
            if (connector.name().equals(name)) {
                return connector;
            }
        }
        return null;
    }

    /*
     * Launch (in server mode) a debuggee with the given address and
     * suspend mode.
     */
    private static ProcessBuilder prepareLauncher(String address, boolean suspend, String class_name) throws Exception {
        List<String> args = new ArrayList<>();
        for(String dbgOption : VMConnection.getDebuggeeVMOptions().split(" ")) {
            args.add(dbgOption);
        }
        String lib = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=";
        if (suspend) {
            lib += "y";
        } else {
            lib += "n";
        }
        lib += ",address=" + address;

        args.add(lib);
        args.add(class_name);

        return ProcessTools.createJavaProcessBuilder(args.toArray(new String[args.size()]));
    }

    /*
     * - pick a TCP port
     * - Launch a debuggee in server=y,suspend=y,address=${port}
     * - Launch a second debuggee in server=y,suspend=n with the same port
     * - Second debuggee should fail with an error (address already in use)
     * - For clean-up we attach to the first debuggee and resume it.
     */
    public static void main(String args[]) throws Exception {
        // find a free port
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        ss.close();

        String address = String.valueOf(port);

        // launch the first debuggee
        ProcessBuilder process1 = prepareLauncher(address, true, "HelloWorld");
        // start the debuggee and wait for the "ready" message
        Process p = ProcessTools.startProcess(
                "process1",
                process1,
                line -> line.equals("Listening for transport dt_socket at address: " + address),
                Math.round(5000 * Utils.TIMEOUT_FACTOR),
                TimeUnit.MILLISECONDS
        );

        // launch a second debuggee with the same address
        ProcessBuilder process2 = prepareLauncher(address, false, "HelloWorld");

        // get exit status from second debuggee
        int exitCode = ProcessTools.startProcess("process2", process2).waitFor();

        // clean-up - attach to first debuggee and resume it
        AttachingConnector conn = (AttachingConnector)findConnector("com.sun.jdi.SocketAttach");
        Map conn_args = conn.defaultArguments();
        Connector.IntegerArgument port_arg =
            (Connector.IntegerArgument)conn_args.get("port");
        port_arg.setValue(port);
        VirtualMachine vm = conn.attach(conn_args);
        vm.resume();

        // if the second debuggee ran to completion then we've got a problem
        if (exitCode == 0) {
            throw new RuntimeException("Test failed - second debuggee didn't fail to bind");
        } else {
            System.out.println("Test passed - second debuggee correctly failed to bind");
        }
    }
}
