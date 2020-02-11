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
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import jdk.test.lib.Platform;
import lib.jdb.Debuggee;

import java.io.IOException;
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

/*
 * @test
 * @bug 8184770
 * @summary Tests for JDWP agent listen functionality (including IPv6 support)
 * @library /test/lib
 *
 * @build HelloWorld JdwpListenTest
 * @run main/othervm JdwpListenTest
 */
public class JdwpListenTest {

    // Set to true to allow testing of attach from wrong address (expected to fail).
    // It's off by default as it causes test time increase and test interference (see JDK-8231915).
    private static boolean allowNegativeTesting = false;

    public static void main(String[] args) throws Exception {
        List<InetAddress> addresses = getAddresses();

        boolean ipv4EnclosedTested = false;
        boolean ipv6EnclosedTested = false;
        for (InetAddress listen: addresses) {
            for (InetAddress attach: addresses) {
                // can connect only from the same address
                // IPv6 cannot connect to IPv4 (::1 to 127.0.0.1) and vice versa.
                // Note: for IPv6 addresses equals() does not compare scopes
                // (so addresses with symbolic and numeric scopes are equals).
                listenTest(listen.getHostAddress(), attach.getHostAddress(), attach.equals(listen));
            }
            // test that addresses enclosed in square brackets are supported.
            if (listen instanceof Inet4Address && !ipv4EnclosedTested) {
                listenTest("[" + listen.getHostAddress() + "]", "[" + listen.getHostAddress() + "]", true);
                ipv4EnclosedTested = true;
            }
            if (listen instanceof Inet6Address && !ipv6EnclosedTested) {
                listenTest("[" + listen.getHostAddress() + "]", "[" + listen.getHostAddress() + "]", true);
                ipv6EnclosedTested = true;
            }
        }
        // listen on "*" - should be accessible from any address
        for (InetAddress attach: addresses) {
            listenTest("*", attach.getHostAddress(), true);
        }
    }

    private static void listenTest(String listenAddress, String connectAddress, boolean expectedResult)
            throws IOException {
        log("\nTest: listen at " + listenAddress + ", attaching to " + connectAddress
                + ", expected: " + (expectedResult ? "SUCCESS" : "FAILURE"));
        if (!expectedResult && !allowNegativeTesting) {
            log("SKIPPED: negative testing is disabled");
            return;
        }

        log("Starting listening debuggee at " + listenAddress);
        try (Debuggee debuggee = Debuggee.launcher("HelloWorld").setAddress(listenAddress + ":0").launch()) {
            log("Debuggee is listening on " + listenAddress + ":" + debuggee.getAddress());
            log("Connecting to " + connectAddress + ", expected: " + (expectedResult ? "SUCCESS" : "FAILURE"));
            try {
                VirtualMachine vm = attach(connectAddress, debuggee.getAddress());
                vm.dispose();
                if (!expectedResult) {
                    throw new RuntimeException("ERROR: attached successfully");
                }
            } catch (IOException ex) {
                if (expectedResult) {
                    throw new RuntimeException("ERROR: failed to attach", ex);
                }
            }
        }
        log("PASSED");
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

    private static String ATTACH_CONNECTOR = "com.sun.jdi.SocketAttach";
    // cache socket attaching connector
    private static AttachingConnector attachingConnector;

    private static VirtualMachine attach(String address, String port) throws IOException {
        if (attachingConnector == null) {
            attachingConnector = (AttachingConnector)getConnector(ATTACH_CONNECTOR);
        }
        Map<String, Connector.Argument> args = attachingConnector.defaultArguments();
        setConnectorArg(args, "hostname", address);
        setConnectorArg(args, "port", port);
        try {
            return attachingConnector.attach(args);
        } catch (IllegalConnectorArgumentsException e) {
            // unexpected.. wrap in RuntimeException
            throw new RuntimeException(e);
        }
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
