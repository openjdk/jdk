/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test to stress directory listings
 * @library /test/lib
 * @run testng/othervm/timeout=180 StressDirListings
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.assertEquals;

public class StressDirListings {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = false;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");
    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    HttpServer simpleFileServer;

    @BeforeTest
    public void setup() throws IOException {
        out.println(now() + " creating server");
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
        simpleFileServer = SimpleFileServer.createFileServer(LOOPBACK_ADDR, CWD, OutputLevel.VERBOSE);
        simpleFileServer.start();
        out.println(now() + " server started");
    }

    @AfterTest
    public void teardown() {
        out.println(now() + " stopping server");
        simpleFileServer.stop(0);
        out.println(now() + " server stopped");
    }

    // Enough to trigger FileSystemException: Too many open files (machine dependent)
    static final int TIMES = 11_000;

    /**
     * Issues a large number of identical GET requests sequentially, each of
     * which returns the root directory listing. An IOException will be thrown
     * if the server does not issue a valid reply, e.g. the server logic that
     * enumerates the directory files fails to close the stream from Files::list.
     */
    @Test
    public void testDirListings() throws Exception {
        out.println(now() + " starting test");
        var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
        var request = HttpRequest.newBuilder(uri(simpleFileServer)).build();
        for (int i=0; i<TIMES; i++) {
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            if (i % 100 == 0) {
                out.print(" " + i + " ");
            }
            if (i % 1000 == 0) {
                out.println(now());
            }
        }
        out.println(now() + " test finished");
    }

    static URI uri(HttpServer server) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/")
                .buildUnchecked();
    }
}
