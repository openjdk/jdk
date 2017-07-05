/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

//package javaapplication16;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A cut-down Http/1 Server for testing various error situations
 *
 * use interrupt() to halt
 */
public class Server extends Thread {

    ServerSocket ss;
    List<Connection> sockets;
    AtomicInteger counter = new AtomicInteger(0);

    // waits up to 20 seconds for something to happen
    // dont use this unless certain activity coming.
    public Connection activity() {
        for (int i = 0; i < 80 * 100; i++) {
            for (Connection c : sockets) {
                if (c.poll()) {
                    return c;
                }
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
            }
        }
        return null;
    }

    // clears all current connections on Server.
    public void reset() {
        for (Connection c : sockets) {
            c.close();
        }
    }

    /**
     * Reads data into an ArrayBlockingQueue<String> where each String
     * is a line of input, that was terminated by CRLF (not included)
     */
    class Connection extends Thread {
        Connection(Socket s) throws IOException {
            this.socket = s;
            id = counter.incrementAndGet();
            is = s.getInputStream();
            os = s.getOutputStream();
            incoming = new ArrayBlockingQueue<>(100);
            setName("Server-Connection");
            setDaemon(true);
            start();
        }
        final Socket socket;
        final int id;
        final InputStream is;
        final OutputStream os;
        final ArrayBlockingQueue<String> incoming;

        final static String CRLF = "\r\n";

        // sentinel indicating connection closed
        final static String CLOSED = "C.L.O.S.E.D";
        volatile boolean closed = false;

        @Override
        public void run() {
            byte[] buf = new byte[256];
            String s = "";
            try {
                while (true) {
                    int n = is.read(buf);
                    if (n == -1) {
                        cleanup();
                        return;
                    }
                    String s0 = new String(buf, 0, n, StandardCharsets.ISO_8859_1);
                    s = s + s0;
                    int i;
                    while ((i=s.indexOf(CRLF)) != -1) {
                        String s1 = s.substring(0, i+2);
                        incoming.put(s1);
                        if (i+2 == s.length()) {
                            s = "";
                            break;
                        }
                        s = s.substring(i+2);
                    }
                }
            } catch (IOException |InterruptedException e1) {
                cleanup();
            } catch (Throwable t) {
                System.out.println("X: " + t);
                cleanup();
            }
        }

        @Override
        public String toString() {
            return "Server.Connection: " + socket.toString();
        }

        public void sendHttpResponse(int code, String body, String... headers)
            throws IOException
        {
            String r1 = "HTTP/1.1 " + Integer.toString(code) + " status" + CRLF;
            for (int i=0; i<headers.length; i+=2) {
                r1 += headers[i] + ": " + headers[i+1] + CRLF;
            }
            int clen = body == null ? 0 : body.length();
            r1 += "Content-Length: " + Integer.toString(clen) + CRLF;
            r1 += CRLF;
            if (body != null) {
                r1 += body;
            }
            send(r1);
        }

        // content-length is 10 bytes too many
        public void sendIncompleteHttpResponseBody(int code) throws IOException {
            String body = "Hello World Helloworld Goodbye World";
            String r1 = "HTTP/1.1 " + Integer.toString(code) + " status" + CRLF;
            int clen = body.length() + 10;
            r1 += "Content-Length: " + Integer.toString(clen) + CRLF;
            r1 += CRLF;
            if (body != null) {
                r1 += body;
            }
            send(r1);
        }

        public void sendIncompleteHttpResponseHeaders(int code)
            throws IOException
        {
            String r1 = "HTTP/1.1 " + Integer.toString(code) + " status" + CRLF;
            send(r1);
        }

        public void send(String r) throws IOException {
            os.write(r.getBytes(StandardCharsets.ISO_8859_1));
        }

        public synchronized void close() {
            cleanup();
            closed = true;
            incoming.clear();
        }

        public String nextInput(long timeout, TimeUnit unit) {
            String result = "";
            while (poll()) {
                try {
                    String s = incoming.poll(timeout, unit);
                    if (s == null && closed) {
                        return CLOSED;
                    } else {
                        result += s;
                    }
                } catch (InterruptedException e) {
                    return null;
                }
            }
            return result;
        }

        public String nextInput() {
            return nextInput(0, TimeUnit.SECONDS);
        }

        public boolean poll() {
            return incoming.peek() != null;
        }

        private void cleanup() {
            try {
                socket.close();
            } catch (IOException e) {}
            sockets.remove(this);
        }
    }

    Server(int port) throws IOException {
        ss = new ServerSocket(port);
        sockets = Collections.synchronizedList(new LinkedList<>());
        setName("Test-Server");
        setDaemon(true);
        start();
    }

    Server() throws IOException {
        this(0);
    }

    int port() {
        return ss.getLocalPort();
    }

    public String getURL() {
        return "http://127.0.0.1:" + port() + "/foo/";
    }

    public void close() {
        try {
            ss.close();
        } catch (IOException e) {
        }
        for (Connection c : sockets) {
            c.close();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Socket s = ss.accept();
                Connection c = new Connection(s);
                sockets.add(c);
            } catch (IOException e) {
            }
        }
    }

}
