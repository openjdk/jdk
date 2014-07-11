/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.attach.AttachOperationFailedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.io.FileWriter;
import java.util.Properties;
import java.util.HashMap;

import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;

import jdk.testlibrary.ProcessThread;
import jdk.testlibrary.Utils;

/*
 * @test
 * @summary Test for VirtualMachine.startManagementAgent and VirtualMachine.startLocalManagementAgent
 * @library /lib/testlibrary
 * @run build Application Shutdown
 * @run main StartManagementAgent
 */

/*
 * This test is not meant to test all possible configuration parameters to
 * the JMX agent, there are other tests for that. This test makes sure it is
 * possible to start the agent via attach.
 */
public class StartManagementAgent {
    public static void main(String[] args) throws Throwable {
        final String pidFile = "StartManagementAgent.Application.pid";
        ProcessThread processThread = null;
        RunnerUtil.ProcessInfo info = null;
        try {
            processThread = RunnerUtil.startApplication(pidFile);
            info = RunnerUtil.readProcessInfo(pidFile);
            runTests(info.pid);
        } catch (Throwable t) {
            System.out.println("StartManagementAgent got unexpected exception: " + t);
            t.printStackTrace();
            throw t;
        } finally {
            // Make sure the Application process is stopped.
            RunnerUtil.stopApplication(info.shutdownPort, processThread);
        }
    }

    private static void basicTests(VirtualMachine vm) throws Exception {

        // Try calling with null argument
        boolean exception = false;
        try {
            vm.startManagementAgent(null);
        } catch (NullPointerException e) {
            exception = true;
        }
        if (!exception) {
            throw new Exception("startManagementAgent(null) should throw NPE");
        }

        // Try calling with a property value with a space in it
        Properties p = new Properties();
        File f = new File("file with space");
        try (FileWriter fw = new FileWriter(f)) {
            fw.write("com.sun.management.jmxremote.port=apa");
        }
        p.put("com.sun.management.config.file", f.getAbsolutePath());
        try {
            vm.startManagementAgent(p);
        } catch(AttachOperationFailedException ex) {
            // We expect parsing of "apa" above to fail, but if the file path
            // can't be read we get a different exception message
            if (!ex.getMessage().contains("Invalid com.sun.management.jmxremote.port number")) {
                throw ex;
            }
        }
    }

    private static final String LOCAL_CONNECTOR_ADDRESS_PROP =
        "com.sun.management.jmxremote.localConnectorAddress";

    private static final int MAX_RETRIES = 10;

    public static void runTests(int pid) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(""+pid);
        try {

            basicTests(vm);

            testLocalAgent(vm);

            // we retry the remote case several times in case the error
            // was caused by a port conflict
            int i = 0;
            boolean success = false;
            do {
                try {
                    System.err.println("Trying remote agent. Try #" + i);
                    testRemoteAgent(vm);
                    success = true;
                } catch(Exception ex) {
                    System.err.println("testRemoteAgent failed with exception:");
                    ex.printStackTrace();
                    System.err.println("Retrying.");
                }
                i++;
            } while(!success && i < MAX_RETRIES);
            if (!success) {
                throw new Exception("testRemoteAgent failed after " + MAX_RETRIES + " tries");
            }
        } finally {
            vm.detach();
        }
    }

    public static void testLocalAgent(VirtualMachine vm) throws Exception {
        Properties agentProps = vm.getAgentProperties();
        String address = (String) agentProps.get(LOCAL_CONNECTOR_ADDRESS_PROP);
        if (address != null) {
            throw new Exception("Local management agent already started");
        }

        String result = vm.startLocalManagementAgent();

        // try to parse the return value as a JMXServiceURL
        new JMXServiceURL(result);

        agentProps = vm.getAgentProperties();
        address = (String) agentProps.get(LOCAL_CONNECTOR_ADDRESS_PROP);
        if (address == null) {
            throw new Exception("Local management agent could not be started");
        }
    }

    public static void testRemoteAgent(VirtualMachine vm) throws Exception {
        int port = Utils.getFreePort();

        // try to connect - should fail
        tryConnect(port, false);

        // start agent
        System.out.println("Starting agent on port: " + port);
        Properties mgmtProps = new Properties();
        mgmtProps.put("com.sun.management.jmxremote.port", port);
        mgmtProps.put("com.sun.management.jmxremote.authenticate", "false");
        mgmtProps.put("com.sun.management.jmxremote.ssl", "false");
        vm.startManagementAgent(mgmtProps);

        // try to connect - should work
        tryConnect(port, true);

        // try to start again - should fail
        boolean exception = false;
        try {
            vm.startManagementAgent(mgmtProps);
        } catch(AttachOperationFailedException ex) {
            // expected
            exception = true;
        }
        if (!exception) {
            throw new Exception("Expected the second call to vm.startManagementAgent() to fail");
        }
    }

    private static void tryConnect(int port, boolean shouldSucceed) throws Exception {
        String jmxUrlStr =
            String.format(
                "service:jmx:rmi:///jndi/rmi://localhost:%d/jmxrmi",
                port);
        JMXServiceURL url = new JMXServiceURL(jmxUrlStr);
        HashMap<String, ?> env = new HashMap<>();

        boolean succeeded;
        try {
            JMXConnector c = JMXConnectorFactory.connect(url, env);
            c.getMBeanServerConnection();
            succeeded = true;
        } catch(Exception ex) {
            succeeded = false;
        }
        if (succeeded && !shouldSucceed) {
            throw new Exception("Could connect to agent, but should not have been possible");
        }
        if (!succeeded && shouldSucceed) {
            throw new Exception("Could not connect to agent");
        }
    }
}
