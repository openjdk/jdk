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

import java.io.*;
import java.net.http.*;
import java.net.*;
import java.util.concurrent.*;
import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

/**
 * @test
 * @bug 8151441
 * @run main/othervm/timeout=10 ShortRequestBody
 */

/**
 * Exception was not being thrown
 */
public class ShortRequestBody {

    static Server server;
    static String reqbody = "Hello world";

    static String response = "HTTP/1.1 200 OK\r\nContent-length: 0\r\n\r\n";

    static class RequestBody implements HttpRequest.BodyProcessor {
        public long onRequestStart(HttpRequest hr, LongConsumer flowController) {
            return reqbody.length() + 1; // wrong!
        }

        public boolean onRequestBodyChunk(ByteBuffer buf) throws IOException {
            byte[] b = reqbody.getBytes();
            buf.put(b);
            return true;
        }
    }

    static void close(Closeable c) {
        try {
            if (c == null)
                return;
            c.close();
        } catch (IOException e) {}
    }

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();
        URI uri = new URI("http://127.0.0.1:" + port + "/");

        HttpRequest request;
        HttpResponse r;
        Socket s = null;
        CompletableFuture<HttpResponse> cf1;
        try {
            cf1 = HttpRequest.create(uri)
                    .body(new RequestBody())
                    .GET()
                    .responseAsync();

            s = server.accept();
            s.getInputStream().readAllBytes();
            try (OutputStream os = s.getOutputStream()) {
                os.write(response.getBytes());
            } catch (IOException ee) {
            }

            try {
                r = cf1.get(3, TimeUnit.SECONDS);
                throw new RuntimeException("Failed");
            } catch (TimeoutException e0) {
                throw new RuntimeException("Failed timeout");
            } catch (ExecutionException e) {
                System.err.println("OK");
            }
        } finally {
            HttpClient.getDefault().executorService().shutdownNow();
            close(s);
            close(server);
        }
    }
}
