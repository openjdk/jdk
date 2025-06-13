/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jdk.internal.util.OperatingSystem;
import static jdk.jpackage.internal.OverridableResource.Source.DefaultResource;
import static jdk.jpackage.internal.OverridableResource.Source.ResourceDir;
import jdk.jpackage.internal.resources.ResourceLocator;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;


public class OverridableResourceTest {

    private static String[] saveResource(ResourceWriter resourceWriter, Path dstPath) throws IOException {
        switch (dstPath.getFileName().toString()) {
            case "file" -> {
                return resourceWriter.saveToFile(dstPath);
            }
            case "dir" -> {
                return resourceWriter.saveInDir(dstPath);
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static String[] saveResource(
            OverridableResource resource, Path dstPath, boolean dstFileOverwrite)
            throws IOException {
        return saveResource(buildResourceWriter(resource).dstFileOverwrite(dstFileOverwrite), dstPath);
    }

    private static List<Object[]> data() {
        List<Object[]> data = new ArrayList<>();

        for (var dstPath : List.of("file", "dir")) {
            for (var dstFileOverwrite : List.of(true, false)) {
                data.add(new Object[]{Path.of(dstPath), dstFileOverwrite});
            }
        }

        for (var dstPath : List.of("dir/file", "dir/dir")) {
            data.add(new Object[]{Path.of(dstPath), false});
        }

        return data;
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testDefault(Path dstPath, boolean dstFileOverwrite,
            @TempDir Path tempFolder) throws IOException {
        final String[] content = saveResource(
                new OverridableResource(DEFAULT_NAME, ResourceLocator.class), tempFolder.resolve(
                        dstPath), dstFileOverwrite);

        try (var resource = ResourceLocator.class.getResourceAsStream(DEFAULT_NAME);
                var isr = new InputStreamReader(resource, StandardCharsets.ISO_8859_1);
                var br = new BufferedReader(isr)) {
            assertArrayEquals(br.lines().toArray(String[]::new), content);
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testDefaultWithSubstitution(Path dstPath, boolean dstFileOverwrite,
            @TempDir Path tempFolder) throws IOException {
        if (SUBSTITUTION_DATA.size() != 1) {
            // Test setup issue
            throw new IllegalArgumentException(
                    "Substitution map should contain only a single entry");
        }

        OverridableResource resource = new OverridableResource(DEFAULT_NAME, ResourceLocator.class);

        var linesBeforeSubstitution = List.of(saveResource(resource, tempFolder.resolve(dstPath), dstFileOverwrite));

        resource.setSubstitutionData(SUBSTITUTION_DATA);
        var linesAfterSubstitution = List.of(saveResource(resource, tempFolder.resolve(dstPath), dstFileOverwrite));

        assertEquals(linesBeforeSubstitution.size(), linesAfterSubstitution.size());

        Iterator<String> beforeIt = linesBeforeSubstitution.iterator();
        Iterator<String> afterIt = linesAfterSubstitution.iterator();

        var substitutionEntry = SUBSTITUTION_DATA.entrySet().iterator().next();

        boolean linesMismatch = false;
        while (beforeIt.hasNext()) {
            String beforeStr = beforeIt.next();
            String afterStr = afterIt.next();

            if (beforeStr.equals(afterStr)) {
                assertFalse(beforeStr.contains(substitutionEntry.getKey()));
            } else {
                linesMismatch = true;
                assertTrue(beforeStr.contains(substitutionEntry.getKey()));
                assertTrue(afterStr.contains(substitutionEntry.getValue()));
                assertFalse(afterStr.contains(substitutionEntry.getKey()));
            }
        }

        assertTrue(linesMismatch);
    }

    private static Stream<Object[]> dataWithResourceName() {
        return data().stream().flatMap(origArgs -> {
            return Stream.of(ResourceName.values()).map(defaultName -> {
                Object[] args = new Object[origArgs.length + 1];
                args[0] = defaultName;
                System.arraycopy(origArgs, 0, args, 1, origArgs.length);
                return args;
            });
        });
    }

    @ParameterizedTest
    @MethodSource("dataWithResourceName")
    public void testResourceDir(ResourceName defaultName, Path dstPath,
            boolean dstFileOverwrite, @TempDir Path tempFolder) throws IOException {
        List<String> expectedResourceData = List.of("A", "B", "C");

        Path customFile = tempFolder.resolve("hello");
        Files.write(customFile, expectedResourceData);

        final var actualResourceData = saveResource(buildResourceWriter(
                new OverridableResource(defaultName.value, ResourceLocator.class)
                        .setPublicName(customFile.getFileName())
                        .setResourceDir(customFile.getParent())
                ).dstFileOverwrite(dstFileOverwrite).expectedSource(ResourceDir),
                tempFolder.resolve(dstPath));

        assertArrayEquals(expectedResourceData.toArray(String[]::new), actualResourceData);
    }

    @ParameterizedTest
    @MethodSource("dataWithResourceName")
    public void testResourceDirWithSubstitution(ResourceName defaultName, Path dstPath,
            boolean dstFileOverwrite, @TempDir Path tempFolder) throws IOException {
        final List<String> resourceData = List.of("A", "[BB]", "C", "Foo", "Foo",
                "GoodbyeHello", "_B");

        final Path customFile = tempFolder.resolve("hello");
        Files.write(customFile, resourceData);

        final Map<String, String> substitutionData = new HashMap<>(Map.of(
                "B", "Bar",
                "Foo", "B",
                "_B", "JJ"));
        substitutionData.put("Hello", null);

        final List<String> expectedResourceData = List.of("A", "[BarBar]", "C",
                "Bar", "Bar", "Goodbye", "JJ");

        final var actualResourceData = saveResource(buildResourceWriter(
                new OverridableResource(defaultName.value, ResourceLocator.class)
                        .setSubstitutionData(substitutionData)
                        .setPublicName(customFile.getFileName())
                        .setResourceDir(customFile.getParent())
                ).dstFileOverwrite(dstFileOverwrite).expectedSource(ResourceDir),
                tempFolder.resolve(dstPath));

        assertArrayEquals(expectedResourceData.toArray(String[]::new), actualResourceData);
    }

    // Test it can derive a file in the resource dir from the name of the output file if the public name is not set
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPublicNameNotSet(boolean namesMatch, @TempDir Path tempFolder) throws IOException {
        final List<String> expectedResourceData = List.of("A", "B", "C");

        final Path customFile = tempFolder.resolve("hello");
        Files.write(customFile, expectedResourceData);

        final Path outputDir = tempFolder.resolve("output");

        var resourceWriter = buildResourceWriter(
                new OverridableResource().setResourceDir(customFile.getParent()));

        if (namesMatch) {
            final var actualResourceData = resourceWriter
                    .expectedSource(ResourceDir)
                    .saveToFile(outputDir.resolve(customFile.getFileName()));
            assertArrayEquals(expectedResourceData.toArray(String[]::new), actualResourceData);
        } else {
            final var actualResourceData = resourceWriter
                    .expectedSource(null)
                    .saveToFile(outputDir.resolve("another"));
            assertNull(actualResourceData);
        }
    }

    // Test setSubstitutionData() stores a copy of passed in data
    @Test
    public void testSubstitutionDataCopied(@TempDir Path tempFolder) throws IOException {
        final Path customFile = tempFolder.resolve("hello");
        Files.write(customFile, List.of("Hello"));

        final Map<String, String> substitutionData = new HashMap<>(Map.of("Hello", "Goodbye"));

        var resource = new OverridableResource()
                .setSubstitutionData(substitutionData)
                .setPublicName(customFile.getFileName())
                .setResourceDir(customFile.getParent());

        final var resourceWriter = buildResourceWriter(resource).expectedSource(ResourceDir);

        var contents = resourceWriter.saveToFile(tempFolder.resolve("output"));
        assertArrayEquals(new String[] { "Goodbye" }, contents);

        substitutionData.put("Hello", "Ciao");
        contents = resourceWriter.saveToFile(tempFolder.resolve("output"));
        assertArrayEquals(new String[] { "Goodbye" }, contents);

        resource.setSubstitutionData(substitutionData);
        contents = resourceWriter.saveToFile(tempFolder.resolve("output"));
        assertArrayEquals(new String[] { "Ciao" }, contents);
    }

    @Test
    public void testNoDefault(@TempDir Path tempFolder) throws IOException {
        var resourceWriter = buildResourceWriter(new OverridableResource()).expectedSource(null);
        assertEquals(null, resourceWriter.saveInDir(tempFolder));

        var dstDir = tempFolder.resolve("foo");
        assertEquals(null, resourceWriter.saveInDir(dstDir));
        assertFalse(Files.exists(dstDir));
    }

    enum ResourceName {
        DEFAULT_NAME(OverridableResourceTest.DEFAULT_NAME),
        NO_NAME("");

        ResourceName(String value) {
            this.value = value;
        }

        private final String value;
    }

    private static final String DEFAULT_NAME;
    private static final Map<String, String> SUBSTITUTION_DATA;
    static {
        switch (OperatingSystem.current()) {
            case WINDOWS -> {
                DEFAULT_NAME = "WinLauncher.template";
                SUBSTITUTION_DATA = Map.of("COMPANY_NAME", "Foo9090345");
            }

            case LINUX -> {
                DEFAULT_NAME = "template.control";
                SUBSTITUTION_DATA = Map.of("APPLICATION_PACKAGE", "Package1967");
            }

            case MACOS -> {
                DEFAULT_NAME = "Info-lite.plist.template";
                SUBSTITUTION_DATA = Map.of("DEPLOY_BUNDLE_IDENTIFIER", "12345");
            }

            default -> {
                throw new IllegalArgumentException("Unsupported platform: " + OperatingSystem.current());
            }
        }
    }

    static class ResourceWriter {

        ResourceWriter(OverridableResource resource) {
            this.resource = Objects.requireNonNull(resource);
        }

        ResourceWriter expectedSource(OverridableResource.Source v) {
            expectedSource = v;
            return this;
        }

        ResourceWriter dstFileOverwrite(boolean v) {
            dstFileOverwrite = v;
            return this;
        }

        String[] saveInDir(Path dstDir) throws IOException {
            Path dstFile;
            if (expectedSource != null) {
                if (!Files.exists(dstDir)) {
                    Files.createDirectories(dstDir);
                }
                dstFile = Files.createTempFile(dstDir, null, null);
            } else if (!Files.exists(dstDir)) {
                dstFile = dstDir.resolve("nonexistant");
            } else {
                dstFile = Files.createTempFile(dstDir, null, null);
                Files.delete(dstFile);
            }
            return saveToFile(dstFile);
        }

        String[] saveToFile(Path dstFile) throws IOException {
            saveResource(dstFile);
            if (expectedSource == null) {
                return null;
            } else {
                return Files.readAllLines(dstFile).toArray(String[]::new);
            }
        }

        private void saveResource(Path dstFile) throws IOException {
            if (dstFileOverwrite && !Files.exists(dstFile)) {
                Files.writeString(dstFile, "abcABC");
            } else if (!dstFileOverwrite && Files.exists(dstFile)) {
                Files.delete(dstFile);
            }

            final byte[] dstFileContent;
            if (expectedSource == null && Files.exists(dstFile)) {
                dstFileContent = Files.readAllBytes(dstFile);
            } else {
                dstFileContent = null;
            }

            var actualSource = resource.saveToFile(dstFile);
            assertEquals(expectedSource, actualSource);
            if (actualSource != null) {
                assertNotEquals(0, Files.size(dstFile));
            } else if (dstFileContent == null) {
                assertFalse(Files.exists(dstFile));
            } else {
                var actualDstFileContent = Files.readAllBytes(dstFile);
                assertArrayEquals(dstFileContent, actualDstFileContent);
            }
        }

        final OverridableResource resource;
        OverridableResource.Source expectedSource = DefaultResource;
        boolean dstFileOverwrite;
    }

    private static ResourceWriter buildResourceWriter(OverridableResource resource) {
        return new ResourceWriter(resource);
    }
}
