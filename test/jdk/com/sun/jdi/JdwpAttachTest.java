/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.ListeningConnector;
import jdk.test.lib.apps.LingeredApp;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * @test
 * @bug 8184770
 * @summary Tests for JDWP agent attach functionality (including IPv6 support)
 * @library /test/lib
 *
 * @build HelloWorld JdwpAttachTest
 * @run main/othervm JdwpAttachTest
 */
public class JdwpAttachTest {

    public static void main(String[] args) throws Exception {
        List<InetAddress> addresses = getAddresses();

        boolean ipv4EnclosedTested = false;
        boolean ipv6EnclosedTested = false;
        for (InetAddress addr: addresses) {
            // also test that addresses enclosed in square brackets are supported
            attachTest(addr.getHostAddress(), addr.getHostAddress());
            // listening on "*" should accept connections from all addresses
            attachTest("*", addr.getHostAddress());

            // test that addresses enclosed in square brackets are supported.
            if (addr instanceof Inet4Address && !ipv4EnclosedTested) {
                attachTest("[" + addr.getHostAddress() + "]", "[" + addr.getHostAddress() + "]");
                ipv4EnclosedTested = true;
            }
            if (addr instanceof Inet6Address && !ipv6EnclosedTested) {
                attachTest("[" + addr.getHostAddress() + "]", "[" + addr.getHostAddress() + "]");
                ipv6EnclosedTested = true;
            }
        }

        // by using "localhost" or empty hostname
        // we should be able to attach to both IPv4 and IPv6 addresses (127.0.0.1 & ::1)
        InetAddress localAddresses[] = InetAddress.getAllByName("localhost");
        for (int i = 0; i < localAddresses.length; i++) {
            attachTest(localAddresses[i].getHostAddress(), "");
        }
    }

    private static void attachTest(String listenAddress, String connectAddresses)
            throws Exception {
        log("Starting listening at " + listenAddress);
        ListeningConnector connector = getListenConnector();
        Map<String, Connector.Argument> args = connector.defaultArguments();
        setConnectorArg(args, "localAddress", listenAddress);
        setConnectorArg(args, "port", "0");

        String actualAddress = connector.startListening(args);
        String actualPort = actualAddress.substring(actualAddress.lastIndexOf(':') + 1);
        String port = args.get("port").value();
        // port from connector.startListening must be the same as values from arguments
        if (!port.equals(actualPort)) {
            throw new RuntimeException("values from connector.startListening (" + actualPort
                    + " is not equal to values from arguments (" + port + ")");
        }
        log("Listening port: " + port);

        log("Attaching from " + connectAddresses);
        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit((Callable<Exception>)() -> {
                VirtualMachine vm = connector.accept(args);
                log("ACCEPTED.");
                vm.dispose();
                return null;
            });
            executor.shutdown();

            LingeredApp debuggee = LingeredApp.startApp(
                    Arrays.asList("-agentlib:jdwp=transport=dt_socket"
                                +",address=" + connectAddresses + ":" + port
                                + ",server=n,suspend=n"));
            debuggee.stopApp();

            executor.awaitTermination(20, TimeUnit.SECONDS);
        } finally {
            connector.stopListening(args);
        }
    }

    private static List<InetAddress> getAddresses() {
        List<InetAddress> result = new LinkedList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface iface = networkInterfaces.nextElement();
                try {
                    if (iface.isUp()) {
                        Enumeration<InetAddress> addresses = iface.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            InetAddress addr = addresses.nextElement();
                            // Java reports link local addresses with named scope,
                            // but Windows sockets routines support only numeric scope id.
                            // skip such addresses.
                            if (addr instanceof Inet6Address) {
                                Inet6Address addr6 = (Inet6Address)addr;
                                if (addr6.getScopedInterface() != null) {
                                    continue;
                                }
                            }
                            log(" - (" + addr.getClass().getSimpleName() + ") " + addr.getHostAddress());
                            result.add(addr);
                        }
                    }
                } catch (SocketException e) {
                    log("Interface " + iface.getDisplayName() + ": failed to get addresses");
                }
            }
        } catch (SocketException e) {
            log("Interface enumeration error: " + e);
        }
        return result;
    }

    private static String LISTEN_CONNECTOR = "com.sun.jdi.SocketListen";

    private static ListeningConnector getListenConnector() {
        return (ListeningConnector)getConnector(LISTEN_CONNECTOR);
    }

    private static Connector getConnector(String name) {
        List<Connector> connectors = Bootstrap.virtualMachineManager().allConnectors();
        for (Iterator<Connector> iter = connectors.iterator(); iter.hasNext(); ) {
            Connector connector = iter.next();
            if (connector.name().equalsIgnoreCase(name)) {
                return connector;
            }
        }
        throw new IllegalArgumentException("Connector " + name + " not found");
    }

    private static void setConnectorArg(Map<String, Connector.Argument> args, String name, String value) {
        Connector.Argument arg = args.get(name);
        if (arg == null) {
            throw new IllegalArgumentException("Argument " + name + " is not defined");
        }
        arg.setValue(value);
    }

    private static void log(Object o) {
        System.out.println(String.valueOf(o));
    }

}
