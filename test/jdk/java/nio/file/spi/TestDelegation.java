/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @test
 * @summary Verifies that a FileSystemProvider's implementation of the exists
 * and readAttributesIfExists methods are invoked
 * @compile testfsp/testfsp/TestProvider.java
 * @run testng TestDelegation
 */
public class TestDelegation {

    // Non-existent Path to be used by the test
    private Path nonExistentFile;
    // Path to Temp directory used by the test
    private Path tempDirectory;
    // Valid file Path used by the test
    private Path fileThatExists;
    // The FileSystemProvider used by the test
    private MyProvider myProvider;

    /**
     * Create the FileSystemProvider, the FileSystem and
     * Path's used by the test.
     *
     * @throws IOException if an error occurs
     */
    @BeforeClass
    public void setup() throws IOException {
        myProvider = new MyProvider();
        FileSystem fs = myProvider.getFileSystem(URI.create("/"));
        // Path to Current Working Directory
        Path cwd = fs.getPath(".");
        tempDirectory = Files.createTempDirectory(cwd, "tmp");
        fileThatExists = Files.createFile(tempDirectory.resolve("file"));
        nonExistentFile = tempDirectory.resolve("doesNotExist");
    }

    /**
     * DataProvider that is used to test Files::exists. The DataProvider's
     * elements are:
     * <UL>
     *     <li>Path to validate</li>
     *     <li>Does the Path Exist</li>
     * </UL>
     * @return The test parameter data
     */
    @DataProvider
    private Object[][] testExists() {
        return new Object[][]{
                {tempDirectory, true},
                {fileThatExists, true},
                {nonExistentFile, false}
        };
    }

    /**
     * DataProvider that is used to test Files::isDirectory. The DataProvider's
     * elements are:
     * <UL>
     *     <li>Path to validate</li>
     *     <li>Is the Path a Directory</li>
     * </UL>
     * @return The test parameter data
     */
    @DataProvider
    private Object[][] testIsDirectory() {
        return new Object[][]{
                {tempDirectory, true},
                {fileThatExists, false},
                {nonExistentFile, false}
        };
    }
    /**
     * DataProvider that is used to test Files::isRegularFile. The DataProvider's
     * elements are:
     * <UL>
     *     <li>Path to validate</li>
     *     <li>Is the Path a regular file</li>
     * </UL>
     * @return The test parameter data
     */
    @DataProvider
    private Object[][] testIsRegularFile() {
        return new Object[][]{
                {tempDirectory, false},
                {fileThatExists, true},
                {nonExistentFile, false}
        };
    }

    /**
     * Clear our Map prior to each test run
     */
    @BeforeMethod
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
    @Test(dataProvider = "testExists")
    public void testExists(Path p, boolean exists) {
        assertEquals(Files.exists(p), exists);
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
    @Test(dataProvider = "testIsDirectory")
    public void testIsDirectory(Path p, boolean isDir) {
        assertEquals(Files.isDirectory(p), isDir);
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
    @Test(dataProvider = "testIsRegularFile")
    public void testIsRegularFile(Path p, boolean isFile) {
        assertEquals(Files.isRegularFile(p), isFile);
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

