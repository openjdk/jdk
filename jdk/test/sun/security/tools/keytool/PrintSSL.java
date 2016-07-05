/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6480981 8160624
 * @modules java.base/sun.security.tools.keytool
 * @summary keytool should be able to import certificates from remote SSL server
 * @run main/othervm PrintSSL
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class PrintSSL {

    public static void main(String[] args) throws Exception {

        int port = new Server().start();
        if(port == -1) {
            throw new RuntimeException("Unable start ssl server.");
        }
        String vmOpt = System.getProperty("TESTTOOLVMOPTS");
        String cmd = String.format(
                "-debug %s -printcert -sslserver localhost:%s",
                ((vmOpt == null) ? "" : vmOpt ), port);
        sun.security.tools.keytool.Main.main(cmd.split("\\s+"));
    }

    private static class Server implements Runnable {

        private volatile int serverPort = -1;
        private final CountDownLatch untilServerReady = new CountDownLatch(1);

        public int start() throws InterruptedException {

            Thread server = new Thread(this);
            server.setDaemon(true);
            server.start();
            untilServerReady.await();
            return this.getServerPort();
        }

        @Override
        public void run() {

            System.setProperty("javax.net.ssl.keyStorePassword", "passphrase");
            System.setProperty("javax.net.ssl.keyStore",
                    System.getProperty("test.src", "./")
                    + "/../../../../javax/net/ssl/etc/keystore");
            SSLServerSocketFactory sslssf =
                    (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            try (ServerSocket server = sslssf.createServerSocket(0)) {
                this.serverPort = server.getLocalPort();
                System.out.printf("%nServer started on: %s%n", getServerPort());
                untilServerReady.countDown();
                ((SSLSocket)server.accept()).startHandshake();
            } catch (Throwable e) {
                e.printStackTrace(System.out);
                untilServerReady.countDown();
            }

        }

        public int getServerPort() {
            return this.serverPort;
        }

    }

}
