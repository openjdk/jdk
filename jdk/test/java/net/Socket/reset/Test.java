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
 * @bug 4517622
 * @summary SocketException on first read after error; -1 on subsequent reads
 */
import java.net.*;
import java.io.*;
import java.util.Random;

public class Test {

    static int TEST_COMBINATIONS = 5;

    // test server that cycles through each combination of response

    static class Server extends Thread {
        ServerSocket ss;

        public Server() throws IOException {
            ss = new ServerSocket(0);
            System.out.println("Server listening on port: " + getPort());
        }

        public void run() {

            int testCombination = 0;

            try {
                for (;;) {
                    Socket s = ss.accept();

                    switch (testCombination) {
                        case 0:
                            s.setTcpNoDelay(false);
                            s.getOutputStream().write(new byte[256]);
                            s.setSoLinger(true, 0);
                            break;

                        case 1:
                            s.setTcpNoDelay(true);
                            s.getOutputStream().write(new byte[256]);
                            s.setSoLinger(true, 0);
                            break;

                        case 2:
                            s.getOutputStream().write("hello".getBytes());
                            s.setSoLinger(true, 0);
                            break;

                        case 3:
                            break;      /* EOF test */

                        case 4:
                            s.getOutputStream().write(new byte[256]);
                            break;
                    }

                    s.close();

                    testCombination = (testCombination + 1) % TEST_COMBINATIONS;
                }
            } catch (IOException ioe) {
                if (!ss.isClosed()) {
                    System.err.println("Server failed: " + ioe);
                }
            }
        }

        public int getPort() {
            return ss.getLocalPort();
        }

        public void shutdown() {
            try {
                ss.close();
            } catch (IOException ioe) { }
        }

    }

    static final int STATE_DATA = 0;
    static final int STATE_EOF = 1;
    static final int STATE_IOE = 2;

    static void Test(SocketAddress sa) throws Exception {
        System.out.println("-----------");

        Socket s = new Socket();
        s.connect(sa);

        byte b[] = new byte[50];
        int state = STATE_DATA;
        boolean failed = false;

        Random rand = new Random();

        for (int i=0; i<200; i++) {
            switch (rand.nextInt(4)) {
                case 0:
                    try {
                        s.getOutputStream().write("data".getBytes());
                    } catch (IOException ioe) { }
                    break;

                case 1:
                    try {
                        int n = s.getInputStream().available();

                        // available should never return > 0 if read
                        // has already thrown IOE or returned EOF

                        if (n > 0 && state != STATE_DATA) {
                            System.out.println("FAILED!! available: " + n +
                                " (unexpected as IOE or EOF already received)");
                            failed = true;
                        }
                    } catch (IOException ioe) {
                        System.out.println("FAILED!!! available: " + ioe);
                        failed = true;
                    }
                    break;

                case 2:
                    try {
                        int n = s.getInputStream().read(b);

                        if (n > 0 && state == STATE_IOE) {
                            System.out.println("FAILED!! read: " + n +
                                " (unexpected as IOE already thrown)");
                            failed = true;
                        }

                        if (n > 0 && state == STATE_EOF) {
                            System.out.println("FAILED!! read: " + n +
                                " (unexpected as EOF already received)");
                            failed = true;
                        }

                        if (n < 0) {
                            if (state == STATE_IOE) {
                                System.out.println("FAILED!! read: EOF " +
                                    " (unexpected as IOE already thrown)");
                                failed = true;
                            }
                            if (state != STATE_EOF) {
                                System.out.println("read: EOF");
                                state = STATE_EOF;
                            }
                        }

                    } catch (IOException ioe) {
                        if (state == STATE_EOF) {
                            System.out.println("FAILED!! read: " + ioe +
                                " (unexpected as EOF already received)");
                            failed = true;
                        }
                        if (state != STATE_IOE) {
                            System.out.println("read: " + ioe);
                            state = STATE_IOE;
                        }
                    }
                    break;

                case 3:
                    try {
                        Thread.currentThread().sleep(100);
                    } catch (Exception ie) { }
            }

            if (failed) {
                failures++;
                break;
            }
        }

        s.close();
    }

    static int failures = 0;

    public static void main(String args[]) throws Exception {
        SocketAddress sa = null;
        Server svr = null;

        // server mode only
        if (args.length > 0) {
            if (args[0].equals("-server")) {
                svr = new Server();
                svr.start();
                return;
            }
        }

        // run standalone or connect to remote server
        if (args.length > 0) {
            InetAddress rh = InetAddress.getByName(args[0]);
            int port = Integer.parseInt(args[1]);
            sa = new InetSocketAddress(rh, port);
        } else {
            svr = new Server();
            svr.start();

            InetAddress lh = InetAddress.getLocalHost();
            sa = new InetSocketAddress(lh, svr.getPort());
        }

        for (int i=0; i<10; i++) {
            Test(sa);
        }

        if (svr != null) {
            svr.shutdown();
        }

        if (failures > 0) {
            throw new Exception(failures + " sub-test(s) failed.");
        }
    }
}
