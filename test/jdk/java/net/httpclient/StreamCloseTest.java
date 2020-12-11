/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, NTT DATA.
 *
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
 * @bug 8257736
 * @modules java.net.http
 * @run testng/othervm StreamCloseTest
 */

import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.CompletionHandler;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.Assert;

public class StreamCloseTest {

    private static class TestInputStream extends InputStream {
        private final boolean exceptionTest;
        private volatile boolean closeCalled;

        public TestInputStream(boolean exceptionTest) {
            super();
            this.exceptionTest = exceptionTest;
            this.closeCalled = false;
        }

        @Override
        public int read() throws IOException {
            if (exceptionTest) {
                throw new IOException("test");
            }
            return -1;
        }

        @Override
        public void close() throws IOException {
            closeCalled = true;
            super.close();
        }
    }

    private static AsynchronousServerSocketChannel server;

    private static HttpClient client;

    private static HttpRequest.Builder requestBuilder;

    private static final String SERVER_RESPONSE_STRING = "HTTP/1.1 200 OK\r\n" +
                                                         "Content-Length: 0\r\n" +
                                                         "\r\n";

    private static final ByteBuffer SERVER_RESPONSE = ByteBuffer.wrap(SERVER_RESPONSE_STRING.getBytes());

    @BeforeTest
    public void setup() throws Exception {
        server = AsynchronousServerSocketChannel.open()
                                                .bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            public void completed(AsynchronousSocketChannel ch, Void att) {
                server.accept(null, this);
                SERVER_RESPONSE.rewind();
                ch.write(SERVER_RESPONSE);
                try {
                    ch.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            public void failed(Throwable exc, Void att) {
                // Do nothing
            }
        });

        client = HttpClient.newBuilder()
                           .version(Version.HTTP_1_1)
                           .followRedirects(Redirect.ALWAYS)
                           .build();
        InetSocketAddress localAddr = (InetSocketAddress)server.getLocalAddress();
        URL url = new URL("http", localAddr.getHostString(), localAddr.getPort(), "/");
        requestBuilder = HttpRequest.newBuilder(url.toURI());
    }

    @AfterTest
    public void teardown() throws Exception {
        server.close();
    }

    @Test
    public void normallyCloseTest() throws Exception{
        TestInputStream in = new TestInputStream(false);
        HttpRequest request = requestBuilder.copy()
                                            .POST(BodyPublishers.ofInputStream(() -> in))
                                            .build();
        client.send(request, BodyHandlers.discarding());
        Assert.assertTrue(in.closeCalled, "InputStream was not closed!");
    }

    @Test
    public void closeTestOnException() throws Exception{
        TestInputStream in = new TestInputStream(true);
        HttpRequest request = requestBuilder.copy()
                                            .POST(BodyPublishers.ofInputStream(() -> in))
                                            .build();
        try {
            client.send(request, BodyHandlers.discarding());
        } catch (IOException e) { // expected
            Assert.assertTrue(in.closeCalled, "InputStream was not closed!");
            return;
        }
        Assert.fail("IOException should be occurred!");
    }
}
