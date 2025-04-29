/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8235459
 * @summary Confirm that HttpRequest.BodyPublishers#ofFile(Path)
 *          assumes the default file system
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=errors,headers FilePublisherTest
 */

import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpOption;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.err;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.testng.Assert.assertEquals;

public class FilePublisherTest implements HttpServerAdapters {
    SSLContext sslContext;
    HttpTestServer httpTestServer;    // HTTP/1.1      [ 4 servers ]
    HttpTestServer httpsTestServer;   // HTTPS/1.1
    HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    HttpTestServer http3TestServer;   // HTTP/3 ( h3  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;
    String http3URI;
    String http3URI_head;

    FileSystem zipFs;
    Path defaultFsPath;
    Path zipFsPath;

    private volatile HttpClient sharedClient;

    // Default file system set up
    static final String DEFAULT_FS_MSG = "default fs";

    static Path defaultFsFile() throws Exception {
        var file = Path.of("defaultFile.txt");
        if (Files.notExists(file)) {
            Files.createFile(file);
            Files.writeString(file, DEFAULT_FS_MSG);
        }
        assertEquals(Files.readString(file), DEFAULT_FS_MSG);
        return file;
    }

    @DataProvider(name = "defaultFsData")
    public Object[][] defaultFsData() {
        return new Object[][]{
                { httpURI,   defaultFsPath, DEFAULT_FS_MSG, true  },
                { httpsURI,  defaultFsPath, DEFAULT_FS_MSG, true  },
                { http2URI,  defaultFsPath, DEFAULT_FS_MSG, true  },
                { https2URI, defaultFsPath, DEFAULT_FS_MSG, true  },
                { http3URI,  defaultFsPath, DEFAULT_FS_MSG, true  },
                { httpURI,   defaultFsPath, DEFAULT_FS_MSG, false },
                { httpsURI,  defaultFsPath, DEFAULT_FS_MSG, false },
                { http2URI,  defaultFsPath, DEFAULT_FS_MSG, false },
                { https2URI, defaultFsPath, DEFAULT_FS_MSG, false },
                { http3URI,  defaultFsPath, DEFAULT_FS_MSG, false },
        };
    }

    @Test(dataProvider = "defaultFsData")
    public void testDefaultFs(String uriString,
                              Path path,
                              String expectedMsg,
                              boolean sameClient) throws Exception {
        out.printf("\n\n--- testDefaultFs(%s, %s, \"%s\", %b): starting\n",
                uriString, path, expectedMsg, sameClient);
        send(uriString, path, expectedMsg, sameClient);
    }

    // Zip file system set up
    static final String ZIP_FS_MSG = "zip fs";

    static FileSystem newZipFs() throws Exception {
        Path zipFile = Path.of("file.zip");
        return FileSystems.newFileSystem(zipFile, Map.of("create", "true"));
    }

    static Path zipFsFile(FileSystem fs) throws Exception {
        var file = fs.getPath("fileInZip.txt");
        if (Files.notExists(file)) {
            Files.createFile(file);
            Files.writeString(file, ZIP_FS_MSG);
        }
        assertEquals(Files.readString(file), ZIP_FS_MSG);
        return file;
    }

    @DataProvider(name = "zipFsData")
    public Object[][] zipFsData() {
        return new Object[][]{
                { httpURI,   zipFsPath, ZIP_FS_MSG, true  },
                { httpsURI,  zipFsPath, ZIP_FS_MSG, true  },
                { http2URI,  zipFsPath, ZIP_FS_MSG, true  },
                { https2URI, zipFsPath, ZIP_FS_MSG, true  },
                { http3URI,  zipFsPath, ZIP_FS_MSG, true  },
                { httpURI,   zipFsPath, ZIP_FS_MSG, false },
                { httpsURI,  zipFsPath, ZIP_FS_MSG, false },
                { http2URI,  zipFsPath, ZIP_FS_MSG, false },
                { https2URI, zipFsPath, ZIP_FS_MSG, false },
                { http3URI,  zipFsPath, ZIP_FS_MSG, false },
        };
    }

    @Test(dataProvider = "zipFsData")
    public void testZipFs(String uriString,
                          Path path,
                          String expectedMsg,
                          boolean sameClient) throws Exception {
        out.printf("\n\n--- testZipFs(%s, %s, \"%s\", %b): starting\n",
                uriString, path, expectedMsg, sameClient);
        send(uriString, path, expectedMsg, sameClient);
    }

    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    static Version version(String uri) {
        if (uri.contains("/http1/") || uri.contains("/https1/"))
            return HTTP_1_1;
        if (uri.contains("/http2/") || uri.contains("/https2/"))
            return HTTP_2;
        if (uri.contains("/http3/"))
            return HTTP_3;
        return null;
    }

    HttpRequest.Builder newRequestBuilder(String uri) {
        var builder = HttpRequest.newBuilder(URI.create(uri));
        if (version(uri) == HTTP_3) {
            builder.version(HTTP_3);
            builder.setOption(H3_DISCOVERY, http3TestServer.h3DiscoveryConfig());
        }
        return builder;
    }

    HttpResponse<String> headRequest(HttpClient client)
            throws IOException, InterruptedException
    {
        out.println("\n" + now() + "--- Sending HEAD request ----\n");
        err.println("\n" + now() + "--- Sending HEAD request ----\n");

        var request = newRequestBuilder(http3URI_head)
                .HEAD().version(HTTP_2).build();
        var response = client.send(request, BodyHandlers.ofString());
        assertEquals(response.statusCode(), 200);
        assertEquals(response.version(), HTTP_2);
        out.println("\n" + now() + "--- HEAD request succeeded ----\n");
        err.println("\n" + now() + "--- HEAD request succeeded ----\n");
        return response;
    }

    private HttpClient makeNewClient() {
        return newClientBuilderForH3()
                .proxy(NO_PROXY)
                .sslContext(sslContext)
                .build();
    }

    HttpClient newHttpClient(boolean share) {
        if (!share) return makeNewClient();
        HttpClient shared = sharedClient;
        if (shared != null) return shared;
        synchronized (this) {
            shared = sharedClient;
            if (shared == null) {
                shared = sharedClient = makeNewClient();
            }
            return shared;
        }
    }

    record CloseableClient(HttpClient client, boolean shared)
            implements Closeable {
        public void close() {
            if (shared) return;
            client.close();
        }
    }

    private static final int ITERATION_COUNT = 3;

    private void send(String uriString,
                      Path path,
                      String expectedMsg,
                      boolean sameClient)
            throws Exception {
        HttpClient client = null;

        for (int i = 0; i < ITERATION_COUNT; i++) {
            if (!sameClient || client == null) {
                client = newHttpClient(sameClient);
                if (!sameClient && version(uriString) == HTTP_3) {
                    headRequest(client);
                }
            }
            try (var cl = new CloseableClient(client, sameClient)) {
                var req = newRequestBuilder(uriString)
                        .POST(BodyPublishers.ofFile(path))
                        .build();
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                out.println("Got response: " + resp);
                out.println("Got body: " + resp.body());
                assertEquals(resp.statusCode(), 200);
                assertEquals(resp.body(), expectedMsg);
                assertEquals(resp.version(), version(uriString));
            }
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        out.println(now() + "begin setup");

        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        defaultFsPath = defaultFsFile();
        zipFs = newZipFs();
        zipFsPath = zipFsFile(zipFs);

        InetSocketAddress sa =
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

        httpTestServer = HttpServerAdapters.HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new HttpEchoHandler(), "/http1/echo");
        httpURI = "http://" + httpTestServer.serverAuthority() + "/http1/echo";

        httpsTestServer = HttpServerAdapters.HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new HttpEchoHandler(), "/https1/echo");
        httpsURI = "https://" + httpsTestServer.serverAuthority() + "/https1/echo";

        http2TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new HttpEchoHandler(), "/http2/echo");
        http2URI = "http://" + http2TestServer.serverAuthority() + "/http2/echo";

        https2TestServer = HttpServerAdapters.HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new HttpEchoHandler(), "/https2/echo");
        https2URI = "https://" + https2TestServer.serverAuthority() + "/https2/echo";

        http3TestServer = HttpTestServer.create(HTTP_3, sslContext);
        http3TestServer.addHandler(new HttpEchoHandler(), "/http3/echo");
        http3TestServer.addHandler(new HttpHeadOrGetHandler(), "/http3/head");
        http3URI = "https://" + http3TestServer.serverAuthority() + "/http3/echo";
        http3URI_head = "https://" + http3TestServer.serverAuthority() + "/http3/head/x";

        err.println(now() + "Starting servers");
        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
        http3TestServer.start();

        out.println("HTTP/1.1 server (http) listening at: " + httpTestServer.serverAuthority());
        out.println("HTTP/1.1 server (TLS)  listening at: " + httpsTestServer.serverAuthority());
        out.println("HTTP/2   server (h2c)  listening at: " + http2TestServer.serverAuthority());
        out.println("HTTP/2   server (h2)   listening at: " + https2TestServer.serverAuthority());
        out.println("HTTP/3   server (h2)   listening at: " + http3TestServer.serverAuthority());
        out.println(" + alt endpoint (h3)   listening at: " + http3TestServer.getH3AltService()
                .map(Http3TestServer::getAddress));

        headRequest(newHttpClient(true));

        out.println(now() + "setup done");
        err.println(now() + "setup done");
    }

    @AfterTest
    public void teardown() throws Exception {
        sharedClient.close();
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
        http3TestServer.stop();
        zipFs.close();
    }

    static class HttpEchoHandler implements HttpServerAdapters.HttpTestHandler {
        @Override
        public void handle(HttpServerAdapters.HttpTestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }
}
