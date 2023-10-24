/*
 * Copyright (c) 2023 SAP SE. All rights reserved.
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
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;
import lib.jdb.Debuggee;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/*
 * @test
 * @bug 8317920
 * @summary Tests for JDWP agent to send valid exception event with onthrow option
 * @library /test/lib
 *
 * @build ThrowCaughtException JdwpOnThrowTest
 * @run main/othervm JdwpOnThrowTest
 */
public class JdwpOnThrowTest {

    private static long TIMEOUT = 10000;

    private static String ATTACH_CONNECTOR = "com.sun.jdi.SocketAttach";
    // cache socket attaching connector
    private static AttachingConnector attachingConnector;

    public static void main(String[] args) throws Exception {
        int port = findFreePort();
        try (Debuggee debuggee = Debuggee.launcher("ThrowCaughtException").setAddress("localhost:" + port)
                                         .enableOnThrow("Ex", "Start").setSuspended(true).launch()) {
            VirtualMachine vm = null;
            try {
                vm = attach("localhost", "" + port);
                EventQueue queue = vm.eventQueue();
                log("Waiting for exception event");
                long start = System.currentTimeMillis();
                while (start + TIMEOUT > System.currentTimeMillis()) {
                    EventSet eventSet = queue.remove(TIMEOUT);
                    EventIterator eventIterator = eventSet.eventIterator();
                    while(eventIterator.hasNext() && start + TIMEOUT > System.currentTimeMillis()) {
                        Event event = eventIterator.next();
                        if (event instanceof ExceptionEvent ex) {
                            verifyExceptionEvent(ex);
                            log("Received exception event: " + event);
                            vm.dispose();
                            return;
                        }
                        log("Received event: " + event);
                    }
                }
                throw new RuntimeException("ERROR: failed to receive exception event");
            } catch (IOException ex) {
                throw new RuntimeException("ERROR: failed to attach", ex);
            }
        }
    }

    private static void verifyExceptionEvent(ExceptionEvent ex) throws Exception {
        if (ex.exception() == null) {
            throw new RuntimeException("Exception is null");
        }
        if (ex.exception().type() == null) {
            throw new RuntimeException("Exception type is null");
        }
        if (ex.exception().referenceType() == null) {
            throw new RuntimeException("Exception reference type is null");
        }
        if (ex.catchLocation() == null) {
            throw new RuntimeException("Exception catch location is null");
        }
        if (!ex.location().equals(ex.thread().frame(0).location())) {
            throw new RuntimeException(
                String.format("Throw location %s and location of first frame %s are not equal",
                                ex.location(), ex.thread().frame(0).location()));
        }
        if (!ex.exception().type().name().equals("Ex")) {
            throw new RuntimeException("Exception has wrong type: " + ex.exception().type().name());
        }
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
        for (Connector connector : Bootstrap.virtualMachineManager().allConnectors()) {
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
