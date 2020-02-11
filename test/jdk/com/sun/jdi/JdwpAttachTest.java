/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Platform;
import jdk.test.lib.apps.LingeredApp;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // Set to true to perform testing of attach from wrong address (expected to fail).
    // It's off by default as it caused significant test time increase\
    // (tests <number_of_addresses> * <number_of_addresses> cases, each case fails by timeout).
    private static boolean testFailedAttach = false;

    public static void main(String[] args) throws Exception {
        List<InetAddress> addresses = getAddresses();

        boolean ipv4EnclosedTested = false;
        boolean ipv6EnclosedTested = false;
        for (InetAddress addr: addresses) {
            if (testFailedAttach) {
                for (InetAddress connectAddr : addresses) {
                    attachTest(addr.getHostAddress(), connectAddr.getHostAddress(), addr.equals(connectAddr));
                }
            } else {
                attachTest(addr.getHostAddress(), addr.getHostAddress(), true);
            }
            // listening on "*" should accept connections from all addresses
            attachTest("*", addr.getHostAddress(), true);

            // also test that addresses enclosed in square brackets are supported.
            if (addr instanceof Inet4Address && !ipv4EnclosedTested) {
                attachTest("[" + addr.getHostAddress() + "]", "[" + addr.getHostAddress() + "]", true);
                ipv4EnclosedTested = true;
            }
            if (addr instanceof Inet6Address && !ipv6EnclosedTested) {
                attachTest("[" + addr.getHostAddress() + "]", "[" + addr.getHostAddress() + "]", true);
                ipv6EnclosedTested = true;
            }
        }

        // by using "localhost" or empty hostname
        // we should be able to attach to both IPv4 and IPv6 addresses (127.0.0.1 & ::1)
        InetAddress localAddresses[] = InetAddress.getAllByName("localhost");
        for (int i = 0; i < localAddresses.length; i++) {
            attachTest(localAddresses[i].getHostAddress(), "", true);
        }
    }

    private static void attachTest(String listenAddress, String connectAddress, boolean expectedResult)
            throws Exception {
        log("\nTest: listen on '" + listenAddress + "', attach to '" + connectAddress + "'");
        log("  Starting listening at " + listenAddress);
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
        log("  Listening port: " + port);

        log("  Attaching to " + connectAddress);
        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit((Callable<Exception>)() -> {
                VirtualMachine vm = connector.accept(args);
                vm.dispose();
                return null;
            });
            executor.shutdown();

            try {
                LingeredApp debuggee = LingeredApp.startApp(
                        "-agentlib:jdwp=transport=dt_socket"
                                + ",address=" + connectAddress + ":" + port
                                + ",server=n,suspend=n"
                                // if failure is expected set small timeout (default is 20 sec)
                                + (!expectedResult ? ",timeout=1000" : ""));
                debuggee.stopApp();
                if (expectedResult) {
                    log("OK: attached as expected");
                } else {
                    throw new RuntimeException("ERROR: LingeredApp.startApp was able to attach");
                }
            } catch (Exception ex) {
                if (expectedResult) {
                    throw new RuntimeException("ERROR: LingeredApp.startApp was able to attach");
                } else {
                    log("OK: failed to attach as expected");
                }
            }
        } finally {
            connector.stopListening(args);
        }
    }

    private static void addAddr(List<InetAddress> list, InetAddress addr) {
        log(" - (" + addr.getClass().getSimpleName() + ") " + addr.getHostAddress());
        list.add(addr);
    }

    private static boolean isTeredo(Inet6Address addr) {
        // Teredo prefix is 2001::/32 (i.e. first 4 bytes are 2001:0000)
        byte[] bytes = addr.getAddress();
        return bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x00 && bytes[3] == 0x00;
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
                            // Java reports link local addresses with symbolic scope,
                            // but on Windows java.net.NetworkInterface generates its own scope names
                            // which are incompatible with native Windows routines.
                            // So on Windows test only addresses with numeric scope.
                            // On other platforms test both symbolic and numeric scopes.
                            if (addr instanceof Inet6Address) {
                                Inet6Address addr6 = (Inet6Address)addr;
                                // Teredo clients cause intermittent errors on listen ("bind failed")
                                // and attach ("no route to host").
                                // Teredo is supposed to be a temporary measure, but some test machines have it.
                                if (isTeredo(addr6)) {
                                    continue;
                                }
                                NetworkInterface scopeIface = addr6.getScopedInterface();
                                if (scopeIface != null && scopeIface.getName() != null) {
                                    // On some test machines VPN creates link local addresses
                                    // which we cannot connect to.
                                    // Skip them.
                                    if (scopeIface.isPointToPoint()) {
                                        continue;
                                    }

                                    try {
                                        // the same address with numeric scope
                                        addAddr(result, Inet6Address.getByAddress(null, addr6.getAddress(), addr6.getScopeId()));
                                    } catch (UnknownHostException e) {
                                        // cannot happen!
                                        throw new RuntimeException("Unexpected", e);
                                    }

                                    if (Platform.isWindows()) {
                                        // don't add addresses with symbolic scope
                                        continue;
                                    }
                                }
                            }
                            addAddr(result, addr);
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

    private static long startTime = System.currentTimeMillis();

    private static void log(Object o) {
        long time = System.currentTimeMillis() - startTime;
        System.out.println(String.format("[%7.3f] %s", (time / 1000f), String.valueOf(o)));
    }

}
