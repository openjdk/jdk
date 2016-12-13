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
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpRequest;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static jdk.incubator.http.HttpResponse.BodyHandler.discard;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @test
 * @bug 8151441
 * @summary Request body of incorrect (larger or smaller) sizes than that
 *          reported by the body processor
 * @run main/othervm ShortRequestBody
 */

public class ShortRequestBody {

    static final Path testSrc = Paths.get(System.getProperty("test.src", "."));
    static volatile HttpClient staticDefaultClient;

    static HttpClient defaultClient() {
        if (staticDefaultClient == null) {
            synchronized (ShortRequestBody.class) {
                staticDefaultClient = HttpClient.newHttpClient();
            }
        }
        return staticDefaultClient;
    }

    // Some body types ( sources ) for testing.
    static final String STRING_BODY = "Hello world";
    static final byte[] BYTE_ARRAY_BODY = new byte[] {
        (byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE };
    static final Path FILE_BODY = testSrc.resolve("docs").resolve("files").resolve("foo.txt");

    // Body lengths and offsets ( amount to be wrong by ), to make coordination
    // between client and server easier.
    static final int[] BODY_LENGTHS = new int[] { STRING_BODY.length(),
                                                  BYTE_ARRAY_BODY.length,
                                                  fileSize(FILE_BODY) };
    static final int[] BODY_OFFSETS = new int[] { 0, +1, -1, +2, -2, +3, -3 };

    // A delegating body processor. Subtypes will have a concrete body type.
    static abstract class AbstractDelegateRequestBody
            implements HttpRequest.BodyProcessor {

        final HttpRequest.BodyProcessor delegate;
        final long contentLength;

        AbstractDelegateRequestBody(HttpRequest.BodyProcessor delegate,
                                    long contentLength) {
            this.delegate = delegate;
            this.contentLength = contentLength;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            delegate.subscribe(subscriber);
        }

        @Override
        public long contentLength() { return contentLength; /* may be wrong! */ }
    }

    // Request body processors that may generate a different number of actual
    // bytes to that of what is reported through their {@code contentLength}.

    static class StringRequestBody extends AbstractDelegateRequestBody {
        StringRequestBody(String body, int additionalLength) {
            super(HttpRequest.BodyProcessor.fromString(body),
                  body.getBytes(UTF_8).length + additionalLength);
        }
    }

    static class ByteArrayRequestBody extends AbstractDelegateRequestBody {
        ByteArrayRequestBody(byte[] body, int additionalLength) {
            super(HttpRequest.BodyProcessor.fromByteArray(body),
                  body.length + additionalLength);
        }
    }

    static class FileRequestBody extends AbstractDelegateRequestBody {
        FileRequestBody(Path path, int additionalLength) throws IOException {
            super(HttpRequest.BodyProcessor.fromFile(path),
                  Files.size(path) + additionalLength);
        }
    }

    // ---

    public static void main(String[] args) throws Exception {
        try (Server server = new Server()) {
            URI uri = new URI("http://127.0.0.1:" + server.getPort() + "/");

            // sanity
            success(uri, new StringRequestBody(STRING_BODY, 0));
            success(uri, new ByteArrayRequestBody(BYTE_ARRAY_BODY, 0));
            success(uri, new FileRequestBody(FILE_BODY, 0));

            for (int i=1; i< BODY_OFFSETS.length; i++) {
                failureBlocking(uri, new StringRequestBody(STRING_BODY, BODY_OFFSETS[i]));
                failureBlocking(uri, new ByteArrayRequestBody(BYTE_ARRAY_BODY, BODY_OFFSETS[i]));
                failureBlocking(uri, new FileRequestBody(FILE_BODY, BODY_OFFSETS[i]));

                failureNonBlocking(uri, new StringRequestBody(STRING_BODY, BODY_OFFSETS[i]));
                failureNonBlocking(uri, new ByteArrayRequestBody(BYTE_ARRAY_BODY, BODY_OFFSETS[i]));
                failureNonBlocking(uri, new FileRequestBody(FILE_BODY, BODY_OFFSETS[i]));
            }
        } finally {
            Executor def = defaultClient().executor();
            if (def instanceof ExecutorService) {
               ((ExecutorService)def).shutdownNow();
            }
        }
    }

    static void success(URI uri, HttpRequest.BodyProcessor processor)
        throws Exception
    {
        CompletableFuture<HttpResponse<Void>> cf;
        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .POST(processor)
                                         .build();
        cf = defaultClient().sendAsync(request, discard(null));

        HttpResponse<Void> resp = cf.get(30, TimeUnit.SECONDS);
        out.println("Response code: " + resp.statusCode());
        check(resp.statusCode() == 200, "Expected 200, got ", resp.statusCode());
    }

    static void failureNonBlocking(URI uri, HttpRequest.BodyProcessor processor)
        throws Exception
    {
        CompletableFuture<HttpResponse<Void>> cf;
        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .POST(processor)
                                         .build();
        cf = defaultClient().sendAsync(request, discard(null));

        try {
            HttpResponse<Void> r = cf.get(30, TimeUnit.SECONDS);
            throw new RuntimeException("Unexpected response: " + r.statusCode());
        } catch (TimeoutException x) {
            throw new RuntimeException("Unexpected timeout", x);
        } catch (ExecutionException expected) {
            out.println("Caught expected: " + expected);
            check(expected.getCause() instanceof IOException,
                  "Expected cause IOException, but got: ", expected.getCause());
        }
    }

    static void failureBlocking(URI uri, HttpRequest.BodyProcessor processor)
        throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .POST(processor)
                                         .build();
        try {
            HttpResponse<Void> r = defaultClient().send(request, discard(null));
            throw new RuntimeException("Unexpected response: " + r.statusCode());
        } catch (IOException expected) {
            out.println("Caught expected: " + expected);
        }
    }

    static class Server extends Thread implements AutoCloseable {

        static String RESPONSE = "HTTP/1.1 200 OK\r\n" +
                                 "Connection: close\r\n"+
                                 "Content-length: 0\r\n\r\n";

        private final ServerSocket ss;
        private volatile boolean closed;

        Server() throws IOException {
            super("Test-Server");
            ss = new ServerSocket(0); this.start();
        }

        int getPort() { return ss.getLocalPort(); }

        @Override
        public void run() {
            int count = 0;
            int offset = 0;

            while (!closed) {
                try (Socket s = ss.accept()) {
                    InputStream is = s.getInputStream();
                    readRequestHeaders(is);
                    byte[] ba = new byte[1024];

                    int length = BODY_LENGTHS[count % 3];
                    length += BODY_OFFSETS[offset];

                    is.readNBytes(ba, 0, length);

                    OutputStream os = s.getOutputStream();
                    os.write(RESPONSE.getBytes(US_ASCII));
                    count++;
                    if (count % 6 == 0) // 6 is the number of failure requests per offset
                        offset++;
                } catch (IOException e) {
                    if (!closed)
                        System.out.println("Unexpected" + e);
                }
            }
        }

        @Override
        public void close() {
            if (closed)
                return;
            closed = true;
            try {
                ss.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Unexpected", e);
            }
        }
    }

    static final byte[] requestEnd = new byte[] {'\r', '\n', '\r', '\n' };

    // Read until the end of a HTTP request headers
    static void readRequestHeaders(InputStream is) throws IOException {
        int requestEndCount = 0, r;
        while ((r = is.read()) != -1) {
            if (r == requestEnd[requestEndCount]) {
                requestEndCount++;
                if (requestEndCount == 4) {
                    break;
                }
            } else {
                requestEndCount = 0;
            }
        }
    }

    static int fileSize(Path p) {
        try { return (int) Files.size(p); }
        catch (IOException x) { throw new UncheckedIOException(x); }
    }

    static boolean check(boolean cond, Object... failedArgs) {
        if (cond)
            return true;
        // We are going to fail...
        StringBuilder sb = new StringBuilder();
        for (Object o : failedArgs)
                sb.append(o);
        throw new RuntimeException(sb.toString());
    }
}
