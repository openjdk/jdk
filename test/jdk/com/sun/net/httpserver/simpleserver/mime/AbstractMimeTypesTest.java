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
import java.io.InputStream;
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

import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.testng.Assert.*;

public abstract class AbstractMimeTypesTest {

    static final Path CWD = Path.of(".").toAbsolutePath();
    static final InetSocketAddress WILDCARD_ADDR = new InetSocketAddress(0);
    static final boolean ENABLE_LOGGING = true;
    static final String FILE_NAME = "empty-file-of-type";
    final Properties ACTUAL_MIME_TYPES = new Properties();
    final Properties EXPECTED_MIME_TYPES = new Properties();
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
        getExpectedOperatingSystemSpecificMimeTypes(EXPECTED_MIME_TYPES);
        root = createFileTreeFromMimeTypes(EXPECTED_MIME_TYPES);
    }


    private List<String> getFileTypes(Properties input) {
        return input
                .entrySet()
                .stream()
                .map(entry -> deserialize((String) entry.getValue(), ";") )
                .filter(properties -> properties.containsKey("file_extensions") )
                .flatMap(properties -> Arrays.asList(
                        ((String)properties.get("file_extensions")).split(",")
                        ).stream()
                )
                .collect(Collectors.toList());
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
        return root;
    }

    private String toFileName(String extension) {
        return "%s%s".formatted(FILE_NAME, extension);
    }

    protected Properties deserialize(String serialized) {
        return deserialize(serialized,null);
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

    protected Properties load(String path) throws IOException {
        InputStream rs = AbstractMimeTypesTest.class.getResourceAsStream(path);
        Properties properties = new Properties();
        properties.load(rs);
        return properties;
    }

    protected abstract Properties getActualOperatingSystemSpecificMimeTypes(Properties properties) throws Exception;
    protected abstract Properties getExpectedOperatingSystemSpecificMimeTypes(Properties properties) throws Exception;

    @Test
    public void testNoDuplicateFileExtensions() throws Exception {
        List<String> fileTypesList = getFileTypes(ACTUAL_MIME_TYPES);
        Set<String> fileTypesSet = new LinkedHashSet<>(fileTypesList);
        assertEquals(
                fileTypesList.size(),
                fileTypesSet.size(),
                "actual MIME types (JDK main codebase) contain duplicate file extensions"
        );
    }

    @Test
    public void verifyMimeTypeDefinitions() throws Exception {
        assertEquals(
                EXPECTED_MIME_TYPES,
                ACTUAL_MIME_TYPES,
                "expected MIME types (JDK test codebase) differ from actual MIME types (JDK main codebase)"
        );
    }

    @Test
    public void testKnownMimeTypeHeaders() throws Exception {
        final var ss = SimpleFileServer.createFileServer(WILDCARD_ADDR, root, OutputLevel.NONE);
        ss.start();
        try {
            final var client = HttpClient.newBuilder().proxy(NO_PROXY).build();
            final Map<String, String> mimeTypesPerFileType = getMimeTypesPerFileType(EXPECTED_MIME_TYPES);
            for (String fileExtension : getFileTypes(EXPECTED_MIME_TYPES)) {
                final var uri = URI.create(
                                    "http://localhost:%s/%s".formatted(
                                            ss.getAddress().getPort(),
                                            toFileName(fileExtension)
                                    )
                );
                final var request = HttpRequest.newBuilder(uri).build();
                final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(response.statusCode(), 200);
                assertEquals(
                        response.headers().firstValue("content-type").get(),
                        mimeTypesPerFileType.get(fileExtension)
                );
            }
        } finally {
            ss.stop(0);
        }
    }

    @Test
    public void testUnKnownMimeTypeHeaders() throws Exception {
        throw new UnsupportedOperationException("should test for unknown type, check docs for applicatiopn octet stream");
    }


}
