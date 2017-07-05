/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6558853
 * @summary  getHostAddress() on connections using IPv6 link-local addrs should have zone id
 */
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Enumeration;

public class B6558853 implements Runnable {
    private InetAddress addr = null;
    private int port = 0;

    public static void main(String[] args) throws Exception {
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        Enumeration<NetworkInterface> l = NetworkInterface.getNetworkInterfaces();
        InetAddress dest = null;
        while (l.hasMoreElements() && dest == null) {
            NetworkInterface nif = l.nextElement();
            if (!nif.isUp())
                continue;

            for (InterfaceAddress a : nif.getInterfaceAddresses()) {
                if (a.getAddress() instanceof Inet6Address) {
                    Inet6Address a6 = (Inet6Address) a.getAddress();
                    if (a6.isLinkLocalAddress()) {
                        dest = a6;
                    }
                    break;
                }
            }
        }
        System.out.println("Using " + dest);
        if (dest != null) {
            B6558853 test = new B6558853(dest, port);
            Thread thread = new Thread(test);
            thread.start();
            Socket s = ss.accept();
            InetAddress a = s.getInetAddress();
            OutputStream out = s.getOutputStream();
            out.write(1);
            out.close();
            if (!(a instanceof Inet6Address) || a.getHostAddress().indexOf("%") == -1) {
                // No Scope found in the address String
                throw new RuntimeException("Wrong address: " + a.getHostAddress());
            }
        }
    }

    public B6558853(InetAddress a, int port) {
        addr = a;
        this.port = port;
    }

    public void run() {
        try {
            Socket s = new Socket(addr, port);
            InputStream in = s.getInputStream();
            int i = in.read();
            in.close();
        } catch (IOException iOException) {
        }
    }
}
