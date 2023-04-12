/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4322806 8189338
 * @summary When an RMI (JRMP) connection is made to a TCP address that is
 * listening, so the connection is accepted, but the server never responds
 * to the initial JRMP handshake (nor does it terminate the connection),
 * the client should not hang forever; instead, it should throw an exception
 * after a reasonable timeout interval.  The exception should be a
 * java.rmi.ConnectException or ConnectIOException, not a MarshalException,
 * because it should be clear that no partial call execution has occurred at
 * this point (because no data for the invocation has yet been written).
 * @author Peter Jones
 *
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 * @run main/othervm/timeout=10 -Dsun.rmi.transport.tcp.handshakeTimeout=1
 *                              HandshakeTimeout
 * @run main/othervm/timeout=10 -Dsun.rmi.transport.tcp.handshakeTimeout=1
 *                              HandshakeTimeout SSL
 */

import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class HandshakeTimeout {

    public static void main(String[] args) throws Exception {

        /*
         * Listen on port, but never process connections made to it.
         */
        ServerSocket serverSocket = new ServerSocket(0);
        InetSocketAddress address = (InetSocketAddress) serverSocket.getLocalSocketAddress();

        /*
         * Attempt RMI call to port in separate thread.
         */
        Registry registry;
        if (args.length == 0) {
            registry = LocateRegistry.getRegistry(address.getPort());
        } else {
            registry = LocateRegistry.getRegistry(address.getHostString(),
                    address.getPort(), new SslRMIClientSocketFactory());
        }

        try {
            registry.lookup("Dale Cooper");
            throw new RuntimeException(
                    "TEST FAILED: remote call succeeded??");
        } catch (ConnectException | ConnectIOException e) {
            System.err.println("Got expected exception:");
            e.printStackTrace();
            System.err.println(
                    "TEST PASSED: java.rmi.ConnectException or " +
                            "ConnectIOException thrown");
        }
    }
}
