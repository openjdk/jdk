/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4636628
 * @summary HttpURLConnection duplicates HTTP GET requests when used with multiple threads
*/

/*
 * This tests keep-alive behavior using chunkedinputstreams
 * It checks that keep-alive connections are used and also
 * that requests are not being repeated (due to errors)
 *
 * It also checks that the keepalive connections are closed eventually
 * because the test will not terminate if the connections
 * are not closed by the keep-alive timer.
 */

import java.net.*;
import java.io.*;

public class MultiThreadTest extends Thread {

    /*
     * Is debugging enabled - start with -d to enable.
     */
    static boolean debug = false;

    static Object threadlock = new Object ();
    static int threadCounter = 0;

    static Object getLock() { return threadlock; }

    static void debug(String msg) {
        if (debug)
            System.out.println(msg);
    }

    static int reqnum = 0;

    void doRequest(String uri) throws Exception {
        URL url = new URL(uri + "?foo="+reqnum);
        reqnum ++;
        HttpURLConnection http = (HttpURLConnection)url.openConnection();

        InputStream in = http.getInputStream();
        byte b[] = new byte[100];
        int total = 0;
        int n;
        do {
            n = in.read(b);
            if (n > 0) total += n;
        } while (n > 0);
        debug ("client: read " + total + " bytes");
        in.close();
        http.disconnect();
    }

    String uri;
    byte[] b;
    int requests;

    MultiThreadTest(int port, int requests) throws Exception {
        uri = "http://localhost:" +
                     port + "/foo.html";

        b = new byte [256];
        this.requests = requests;

        synchronized (threadlock) {
            threadCounter ++;
        }
    }

    public void run () {
        try {
            for (int i=0; i<requests; i++) {
                doRequest (uri);
            }
        } catch (Exception e) {
            throw new RuntimeException (e.getMessage());
        } finally {
            synchronized (threadlock) {
                threadCounter --;
                if (threadCounter == 0) {
                    threadlock.notifyAll();
                }
            }
        }
    }

    static int threads=5;

    public static void main(String args[]) throws Exception {

        int x = 0, arg_len = args.length;
        int requests = 20;

        if (arg_len > 0 && args[0].equals("-d")) {
            debug = true;
            x = 1;
            arg_len --;
        }
        if (arg_len > 0) {
            threads = Integer.parseInt (args[x]);
            requests = Integer.parseInt (args[x+1]);
        }

        /* start the server */
        ServerSocket ss = new ServerSocket(0);
        Server svr = new Server(ss);
        svr.start();

        Object lock = MultiThreadTest.getLock();
        synchronized (lock) {
            for (int i=0; i<threads; i++) {
                MultiThreadTest t = new MultiThreadTest(ss.getLocalPort(), requests);
                t.start ();
            }
            try {
                lock.wait();
            } catch (InterruptedException e) {}
        }

        // shutdown server - we're done.
        svr.shutdown();

        int cnt = svr.connectionCount();
        MultiThreadTest.debug("Connections = " + cnt);
        int reqs = Worker.getRequests ();
        MultiThreadTest.debug("Requests = " + reqs);
        System.out.println ("Connection count = " + cnt + " Request count = " + reqs);
        if (cnt > threads) { // could be less
            throw new RuntimeException ("Expected "+threads + " connections: used " +cnt);
        }
        if  (reqs != threads*requests) {
            throw new RuntimeException ("Expected "+ threads*requests+ " requests: got " +reqs);
        }
    }
}

    /*
     * Server thread to accept connection and create worker threads
     * to service each connection.
     */
    class Server extends Thread {
        ServerSocket ss;
        int connectionCount;
        boolean shutdown = false;

        Server(ServerSocket ss) {
            this.ss = ss;
        }

        public synchronized int connectionCount() {
            return connectionCount;
        }

        public synchronized void shutdown() {
            shutdown = true;
        }

        public void run() {
            try {
                ss.setSoTimeout(2000);

                for (;;) {
                    Socket s;
                    try {
                        MultiThreadTest.debug("server: calling accept.");
                        s = ss.accept();
                        MultiThreadTest.debug("server: return accept.");
                    } catch (SocketTimeoutException te) {
                        MultiThreadTest.debug("server: STE");
                        synchronized (this) {
                            if (shutdown) {
                                MultiThreadTest.debug("server: Shuting down.");
                                return;
                            }
                        }
                        continue;
                    }

                    int id;
                    synchronized (this) {
                        id = connectionCount++;
                    }

                    Worker w = new Worker(s, id);
                    w.start();
                    MultiThreadTest.debug("server: Started worker " + id);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    ss.close();
                } catch (Exception e) { }
            }
        }
    }

    /*
     * Worker thread to service single connection - can service
     * multiple http requests on same connection.
     */
    class Worker extends Thread {
        Socket s;
        int id;

        Worker(Socket s, int id) {
            this.s = s;
            this.id = id;
        }

        static int requests = 0;
        static Object rlock = new Object();

        public static int getRequests () {
            synchronized (rlock) {
                return requests;
            }
        }
        public static void incRequests () {
            synchronized (rlock) {
                requests++;
            }
        }

        int readUntil (InputStream in, char[] seq) throws IOException {
            int i=0, count=0;
            while (true) {
                int c = in.read();
                if (c == -1)
                    return -1;
                count++;
                if (c == seq[i]) {
                    i++;
                    if (i == seq.length)
                        return count;
                    continue;
                } else {
                    i = 0;
                }
            }
        }

        public void run() {
            try {
                int max = 400;
                byte b[] = new byte[1000];
                InputStream in = new BufferedInputStream (s.getInputStream());
                // response to client
                PrintStream out = new PrintStream(
                                    new BufferedOutputStream(
                                                s.getOutputStream() ));

                for (;;) {

                    // read entire request from client
                    int n=0;

                    n = readUntil (in, new char[] {'\r','\n', '\r','\n'});

                    if (n <= 0) {
                        MultiThreadTest.debug("worker: " + id + ": Shutdown");
                        s.close();
                        return;
                    }

                    MultiThreadTest.debug("worker " + id +
                        ": Read request from client " +
                        "(" + n + " bytes).");

                    incRequests();
                    out.print("HTTP/1.1 200 OK\r\n");
                    out.print("Transfer-Encoding: chunked\r\n");
                    out.print("Content-Type: text/html\r\n");
                    out.print("Connection: Keep-Alive\r\n");
                    out.print ("Keep-Alive: timeout=15, max="+max+"\r\n");
                    out.print("\r\n");
                    out.print ("6\r\nHello \r\n");
                    out.print ("5\r\nWorld\r\n");
                    out.print ("0\r\n\r\n");
                    out.flush();

                    if (--max == 0) {
                        s.close();
                        return;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    s.close();
                } catch (Exception e) { }
            }
        }
    }
