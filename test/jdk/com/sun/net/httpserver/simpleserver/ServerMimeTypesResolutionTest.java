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

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import sun.net.www.MimeTable;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.*;
/*
 * @test
 * @summary Basic tests for MIME types in response headers
 * @modules java.base/sun.net.www:+open
 * @run testng/othervm ServerMimeTypesResolutionTest
 */
public class ServerMimeTypesResolutionTest {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final InetSocketAddress WILDCARD_ADDR = new InetSocketAddress(0);
    static final boolean ENABLE_LOGGING = true;
    static final String FILE_NAME = "empty-file-of-type";
    static final String UNKNOWN_FILE_EXTENSION = ".unknown-file-extension";
    final Properties ACTUAL_MIME_TYPES = new Properties();
    Path root;

    @BeforeTest
    public void setup() throws Exception {
        if (ENABLE_LOGGING) {
            Logger logger = Logger.getLogger("com.sun.net.httpserver");
            ConsoleHandler ch = new ConsoleHandler();
            logger.setLevel(Level.ALL);
            ch.setLevel(Level.ALL);
            logger.addHandler(ch);
        }
        getActualOperatingSystemSpecificMimeTypes(ACTUAL_MIME_TYPES);
        root = createFileTreeFromMimeTypes(ACTUAL_MIME_TYPES);
    }

    private List<String> getFileTypes(Properties input) {
        return new ArrayList<>(getMimeTypesPerFileType(input).keySet());
    }

    private Map<String,String> getMimeTypesPerFileType(Properties input) {
        return input
                .entrySet()
                .stream()
                .filter( entry -> ((String)entry.getValue()).contains("file_extensions"))
                .flatMap(entry ->
                        Arrays.asList(
                                ((String)deserialize((String) entry.getValue(), ";")
                                        .get("file_extensions")).split(",")
                        )
                        .stream()
                        .map( extension ->
                                Map.entry(extension, entry.getKey().toString())
                        )
                )
                .collect(
                      Collectors.toMap(
                          entry -> entry.getKey(),
                          entry -> entry.getValue()
                      )
                );
    }

    private Path createFileTreeFromMimeTypes(Properties properties) throws IOException {
        final Path root = Files.createDirectory(CWD.resolve(getClass().getSimpleName()));
        for (String type : getFileTypes(properties)) {
            Files.createFile(root.resolve(toFileName(type)));
        }
        Files.createFile(root.resolve(toFileName(UNKNOWN_FILE_EXTENSION)));
        return root;
    }

    private String toFileName(String extension) {
        return "%s%s".formatted(FILE_NAME, extension);
    }

    protected Properties deserialize(String serialized, String delimiter) {
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
        }
        catch (IOException exception) {
            exception.printStackTrace();
            throw new RuntimeException(
                    "error while deserializing string %s to properties".formatted(serialized),
                    exception
            );
        }
    }

    public Properties getActualOperatingSystemSpecificMimeTypes(Properties properties) throws Exception {
        properties.load(MimeTable.class.getResourceAsStream("content-types.properties"));
        return properties;
    }

    @Test
    public void testMimeTypeHeaders() throws Exception {
        final var serverUnderTest = SimpleFileServer.createFileServer(WILDCARD_ADDR, root, OutputLevel.NONE);
        serverUnderTest.start();
        try {
            final var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            final Map<String, String> mimeTypesPerFileType = getMimeTypesPerFileType(ACTUAL_MIME_TYPES);
            for (String fileExtension : getFileTypes(ACTUAL_MIME_TYPES)) {
                final String expectedMimeType = mimeTypesPerFileType.get(fileExtension);
                execute(serverUnderTest, client, fileExtension, expectedMimeType);
            }
            execute(serverUnderTest, client, UNKNOWN_FILE_EXTENSION,"application/octet-stream");
        } finally {
            serverUnderTest.stop(0);
        }
    }

    private void execute(HttpServer server, HttpClient client, String inputFileExtension, String expectedMimeType)
                                                                        throws IOException, InterruptedException {
        final var uri = URI.create(
                            "http://localhost:%s/%s".formatted(
                                    server.getAddress().getPort(),
                                    toFileName(inputFileExtension)
                            )
        );
        final var request = HttpRequest.newBuilder(uri).build();
        final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(response.statusCode(), 200);
        assertEquals(response.headers().firstValue("content-type").get(),expectedMimeType);
    }

    @Test
    public void verifyCommonMimeTypes() {
        List<String> commonMimeTypes = Arrays.asList(".aac", ".abw", ".arc", ".avi", ".azw", ".bin", ".bmp", ".bz",
                ".bz2", ".csh", ".css", ".csv", ".doc", ".docx",".eot", ".epub", ".gz", ".gif", ".htm", ".html", ".ico",
                ".ics", ".jar", ".jpeg", ".jpg", ".js", ".json", ".jsonld", ".mid", ".midi", ".mjs", ".mp3", ".cda",
                ".mp4", ".mpeg", ".mpkg", ".odp", ".ods", ".odt", ".oga", ".ogv", ".ogx", ".opus", ".otf", ".png",
                ".pdf", ".php", ".ppt", ".pptx", ".rar", ".rtf", ".sh", ".svg", ".swf", ".tar", ".tif", ".tiff", ".ts",
                ".ttf", ".txt", ".vsd", ".wav", ".weba", ".webm", ".webp", ".woff", ".woff2", ".xhtml", ".xls", ".xlsx",
                ".xml", ".xul", ".zip", ".3gp", "3g2", ".7z");
        Set<String> actualFileTypes = new HashSet<>(getFileTypes(ACTUAL_MIME_TYPES));
        for (String commonMimeType : commonMimeTypes) {
            assertTrue(!actualFileTypes.add(commonMimeType), "expecting %s to be present".formatted(commonMimeType));
        }
    }


}
