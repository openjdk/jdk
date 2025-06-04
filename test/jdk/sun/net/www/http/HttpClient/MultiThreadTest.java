/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @run main MultiThreadTest
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MultiThreadTest extends Thread {

    /*
     * Is debugging enabled - start with -d to enable.
     */
    static boolean debug = true; // disable debug once stability proven

    static final Object threadlock = new Object ();
    static int threadCounter = 0;
    // KEEP_ALIVE sent by the server
    static final int KEEP_ALIVE = 1; // seconds
    // The sending thread will sleep for this time after sending
    // half the number of its requests
    static final int SLEEP = KEEP_ALIVE * 1000 + 500; // ms

    static Object getLock() { return threadlock; }

    static void debug(String msg) {
        if (debug)
            System.out.println(msg);
    }

    static final AtomicInteger reqnum = new AtomicInteger();

    // Set to true after all requests have been sent
    static final AtomicBoolean DONE = new AtomicBoolean();

    void doRequest(String uri) throws Exception {
        URL url = new URL(uri + "?foo="+reqnum.getAndIncrement());
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

    final String uri;
    final byte[] b;
    final int requests;
    final CountDownLatch countDown;

    MultiThreadTest(String authority, int requests, CountDownLatch latch) throws Exception {
        countDown = latch;
        uri = "http://" + authority + "/foo.html";
        b = new byte [256];
        this.requests = requests;
    }

    public void run() {
        long start = System.nanoTime();
        try {
            for (int i=0; i<requests; i++) {
                doRequest (uri);
                // sleep after sending half of the requests, that
                // should cause the connections to be closed as idle
                // if sleeping more than KeepAlive.
                if (i == requests/2) Thread.sleep(SLEEP);
            }
        } catch (Exception e) {
            throw new RuntimeException (e.getMessage());
        } finally {
            countDown.countDown();
        }
        debug("client: end at " + at() + "ms, thread duration "
                + Duration.ofNanos(System.nanoTime() - start).toMillis() + "ms");
    }

    static int threads=5;
    // time at which main() started its work.
    static volatile long MAIN_START;

    // number of millis since MAIN_START
    public static long at() {
        return at(System.nanoTime());
    }
    // number of millis between MAIN_START and the given time stamp
    public static long at(long nanoTime) {
        return Duration.ofNanos(nanoTime - MAIN_START).toMillis();
    }

    public static void main(String args[]) throws Exception {
        long start = System.nanoTime();

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
        InetAddress loopback = InetAddress.getLoopbackAddress();
        ServerSocket ss = new ServerSocket();
        ss.bind(new InetSocketAddress(loopback, 0));
        Server svr = new Server(ss);
        svr.start();
        var latch = new CountDownLatch(threads);

        MAIN_START = System.nanoTime();
        try {
            Object lock = MultiThreadTest.getLock();
            List<MultiThreadTest> tests = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                MultiThreadTest t = new MultiThreadTest(svr.getAuthority(), requests, latch);
                tests.add(t);
                t.start();
            }

            latch.await();
            long end = System.nanoTime();
            DONE.compareAndSet(false, true);
            for (var test : tests) test.join();

            MultiThreadTest.debug("DONE at " + at(end) + "ms");

            // shutdown server - we're done.
            svr.shutdown();

            int cnt = svr.connectionCount();
            MultiThreadTest.debug("Connections = " + cnt);
            int reqs = Worker.getRequests();
            MultiThreadTest.debug("Requests = " + reqs);
            System.out.println("Connection count = " + cnt + " Request count = " + reqs);

            // We may have received traffic from something else than
            // our client. We should only count those workers for which
            // the expected header has been found.
            int validConnections = 0;
            // We detect worker threads that may have timed out, so we don't include them in
            // the count to compare with the number of connections.
            int becameIdle = 0;
            for (Worker w : svr.workers()) {
                if (w.headerFound > 0) {
                    validConnections++;
                    if (w.mayHaveTimedOut(end)) {
                        debug("Worker " + w.id + " may have timed out");
                        becameIdle++;
                    } else {
                        long at0 = at(w.lastReading);
                        long at1 = at(w.lastReplied);
                        debug("Worker " + w.id +" has not timed out - last used at " +
                            Math.max(at0, at1));
                    }
                } else {
                    debug("Worker " + w.id + " is not a valid connection");
                }
            }

            if (validConnections > threads) {
                if (SLEEP > KEEP_ALIVE) {
                    debug("INFO: " + validConnections
                            + " have been used, with " + becameIdle
                            + " becoming idle for more than " + KEEP_ALIVE + "s"
                            + " while using " + threads
                            + " threads to make concurrent connections");
                } else {
                    debug("WARNING: " + validConnections
                            + " have been used, with " + becameIdle
                            + " becoming idle for more than " + KEEP_ALIVE + "s"
                            + " where only " + threads
                            + " connections and none idle were expected!");
                }
            }

            if (validConnections > threads + becameIdle || validConnections == 0) { // could be less
                throw new RuntimeException("Expected " + (threads + becameIdle) + " connections: used " + validConnections);
            }

            if (validConnections != cnt) {
                debug("INFO: got " + (cnt - validConnections) + " unexpected connections");
            }
            if (reqs != threads * requests) {
                throw new RuntimeException("Expected " + threads * requests + " requests: got " + reqs);
            }

        } finally {
            debug("waiting for worker to shutdown at " + at() +"ms");
            for (Worker worker : svr.workers()) {
                // We want to verify that the client will eventually
                // close the idle connections. So just join the worker
                // and wait... This shouldn't take more than the granularity
                // of the keep-alive cache timer - so we're not actually
                // going to have to wait for one full minute here.
                worker.join(60_000);
            }
        }

        debug("main thread end - " + at() + "ms");
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
        private final Queue<Worker> workers = new ConcurrentLinkedQueue<>();

        Server(ServerSocket ss) {
            this.ss = ss;
        }

        public String getAuthority() {
            InetAddress address = ss.getInetAddress();
            String hostaddr = address.isAnyLocalAddress()
                ? "localhost" : address.getHostAddress();
            if (hostaddr.indexOf(':') > -1) {
                hostaddr = "[" + hostaddr + "]";
            }
            return hostaddr + ":" + ss.getLocalPort();
        }

        public Queue<Worker> workers() {
            return workers;
        }

        public synchronized int connectionCount() {
            return connectionCount;
        }

        public synchronized void shutdown() {
            shutdown = true;
            try {
                ss.close();
            } catch (IOException x) {
            }
        }

        public void run() {
            try {
                ss.setSoTimeout(6000);
                long startServer = System.nanoTime();

                for (;;) {
                    Socket s;
                    long acceptTime;
                    try {
                        MultiThreadTest.debug("server: calling accept.");
                        s = ss.accept();
                        acceptTime = System.nanoTime();
                        MultiThreadTest.debug("server: return accept (at " +
                                MultiThreadTest.at(acceptTime)+ "ms)");
                    } catch (IOException te) {
                        MultiThreadTest.debug("server: STE");
                        synchronized (this) {
                            if (shutdown) {
                                MultiThreadTest.debug("server: Shuting down at: "
                                        + MultiThreadTest.at() + "ms");
                                return;
                            }
                        }
                        if (te instanceof SocketTimeoutException)
                            continue;
                        throw te;
                    }

                    int id;
                    Worker w;
                    synchronized (this) {
                        id = connectionCount++;
                        w = new Worker(s, id, acceptTime);
                        workers.add(w);
                    }
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
        final long TIMEOUT = MultiThreadTest.KEEP_ALIVE; // seconds
        final long KEEP_ALIVE_NS = Duration.ofSeconds(TIMEOUT).toNanos(); // nanos
        final Socket s;
        final int id;

        // time at which the connection was accepted (nanos)
        final long acceptTime;

        // number of requests that had the expected URI
        volatile int headerFound;
        // time at which the worker thread exited
        volatile long stopTime;
        // Time at which the first call to is.read() for the last request
        // returned. This includes cases where -1 was returned.
        volatile long startReading;
        // Lat time at which a byte was read from the stream.
        volatile long lastReading;
        // number of times that the time between two consecutive received requests
        // exceeded the KEEP_ALIVE timeout.
        volatile int timeoutExceeded;
        // Number of requests handled by this worker
        volatile int requestHandled;
        // Time at which the last byte of the last reply was sent
        volatile long lastReplied;
        // Whether the worker was asked to stop
        volatile boolean done;

        Worker(Socket s, int id, long acceptTime) {
            super("Worker-" + id);
            this.s = s;
            this.id = id;
            // no time can have a value before accepTime
            this.acceptTime = lastReading = lastReplied = startReading = acceptTime;
        }

        static int requests = 0;
        static final Object rlock = new Object();

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

        /**
         * {@return Whether this worker might have been idle for more
         * than the KEEP_ALIVE timeout}
         * This will be true if the worker detected that the idle timeout
         * was exceeded between two consecutive request, or
         * if the time between the last reply and `nanosNow` exceeds
         * the keep-alive time.
         * @param nanosNow a timestamp in nano seconds
         */
        public boolean mayHaveTimedOut(long nanosNow) {
            // the minimum time elapsed between nanosNow and:
            //  - the time the socket was accepted
            //  - the last time a byte was received
            //  - the last time a reply was sent.
            // We must not use `startReading` here because `startReading` may
            // be set if the client asynchronously closes the connection
            // after all requests have been sent. We should really only
            // take into account `lastReading` and `lastReplied`.
            long idle = Math.min(nanosNow - lastReading, nanosNow - lastReplied);
            return timeoutExceeded > 0 || idle >= KEEP_ALIVE_NS;
        }

        int readUntil(InputStream in, StringBuilder headers, char[] seq) throws IOException {
            int i=0, count=0;
            long last;
            while (true) {
                int c = in.read();
                last = System.nanoTime();
                if (count == 0) {
                    // time at which the first byte of the request (or EOF) was received
                    startReading = last;
                }
                if (c == -1)
                    return -1;
                // time at which the last byte of the request was received (excludes EOF)
                lastReading = last;
                headers.append((char)c);
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
            long start = System.nanoTime();

            // lastUsed starts when the connection was accepted
            long lastUsed = acceptTime;
            int expectedReqs = 0;
            try {
                int max = 400;
                byte b[] = new byte[1000];
                InputStream in = new BufferedInputStream(s.getInputStream());
                // response to client
                PrintStream out = new PrintStream(
                                    new BufferedOutputStream(
                                                s.getOutputStream() ));

                for (;;) {
                    // read entire request from client
                    int n;
                    StringBuilder headers = new StringBuilder();
                    n = readUntil(in, headers, new char[] {'\r','\n', '\r','\n'});
                    long idle = startReading - lastUsed;
                    if (idle >= KEEP_ALIVE_NS) {
                        if (!MultiThreadTest.DONE.get()) {
                            // avoid increasing timeoutExceeded after the test is no
                            // longer sending requests.
                            timeoutExceeded++;
                        }
                    }
                    if (n <= 0) {
                        MultiThreadTest.debug("worker: " + id + ": Shutdown at "
                                + MultiThreadTest.at() + "ms");
                        s.close();
                        return;
                    }
                    if (headers.toString().contains("/foo.html?foo=")) {
                        headerFound = ++expectedReqs;
                        incRequests();
                    } else {
                        MultiThreadTest.debug("worker: " + id + ": Unexpected request received: " + headers);
                        s.close();
                        return;
                    }

                    MultiThreadTest.debug("worker " + id +
                        ": Read request from client " +
                        "(" + n + " bytes) at " + MultiThreadTest.at() + "ms");

                    out.print("HTTP/1.1 200 OK\r\n");
                    out.print("Transfer-Encoding: chunked\r\n");
                    out.print("Content-Type: text/html\r\n");
                    out.print("Connection: Keep-Alive\r\n");
                    out.print("Keep-Alive: timeout=" + TIMEOUT + ", max="+max+"\r\n");
                    out.print("\r\n");
                    out.print("6\r\nHello \r\n");
                    out.print("5\r\nWorld\r\n");
                    out.print("0\r\n\r\n");
                    out.flush();
                    requestHandled++;
                    lastUsed = lastReplied = System.nanoTime();
                    if (--max == 0) {
                        s.close();
                        return;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                long end = stopTime = System.nanoTime();
                try {
                    s.close();
                } catch (Exception e) { }
                MultiThreadTest.debug("worker: " + id + " end at " +
                            MultiThreadTest.at() + "ms,  elapsed since worker start: " +
                            Duration.ofNanos(end - start).toMillis() + "ms, elapsed since accept: " +
                            Duration.ofNanos(end - acceptTime).toMillis() +
                            "ms, timeout exceeded: " + timeoutExceeded +
                            ", successfuly handled " + requestHandled + "/" +
                             expectedReqs + " genuine requests, " +
                            ", mayHaveTimedOut: " + mayHaveTimedOut(end));
            }
        }

    }
