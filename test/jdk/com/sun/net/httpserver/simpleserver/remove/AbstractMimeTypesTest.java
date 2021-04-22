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


import jdk.com.sun.net.httpserver.simpleserver.ServerMimeTypesResolutionTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

public abstract class AbstractMimeTypesTest {

    final Properties ACTUAL_MIME_TYPES = new Properties();
    final Properties EXPECTED_MIME_TYPES = new Properties();
    Path root;

    @BeforeTest
    public void setup() throws Exception {
        getActualOperatingSystemSpecificMimeTypes(ACTUAL_MIME_TYPES);
        getExpectedOperatingSystemSpecificMimeTypes(EXPECTED_MIME_TYPES);
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
        InputStream rs = ServerMimeTypesResolutionTest.class.getResourceAsStream(path);
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
    public void testCommonMimeTypes() {
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
