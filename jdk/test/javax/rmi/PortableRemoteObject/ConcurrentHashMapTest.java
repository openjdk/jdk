/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8068721
 * @summary test RMI-IIOP call with ConcurrentHashMap as an argument
 * @library /lib/testlibrary
 * @modules java.corba
 *          java.naming
 *          java.rmi
 * @build jdk.testlibrary.*
 * @compile Test.java HelloInterface.java HelloServer.java HelloClient.java
 *    HelloImpl.java _HelloImpl_Tie.java _HelloInterface_Stub.java ConcurrentHashMapTest.java
 * @run main/othervm -Djava.naming.provider.url=iiop://localhost:1050
 *    -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory ConcurrentHashMapTest
 * @run main/othervm/secure=java.lang.SecurityManager/policy=jtreg.test.policy
 *    -Djava.naming.provider.url=iiop://localhost:1050
 *    -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory ConcurrentHashMapTest
 * @key intermittent
 */


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.testlibrary.JDKToolFinder;
import jdk.testlibrary.JDKToolLauncher;

public class ConcurrentHashMapTest {

    static final String ORBD = JDKToolFinder.getTestJDKTool("orbd");
    static final String JAVA = JDKToolFinder.getTestJDKTool("java");
    static final JDKToolLauncher orbdLauncher = JDKToolLauncher.createUsingTestJDK("orbd");
    static final String CLASSPATH = System.getProperty("java.class.path");
    static final int FIVE_SECONDS = 5000;

    private static Exception clientException;
    private static boolean exceptionInClient;
    private static Process orbdProcess;
    private static Process rmiServerProcess;

    public static void main(String[] args) throws Exception {
        try {
            startTestComponents();
        } finally {
            stopTestComponents();
        }
        System.err.println("Test completed OK ");
    }

    static void startTestComponents () throws Exception {
        startOrbd();
        Thread.sleep(FIVE_SECONDS);
        startRmiIiopServer();
        Thread.sleep(FIVE_SECONDS);
        executeRmiIiopClient();
    }

    private static void stopTestComponents() throws Exception {
        stopRmiIiopServer();
        stopOrbd();
        if (exceptionInClient) {
            throw new RuntimeException(clientException);
        } else if (!isResponseReceived()) {
            throw new RuntimeException("Expected Response not received");
        }
    }

    static void startOrbd() throws Exception {
        System.out.println("\nStarting orbd with NS port 1050 ");

        //orbd -ORBInitialHost localhost -ORBInitialPort 1050
        orbdLauncher.addToolArg("-ORBInitialHost").addToolArg("localhost")
            .addToolArg("-ORBInitialPort").addToolArg("1050");

        System.out.println("ConcurrentHashMapTest: Executing: " + Arrays.asList(orbdLauncher.getCommand()));
        ProcessBuilder pb = new ProcessBuilder(orbdLauncher.getCommand());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        orbdProcess = pb.start();
    }


    static void startRmiIiopServer() throws Exception {
        System.out.println("\nStarting RmiServer");
        // java -cp . --add-modules java.corba
        // -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory
        // -Djava.naming.provider.url=iiop://localhost:1050 HelloServer
        List<String> commands = new ArrayList<>();
        commands.add(ConcurrentHashMapTest.JAVA);
        commands.add("--add-modules");
        commands.add("java.corba");
        commands.add("-Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory");
        commands.add("-Djava.naming.provider.url=iiop://localhost:1050");
        commands.add("-cp");
        commands.add(ConcurrentHashMapTest.CLASSPATH);
        commands.add("HelloServer");

        System.out.println("ConcurrentHashMapTest: Executing: " + commands);
        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        rmiServerProcess = pb.start();
    }

    static boolean isResponseReceived() {
        return HelloClient.isResponseReceived();
    }

    static void stopRmiIiopServer() throws Exception {
        if (rmiServerProcess != null) {
            rmiServerProcess.destroyForcibly();
            rmiServerProcess.waitFor();
            System.out.println("serverProcess exitCode:"
                + rmiServerProcess.exitValue());
        }
    }

    static void stopOrbd() throws Exception {
        if (orbdProcess != null) {
            orbdProcess.destroyForcibly();
            orbdProcess.waitFor();
            System.out.println("orbd exitCode:"
                + orbdProcess.exitValue());
        }
    }

    static void executeRmiIiopClient() throws Exception {
        try {
            HelloClient.executeRmiClientCall();
        } catch (Exception ex) {
            clientException = ex;
            exceptionInClient = true;
        }
    }
}
