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
 *
 */
package test;

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import util.ZipFsBaseTest;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Map;
import java.util.zip.ZipEntry;

/**
 * @test
 * @bug 8273935
 * @summary Validate that Files.getFileAttributeView will not throw an
 * Exception when the attribute view PosixFileAttributeView is not available
 */
public class PosixAttributeViewTest extends ZipFsBaseTest {
    public static final String ZIP_ENTRY = "Entry-0";
    private static final Path ZIP_FILE = Path.of("posixTest.zip");

    /**
     * Create initial Zip File
     * @throws IOException if an error occurs
     */
    @BeforeTest
    public void setup() throws IOException {
        Files.deleteIfExists(ZIP_FILE);
        Entry entry = Entry.of(ZIP_ENTRY, ZipEntry.DEFLATED,
                "Tennis Anyone");
        zip(ZIP_FILE, Map.of("create", "true"), entry);
    }

    /**
     * Remove Zip File used by Test
     * @throws IOException if an error occurs
     */
    @AfterTest
    public void cleanup() throws IOException {
        Files.deleteIfExists(ZIP_FILE);
    }

    /**
     * DataProvider used to specify the Map indicating whether Posix
     * file attributes have been enabled
     * @return  map of the Zip FS properties to configure
     */
    @DataProvider
    protected Object[][] zipfsMap() {
        return new Object[][]{
                {Map.of()},
                {Map.of("enablePosixFileAttributes", "true")}
        };
    }

    /**
     * Verify that Files.getFileAttributeView will not throw
     * an Exception when the attribute view
     * PosixFileAttributeView is not available
     * @param env map of the Zip FS properties to configure
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "zipfsMap")
    public void testPosixAttributeView(Map<String, String> env) throws Exception {
        try (FileSystem fs = FileSystems.newFileSystem(ZIP_FILE, env)) {
            Path entry = fs.getPath(ZIP_ENTRY);
            PosixFileAttributeView view = Files.getFileAttributeView(entry,
                    PosixFileAttributeView.class);
            System.out.printf("View returned: %s, Map= %s%n", view,
                    formatMap(env));
        }
    }
}
