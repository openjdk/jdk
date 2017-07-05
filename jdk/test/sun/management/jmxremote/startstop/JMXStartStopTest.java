/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.net.ConnectException;
import java.net.ServerSocket;
import java.rmi.NoSuchObjectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.management.*;
import javax.management.remote.*;
import javax.net.ssl.SSLHandshakeException;

import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.JDKToolLauncher;

/**
 * @test
 * @bug 7110104
 * @library /lib/testlibrary
 * @build jdk.testlibrary.* JMXStartStopTest JMXStartStopDoSomething
 * @run main/othervm/timeout=600 -XX:+UsePerfData JMXStartStopTest
 * @summary Makes sure that enabling/disabling the management agent through
 *          JCMD achieves the desired results
 */
public class JMXStartStopTest {
    private static final String TEST_SRC = System.getProperty("test.src");

    private static final boolean verbose = false;

    /**
     * Dynamically allocates two distinct ports using {@linkplain java.net.ServerSocket}
     * It keeps each of those ports blocked until it is first accessed by its getter
     */
    private static class PortAllocator {
        private final int port1, port2;
        private final ServerSocket ss1, ss2;
        PortAllocator() {
            try {
                ss1 = new ServerSocket(0);
                ss2 = new ServerSocket(0);
                port1 = ss1.getLocalPort();
                port2 = ss2.getLocalPort();
            } catch (IOException e) {
                throw new Error("Error while obtaining free ports", e);
            }
        }

        public int getPort1() {
            if (!ss1.isClosed()) {
                try {
                    ss1.close();
                } catch (IOException e) {
                    // just ignore
                }
            }
            return port1;
        }

        public int getPort2() {
            if (!ss2.isClosed()) {
                try {
                    ss2.close();
                } catch (IOException e) {
                    // just ignore
                }
            }
            return port2;
        }
    }

    private static void dbg_print(String msg){
        if (verbose) {
            System.out.println("DBG: " +msg);
        }
    }

    private static int listMBeans(MBeanServerConnection server,
                                  ObjectName pattern,
                                  QueryExp query)
    throws Exception {

        Set<ObjectName> names = server.queryNames(pattern,query);
        for (Iterator<ObjectName> i = names.iterator(); i.hasNext(); ) {
            ObjectName name = (ObjectName)i.next();
            MBeanInfo info = server.getMBeanInfo(name);
            dbg_print("Got MBean: " + name);

            MBeanAttributeInfo[] attrs = info.getAttributes();
            if (attrs == null)
                continue;
            for (MBeanAttributeInfo attr : attrs) {
                if (attr.isReadable()) {
                    server.getAttribute(name, attr.getName());
                }
            }
        }
        return names.size();
    }


    private static void testConnectLocal(long pid)
    throws Exception {

        String jmxUrlStr = null;

        try {
            jmxUrlStr = sun.management.ConnectorAddressLink.importFrom((int)pid);
            dbg_print("Local Service URL: " +jmxUrlStr);
            if ( jmxUrlStr == null ) {
                throw new Exception("No Service URL. Local agent not started?");
            }

            JMXServiceURL url = new JMXServiceURL(jmxUrlStr);

            JMXConnector c = JMXConnectorFactory.connect(url, null);

            MBeanServerConnection conn = c.getMBeanServerConnection();
            ObjectName pattern = new ObjectName("java.lang:type=Memory,*");

            int count = listMBeans(conn,pattern,null);
            if (count == 0)
                throw new Exception("Expected at least one matching "+
                                    "MBean for "+pattern);

        } catch (IOException e) {
            dbg_print("Cannot find process : " + pid);
            throw e;
        }
    }

    private static void testNoConnect(int port) throws Exception {
        testNoConnect(port, 0);
    }

    private static void testNoConnect(int port, int rmiPort) throws Exception {
        try {
            testConnect(port, rmiPort);
            throw new Exception("Didn't expect the management agent running");
        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                if (t instanceof NoSuchObjectException ||
                    t instanceof ConnectException ||
                    t instanceof SSLHandshakeException) {
                    break;
                }
                t = t.getCause();
            }
            if (t == null) {
                throw new Exception("Unexpected exception", e);
            }
        }
    }

    private static void testConnect(int port) throws Exception {
        testConnect(port, 0);
    }

    private static void testConnect(int port, int rmiPort) throws Exception {

        dbg_print("RmiRegistry lookup...");

        dbg_print("Using port: " + port);

        dbg_print("Using rmi port: " + rmiPort);

        Registry registry = LocateRegistry.getRegistry(port);

        // "jmxrmi"
        String[] relist = registry.list();
        for (int i = 0; i < relist.length; ++i) {
            dbg_print("Got registry: " + relist[i]);
        }

        String jmxUrlStr = (rmiPort != 0) ?
            String.format(
                "service:jmx:rmi://localhost:%d/jndi/rmi://localhost:%d/jmxrmi",
                rmiPort,
                port) :
            String.format(
                "service:jmx:rmi:///jndi/rmi://localhost:%d/jmxrmi",
                port);

        JMXServiceURL url = new JMXServiceURL(jmxUrlStr);

        JMXConnector c = JMXConnectorFactory.connect(url, null);

        MBeanServerConnection conn = c.getMBeanServerConnection();
        ObjectName pattern = new ObjectName("java.lang:type=Memory,*");

        int count = listMBeans(conn,pattern,null);
        if (count == 0)
            throw new Exception("Expected at least one matching " +
                                "MBean for " + pattern);
    }

    private static class Failure {
        private final Throwable cause;
        private final String msg;

        public Failure(Throwable cause, String msg) {
            this.cause = cause;
            this.msg = msg;
        }

        public Failure(String msg) {
            this(null, msg);
        }

        public Throwable getCause() {
            return cause;
        }

        public String getMsg() {
            return msg;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.cause);
            hash = 97 * hash + Objects.hashCode(this.msg);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Failure other = (Failure) obj;
            if (!Objects.equals(this.cause, other.cause)) {
                return false;
            }
            if (!Objects.equals(this.msg, other.msg)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            if (cause != null) {
                return msg + "\n" + cause;
            } else {
                return msg;
            }
        }
    }

    private static List<Failure> failures = new ArrayList<>();

    public static void main(String args[]) throws Exception {
        for (Method m : JMXStartStopTest.class.getDeclaredMethods()) {
            if (m.getName().startsWith("test_")) {
                long t1 = System.currentTimeMillis();
                try {
                    m.invoke(null);
                    System.out.println("=== PASSED");
                } catch (Throwable e) {
                    failures.add(new Failure(e, m.getName() + " failed"));
                } finally {
                    System.out.println("(took " + (System.currentTimeMillis() - t1) + "ms)\n");
                }
            }
        }

        if (!failures.isEmpty()) {
            for(Failure f : failures) {
                System.err.println(f.getMsg());
                f.getCause().printStackTrace(System.err);
            }
            throw new Error();
        }
    }

    private static class Something {
        private Process p;
        private final ProcessBuilder pb;
        private final String name;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private volatile long pid = -1;

        public Something(ProcessBuilder pb, String name) {
            this.pb = pb;
            this.name = name;
        }

        public synchronized void start() throws InterruptedException, IOException, TimeoutException {
            if (started.compareAndSet(false, true)) {
                try {
                    p = ProcessTools.startProcess(
                        "JMXStartStopDoSomething",
                        pb,
                        (line) -> line.equals("main enter"),
                        5,
                        TimeUnit.SECONDS
                    );
                    pid = p.getPid();
                } catch (TimeoutException e) {
                    p.destroy();
                    p.waitFor();
                    throw e;
                }
            }
        }

        public long getPid() {
            return pid;
        }

        public synchronized void stop()
            throws IOException, InterruptedException {
            if (started.compareAndSet(true, false)) {
                p.getOutputStream().write(0);
                p.getOutputStream().flush();
                int ec = p.waitFor();
                if (ec != 0) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("Test application '").append(name);
                    msg.append("' failed with exit code: ");
                    msg.append(ec);

                    failures.add(new Failure(msg.toString()));
                }
            }
        }
    }

    /**
     * Runs the test application "JMXStartStopDoSomething"
     * @param name Test run name
     * @param args Additional arguments
     * @return Returns a {@linkplain Something} instance representing the run
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     */
    private static Something doSomething(String name, String ... args)
    throws Exception {
        List<String> pbArgs = new ArrayList<>(Arrays.asList(
            "-cp",
            System.getProperty("test.class.path")
        ));
        pbArgs.addAll(Arrays.asList(args));
        pbArgs.add("JMXStartStopDoSomething");

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            pbArgs.toArray(new String[pbArgs.size()])
        );
        Something s = new Something(pb, name);
        s.start();
        return s;
    }

    /**
     * Run the "jcmd" command
     *
     * @param command Command with parameters; space separated string
     * @throws IOException
     * @throws InterruptedException
     */
    private static void jcmd(String ... command) throws IOException, InterruptedException {
        if (command.length == 0) {
            jcmd(null, (Consumer<String>)null);
        } else {
            jcmd(null, command);
        }
    }

    /**
     * Run the "jcmd" command
     *
     * @param c {@linkplain Consumer} instance; may be null
     * @param command Command with parameters; space separated string
     * @throws IOException
     * @throws InterruptedException
     */
    private static void jcmd(Consumer<String> c, String ... command) throws IOException, InterruptedException {
        jcmd("JMXStartStopDoSomething", c, command);
    }

    /**
     * Run the "jcmd" command
     * @param target The target application name (or PID)
     * @param c {@linkplain Consumer} instance; may be null
     * @param command Command with parameters; space separated string
     * @throws IOException
     * @throws InterruptedException
     */
    private static void jcmd(String target, final Consumer<String> c, String ... command) throws IOException, InterruptedException {
        dbg_print("[jcmd] " + (command.length > 0 ? command[0] : "list"));

        JDKToolLauncher l = JDKToolLauncher.createUsingTestJDK("jcmd");
        l.addToolArg(target);
        for(String cmd : command) {
            l.addToolArg(cmd);
        }
        Process p = ProcessTools.startProcess(
            "jcmd",
            new ProcessBuilder(l.getCommand()),
            c
        );

        p.waitFor();
        dbg_print("[jcmd] --------");
    }

    private static final String CMD_STOP = "ManagementAgent.stop";
    private static final String CMD_START= "ManagementAgent.start";
    private static final String CMD_START_LOCAL = "ManagementAgent.start_local";

    static void test_01() throws Exception {
        // Run an app with JMX enabled stop it and
        // restart on other port

        System.out.println("**** Test one ****");
        PortAllocator pa = new PortAllocator();

        Something s = doSomething(
            "test_01",
            "-Dcom.sun.management.jmxremote.port=" + pa.getPort1(),
            "-Dcom.sun.management.jmxremote.authenticate=false",
            "-Dcom.sun.management.jmxremote.ssl=false");

        try {
            testConnect(pa.getPort1());

            jcmd(CMD_STOP);
            testNoConnect(pa.getPort1());

            jcmd(CMD_START, "jmxremote.port=" + pa.getPort2());
            testConnect(pa.getPort2());
        } finally {
            s.stop();
        }
    }

    static void test_02() throws Exception {
        // Run an app without JMX enabled
        // start JMX by jcmd

        System.out.println("**** Test two ****");

        Something s = doSomething("test_02");
        PortAllocator pa = new PortAllocator();
        try {
            jcmd(CMD_START,
                "jmxremote.port=" + pa.getPort1(),
                "jmxremote.authenticate=false",
                "jmxremote.ssl=false");

            testConnect(pa.getPort1());
        } finally {
//            debugPortUsage(pa);
            s.stop();
        }
    }

    static void test_03() throws Exception {
        // Run an app without JMX enabled
        // start JMX by jcmd on one port than on other one

        System.out.println("**** Test three ****");

        Something s = doSomething("test_03");
        PortAllocator pa = new PortAllocator();
        try {
            jcmd(CMD_START,
                "jmxremote.port=" + pa.getPort1(),
                "jmxremote.authenticate=false",
                "jmxremote.ssl=false");

            // Second agent shouldn't start
            jcmd(CMD_START,
                "jmxremote.port=" + pa.getPort2(),
                "jmxremote.authenticate=false",
                "jmxremote.ssl=false");

            // First agent should connect
            testConnect(pa.getPort1());

            // Second agent should not connect
            testNoConnect(pa.getPort2());
        } finally {
            s.stop();
        }
    }

    static void test_04() throws Exception {
        // Run an app without JMX enabled
        // start JMX by jcmd on one port, specify rmi port explicitly

        System.out.println("**** Test four ****");

        Something s = doSomething("test_04");
        PortAllocator pa = new PortAllocator();
        try {
            jcmd(CMD_START,
                 "jmxremote.port=" + pa.getPort1(),
                 "jmxremote.rmi.port=" + pa.getPort2(),
                 "jmxremote.authenticate=false",
                 "jmxremote.ssl=false");

            testConnect(pa.getPort1(), pa.getPort2());
        } finally {
            s.stop();
        }
    }

    static void test_05() throws Exception {
        // Run an app without JMX enabled, it will enable local server
        // but should leave remote server disabled

        System.out.println("**** Test five ****");

        Something s = doSomething("test_05");
        PortAllocator pa = new PortAllocator();
        try {
            jcmd(CMD_START_LOCAL);

            testNoConnect(pa.getPort1());
            testConnectLocal(s.getPid());
        } finally {
            s.stop();
        }
    }

    static void test_06() throws Exception {
        // Run an app without JMX enabled
        // start JMX by jcmd on one port, specify rmi port explicitly
        // attempt to start it again
        // 1) with the same port
        // 2) with other port
        // 3) attempt to stop it twice
        // Check for valid messages in the output

        System.out.println("**** Test six ****");

        Something s = doSomething("test_06");
        PortAllocator pa = new PortAllocator();
        try {
            jcmd(CMD_START,
                 "jmxremote.port=" + pa.getPort1(),
                 "jmxremote.authenticate=false",
                 "jmxremote.ssl=false");

            testConnect(pa.getPort1(), pa.getPort2());

            final AtomicInteger checks = new AtomicInteger();
            jcmd(
                line -> {
                    if (line.contains("java.lang.RuntimeException: Invalid agent state")) {
                        checks.getAndUpdate((op) -> op | 1);
                    }
                },
                CMD_START,
                 "jmxremote.port=" + pa.getPort1(),
                 "jmxremote.authenticate=false",
                 "jmxremote.ssl=false");

            jcmd(
                line -> {
                    if (line.contains("java.lang.RuntimeException: Invalid agent state")) {
                        checks.getAndUpdate((op) -> op | 2);
                    }
                },
                CMD_START,
                "jmxremote.port=" + pa.getPort2(),
                "jmxremote.authenticate=false",
                "jmxremote.ssl=false");

            jcmd(CMD_STOP);
            jcmd(CMD_STOP);

            int busyPort;
            try (ServerSocket ss = new ServerSocket(0))
            {
                busyPort = ss.getLocalPort();
                jcmd(
                    line -> {
                        if (line.contains("Port already in use: " + busyPort)) {
                            checks.getAndUpdate((op) -> op | 4);
                        }
                    },
                    CMD_START,
                    "jmxremote.port=" + ss.getLocalPort(),
                    "jmxremote.rmi.port=" + pa.getPort2(),
                    "jmxremote.authenticate=false",
                    "jmxremote.ssl=false");
            }
            if ((checks.get() & 1) == 0) {
                throw new Exception("Starting agent on port " + pa.getPort1() + " should " +
                                    "report an invalid agent state");
            }
            if ((checks.get() & 2) == 0) {
                throw new Exception("Starting agent on poprt " + pa.getPort2() + " should " +
                                    "report an invalid agent state");
            }
            if ((checks.get() & 4) == 0) {
                throw new Exception("Starting agent on port " + busyPort + " should " +
                                    "report port in use");
            }
        } finally {
            s.stop();
        }
    }

    static void test_07() throws Exception {
        // Run an app without JMX enabled, but with some properties set
        // in command line.
        // make sure these properties overridden corectly

        System.out.println("**** Test seven ****");

        Something s = doSomething(
            "test_07",
            "-Dcom.sun.management.jmxremote.authenticate=false",
            "-Dcom.sun.management.jmxremote.ssl=true");
        PortAllocator pa = new PortAllocator();

        try {
            testNoConnect(pa.getPort1());
            jcmd(
                CMD_START,
                "jmxremote.port=" + pa.getPort2(),
                "jmxremote.authenticate=false",
                "jmxremote.ssl=false"
            );
            testConnect(pa.getPort2());
        } finally {
            s.stop();
        }
    }

    static void test_08() throws Exception {
        // Run an app with JMX enabled and with some properties set
        // in command line.
        // stop JMX agent and then start it again with different property values
        // make sure these properties overridden corectly

        System.out.println("**** Test eight ****");
        PortAllocator pa = new PortAllocator();

        Something s = doSomething(
            "test_08",
            "-Dcom.sun.management.jmxremote.port=" + pa.getPort1(),
            "-Dcom.sun.management.jmxremote.authenticate=false",
            "-Dcom.sun.management.jmxremote.ssl=true");

        try {
            testNoConnect(pa.getPort1());

            jcmd(CMD_STOP);

            testNoConnect(pa.getPort1());

            jcmd(
                CMD_START,
                "jmxremote.port=" + pa.getPort2(),
                "jmxremote.authenticate=false",
                "jmxremote.ssl=false"
            );

            testConnect(pa.getPort2());
        } finally {
            s.stop();
        }
    }

    static void test_09() throws Exception {
        // Run an app with JMX enabled and with some properties set
        // in command line.
        // stop JMX agent and then start it again with different property values
        // specifing some property in management config file and some of them
        // in command line
        // make sure these properties overridden corectly

        System.out.println("**** Test nine ****");

        Something s = doSomething("test_09",
            "-Dcom.sun.management.config.file=" +
                TEST_SRC + File.separator + "management_cl.properties",
            "-Dcom.sun.management.jmxremote.authenticate=false"
        );
        PortAllocator pa = new PortAllocator();

        try {
            testNoConnect(pa.getPort1());

            jcmd(CMD_STOP);

            testNoConnect(pa.getPort1());

            jcmd(CMD_START,
                "config.file=" + TEST_SRC + File.separator +
                    "management_jcmd.properties",
                "jmxremote.authenticate=false",
                "jmxremote.port=" + pa.getPort2()
            );

            testConnect(pa.getPort2());
        } finally {
            s.stop();
        }
    }

    static void test_10() throws Exception {
        // Run an app with JMX enabled and with some properties set
        // in command line.
        // stop JMX agent and then start it again with different property values
        // stop JMX agent again and then start it without property value
        // make sure these properties overridden corectly

        System.out.println("**** Test ten ****");
        PortAllocator pa = new PortAllocator();

        Something s = doSomething(
            "test_10",
            "-Dcom.sun.management.jmxremote.port=" + pa.getPort1(),
            "-Dcom.sun.management.jmxremote.authenticate=false",
            "-Dcom.sun.management.jmxremote.ssl=true");

        try {
            testNoConnect(pa.getPort1());

            jcmd(CMD_STOP);
            jcmd(CMD_START,
                "jmxremote.ssl=false",
                "jmxremote.port=" + pa.getPort1()
            );
            testConnect(pa.getPort1());

            jcmd(CMD_STOP);
            jcmd(CMD_START,
                "jmxremote.port=" + pa.getPort1()
            );

            testNoConnect(pa.getPort1());
        } finally {
            s.stop();
        }
    }

    static void test_11() throws Exception {
        // Run an app with JMX enabled
        // stop remote agent
        // make sure local agent is not affected

        System.out.println("**** Test eleven ****");
        PortAllocator pa = new PortAllocator();

        Something s = doSomething(
            "test_11",
            "-Dcom.sun.management.jmxremote.port=" + pa.getPort1(),
            "-Dcom.sun.management.jmxremote.authenticate=false",
            "-Dcom.sun.management.jmxremote.ssl=false");
        try {
            testConnect(pa.getPort1());
            jcmd(CMD_STOP);
            testConnectLocal(s.getPid());
        } finally {
            s.stop();
        }
    }

    static void test_12() throws Exception {
        // Run an app with JMX disabled
        // start local agent only

        System.out.println("**** Test twelve ****");

        Something s = doSomething("test_12");
        PortAllocator pa = new PortAllocator();

        try {
            testNoConnect(pa.getPort1());
            jcmd(CMD_START + "_local");

            testConnectLocal(s.getPid());

        } finally {
            s.stop();
        }
    }

}
