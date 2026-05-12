/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @test
 * @summary Verifies that a FileSystemProvider's implementation of the exists
 * and readAttributesIfExists methods are invoked
 * @compile testfsp/testfsp/TestProvider.java
 * @run junit TestDelegation
 */
public class TestDelegation {

    // Non-existent Path to be used by the test
    private static Path nonExistentFile;
    // Path to Temp directory used by the test
    private static Path tempDirectory;
    // Valid file Path used by the test
    private static Path fileThatExists;
    // The FileSystemProvider used by the test
    private static MyProvider myProvider;

    /**
     * Create the FileSystemProvider, the FileSystem and
     * Path's used by the test.
     *
     * @throws IOException if an error occurs
     */
    @BeforeAll
    public static void setup() throws IOException {
        myProvider = new MyProvider();
        FileSystem fs = myProvider.getFileSystem(URI.create("/"));
        // Path to Current Working Directory
        Path cwd = fs.getPath(".");
        tempDirectory = Files.createTempDirectory(cwd, "tmp");
        fileThatExists = Files.createFile(tempDirectory.resolve("file"));
        nonExistentFile = tempDirectory.resolve("doesNotExist");
    }

    /**
     * MethodSource that is used to test Files::exists. The Arguments'
     * elements are:
     * <UL>
     *     <li>Path to validate</li>
     *     <li>Does the Path Exist</li>
     * </UL>
     * @return The test parameter data
     */
    private static Stream<Arguments> testExists() {
        return Stream.of(Arguments.of(tempDirectory, true),
                         Arguments.of(fileThatExists, true),
                         Arguments.of(nonExistentFile, false));
    }

    /**
     * MethodSource that is used to test Files::isDirectory. The Arguments'
     * elements are:
     * <UL>
     *     <li>Path to validate</li>
     *     <li>Is the Path a Directory</li>
     * </UL>
     * @return The test parameter data
     */
    private static Stream<Arguments> testIsDirectory() {
        return Stream.of(Arguments.of(tempDirectory, true),
                         Arguments.of(fileThatExists, false),
                         Arguments.of(nonExistentFile, false));
    }
    /**
     * MethodSource that is used to test Files::isRegularFile. The MethodSource's
     * elements are:
     * <UL>
     *     <li>Path to validate</li>
     *     <li>Is the Path a regular file</li>
     * </UL>
     * @return The test parameter data
     */
    private static Stream<Arguments> testIsRegularFile() {
        return Stream.of(Arguments.of(tempDirectory, false),
                         Arguments.of(fileThatExists, true),
                         Arguments.of(nonExistentFile, false));
    }

    /**
     * Clear our Map prior to each test run
     */
    @BeforeEach
    public void resetParams() {
        myProvider.resetCalls();
    }

    /**
     * Validate that Files::exists delegates to the FileSystemProvider's
     * implementation of exists.
     *
     * @param p      the path to the file to test
     * @param exists does the path exist
     */
    @ParameterizedTest
    @MethodSource("testExists")
    public void testExists(Path p, boolean exists) {
        assertEquals(exists, Files.exists(p));
        // We should only have called exists once
        assertEquals(1, myProvider.findCall("exists").size());
        assertEquals(0, myProvider.findCall("readAttributesIfExists").size());
    }

    /**
     * Validate that Files::isDirectory delegates to the FileSystemProvider's
     * implementation readAttributesIfExists.
     *
     * @param p      the path to the file to test
     * @param isDir  is the path a directory
     */
    @ParameterizedTest
    @MethodSource("testIsDirectory")
    public void testIsDirectory(Path p, boolean isDir) {
        assertEquals(isDir, Files.isDirectory(p));
        // We should only have called readAttributesIfExists once
        assertEquals(0, myProvider.findCall("exists").size());
        assertEquals(1, myProvider.findCall("readAttributesIfExists").size());
    }

    /**
     * Validate that Files::isRegularFile delegates to the FileSystemProvider's
     * implementation readAttributesIfExists.
     *
     * @param p      the path to the file to test
     * @param isFile is the path a regular file
     */
    @ParameterizedTest
    @MethodSource("testIsRegularFile")
    public void testIsRegularFile(Path p, boolean isFile) {
        assertEquals(isFile, Files.isRegularFile(p));
        // We should only have called readAttributesIfExists once
        assertEquals(0, myProvider.findCall("exists").size());
        assertEquals(1, myProvider.findCall("readAttributesIfExists").size());
    }

    /**
     * The FileSystemProvider implementation used by the test
     */
    static class MyProvider extends testfsp.TestProvider {
        private final Map<String, List<Path>> calls = new HashMap<>();

        private MyProvider() {
            super(FileSystems.getDefault().provider());
        }

        private void recordCall(String op, Path path) {
            calls.computeIfAbsent(op, k -> new ArrayList<>()).add(path);
        }

        List<Path> findCall(String op) {
            return calls.getOrDefault(op, List.of());
        }

        void resetCalls() {
            calls.clear();
        }

        @Override
        public boolean exists(Path path, LinkOption... options) {
            recordCall("exists", path);
            return super.exists(path, options);
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributesIfExists(Path path,
                                                                        Class<A> type,
                                                                        LinkOption... options)
                throws IOException {
            recordCall("readAttributesIfExists", path);
            return super.readAttributesIfExists(path, type, options);
        }
    }
}

