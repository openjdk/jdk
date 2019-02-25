/*
 * Copyright (c) 2015, Red Hat Inc
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

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jdk.test.lib.thread.ProcessThread;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @bug     6425769
 * @summary Test JMX agent host address binding. Same ports but different
 *          interfaces to bind to (selecting plain or SSL sockets at random
 * @key intermittent
 *
 * @library /test/lib
 * @modules java.management.rmi
 *
 * @build JMXAgentInterfaceBinding
 * @run main/timeout=60 JMXInterfaceBindingTest
 */
public class JMXInterfaceBindingTest {

    public static final int COMMUNICATION_ERROR_EXIT_VAL = 1;
    public static final int STOP_PROCESS_EXIT_VAL = 10;
    public static final int JMX_PORT_RANGE_LOWER = 9100;
    public static final int JMX_PORT_RANGE_UPPER = 9200;
    public static final int JMX_PORT = getRandomPortInRange(JMX_PORT_RANGE_LOWER,
                                                            JMX_PORT_RANGE_UPPER);
    public static final int JMX_PORT_RANGE_LOWER_SSL = 9201; // 9200 might be RMI Port
    public static final int JMX_PORT_RANGE_UPPER_SSL = 9300;
    public static final int JMX_PORT_SSL = getRandomPortInRange(JMX_PORT_RANGE_LOWER_SSL,
                                                                JMX_PORT_RANGE_UPPER_SSL);
    public static final int RMI_PORT = JMX_PORT + 1;
    public static final int RMI_PORT_SSL = JMX_PORT_SSL + 1;
    public static final String READY_MSG = "MainThread: Ready for connections";
    public static final String TEST_CLASS = JMXAgentInterfaceBinding.class.getSimpleName();
    public static final String KEYSTORE_LOC = System.getProperty("test.src", ".") +
                                              File.separator +
                                              "ssl" +
                                              File.separator +
                                              "keystore";
    public static final String TRUSTSTORE_LOC = System.getProperty("test.src", ".") +
                                                File.separator +
                                                "ssl" +
                                                File.separator +
                                                "truststore";
    public static final String TEST_CLASSPATH = System.getProperty("test.classes", ".");

    public void run(List<InetAddress> addrs) {
        System.out.println("DEBUG: Running tests with plain sockets.");
        runTests(addrs, false);
        System.out.println("DEBUG: Running tests with SSL sockets.");
        runTests(addrs, true);
    }

    private void runTests(List<InetAddress> addrs, boolean useSSL) {
        List<ProcessThread> jvms = new ArrayList<>(addrs.size());
        int i = 1;
        for (InetAddress addr : addrs) {
            String address = JMXAgentInterfaceBinding.wrapAddress(addr.getHostAddress());
            System.out.println();
            String msg = String.format("DEBUG: Launching java tester for triplet (HOSTNAME,JMX_PORT,RMI_PORT) == (%s,%d,%d)",
                    address,
                    useSSL ? JMX_PORT_SSL : JMX_PORT,
                    useSSL ? RMI_PORT_SSL : RMI_PORT);
            System.out.println(msg);
            ProcessThread jvm = runJMXBindingTest(address, useSSL);
            jvms.add(jvm);
            jvm.start();
            System.out.println("DEBUG: Started " + (i++) + " Process(es).");
        }
        int failedProcesses = 0;
        for (ProcessThread pt: jvms) {
            try {
                pt.sendMessage("Exit: " + STOP_PROCESS_EXIT_VAL);
                pt.join();
            } catch (Throwable e) {
                System.err.println("Failed to stop process: " + pt.getName());
                throw new RuntimeException("Test failed", e);
            }
            int exitValue = pt.getOutput().getExitValue();
            // If there is a communication error (the case we care about)
            // we get a exit code of 1
            if (exitValue == COMMUNICATION_ERROR_EXIT_VAL) {
                // Failure case since the java processes should still be
                // running.
                System.err.println("Test FAILURE on " + pt.getName());
                failedProcesses++;
            } else if (exitValue == STOP_PROCESS_EXIT_VAL) {
                System.out.println("DEBUG: OK. Spawned java process terminated with expected exit code of " + STOP_PROCESS_EXIT_VAL);
            } else {
                System.err.println("Test FAILURE on " + pt.getName() + " reason: Unexpected exit code => " + exitValue);
                failedProcesses++;
            }
        }
        if (failedProcesses > 0) {
            throw new RuntimeException("Test FAILED. " + failedProcesses + " out of " + addrs.size() + " process(es) failed to start the JMX agent.");
        }
    }

    private ProcessThread runJMXBindingTest(String address, boolean useSSL) {
        List<String> args = new ArrayList<>();
        args.add("-classpath");
        args.add(TEST_CLASSPATH);
        args.add("-Dcom.sun.management.jmxremote.host=" + address);
        args.add("-Dcom.sun.management.jmxremote.port=" + (useSSL ? JMX_PORT_SSL : JMX_PORT));
        args.add("-Dcom.sun.management.jmxremote.rmi.port=" + (useSSL ? RMI_PORT_SSL : RMI_PORT));
        args.add("-Dcom.sun.management.jmxremote.authenticate=false");
        args.add("-Dcom.sun.management.jmxremote.ssl=" + Boolean.toString(useSSL));
        // This is needed for testing on loopback
        args.add("-Djava.rmi.server.hostname=" + address);
        if (useSSL) {
            args.add("-Dcom.sun.management.jmxremote.registry.ssl=true");
            args.add("-Djavax.net.ssl.keyStore=" + KEYSTORE_LOC);
            args.add("-Djavax.net.ssl.trustStore=" + TRUSTSTORE_LOC);
            args.add("-Djavax.net.ssl.keyStorePassword=password");
            args.add("-Djavax.net.ssl.trustStorePassword=trustword");
        }
        args.add(TEST_CLASS);
        args.add(address);
        args.add(Integer.toString(useSSL ? JMX_PORT_SSL : JMX_PORT));
        args.add(Integer.toString(useSSL ? RMI_PORT_SSL : RMI_PORT));
        args.add(Boolean.toString(useSSL));
        try {
            ProcessBuilder builder = ProcessTools.createJavaProcessBuilder(args.toArray(new String[] {}));
            System.out.println(ProcessTools.getCommandLine(builder));
            ProcessThread jvm = new ProcessThread("JMX-Tester-" + address, JMXInterfaceBindingTest::isJMXAgentResponseAvailable, builder);
            return jvm;
        } catch (Exception e) {
            throw new RuntimeException("Test failed", e);
        }

    }

    private static boolean isJMXAgentResponseAvailable(String line) {
        if (line.equals(READY_MSG)) {
            System.out.println("DEBUG: Found expected READY_MSG.");
            return true;
        } else if (line.startsWith("Error:")) {
            // Allow for a JVM process that exits with
            // "Error: JMX connector server communication error: ..."
            // to continue as well since we handle that case elsewhere.
            // This has the effect that the test does not timeout and
            // fails with an exception in the test.
            System.err.println("PROBLEM: JMX agent of target JVM did not start as it should.");
            return true;
        } else {
            return false;
        }
    }

    private static int getRandomPortInRange(int lower, int upper) {
        if (upper <= lower) {
            throw new IllegalArgumentException("upper <= lower");
        }
        int range = upper - lower;
        int randPort = lower + (int)(Math.random() * range);
        return randPort;
    }

    public static void main(String[] args) {
        List<InetAddress> addrs = getNonLoopbackAddressesForLocalHost();
        if (addrs.isEmpty()) {
            System.out.println("Ignoring test since no non-loopback IPs are available to bind to " +
                               "in addition to the loopback interface.");
            return;
        }
        JMXInterfaceBindingTest test = new JMXInterfaceBindingTest();
        // Add loopback interface too as we'd like to verify whether it's
        // possible to bind to multiple addresses on the same host. This
        // wasn't possible prior JDK-6425769. It used to bind to *all* local
        // interfaces. We add loopback here, since that eases test setup.
        addrs.add(InetAddress.getLoopbackAddress());
        test.run(addrs);
        System.out.println("All tests PASSED.");
    }

    private static List<InetAddress> getNonLoopbackAddressesForLocalHost() {
        List<InetAddress> addrs = new ArrayList<>();
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            if (!localHost.isLoopbackAddress()) {
                addrs.add(localHost);
            }
            return addrs;
        } catch (UnknownHostException e) {
            throw new RuntimeException("Test failed", e);
        }
    }
}
