/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.UIBuilder;

import javax.swing.*;
import java.net.InetAddress;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Set;

/* @test
 * @bug 8174770
 * @summary Verify that JMX Registry rejects non-local access for bind, unbind, rebind.
 *    The test is manual because the (non-local) host and port running JMX must be supplied as properties.
 * @library /test/lib
 * @run main/othervm/manual NonLocalJMXRemoteTest 0
 */

/* @test
 * @bug 8174770
 * @summary Verify that JMX Registry rejects non-local access for bind, unbind, rebind.
 *    The test is manual because the (non-local) host and port running JMX must be supplied as properties.
 * @library /test/lib
 * @run main/othervm/manual NonLocalJMXRemoteTest 1
 */

/**
 * Verify that access checks for the Registry exported by JMX Registry.bind(),
 * .rebind(), and .unbind() are prevented on remote access to the registry.
 * The test verifies that the access check is performed *before* the object to be
 * bound or rebound is deserialized.
 * This tests the SingleEntryRegistry implemented by JMX.
 * This test is a manual test and uses JMX running on a *different* host.
 * JMX can be enabled in any Java runtime; for example:
 *
 * Note: Use remote host with latest JDK update release for invoking rmiregistry.
 *
 * Note: Test should be ran twice once using arg1 and once using arg2.
 *
 * login or ssh to the remote host and invoke rmiregistry with arg1.
 * It will not show any output.
 * Execute the test, after test completes execution, stop the server.
 *
 * repeat above step using arg2 and execute the test.
 *
 *
 * arg1: {@code $JDK_HOME/bin/rmiregistry \
 *         -J-Dcom.sun.management.jmxremote.port=8888 \
 *         -J-Dcom.sun.management.jmxremote.local.only=false \
 *         -J-Dcom.sun.management.jmxremote.ssl=false \
 *         -J-Dcom.sun.management.jmxremote.authenticate=false
 * }
 *
 *
 * replace "registry-host" with the hostname or IP address of the remote host
 * for property "-J-Dcom.sun.management.jmxremote.host" below.
 *
 * arg2: {@code $JDK_HOME/bin/rmiregistry \
 *         -J-Dcom.sun.management.jmxremote.port=8888 \
 *         -J-Dcom.sun.management.jmxremote.local.only=false \
 *         -J-Dcom.sun.management.jmxremote.ssl=false \
 *         -J-Dcom.sun.management.jmxremote.authenticate=false \
 *         -J-Dcom.sun.management.jmxremote.host="registry-host"
 * }
 *
 * On the first host modify the @run command above to replace "jmx-registry-host"
 * with the hostname or IP address of the different host and run the test with jtreg.
 */
public class NonLocalJMXRemoteTest {

    static final String[] instructions = new String[]{
            "This is a manual test that requires rmiregistry run on a different host"
                    + ". Login or ssh to a different host, install the latest JDK "
                    + "build and invoke:\n\n"
                    + "$JDK_HOME/bin/rmiregistry \\\n"
                    + "-J-Dcom.sun.management.jmxremote.port=8888 \\\n"
                    + "-J-Dcom.sun.management.jmxremote.local.only=false \\\n"
                    + "-J-Dcom.sun.management.jmxremote.ssl=false \\\n"
                    + "-J-Dcom.sun.management.jmxremote.authenticate=false"
                    + "\n\nRegistry service is run in the background without any "
                    + "output. Enter the hostname or IP address of the different "
                    + "host and the port separated by a semicolon below and continue "
                    + "the test.",
            "This is a manual test that requires rmiregistry run on a different host"
                    + ". Login or ssh to a different host, install the latest JDK "
                    + "build and invoke :\n"
                    + "(Stop the current running rmi server by typing Ctrl-C)\n\n"
                    + "$JDK_HOME/bin/rmiregistry \\\n"
                    + "-J-Dcom.sun.management.jmxremote.port=8888 \\\n"
                    + "-J-Dcom.sun.management.jmxremote.local.only=false \\\n"
                    + "-J-Dcom.sun.management.jmxremote.ssl=false \\\n"
                    + "-J-Dcom.sun.management.jmxremote.authenticate=false \\\n"
                    + "-J-Dcom.sun.management.jmxremote.host=<registry-host>"
                    + "\n\nRegistry service is run in the background without any "
                    + "output. Enter the hostname or IP address of the different "
                    + "host and the port separated by a semicolon below and continue "
                    + "the test."
                    + "\n\n",
    };
    static final String message = "Enter <registry.host>:<port> and submit:";
    static final int TIMEOUT_MS = 3600000;
    private volatile boolean abort = false;

    public static void main(String[] args) throws Exception {

        String host = System.getProperty("registry.host");
        int port = Integer.getInteger("registry.port", -1);
        if (host == null || host.isEmpty() || port <= 0) {
            NonLocalJMXRemoteTest test = new NonLocalJMXRemoteTest();

            int testId = 0;
            if (args.length > 0) {
                testId = Integer.valueOf(args[0]);
            }
            String input = test.readHostInput(testId);
            String[] hostAndPort = input.split(":");

            if (hostAndPort.length >= 1) {
                host = hostAndPort[0];
                port = 8888;
            }

            if (hostAndPort.length == 2) {
                port = Integer.valueOf(hostAndPort[1]);
            }

            if (host == null || host.isEmpty() || port <= 0) {
                throw new RuntimeException(
                        "Specify host with system property: -Dregistry"
                                + ".host=<host> and -Dregistry.port=<port>");
            }
        }

        // Check if running the test on a local system; it only applies to remote
        String myHostName = InetAddress.getLocalHost().getHostName();
        Set<InetAddress> myAddrs =
                Set.copyOf(Arrays.asList(InetAddress.getAllByName(myHostName)));
        Set<InetAddress> hostAddrs =
                Set.copyOf(Arrays.asList(InetAddress.getAllByName(host)));
        if (hostAddrs.stream().anyMatch(i -> myAddrs.contains(i))
                || hostAddrs.stream().anyMatch(h -> h.isLoopbackAddress())) {
            throw new RuntimeException(
                    "Error: property 'jmx-registry.host' must not be the local host%n");
        }

        Registry registry = LocateRegistry.getRegistry(host, port);
        try {
            // Verify it is a JMX Registry
            registry.lookup("jmxrmi");
        } catch (NotBoundException nf) {
            throw new RuntimeException("Not a JMX registry, jmxrmi is not bound", nf);
        }

        try {
            registry.bind("foo", null);
            throw new RuntimeException("Remote access should not succeed for method: bind");
        } catch (Exception e) {
            assertIsAccessException(e);
        }

        try {
            registry.rebind("foo", null);
            throw new RuntimeException("Remote access should not succeed for method: rebind");
        } catch (Exception e) {
            assertIsAccessException(e);
        }

        try {
            registry.unbind("foo");
            throw new RuntimeException("Remote access should not succeed for method: unbind");
        } catch (Exception e) {
            assertIsAccessException(e);
        }
    }

    /**
     * Check the exception chain for the expected AccessException and message.
     * @param ex the exception from the remote invocation.
     */
    private static void assertIsAccessException(Throwable ex) {
        Throwable t = ex;
        while (!(t instanceof AccessException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof AccessException) {
            String msg = t.getMessage();
            int asIndex = msg.indexOf("Registry");
            int disallowIndex = msg.indexOf("disallowed");
            int nonLocalHostIndex = msg.indexOf("non-local host");
            if (asIndex < 0 ||
                    disallowIndex < 0 ||
                    nonLocalHostIndex < 0 ) {
                System.out.println("Exception message is " + msg);
                throw new RuntimeException("exception message is malformed", t);
            }
            System.out.printf("Found expected AccessException: %s%n%n", t);
        } else {
            throw new RuntimeException("AccessException did not occur when expected", ex);
        }
    }

    private String readHostInput(int index) {
        String host = "";
        Thread currentThread = Thread.currentThread();
        UIBuilder.DialogBuilder db = new UIBuilder.DialogBuilder()
                .setTitle("NonLocalRegistrTest")
                .setInstruction(instructions[index])
                .setMessage(message)
                .setSubmitAction(e -> currentThread.interrupt())
                .setCloseAction(() -> {
                    abort = true;
                    currentThread.interrupt();
                });
        JTextArea input = db.getMessageText();
        JDialog dialog = db.build();

        SwingUtilities.invokeLater(() -> {
            try {
                dialog.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            Thread.sleep(TIMEOUT_MS);
            //Timed out, so fail the test
            throw new RuntimeException(
                    "Timed out after " + TIMEOUT_MS / 1000 + " seconds");
        } catch (InterruptedException e) {
        } finally {
            if (abort) {
                throw new RuntimeException("TEST ABORTED");
            }
            host = input.getText().replaceAll(message, "").strip().trim();
            dialog.dispose();
        }
        return host;
    }
}
