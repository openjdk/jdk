/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8238274
 * @summary Potential leak file descriptor for SCTP
 * @requires (os.family == "linux")
 * @run main/othervm/timeout=250 CloseDescriptors
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;

public class CloseDescriptors {
    private static Selector selector;
    private static final int LOOP = 10;
    private static final int LIMIT_LINES = 2;
    private static SelectorThread selThread;
    private static boolean finished = false;

    public static void main(String[] args) throws Exception {
        if (!Util.isSCTPSupported()) {
            System.out.println("SCTP protocol is not supported");
            System.out.println("Test cannot be run");
            return;
        }

        List<String> lsofDirs = List.of("/usr/bin", "/usr/sbin");
        Optional<Path> lsof = lsofDirs.stream()
                            .map(s -> Path.of(s, "lsof"))
                            .filter(f -> Files.isExecutable(f))
                            .findFirst();
        if (!lsof.isPresent()) {
            System.out.println("Cannot locate lsof in " + lsofDirs);
            System.out.println("Test cannot be run");
            return;
        }

        try (ServerSocket ss = new ServerSocket(0)) {
            int port = ss.getLocalPort();

            Server server = new Server(port);
            server.start();

            selector = Selector.open();

            selThread = new SelectorThread();
            selThread.start();

            // give time for the server and selector to start
            Thread.sleep(100);
            for (int i = 0 ; i < 100 ; ++i) {
                System.out.println(i);
                doIt(port);
                Thread.sleep(100);
            }
            System.out.println("end");
            if (!check()) {
                cleanup(port);
                throw new RuntimeException("Failed: detected unclosed FD.");
            }
            cleanup(port);
            server.join();
            selThread.join();
        }
    }

    private static void doIt(int port) throws Exception {
        InetSocketAddress sa = new InetSocketAddress("localhost", port);

        for (int i = 0 ; i < LOOP ; ++i) {
            System.out.println("  " + i);
            try (SctpChannel channel = SctpChannel.open(sa, 1, 1)) {
                channel.configureBlocking(false);

                SelectionKey key = selThread.regChannel(channel);

                key.cancel();
                selector.wakeup();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
            Thread.sleep(200);
        }
    }

    private static boolean check() throws Exception {
        long myPid = ProcessHandle.current().pid();
        ProcessBuilder pb = new ProcessBuilder(
                        "lsof", "-U", "-a", "-w", "-p", Long.toString(myPid));
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
        if (p.exitValue() != 0) {
            return false;
        }

        boolean result = true;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
            p.getInputStream()))) {
            int count = 0;
            String line = br.readLine();
            while (line != null) {
                System.out.println(line);
                count++;
                if (count > LIMIT_LINES) {
                    result = false;
                }
                line = br.readLine();
            }
        }
        return result;
    }

    private static void cleanup(int port) throws IOException {
        finished = true;
        InetSocketAddress sa = new InetSocketAddress("localhost", port);
        SctpChannel channel = SctpChannel.open(sa, 1, 1);
        channel.close();
    }

    private static class SelectorThread extends Thread {
        private Object lock = new Object();
        private SctpChannel channel;
        private SelectionKey key;

        public SelectionKey regChannel(SctpChannel ch) throws Exception {
            synchronized (lock) {
                channel = ch;
                selector.wakeup();
                lock.wait();
            }
            return key;
        }

        public void run() {
            try {
                while (!finished) {
                    selector.select(1000);
                    synchronized (lock) {
                        if (channel != null) {
                            key = channel.register(selector, SelectionKey.OP_READ);
                            channel = null;
                            lock.notify();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class Server extends Thread {
        private int port;

        public Server(int port) { this.port = port; }

        public void run() {
            try {
                SctpServerChannel ss = SctpServerChannel.open();
                InetSocketAddress sa = new InetSocketAddress("localhost", port);
                ss.bind(sa);
                while (!finished) {
                    SctpChannel soc = ss.accept();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

