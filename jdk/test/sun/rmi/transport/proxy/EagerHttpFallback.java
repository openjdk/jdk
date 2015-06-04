/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4290727
 * @summary Verify that ConnectException will trigger HTTP fallback if
 *          sun.rmi.transport.proxy.eagerHttpFallback system property is set.
 *
 * @library ../../../../java/rmi/testlibrary
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 * @build TestLibrary
 * @run main/othervm EagerHttpFallback
 */

import java.rmi.*;
import java.rmi.registry.*;

public class EagerHttpFallback {

    static final int INITIAL_PORT = TestLibrary.getUnusedRandomPort();
    static final int FALLBACK_PORT = TestLibrary.getUnusedRandomPort();

    public static void main(String[] args) throws Exception {
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", Integer.toString(FALLBACK_PORT));
        System.setProperty("sun.rmi.transport.proxy.eagerHttpFallback",
                           "true");
        LocateRegistry.createRegistry(FALLBACK_PORT);

        /*
         * The call below should trigger a ConnectException in the
         * RMIMasterSocketFactory when it attempts a direct connection to
         * INITIAL_PORT, which no one is listening on.  Since
         * eagerHttpFallback is set, this ConnectException should trigger HTTP
         * fallback, which will send a call through the HTTP proxy, which is
         * configured to be localhost with a port behind which a registry is
         * listening--so if fallback works properly, the list() call should
         * succeed.
         */
        try {
            LocateRegistry.getRegistry(INITIAL_PORT).list();
        } catch (Exception e) {
            System.err.println(
                "call on registry stub with port " + INITIAL_PORT +
                "did not successfully perform HTTP fallback to " +
                FALLBACK_PORT);
            throw e;
        }
    }
}
