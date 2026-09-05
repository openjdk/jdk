/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4111507
 * @summary retryServerSocket should not retry on BindException
 *
 * @run main/othervm AddrInUse
 */

import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;

public class AddrInUse {

    private static volatile Throwable registryExportFailure = null;

    public static void main(String[] args) throws Exception {
        /*
         * Bind a server socket to a port.
         */
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            System.err.println("Created a ServerSocket on port " + port + "...");

            /*
             * Start a thread that creates a registry on the same port,
             * and analyze the result.
             */
            System.err.println("create a registry on the same port...");
            System.err.println("(should cause an ExportException)");

            Thread exportRegistryThread = new Thread(() -> {
                /*
                 * Attempt to create (i.e. export) a registry on the port that
                 * has already been bound, and record the result.
                 */
                try {
                    LocateRegistry.createRegistry(port);
                } catch (Throwable t) {
                    registryExportFailure = t;
                }
            }, "ExportRegistry-Thread");

            exportRegistryThread.start();

            /*
             * Wait for the LocateRegistry.createRegistry() call to complete or
             * if it blocks forever (due to the original bug), then let jtreg fail
             * the test with a timeout
             */
            exportRegistryThread.join();
            if (registryExportFailure == null) {
                throw new RuntimeException(
                        "TEST FAILED: export on already-bound port succeeded");
            }
            if (!(registryExportFailure instanceof ExportException)) {
                throw new RuntimeException(
                        "TEST FAILED: unexpected exception occurred", registryExportFailure);
            }
            System.err.println("TEST PASSED, received expected exception: " + registryExportFailure);
        }
    }
}
