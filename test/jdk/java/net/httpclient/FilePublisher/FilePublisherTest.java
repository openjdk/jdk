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
 * @bug 8235459 8358688
 * @summary Verifies `HttpRequest.BodyPublishers#ofFile(Path)` against file
 *          systems that support `Path#toFile()` and also those that don't
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm FilePublisherTest
 */

import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import jdk.httpclient.test.lib.common.HttpServerAdapters;

import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class FilePublisherTest implements HttpServerAdapters {
    SSLContext sslContext;
    HttpServerAdapters.HttpTestServer httpTestServer;    // HTTP/1.1      [ 4 servers ]
    HttpServerAdapters.HttpTestServer httpsTestServer;   // HTTPS/1.1
    HttpServerAdapters.HttpTestServer http2TestServer;   // HTTP/2 ( h2c )
    HttpServerAdapters.HttpTestServer https2TestServer;  // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    FileSystem zipFs;
    Path defaultFsPath;
    Path zipFsPath;

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
                { httpURI,   defaultFsPath, DEFAULT_FS_MSG, false },
                { httpsURI,  defaultFsPath, DEFAULT_FS_MSG, false },
                { http2URI,  defaultFsPath, DEFAULT_FS_MSG, false },
                { https2URI, defaultFsPath, DEFAULT_FS_MSG, false },
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
                { httpURI,   zipFsPath, ZIP_FS_MSG, false },
                { httpsURI,  zipFsPath, ZIP_FS_MSG, false },
                { http2URI,  zipFsPath, ZIP_FS_MSG, false },
                { https2URI, zipFsPath, ZIP_FS_MSG, false },
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

    @Test
    public void testFileNotFound() throws Exception {
        out.printf("\n\n--- testFileNotFound(): starting\n");
        try (FileSystem fs = newZipFs()) {
            Path fileInZip = fs.getPath("non-existent.txt");
            BodyPublishers.ofFile(fileInZip);
            fail();
        } catch (FileNotFoundException e) {
            out.println("Caught expected: " + e);
        }
        var path = Path.of("fileNotFound.txt");
        try {
            Files.deleteIfExists(path);
            BodyPublishers.ofFile(path);
            fail();
        } catch (FileNotFoundException e) {
            out.println("Caught expected: " + e);
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
                client = HttpClient.newBuilder()
                        .proxy(NO_PROXY)
                        .sslContext(sslContext)
                        .build();
            }
            var req = HttpRequest.newBuilder(URI.create(uriString))
                .POST(BodyPublishers.ofFile(path))
                .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            out.println("Got response: " + resp);
            out.println("Got body: " + resp.body());
            assertEquals(resp.statusCode(), 200);
            assertEquals(resp.body(), expectedMsg);
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        defaultFsPath = defaultFsFile();
        zipFs = newZipFs();
        zipFsPath = zipFsFile(zipFs);

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

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
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
