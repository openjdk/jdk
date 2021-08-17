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

import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jdk.test.lib.net.URIBuilder;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import sun.net.www.MimeTable;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @summary Tests for MIME types in response headers
 * @modules java.base/sun.net.www:+open
 * @library /test/lib
 * @build jdk.test.lib.net.URIBuilder
 * @run testng/othervm ServerMimeTypesResolutionTest
 */
public class ServerMimeTypesResolutionTest {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final InetSocketAddress LOOPBACK_ADDR = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    static final String FILE_NAME = "empty-file-of-type";
    static final String UNKNOWN_FILE_EXTENSION = ".unknown-file-extension";
    static final Properties SUPPORTED_MIME_TYPES = new Properties();
    static final Set<String> UNSUPPORTED_FILE_EXTENSIONS = new HashSet<>();
    static List<String> supportedFileExtensions;
    static Path root;

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
        getSupportedMimeTypes(SUPPORTED_MIME_TYPES);
        supportedFileExtensions = getFileExtensions(SUPPORTED_MIME_TYPES);
        root = createFileTreeFromMimeTypes(SUPPORTED_MIME_TYPES);
    }

    public static Properties getSupportedMimeTypes(Properties properties) throws IOException {
        properties.load(MimeTable.class.getResourceAsStream("content-types.properties"));
        return properties;
    }

    private static List<String> getFileExtensions(Properties input) {
        return new ArrayList<>(getMimeTypesPerFileExtension(input).keySet());
    }

    private static Map<String,String> getMimeTypesPerFileExtension(Properties input) {
        return input
                .entrySet()
                .stream()
                .filter(entry -> ((String)entry.getValue()).contains("file_extensions"))
                .flatMap(entry ->
                        Arrays.stream(
                                ((String)deserialize((String) entry.getValue(), ";")
                                        .get("file_extensions")).split(","))
                        .map(extension ->
                                Map.entry(extension, entry.getKey().toString())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Path createFileTreeFromMimeTypes(Properties properties)
            throws IOException {
        final Path root = Files.createDirectory(CWD.resolve(ServerMimeTypesResolutionTest.class.getSimpleName()));
        for (String extension : supportedFileExtensions) {
            Files.createFile(root.resolve(toFileName(extension)));
        }
        Files.createFile(root.resolve(toFileName(UNKNOWN_FILE_EXTENSION)));
        return root;
    }

    private static String toFileName(String extension) {
        return "%s%s".formatted(FILE_NAME, extension);
    }

    protected static Properties deserialize(String serialized, String delimiter) {
        try {
            Properties properties = new Properties();
            properties.load(
                new StringReader(
                    Optional.ofNullable(delimiter)
                            .map(d -> serialized.replaceAll(delimiter, System.lineSeparator()))
                            .orElse(serialized)
                )
            );
            return properties;
        } catch (IOException exception) {
            exception.printStackTrace();
            throw new RuntimeException(("error while deserializing string %s " +
                    "to properties").formatted(serialized), exception);
        }
    }

    @Test
    public static void testMimeTypeHeaders() throws Exception {
        final var server = SimpleFileServer.createFileServer(LOOPBACK_ADDR, root, SimpleFileServer.OutputLevel.VERBOSE);
        server.start();
        try {
            final var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            final Map<String, String> mimeTypesPerFileExtension = getMimeTypesPerFileExtension(SUPPORTED_MIME_TYPES);
            for (String extension : supportedFileExtensions) {
                final String expectedMimeType = mimeTypesPerFileExtension.get(extension);
                execute(server, client, extension, expectedMimeType);
            }
            execute(server, client, UNKNOWN_FILE_EXTENSION,"application/octet-stream");
        } finally {
            server.stop(0);
        }
    }

    private static void execute(HttpServer server,
                                HttpClient client,
                                String extension,
                                String expectedMimeType)
            throws IOException, InterruptedException {
        final var uri = uri(server, toFileName(extension));
        final var request = HttpRequest.newBuilder(uri).build();
        final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(response.statusCode(), 200);
        assertEquals(response.headers().firstValue("content-type").get(),expectedMimeType);
    }

    static URI uri(HttpServer server, String path) {
        return URIBuilder.newBuilder()
                .host("localhost")
                .port(server.getAddress().getPort())
                .scheme("http")
                .path("/" + path)
                .buildUnchecked();
    }

    @DataProvider
    public static Object[][] commonExtensions() {
        Set<String> extensions = Set.of(".aac", ".abw", ".arc", ".avi", ".azw", ".bin", ".bmp", ".bz",
                ".bz2", ".csh", ".css", ".csv", ".doc", ".docx",".eot", ".epub", ".gz", ".gif", ".htm", ".html", ".ico",
                ".ics", ".jar", ".jpeg", ".jpg", ".js", ".json", ".jsonld", ".mid", ".midi", ".mjs", ".mp3", ".cda",
                ".mp4", ".mpeg", ".mpkg", ".odp", ".ods", ".odt", ".oga", ".ogv", ".ogx", ".opus", ".otf", ".png",
                ".pdf", ".php", ".ppt", ".pptx", ".rar", ".rtf", ".sh", ".svg", ".swf", ".tar", ".tif", ".tiff", ".ts",
                ".ttf", ".txt", ".vsd", ".wav", ".weba", ".webm", ".webp", ".woff", ".woff2", ".xhtml", ".xls", ".xlsx",
                ".xml", ".xul", ".zip", ".3gp", ".3g2", ".7z");
        return extensions.stream().map(e -> new Object[]{e}).toArray(Object[][]::new);
    }

    /**
     * This is a one-off test to check which common file extensions are
     * currently supported by the system-wide mime table returned by
     * {@linkplain java.net.FileNameMap#getContentTypeFor(String) getContentTypeFor}.
     *
     * Source common mime types:
     * https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Common_types
     */
//    @Test(dataProvider = "commonExtensions")
    public static void testCommonExtensions(String extension) {
        var contains = supportedFileExtensions.contains(extension);
        if (!contains) UNSUPPORTED_FILE_EXTENSIONS.add(extension);
        assertTrue(contains, "expecting %s to be present".formatted(extension));
    }

//    @AfterTest
    public static void printUnsupportedFileExtensions() {
        System.out.println("Unsupported file extensions: " + UNSUPPORTED_FILE_EXTENSIONS.size());
        UNSUPPORTED_FILE_EXTENSIONS.forEach(System.out::println);
    }
}
