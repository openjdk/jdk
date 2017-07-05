/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

// Read printssl.sh, this Java program starts an SSL server

import java.net.ServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class PrintSSL {
    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.ssl.keyStorePassword", "passphrase");
        System.setProperty("javax.net.ssl.keyStore",
                System.getProperty("test.src", "./") + "/../../ssl/etc/keystore");
        SSLServerSocketFactory sslssf =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        final ServerSocket server = sslssf.createServerSocket(0);
        System.out.println(server.getLocalPort());
        System.out.flush();
        Thread t = new Thread() {
            public void run() {
                try {
                    Thread.sleep(30000);
                    server.close();
                } catch (Exception e) {
                    ;
                }
                throw new RuntimeException("Timeout");
            }
        };
        t.setDaemon(true);
        t.start();
        ((SSLSocket)server.accept()).startHandshake();
    }
}
