/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @test
 * @library /lib/testlibrary
 * @bug 5016507 6173612 6319776 6342019 6484550 8004926
 * @summary Start a managed VM and test that a management tool can connect
 *          without connection or username/password details.
 *          TestManager will attempt a connection to the address obtained from
 *          both agent properties and jvmstat buffer.
 * @build jdk.testlibrary.ProcessTools
 * @build jdk.testlibrary.Utils
 * @build TestManager TestApplication
 * @run main/othervm/timeout=300 -XX:+UsePerfData LocalManagementTest
 */

import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.Utils;

public class LocalManagementTest {
    private static final String TEST_CLASSPATH = System.getProperty("test.class.path");
    private static final String TEST_JDK = System.getProperty("test.jdk");
    private static int MAX_GET_FREE_PORT_TRIES = 10;

    public static void main(String[] args) throws Exception {
        try {
            MAX_GET_FREE_PORT_TRIES = Integer.parseInt(System.getProperty("test.getfreeport.max.tries", "10"));
        } catch (NumberFormatException ex) {
        }

        int failures = 0;
        for(Method m : LocalManagementTest.class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) &&
                m.getName().startsWith("test")) {
                m.setAccessible(true);
                try {
                    System.out.println(m.getName());
                    System.out.println("==========");
                    Boolean rslt = (Boolean)m.invoke(null);
                    if (!rslt) {
                        System.err.println(m.getName() + " failed");
                        failures++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    failures++;
                }
            }
        }
        if (failures > 0) {
            throw new Error("Test failed");
        }
    }

    private static boolean test1() throws Exception {
        return doTest("1", "-Dcom.sun.management.jmxremote");
    }

    private static boolean test2() throws Exception {
        Path agentPath = findAgent();
        if (agentPath != null) {
            String agent = agentPath.toString();
            return doTest("2", "-javaagent:" + agent);
        } else {
            return false;
        }
    }

    /**
     * no args (blank) - manager should attach and start agent
     */
    private static boolean test3() throws Exception {
        return doTest("3", null);
    }

    /**
     * sanity check arguments to management-agent.jar
     */
    private static boolean test4() throws Exception {
        Path agentPath = findAgent();
        if (agentPath != null) {

            for (int i = 0; i < MAX_GET_FREE_PORT_TRIES; ++i) {
                ProcessBuilder builder = ProcessTools.createJavaProcessBuilder(
                        "-javaagent:" + agentPath.toString() +
                                "=com.sun.management.jmxremote.port=" + Utils.getFreePort() + "," +
                                "com.sun.management.jmxremote.authenticate=false," +
                                "com.sun.management.jmxremote.ssl=false",
                        "-cp",
                        TEST_CLASSPATH,
                        "TestApplication",
                        "-exit"
                );

                Process prc = null;
                final AtomicReference<Boolean> isBindExceptionThrown = new AtomicReference<>();
                isBindExceptionThrown.set(new Boolean(false));
                try {
                    prc = ProcessTools.startProcess(
                            "TestApplication",
                            builder,
                            (String line) -> {
                                if (line.contains("Exception thrown by the agent : " +
                                        "java.rmi.server.ExportException: Port already in use")) {
                                    isBindExceptionThrown.set(new Boolean(true));
                                }
                            });

                    prc.waitFor();

                    if (prc.exitValue() == 0) {
                        return true;
                    }

                    if (isBindExceptionThrown.get().booleanValue()) {
                        System.out.println("'Port already in use' error detected. Try again");
                    } else {
                        return false;
                    }
                } finally {
                    if (prc != null) {
                        prc.destroy();
                        prc.waitFor();
                    }
                }
            }
        }
        return false;
    }

    /**
     * use DNS-only name service
     */
    private static boolean test5() throws Exception {
        return doTest("5", "-Dsun.net.spi.namservice.provider.1=\"dns,sun\"");
    }

    private static Path findAgent() {
        FileSystem FS = FileSystems.getDefault();
        Path agentPath = FS.getPath(
            TEST_JDK, "jre", "lib", "management-agent.jar"
        );
        if (!isFileOk(agentPath)) {
            agentPath = FS.getPath(
                TEST_JDK, "lib", "management-agent.jar"
            );
        }
        if (!isFileOk(agentPath)) {
            System.err.println("Can not locate management-agent.jar");
            return null;
        }
        return agentPath;
    }

    private static boolean isFileOk(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path);
    }

    private static boolean doTest(String testId, String arg) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("-cp");
        args.add(TEST_CLASSPATH);

        if (arg != null) {
            args.add(arg);
        }
        args.add("TestApplication");
        ProcessBuilder server = ProcessTools.createJavaProcessBuilder(
            args.toArray(new String[args.size()])
        );

        Process serverPrc = null, clientPrc = null;
        try {
            final AtomicReference<String> port = new AtomicReference<>();
            final AtomicReference<String> pid = new AtomicReference<>();

            serverPrc = ProcessTools.startProcess(
                "TestApplication(" + testId + ")",
                server,
                (String line) -> {
                    if (line.startsWith("port:")) {
                         port.set(line.split("\\:")[1]);
                     } else  if (line.startsWith("pid:")) {
                         pid.set(line.split("\\:")[1]);
                     } else if (line.startsWith("waiting")) {
                         return true;
                     }
                     return false;
                },
                5,
                TimeUnit.SECONDS
            );

            System.out.println("Attaching test manager:");
            System.out.println("=========================");
            System.out.println("  PID           : " + pid.get());
            System.out.println("  shutdown port : " + port.get());

            ProcessBuilder client = ProcessTools.createJavaProcessBuilder(
                "-cp",
                TEST_CLASSPATH +
                    File.pathSeparator +
                    TEST_JDK +
                    File.separator +
                    "lib" +
                    File.separator +
                    "tools.jar",
                "TestManager",
                pid.get(),
                port.get(),
                "true"
            );

            clientPrc = ProcessTools.startProcess(
                "TestManager",
                client,
                (String line) -> line.startsWith("Starting TestManager for PID"),
                10,
                TimeUnit.SECONDS
            );

            int clientExitCode = clientPrc.waitFor();
            int serverExitCode = serverPrc.waitFor();
            return clientExitCode == 0 && serverExitCode == 0;
        } finally {
            if (clientPrc != null) {
                System.out.println("Stopping process " + clientPrc);
                clientPrc.destroy();
                clientPrc.waitFor();
            }
            if (serverPrc != null) {
                System.out.println("Stopping process " + serverPrc);
                serverPrc.destroy();
                serverPrc.waitFor();
            }
        }
    }
}