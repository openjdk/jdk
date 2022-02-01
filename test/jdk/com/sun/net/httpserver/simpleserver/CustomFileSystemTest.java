/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests for SimpleFileServer with a root that is not of the default
 *          file system
 * @library /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.net.URIBuilder
 * @run testng/othervm CustomFileSystemTest
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import jdk.test.lib.Platform;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CustomFileSystemTest {
    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    static final boolean ENABLE_LOGGING = true;
    static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");

    @BeforeTest
    public void setup() throws Exception {
        if (ENABLE_LOGGING) {
            ConsoleHandler ch = new ConsoleHandler();
            LOGGER.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            LOGGER.addHandler(ch);
        }
    }

    @Test
    public void testFileGET() throws Exception {
        var root = createDirectoryInCustomFs("testFileGET");
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(file);
        var expectedLength = Long.toString(Files.size(file));

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "aFile.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), "some text");
            assertEquals(response.headers().firstValue("content-type").get(), "text/plain");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testDirectoryGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                <li><a href="aFile.txt">aFile.txt</a></li>
                </ul>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createDirectoryInCustomFs("testDirectoryGET");
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(root);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), expectedBody);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testFileHEAD() throws Exception {
        var root = createDirectoryInCustomFs("testFileHEAD");
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(file);
        var expectedLength = Long.toString(Files.size(file));

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "aFile.txt"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/plain");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), "");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testDirectoryHEAD() throws Exception {
        var expectedLength = Integer.toString(
                (openHTML + """
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                <li><a href="aFile.txt">aFile.txt</a></li>
                </ul>
                """ + closeHTML).getBytes(UTF_8).length);
        var root = createDirectoryInCustomFs("testDirectoryHEAD");
        var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);
        var lastModified = getLastModified(root);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, ""))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), "");
        } finally {
            server.stop(0);
        }
    }

    @DataProvider
    public Object[][] indexFiles() {
        var fileContent = openHTML + """
                <h1>This is an index file</h1>
                """ + closeHTML;
        var dirListing = openHTML + """
                <h1>Directory listing for &#x2F;</h1>
                <ul>
                </ul>
                """ + closeHTML;
        return new Object[][] {
                {"1", "index.html", "text/html",                "116", fileContent, true},
                {"2", "index.htm",  "text/html",                "116", fileContent, true},
                {"3", "index.txt",  "text/html; charset=UTF-8", "134", dirListing,  false}
        };
    }

    @Test(dataProvider = "indexFiles")
    public void testDirectoryWithIndexGET(String id,
                                          String filename,
                                          String contentType,
                                          String contentLength,
                                          String expectedBody,
                                          boolean serveIndexFile) throws Exception {
        var root = createDirectoryInCustomFs("testDirectoryWithIndexGET"+id);
        var lastModified = getLastModified(root);
        if (serveIndexFile) {
            var file = Files.writeString(root.resolve(filename), expectedBody, CREATE);
            lastModified = getLastModified(file);
        }

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 200);
            assertEquals(response.headers().firstValue("content-type").get(), contentType);
            assertEquals(response.headers().firstValue("content-length").get(), contentLength);
            assertEquals(response.headers().firstValue("last-modified").get(), lastModified);
            assertEquals(response.body(), expectedBody);
        } finally {
            server.stop(0);
            if (serveIndexFile) {
                Files.delete(root.resolve(filename));
            }
        }
    }

    @Test
    public void testNotReadableFileGET() throws Exception {
        if (!Platform.isWindows()) {  // not applicable on Windows
            var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;aFile.txt</p>
                """ + closeHTML;
            var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
            var root = createDirectoryInCustomFs("testNotReadableFileGET");
            var file = Files.writeString(root.resolve("aFile.txt"), "some text", CREATE);

            file.toFile().setReadable(false, false);
            assert !Files.isReadable(file);

            var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
            server.start();
            try {
                var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
                var request = HttpRequest.newBuilder(uri(server, "aFile.txt")).build();
                var response = client.send(request, BodyHandlers.ofString());
                assertEquals(response.statusCode(), 404);
                assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
                assertEquals(response.body(), expectedBody);
            } finally {
                server.stop(0);
                file.toFile().setReadable(true, false);
            }
        }
    }

    @Test
    public void testNotReadableSegmentGET() throws Exception {
        if (!Platform.isWindows()) {  // not applicable on Windows
            var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;dir&#x2F;aFile.txt</p>
                """ + closeHTML;
            var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
            var root = createDirectoryInCustomFs("testNotReadableSegmentGET");
            var dir = Files.createDirectory(root.resolve("dir"));
            var file = Files.writeString(dir.resolve("aFile.txt"), "some text", CREATE);

            dir.toFile().setReadable(false, false);
            assert !Files.isReadable(dir);
            assert Files.isReadable(file);

            var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
            server.start();
            try {
                var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
                var request = HttpRequest.newBuilder(uri(server, "dir/aFile.txt")).build();
                var response = client.send(request, BodyHandlers.ofString());
                assertEquals(response.statusCode(), 404);
                assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
                assertEquals(response.body(), expectedBody);
            } finally {
                server.stop(0);
                dir.toFile().setReadable(true, false);
            }
        }
    }

    @Test
    public void testInvalidRequestURIGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;aFile?#.txt</p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createDirectoryInCustomFs("testInvalidRequestURIGET");

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "aFile?#.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testNotFoundGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;doesNotExist.txt</p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createDirectoryInCustomFs("testNotFoundGET");

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "doesNotExist.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testNotFoundHEAD() throws Exception {
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;doesNotExist.txt</p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createDirectoryInCustomFs("testNotFoundHEAD");

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "doesNotExist.txt"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), "");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testSymlinkGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;symlink</p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createDirectoryInCustomFs("testSymlinkGET");
        var symlink = root.resolve("symlink");
        var target = Files.writeString(root.resolve("target.txt"), "some text", CREATE);
        Files.createSymbolicLink(symlink, target);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "symlink")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testSymlinkSegmentGET() throws Exception {
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;symlink&#x2F;aFile.txt</p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);
        var root = createDirectoryInCustomFs("testSymlinkSegmentGET");
        var symlink = root.resolve("symlink");
        var target = Files.createDirectory(root.resolve("target"));
        Files.writeString(target.resolve("aFile.txt"), "some text", CREATE);
        Files.createSymbolicLink(symlink, target);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "symlink/aFile.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testHiddenFileGET() throws Exception {
        var root = createDirectoryInCustomFs("testHiddenFileGET");
        var file = createHiddenFile(root);
        var fileName = file.getFileName().toString();
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;""" + fileName +
                """
                </p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, fileName)).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testHiddenSegmentGET() throws Exception {
        var root = createDirectoryInCustomFs("testHiddenSegmentGET");
        var file = createFileInHiddenDirectory(root);
        var expectedBody = openHTML + """
                <h1>File not found</h1>
                <p>&#x2F;.hiddenDirectory&#x2F;aFile.txt</p>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, ".hiddenDirectory/aFile.txt")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            assertEquals(response.body(), expectedBody);
        } finally {
            server.stop(0);
        }
    }

    private Path createHiddenFile(Path root) throws IOException {
        Path file;
        if (Platform.isWindows()) {
            file = Files.createFile(root.resolve("aFile.txt"));
            Files.setAttribute(file, "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
        } else {
            file = Files.writeString(root.resolve(".aFile.txt"), "some text", CREATE);
        }
        assertTrue(Files.isHidden(file));
        return file;
    }

    private Path createFileInHiddenDirectory(Path root) throws IOException {
        Path dir;
        Path file;
        if (Platform.isWindows()) {
            dir = Files.createDirectory(root.resolve("hiddenDirectory"));
            Files.setAttribute(dir, "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
        } else {
            dir = Files.createDirectory(root.resolve(".hiddenDirectory"));
        }
        file = Files.writeString(dir.resolve("aFile.txt"), "some text", CREATE);
        assertTrue(Files.isHidden(dir));
        assertFalse(Files.isHidden(file));
        return file;
    }

    @Test
    public void testMovedPermanently() throws Exception {
        var root = createDirectoryInCustomFs("testMovedPermanently");
        Files.createDirectory(root.resolve("aDirectory"));
        var expectedBody = openHTML + """
                <h1>Directory listing for &#x2F;aDirectory&#x2F;</h1>
                <ul>
                </ul>
                """ + closeHTML;
        var expectedLength = Integer.toString(expectedBody.getBytes(UTF_8).length);

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            {
                var client = HttpClient.newBuilder().proxy(NO_PROXY)
                        .followRedirects(HttpClient.Redirect.NEVER).build();
                var uri = uri(server, "aDirectory");
                var request = HttpRequest.newBuilder(uri).build();
                var response = client.send(request, BodyHandlers.ofString());
                assertEquals(response.statusCode(), 301);
                assertEquals(response.headers().firstValue("content-length").get(), "0");
                assertEquals(response.headers().firstValue("location").get(), "/aDirectory/");

                // tests that query component is preserved during redirect
                var uri2 = uri(server, "aDirectory", "query");
                var req2 = HttpRequest.newBuilder(uri2).build();
                var res2 = client.send(req2, BodyHandlers.ofString());
                assertEquals(res2.statusCode(), 301);
                assertEquals(res2.headers().firstValue("content-length").get(), "0");
                assertEquals(res2.headers().firstValue("location").get(), "/aDirectory/?query");
            }

            {   // tests that redirect to returned relative URI works
                var client = HttpClient.newBuilder().proxy(NO_PROXY)
                        .followRedirects(HttpClient.Redirect.ALWAYS).build();
                var uri = uri(server, "aDirectory");
                var request = HttpRequest.newBuilder(uri).build();
                var response = client.send(request, BodyHandlers.ofString());
                assertEquals(response.statusCode(), 200);
                assertEquals(response.body(), expectedBody);
                assertEquals(response.headers().firstValue("content-type").get(), "text/html; charset=UTF-8");
                assertEquals(response.headers().firstValue("content-length").get(), expectedLength);
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testXss() throws Exception {
        var root = createDirectoryInCustomFs("testXss");

        var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, OutputLevel.VERBOSE);
        server.start();
        try {
            var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            var request = HttpRequest.newBuilder(uri(server, "beginDelim%3C%3EEndDelim")).build();
            var response = client.send(request, BodyHandlers.ofString());
            assertEquals(response.statusCode(), 404);
            assertTrue(response.body().contains("beginDelim%3C%3EEndDelim"));
            assertTrue(response.body().contains("File not found"));
        } finally {
            server.stop(0);
        }
    }

    static Path createDirectoryInCustomFs(String name) throws Exception {
        var defaultFs = FileSystems.getDefault();
        var fs = new CustomProvider(defaultFs.provider()).newFileSystem(defaultFs);
        var dir = fs.getPath(name);
        if (Files.notExists(dir)) {
            Files.createDirectory(dir);
        }
        return dir.toAbsolutePath();
    }

    static final String openHTML = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8"/>
                </head>
                <body>
                """;

    static final String closeHTML = """
                </body>
                </html>
                """;

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }

    static URI uri(HttpServer server, String path, String query) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .query(query)
                .buildUnchecked();
    }

    static String getLastModified(Path path) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.of("GMT"))
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    // --- Custom File System ---

    static class CustomProvider extends FileSystemProvider {
        private final ConcurrentHashMap<FileSystem, CustomFileSystem> map =
                new ConcurrentHashMap<>();
        private final FileSystemProvider defaultProvider;

        public CustomProvider(FileSystemProvider provider) {
            defaultProvider = provider;
        }

        @Override
        public String getScheme() {
            return defaultProvider.getScheme();
        }

        public FileSystem newFileSystem(FileSystem fs) {
            return map.computeIfAbsent(fs, (sfs) ->
                    new CustomFileSystem(this, fs));
        }

        @Override
        public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
            FileSystem fs = defaultProvider.newFileSystem(uri, env);
            return map.computeIfAbsent(fs, (sfs) ->
                    new CustomFileSystem(this, fs)
            );
        }

        @Override
        public FileSystem getFileSystem(URI uri) {
            return map.get(defaultProvider.getFileSystem(uri));
        }

        @Override
        public Path getPath(URI uri) {
            Path p = defaultProvider.getPath(uri);
            return map.get(defaultProvider.getFileSystem(uri)).wrap(p);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            Path p = toCustomPath(path).unwrap();
            return defaultProvider.newByteChannel(p, options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            throw new RuntimeException("not implemented");
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            Path p = toCustomPath(dir).unwrap();
            defaultProvider.createDirectory(p, attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            Path p = toCustomPath(path).unwrap();
            defaultProvider.delete(p);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            Path sp = toCustomPath(source).unwrap();
            Path tp = toCustomPath(target).unwrap();
            defaultProvider.copy(sp, tp, options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options)
                throws IOException {
            Path sp = toCustomPath(source).unwrap();
            Path tp = toCustomPath(target).unwrap();
            defaultProvider.move(sp, tp, options);
        }

        @Override
        public boolean isSameFile(Path path, Path path2)
                throws IOException {
            Path p = toCustomPath(path).unwrap();
            Path p2 = toCustomPath(path2).unwrap();
            return defaultProvider.isSameFile(p, p2);
        }

        @Override
        public boolean isHidden(Path path) throws IOException {
            Path p = toCustomPath(path).unwrap();
            return defaultProvider.isHidden(p);
        }

        @Override
        public FileStore getFileStore(Path path) throws IOException {
            Path p = toCustomPath(path).unwrap();
            return defaultProvider.getFileStore(p);
        }

        @Override
        public void checkAccess(Path path, AccessMode... modes) throws IOException {
            Path p = toCustomPath(path).unwrap();
            defaultProvider.checkAccess(p, modes);
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(Path path,
                                                                    Class<V> type,
                                                                    LinkOption... options) {
            Path p = toCustomPath(path).unwrap();
            return defaultProvider.getFileAttributeView(p, type, options);
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes(Path path,
                                                                Class<A> type,
                                                                LinkOption... options)
                throws IOException {
            Path p = toCustomPath(path).unwrap();
            return defaultProvider.readAttributes(p, type, options);
        }

        @Override
        public Map<String, Object> readAttributes(Path path,
                                                  String attributes,
                                                  LinkOption... options)
                throws IOException {
            Path p = toCustomPath(path).unwrap();
            return defaultProvider.readAttributes(p, attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute,
                                 Object value, LinkOption... options)
                throws IOException {
            Path p = toCustomPath(path).unwrap();
            defaultProvider.setAttribute(p, attribute, options);
        }

        // Checks that the given file is a CustomPath
        static CustomPath toCustomPath(Path obj) {
            if (obj == null)
                throw new NullPointerException();
            if (!(obj instanceof CustomPath cp))
                throw new ProviderMismatchException();
            return cp;
        }
    }

    static class CustomFileSystem extends FileSystem {

        private final CustomProvider provider;
        private final FileSystem delegate;

        public CustomFileSystem(CustomProvider provider, FileSystem delegate) {
            this.provider = provider;
            this.delegate = delegate;
        }

        @Override
        public FileSystemProvider provider() {
            return provider;
        }

        @Override
        public void close() throws IOException { delegate.close(); }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public String getSeparator() { return delegate.getSeparator(); }

        @Override
        public Iterable<Path> getRootDirectories() {
            return null;
        }

        @Override
        public Iterable<FileStore> getFileStores() {
            return null;
        }

        @Override
        public Set<String> supportedFileAttributeViews() {
            return null;
        }

        @Override
        public Path getPath(String first, String... more) {
            return delegate.getPath(first, more);
        }

        @Override
        public PathMatcher getPathMatcher(String syntaxAndPattern) {
            return null;
        }

        @Override
        public UserPrincipalLookupService getUserPrincipalLookupService() {
            return null;
        }

        @Override
        public WatchService newWatchService() throws IOException {
            return null;
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        Path wrap(Path path) {
            return (path != null) ? new CustomPath(this, path) : null;
        }

        Path unwrap(Path wrapper) {
            if (wrapper == null)
                throw new NullPointerException();
            if (!(wrapper instanceof CustomPath cp))
                throw new ProviderMismatchException();
            return cp.unwrap();
        }
    }

    static class CustomPath implements Path {

        private final CustomFileSystem fs;
        private final Path delegate;

        CustomPath(CustomFileSystem fs, Path delegate) {
            this.fs = fs;
            this.delegate = delegate;
        }

        @Override
        public FileSystem getFileSystem() {
            return fs;
        }

        @Override
        public boolean isAbsolute() {
            return delegate.isAbsolute();
        }

        @Override
        public Path getRoot() {
            return fs.wrap(delegate.getRoot());
        }

        @Override
        public Path getFileName() {
            return null;
        }

        @Override
        public Path getParent() {
            return null;
        }

        @Override
        public int getNameCount() {
            return 0;
        }

        @Override
        public Path getName(int index) {
            return null;
        }

        @Override
        public Path subpath(int beginIndex, int endIndex) {
            return null;
        }

        @Override
        public boolean startsWith(Path other) {
            return delegate.startsWith(other);
        }

        @Override
        public boolean endsWith(Path other) {
            return false;
        }

        @Override
        public Path normalize() {
            return fs.wrap(delegate.normalize());
        }

        @Override
        public Path resolve(Path other) {
            return fs.wrap(delegate.resolve(fs.unwrap(other)));
        }

        @Override
        public Path relativize(Path other) {
            return null;
        }

        @Override
        public URI toUri() {
            return delegate.toUri();
        }

        @Override
        public Path toAbsolutePath() {
            return fs.wrap(delegate.toAbsolutePath());
        }

        @Override
        public Path toRealPath(LinkOption... options) throws IOException {
            return null;
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
            return null;
        }

        @Override
        public int compareTo(Path other) {
            return 0;
        }

        Path unwrap() {
            return delegate;
        }
    }
}
