/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4203167
 *
 * @summary RMI blocks in HttpAwareServerSocket.accept() if you telnet to it
 * @author Adrian Colley
 *
 * @library ../../../../../java/rmi/testlibrary/
 * @build TestIface
 * @build TestImpl
 * @build TestImpl_Stub
 * @build BlockAcceptTest
 * @run main/othervm/policy=security.policy/timeout=60 BlockAcceptTest
 */

/* This test attempts to stymie the RMI accept loop.  The accept loop in
 * RMI endlessly accepts a connection, spawns a thread for it, and repeats.
 * The accept() call can be replaced by a user-supplied library which
 * might foolishly block indefinitely in its accept() method, which would
 * prevent RMI from accepting other connections on that socket.
 *
 * Unfortunately, HttpAwareServerSocket (default server socket) is/was such
 * a foolish thing.  It reads 4 bytes to see if they're "POST" before
 * returning.  The bug fix is to move the HTTP stuff into the mainloop,
 * which has the side effect of enabling it for non-default socketfactories.
 *
 * This test:
 * 1. Creates an object and exports it.
 * 2. Connects to the listening RMI port and sends nothing, to hold it up.
 * 3. Makes a regular call, using HTTP tunnelling.
 * 4. Fails to deadlock, thereby passing the test.
 *
 * Some runtime dependencies I'm trying to eliminate:
 * 1. We don't know the port number until after exporting the object, but
 *    have to set it in http.proxyPort somehow.  Hopefully http.proxyPort
 *    isn't read too soon or this test will fail with a ConnectException.
 */

import java.rmi.*;
import java.rmi.server.RMISocketFactory;
import java.io.*;
import java.net.*;

import sun.rmi.transport.proxy.RMIMasterSocketFactory;
import sun.rmi.transport.proxy.RMIHttpToPortSocketFactory;

public class BlockAcceptTest
{
    public static void main(String[] args)
        throws Exception
    {
        // Make trouble for ourselves
        if (System.getSecurityManager() == null)
            System.setSecurityManager(new RMISecurityManager());

        // HTTP direct to the server port
        System.setProperty("http.proxyHost", "127.0.0.1");

        // Set the socket factory.
        System.err.println("(installing HTTP-out socket factory)");
        HttpOutFactory fac = new HttpOutFactory();
        RMISocketFactory.setSocketFactory(fac);

        // Create remote object
        TestImpl impl = new TestImpl();

        // Export and get which port.
        System.err.println("(exporting remote object)");
        TestIface stub = impl.export();
        try {
            int port = fac.whichPort();

            // Sanity
            if (port == 0)
                throw new Error("TEST FAILED: export didn't reserve a port(?)");

            // Set the HTTP port, at last.
            System.setProperty("http.proxyPort", port+"");

            // Now, connect to that port
            //Thread.sleep(2000);
            System.err.println("(connecting to listening port on 127.0.0.1:" +
                               port + ")");
            Socket DoS = new Socket("127.0.0.1", port);
            // we hold the connection open until done with the test.

            // The test itself: make a remote call and see if it's blocked or
            // if it works
            //Thread.sleep(2000);
            System.err.println("(making RMI-through-HTTP call)");
            System.err.println("(typical test failure deadlocks here)");
            String result = stub.testCall("dummy load");

            System.err.println(" => " + result);
            if (!("OK".equals(result)))
                throw new Error("TEST FAILED: result not OK");
            System.err.println("Test passed.");

            // Clean up, including writing a byte to that connection just in
            // case an optimizer thought of optimizing it out of existence
            try {
                DoS.getOutputStream().write(0);
                DoS.getOutputStream().close();
            } catch (Throwable apathy) {
            }

        } finally {
            try {
                impl.unexport();
            } catch (Throwable unmatter) {
            }
        }

        // Should exit here
    }

    private static class HttpOutFactory
        extends RMISocketFactory
    {
        private int servport = 0;

        public Socket createSocket(String h, int p)
            throws IOException
        {
            return ((new RMIHttpToPortSocketFactory()).createSocket(h, p));
        }

        /** Create a server socket and remember which port it's on.
         * Aborts if createServerSocket(0) is called twice, because then
         * it doesn't know whether to remember the first or second port.
         */
        public ServerSocket createServerSocket(int p)
            throws IOException
        {
            ServerSocket ss;
            ss = (new RMIMasterSocketFactory()).createServerSocket(p);
            if (p == 0) {
                if (servport != 0) {
                    System.err.println("TEST FAILED: " +
                                       "Duplicate createServerSocket(0)");
                    throw new Error("Test aborted (createServerSocket)");
                }
                servport = ss.getLocalPort();
            }
            return (ss);
        }

        /** Return which port was reserved by createServerSocket(0).
         * If the return value was 0, createServerSocket(0) wasn't called.
         */
        public int whichPort() {
            return (servport);
        }
    } // end class HttpOutFactory
}
