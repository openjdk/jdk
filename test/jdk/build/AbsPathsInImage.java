/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.util.Comparator.comparing;

/*
 * @test
 * @bug 8226346
 * @summary Check all output files for absolute path fragments
 * @requires !vm.debug
 * @comment ASAN keeps the 'unwanted' paths in the binaries because of its build options
 * @requires !vm.asan
 * @run main AbsPathsInImage
 */
public class AbsPathsInImage {

    // Set this property on command line to scan an alternate dir or file:
    // JTREG=JAVA_OPTIONS=-Djdk.test.build.AbsPathInImage.dir=/path/to/dir
    public static final String DIR_PROPERTY = "jdk.test.build.AbsPathsInImage.dir";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
    private static final boolean IS_LINUX   = System.getProperty("os.name").toLowerCase().contains("linux");
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static List<byte[]> searchPatterns = new ArrayList<>();
    private static List<int[]> prefixTables = new ArrayList<>();
    private static boolean containsNonASCII = false;

    private boolean matchFound = false;

    record Match(int begin, int end) { }

    public static void main(String[] args) throws Exception {
        String jdkPathString = System.getProperty("test.jdk");
        Path jdkHome = Paths.get(jdkPathString);

        Path dirToScan = jdkHome;
        String overrideDir = System.getProperty(DIR_PROPERTY);
        if (overrideDir != null) {
            dirToScan = Paths.get(overrideDir);
        }

        String buildWorkspaceRoot = null;
        String buildOutputRoot = null;
        String testImageDirString = System.getenv("TEST_IMAGE_DIR");
        if (testImageDirString != null) {
            Path testImageDir = Paths.get(testImageDirString);
            Path buildInfoPropertiesFile = testImageDir.resolve("build-info.properties");
            System.out.println("Getting patterns from " + buildInfoPropertiesFile.toString());
            Properties buildInfoProperties = new Properties();
            try (InputStream inStream = Files.newInputStream(buildInfoPropertiesFile)) {
                buildInfoProperties.load(inStream);
            }
            buildWorkspaceRoot = buildInfoProperties.getProperty("build.workspace.root");
            buildOutputRoot = buildInfoProperties.getProperty("build.output.root");
        } else {
            System.out.println("Getting patterns from local environment");
            // Try to resolve the workspace root based on the jtreg test root dir
            String testRootDirString = System.getProperty("test.root");
            if (testRootDirString != null) {
                Path testRootDir = Paths.get(testRootDirString);
                // Remove /test/jdk suffix
                buildWorkspaceRoot = testRootDir.getParent().getParent().toString();
            }
            // Remove /jdk
            Path buildOutputRootPath = jdkHome.getParent();
            if (buildOutputRootPath.endsWith("images")) {
                buildOutputRootPath = buildOutputRootPath.getParent();
            }
            buildOutputRoot = buildOutputRootPath.toString();
        }
        if (buildWorkspaceRoot == null) {
            throw new Error("Could not find workspace root, test cannot run");
        }
        if (buildOutputRoot == null) {
            throw new Error("Could not find build output root, test cannot run");
        }
        // Validate the root paths
        if (!Paths.get(buildWorkspaceRoot).isAbsolute()) {
            throw new Error("Workspace root is not an absolute path: " + buildWorkspaceRoot);
        }
        if (!Paths.get(buildOutputRoot).isAbsolute()) {
            throw new Error("Output root is not an absolute path: " + buildOutputRoot);
        }

        expandPatterns(buildWorkspaceRoot);
        expandPatterns(buildOutputRoot);

        System.out.println("Looking for:");
        for (byte[] searchPattern : searchPatterns) {
            System.out.println(new String(searchPattern));
        }
        System.out.println();

        createPrefixTables();

        AbsPathsInImage absPathsInImage = new AbsPathsInImage();
        absPathsInImage.scanFiles(dirToScan);

        if (absPathsInImage.matchFound) {
            throw new Exception("Test failed");
        }
    }

    /**
     * Add path pattern to list of patterns to search for. Create all possible
     * variants depending on platform.
     */
    private static void expandPatterns(String pattern) {
        if (IS_WINDOWS) {
            String forward = pattern.replace('\\', '/');
            String back = pattern.replace('/', '\\');
            if (pattern.charAt(1) == ':') {
                String forwardUpper = String.valueOf(pattern.charAt(0)).toUpperCase() + forward.substring(1);
                String forwardLower = String.valueOf(pattern.charAt(0)).toLowerCase() + forward.substring(1);
                String backUpper = String.valueOf(pattern.charAt(0)).toUpperCase() + back.substring(1);
                String backLower = String.valueOf(pattern.charAt(0)).toLowerCase() + back.substring(1);
                searchPatterns.add(forwardUpper.getBytes());
                searchPatterns.add(forwardLower.getBytes());
                searchPatterns.add(backUpper.getBytes());
                searchPatterns.add(backLower.getBytes());
            } else {
                searchPatterns.add(forward.getBytes());
                searchPatterns.add(back.getBytes());
            }
        } else {
            searchPatterns.add(pattern.getBytes());
        }
    }

    // KMP failure function
    private static int getPrefixIndex(int patternIdx, int state, byte match) {
        if (state == 0) {
            return 0;
        }
        byte[] searchPattern = searchPatterns.get(patternIdx);
        int[] prefixTable = prefixTables.get(patternIdx);
        int i = prefixTable[state - 1];
        while (i > 0 && searchPattern[i] != match) {
            i = prefixTable[i - 1];
        }
        return searchPattern[i] == match ? i + 1 : i;
    }

    // precompute KMP prefixes
    private static void createPrefixTables() {
        for (int patternIdx = 0; patternIdx < searchPatterns.size(); patternIdx++) {
            int patternSize = searchPatterns.get(patternIdx).size();
            int[] prefixTable = new int[patternSize];
            prefixTables.add(prefixTable);
            for (int i = 1; i < patternSize; i++) {
                prefixTable[i] = getPrefixIndex(patternIdx, i, searchPatterns.get(patternIdx)[i]);
            }
        }
    }

    private void scanFiles(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String dirName = dir.toString();
                if (dirName.endsWith(".dSYM")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.toString();
                if (Files.isSymbolicLink(file)) {
                    return super.visitFile(file, attrs);
                } else if ((fileName.endsWith(".debuginfo") && !IS_LINUX) || fileName.endsWith(".pdb")) {
                    // Do nothing
                } else if (fileName.endsWith(".zip")) {
                    scanZipFile(file);
                } else {
                    scanFile(file);
                }
                return super.visitFile(file, attrs);
            }
        });
    }

    private void scanFile(Path file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            scanBytes(inputStream, file + ":");
        }
    }

    private void scanZipFile(Path zipFile) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                scanBytes(zipInputStream, zipFile + ", " + zipEntry.getName() + ":");
            }
        }
    }

    private void scanBytes(InputStream input, final String HEADER) throws IOException {
        List<Match> matches = new ArrayList<>();
        byte[] buf = new byte[DEFAULT_BUFFER_SIZE];
        int[] states = new int[searchPatterns.size()];
        int fileIdx = 0;
        int bytesRead, patternLen;
        while ((bytesRead = input.read(buf)) != -1) {
            for (int bufIdx = 0; bufIdx < bytesRead; bufIdx++, fileIdx++) {
                byte datum = buf[bufIdx];
                for (int i = 0; i < searchPatterns.size(); i++) {
                    patternLen = searchPatterns.get(i).length;
                    if (datum != searchPatterns.get(i)[states[i]]) {
                        states[i] = getPrefixIndex(i, states[i], datum);
                    } else if (++states[i] == patternLen) {
                        states[i] = 0;
                        matches.add(new Match(fileIdx - patternLen + 1, fileIdx));
                        break;
                    }
                }
            }
        }
        // test succeeds; common case
        if (matches.size() == 0) {
            return;
        }
        // test fails; pay penalty and re-scan file
        matchFound = true;
        System.out.println(HEADER);
        matches.sort(comparing(Match::begin));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int matchIdx = 0;
        fileIdx = 0;
        input.reset();
        while (matchIdx < matches.size() && (bytesRead = input.read(buf)) != -1) {
            for (int i = 0; matchIdx < matches.size() && i < bytesRead; i++, fileIdx++) {
                byte datum = buf[i];
                if (datum >= 32 && datum <= 126) {
                    output.write(datum);
                } else if (fileIdx < matches.get(matchIdx).begin()) {
                    output.reset();
                } else if (fileIdx > matches.get(matchIdx).end()) {
                    System.println(output.toString());
                    output.reset();
                    for (; matchIdx < matches.size() && matches.get(matchIdx).end() < fileIdx; matchIdx++);
                } else {
                    output.write(datum);
                }
            }
        }
        System.out.println();
    }
}
