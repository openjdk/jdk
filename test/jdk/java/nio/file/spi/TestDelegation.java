/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @build TestDelegation TestProvider
 * @run testng/othervm  TestDelegation
 */
public class TestDelegation {

    // Non-existent Path to be used by the test
    private Path NON_EXISTENT_FILE;
    // Path to Temp directory used by the test
    private Path TEMP_DIRECTORY;
    // Valid file Path used by the test
    private Path FILE_THAT_EXISTS;
    // The FileSystemProvider used by the test
    private MyProvider PROVIDER;


    /**
     * Create the FileSystemProvider, the FileSystem and
     * Path's used by the test.
     *
     * @throws IOException if an error occurs
     */
    @BeforeClass
    public void setup() throws IOException {
        PROVIDER = new MyProvider();
        FileSystem fs = PROVIDER.getFileSystem(URI.create("/"));
        // Path to Current Working Directory
        Path cwd = fs.getPath(".");
        TEMP_DIRECTORY = Files.createTempDirectory(cwd, "tmp");
        FILE_THAT_EXISTS = Files.createFile(TEMP_DIRECTORY.resolve("file"));
        NON_EXISTENT_FILE = TEMP_DIRECTORY.resolve("doesNotExist");
    }

    /**
     * DataProvider that is used by the test.  The DataProvider's elements are:
     * <UL>
     *     <li>Path to validate</li>
     *     <li>Does the Path Exist</li>
     *     <li>Is the Path a Directory</li>
     *     <li>Is the Path a regular file</li>
     * </UL>
     * @return The test parameter data
     */
    @DataProvider
    private Object[][] testPaths() {
        return new Object[][]{
                {TEMP_DIRECTORY, true, true, false},
                {FILE_THAT_EXISTS, true, false, true},
                {NON_EXISTENT_FILE, false, false, false}
        };
    }

    /**
     * Validate that a FileSystemProvider's implementation of exists and
     * readAttributesIfExists is delegated to.
     *
     * @param p      the path to the file to test
     * @param exists does the path exist
     * @param isDir  is the path a directory
     * @param isFile is the path a regular file
     */
    @Test(dataProvider = "testPaths")
    public void testDelegation(Path p, boolean exists, boolean isDir,
                               boolean isFile) {
        assertEquals(Files.exists(p), exists);
        assertEquals(Files.isDirectory(p), isDir);
        assertEquals(Files.isRegularFile(p), isFile);
        assertEquals(1, PROVIDER.findCall("exists").size());
        assertEquals(2, PROVIDER.findCall("readAttributesIfExists").size());
        PROVIDER.resetCalls();
    }

    /**
     * The FileSystemProvider implementation used by the test
     */
    static class MyProvider extends TestProvider {
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

