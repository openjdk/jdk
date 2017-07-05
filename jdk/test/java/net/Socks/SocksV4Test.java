/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4727547
 * @summary SocksSocketImpl throws NullPointerException
 * @build SocksServer
 * @run main SocksV4Test
 */

import java.net.*;

public class SocksV4Test {
    public static void main(String[] args) throws Exception {
        // Create a SOCKS V4 proxy
        SocksServer srvr = new SocksServer(0, true);
        srvr.start();
        Proxy sp = new Proxy(Proxy.Type.SOCKS,
                             new InetSocketAddress("localhost", srvr.getPort()));
        // Let's create an unresolved address
        InetSocketAddress ad = new InetSocketAddress("doesnt.exist.name", 1234);
        try (Socket s = new Socket(sp)) {
            s.connect(ad, 10000);
        } catch (UnknownHostException ex) {
            // OK, that's what we expected
        } catch (NullPointerException npe) {
            // Not OK, this used to be the bug
            throw new RuntimeException("Got a NUllPointerException");
        } finally {
            srvr.terminate();
        }
    }
}
