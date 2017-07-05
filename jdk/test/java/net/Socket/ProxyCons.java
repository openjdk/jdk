/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4097826
 * @summary SOCKS support inadequate
 * @run main/timeout=40/othervm -DsocksProxyHost=nonexistant ProxyCons
 */

import java.net.*;
public class ProxyCons {
    class Server extends Thread {
        ServerSocket server;
        Server (ServerSocket server) {
            super ();
            this.server = server;
        }
        public void run () {
            try {
                Socket s = server.accept ();
                s.close();
                while (!finished ()) {
                    Thread.sleep (500);
                }
            } catch (Exception e) {
            }
        }
        boolean isFinished = false;

        synchronized boolean finished () {
            return (isFinished);
        }
        synchronized void done () {
            isFinished = true;
        }
    }

    public ProxyCons() {
    }

    void test() throws Exception {
        ServerSocket ss = new ServerSocket(0);
        try {
            Server s = new Server(ss);
            s.start();
            Socket sock = new Socket(Proxy.NO_PROXY);
            sock.connect(new InetSocketAddress("localhost", ss.getLocalPort()));
            s.done();
            sock.close();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            ss.close();
        }
    }

    public static void main(String[] args) throws Exception {
        ProxyCons c = new ProxyCons();
        c.test();
    }
}
