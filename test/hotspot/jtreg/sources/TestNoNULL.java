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

/*
 * @test
 * @bug 8343802
 * @summary Test prevent NULL backsliding in hotspot code and tests
 * @run main TestNoNULL
 */

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class TestNoNULL {
    private static final Set<String> excludedSourceFiles = new HashSet<>();
    private static final Set<String> excludedTestFiles = new HashSet<>();
    private static final Set<String> excludedTestExtensions = extend(new HashSet<>(List.of(".c", ".java", ".jar", ".class", ".zip")), "excludedTestExtensions");
    private static final Pattern NULL_PATTERN = Pattern.compile("\\bNULL\\b");
    private static Path dir = Paths.get(System.getProperty("test.src"));
    private static int errorCount = 0;

    /**
     * Extends {@code toExtend} with the comma separated entries in the value of the
     * {@code propertyName} system property.
     */
    private static <T extends Collection<String>> T extend(T toExtend, String propertyName) {
        String extensions = System.getProperty(propertyName);
        if (extensions != null) {
            toExtend.addAll(List.of(extensions.split(",")));
        }
        return toExtend;
    }

    public static void main(String[] args) throws IOException {
        int maxIter = 20;
        while (maxIter-- > 0 && dir != null && !Files.exists(dir.resolve("src"))) {
            dir = dir.getParent();
        }

        if (dir == null) {
            throw new RuntimeException("Could not locate the 'src' directory within 20 parent directories.");
        }

        Path srcPath = dir.resolve("src").resolve("hotspot");
        Path testPath = dir.resolve("test").resolve("hotspot");

        initializeExcludedPaths(dir);

        if (Files.exists(srcPath)) {
            processFiles(srcPath, excludedSourceFiles, Set.of());
        }
        processFiles(testPath, excludedTestFiles, excludedTestExtensions);

        if (errorCount > 0) {
            throw new RuntimeException("Test found " + errorCount + " usages of 'NULL' in source files. See errors above.");
        }
    }

    private static void initializeExcludedPaths(Path rootDir) {
        List<String> sourceExclusions = extend(new ArrayList<>(List.of(
                "src/hotspot/share/prims/jvmti.xml",
                "src/hotspot/share/prims/jvmti.xsl"
        )), "sourceExclusions");

        List<String> testExclusions = extend(new ArrayList<>(List.of(
                "test/hotspot/jtreg/vmTestbase/nsk/share/jvmti/README",
                "test/hotspot/jtreg/vmTestbase/nsk/share/jni/README"
        )), "testExclusions");

        sourceExclusions.forEach(relativePath ->
                excludedSourceFiles.add(rootDir.resolve(relativePath).normalize().toString()));
        testExclusions.forEach(relativePath ->
                excludedTestFiles.add(rootDir.resolve(relativePath).normalize().toString()));
    }

    private static void processFiles(Path directory, Set<String> excludedFiles, Set<String> excludedExtensions) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isIncluded(file, excludedFiles, excludedExtensions)) {
                    checkForNull(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isIncluded(Path file, Set<String> excludedFiles, Set<String> excludedExtensions) {
        String filePath = file.normalize().toString();

        if (excludedFiles.contains(filePath)) {
            return false;
        }

        for (String ext : excludedExtensions) {
            if (filePath.endsWith(ext)) {
                return false;
            }
        }

        return true;
    }

    private static void checkForNull(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (NULL_PATTERN.matcher(lines.get(i)).find()) {
                    errorCount++;
                    System.err.printf("Error: 'NULL' found in %s at line %d:%n%s%n", path, i + 1, lines.get(i));
                }
            }
        } catch (MalformedInputException e) {
            System.err.println("Skipping binary file: " + path);
        } catch (IOException e) {
            System.err.printf("Skipping unreadable file: " + path);
        }
    }
}
