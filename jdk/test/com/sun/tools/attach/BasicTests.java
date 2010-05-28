/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * Unit test for Attach API. Attaches to the given VM and performs a number
 * unit tests.
 */
import com.sun.tools.attach.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.Properties;
import java.util.List;

public class BasicTests {
    public static void main(String args[]) throws Exception {
        String pid = args[0];
        String agent = args[1];
        String badagent = args[2];
        String redefineagent = args[3];

        System.out.println(" - Attaching to application ...");
        VirtualMachine vm = VirtualMachine.attach(pid);

        // Test 1 - read the system properties from the target VM and
        // check that property is set
        System.out.println(" - Test: system properties in target VM");
        Properties props = vm.getSystemProperties();
        String value = props.getProperty("attach.test");
        if (value == null || !value.equals("true")) {
            throw new RuntimeException("attach.test property not set");
        }
        System.out.println(" - attach.test property set as expected");

        // Test 1a - read the agent properties from the target VM.
        // By default, the agent property contains "sun.java.command",
        // "sun.jvm.flags", and "sun.jvm.args".
        // Just sanity check - make sure not empty.
        System.out.println(" - Test: agent properties in target VM");
        props = vm.getAgentProperties();
        if (props == null || props.size() == 0) {
            throw new RuntimeException("Agent properties is empty");
        }
        System.out.println(" - agent properties non-empty as expected");

        // Test 2 - attempt to load an agent that does not exist
        System.out.println(" - Test: Load an agent that does not exist");
        try {
            vm.loadAgent("SilverBullet.jar");
        } catch (AgentLoadException x) {
            System.out.println(" - AgentLoadException thrown as expected!");
        }

        // Test 3 - load an "bad" agent (agentmain throws an exception)
        System.out.println(" - Test: Load a bad agent");
        try {
            vm.loadAgent(badagent);
        } catch (AgentInitializationException x) {
            System.out.println(" - AgentInitializationException throws as expected!");
        }

        // Test 4 - detach from the VM and attempt a load (should throw IOE)
        System.out.println(" - Test: Detach from VM");
        vm.detach();
        try {
            vm.loadAgent(agent);
            throw new RuntimeException("loadAgent did not throw an exception!!");
        } catch (IOException ioe) {
            System.out.println(" - IOException as expected");
        }

        // Test 5 - functional "end-to-end" test.
        // Create a listener socket. Load Agent.jar into the target VM passing
        // it the port number of our listener. When agent loads it should connect
        // back to the tool.

        System.out.println(" - Re-attaching to application ...");
        vm = VirtualMachine.attach(pid);

        System.out.println(" - Test: End-to-end connection with agent");

        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();

        System.out.println(" - Loading Agent.jar into target VM ...");
        vm.loadAgent(agent, Integer.toString(port));

        System.out.println(" - Waiting for agent to connect back to tool ...");
        Socket s = ss.accept();
        System.out.println(" - Connected to agent.");

        // Test 5b - functional "end-to-end" test.
        // Now with an agent that does redefine.

        System.out.println(" - Re-attaching to application ...");
        vm = VirtualMachine.attach(pid);

        System.out.println(" - Test: End-to-end connection with RedefineAgent");

        ServerSocket ss2 = new ServerSocket(0);
        int port2 = ss2.getLocalPort();

        System.out.println(" - Loading RedefineAgent.jar into target VM ...");
        vm.loadAgent(redefineagent, Integer.toString(port2));

        System.out.println(" - Waiting for RedefineAgent to connect back to tool ...");
        Socket s2 = ss2.accept();
        System.out.println(" - Connected to RedefineAgent.");

        // Test 6 - list method should list the target VM
        System.out.println(" - Test: VirtualMachine.list");
        List<VirtualMachineDescriptor> l = VirtualMachine.list();
        if (!l.isEmpty()) {
            boolean found = false;
            for (VirtualMachineDescriptor vmd: l) {
                if (vmd.id().equals(pid)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                System.out.println(" - " + pid + " found.");
            } else {
                throw new RuntimeException(pid + " not found in VM list");
            }
        }

        // test 7 - basic hashCode/equals tests
        System.out.println(" - Test: hashCode/equals");

        VirtualMachine vm1 = VirtualMachine.attach(pid);
        VirtualMachine vm2 = VirtualMachine.attach(pid);
        if (!vm1.equals(vm2)) {
            throw new RuntimeException("virtual machines are not equal");
        }
        if (vm.hashCode() != vm.hashCode()) {
            throw new RuntimeException("virtual machine hashCodes not equal");
        }
        System.out.println(" - hashCode/equals okay");


        // ---
        System.out.println(" - Cleaning up...");
        s.close();
        ss.close();
        s2.close();
        ss2.close();
    }
}
