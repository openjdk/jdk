/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @test
 * @bug 8353662 8202708
 * @summary Verify long-standing, disabled by default behavior of resolving non-local
 *          file URLs using FTP.
 * @run junit/othervm -Djdk.net.file.ftpfallback=true NonLocalFtpFallback
 */

public class NonLocalFtpFallback {

    // Port 21 may not be available, use an HTTP proxy with an ephemeral port
    private HttpServer proxyServer;

    // The file requested in this test
    private Path file;

    // FTP URIs requested by the proxy client
    private Set<URI> uris = new HashSet<>();

    /**
     * Set up the HTTP proxy used for serving FTP in this test
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @BeforeEach
    public void setup() throws IOException {
        // Create a file with some random data
        byte[] data = new byte[512];
        new Random().nextBytes(data);
        file = Files.write(Path.of("ftp-file.txt"), data);

        // Set up an HTTP proxy server
        proxyServer = HttpServer.create();
        // Bind to the loopback address with an ephemeral port
        InetAddress loopbackAddress = InetAddress.getLoopbackAddress();
        proxyServer.bind(new InetSocketAddress(loopbackAddress, 0), 0);
        // Handler for the FTP proxy request
        proxyServer.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                // Record the URI requested
                uris.add(exchange.getRequestURI());
                // Send the data
                exchange.sendResponseHeaders(200, Files.size(file));
                try (OutputStream out = exchange.getResponseBody()) {
                    Files.copy(file, out);
                }
                // Complete the exchange
                exchange.close();
            }
        });
        // Start the proxy server
        proxyServer.start();
    }

    /**
     * Shut down proxy server and clean up files created
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @AfterEach
    public void destroy() throws IOException {
        proxyServer.stop(2);
        Files.delete(file);
    }

    /**
     * Verifies the long-standing and unspecified FTP fallback feature where the file
     * URL scheme handler attempts an FTP connection for non-local files.
     *
     * The non-local file URL used here is of the form 'file://remotehost/path'. Since the
     * host component is not equal to 'localhost', this is considered a non-local URL.
     *
     * @throws Exception
     */
    @Test
    public void verifyNonLocalFtpFallback() throws Exception {
        URL localURL = file.toUri().toURL();
        // We can use a fake host name here, no actual FTP request will be made
        String hostname = "remotehost";
        URL nonLocalURL = new URL("file", hostname, localURL.getFile());

        // Open the non-local file: URL connection using a proxy
        Proxy proxy = new Proxy(Proxy.Type.HTTP,
                new InetSocketAddress(proxyServer.getAddress().getAddress(),
                        proxyServer.getAddress().getPort()));
        URLConnection con = nonLocalURL.openConnection(proxy);

        // Assert that the expected file content is retrieved
        try (InputStream in = con.getInputStream()) {
            byte[] retrived = in.readAllBytes();
            assertArrayEquals(Files.readAllBytes(file), retrived);
        }

        // Assert that the expected FTP URI was requested in the HTTP proxy
        assertEquals(1, uris.size());
        URL ftpURL = new URL("ftp", hostname, localURL.getFile());
        assertEquals(ftpURL.toURI(), uris.iterator().next());
    }

    /**
     * Sanity check that a local file URL (with a host component equal to 'localhost')
     * does not open any FtpURLConnection when the FTP fallback feature is enabled.
     *
     * @throws Exception
     */
    @Test
    public void verifyLocalFileURL() throws Exception {
        URL localURL = file.toUri().toURL();
        URL nonLocalURL = new URL("file", "localhost", localURL.getFile());

        // Open the local file: URL connection supplying a proxy
        Proxy proxy = new Proxy(Proxy.Type.HTTP,
                new InetSocketAddress(proxyServer.getAddress().getAddress(),
                        proxyServer.getAddress().getPort()));
        URLConnection con = nonLocalURL.openConnection(proxy);

        // Assert that the expected file content is read
        try (InputStream in = con.getInputStream()) {
            byte[] retrived = in.readAllBytes();
            assertArrayEquals(Files.readAllBytes(file), retrived);
        }

        // Assert that no FTP URIs were requested in the HTTP proxy
        assertEquals(0, uris.size());
    }

    /**
     * Verify that opening a stream on a non-proxy URLConnection for a non-local
     * file URL with an unknown host fails with UnknownHostException
     * when the fallback FtpURLConnection attempts to connect to the non-existing
     * FTP server.
     */
    @Test
    public void verifyFtpUnknownHost() throws IOException {
        URL url = new URL("file://nonexistinghost/not-exist.txt");
        assertThrows(UnknownHostException.class, () -> {
            InputStream in = url.openConnection(Proxy.NO_PROXY).getInputStream();
        });
    }
}
