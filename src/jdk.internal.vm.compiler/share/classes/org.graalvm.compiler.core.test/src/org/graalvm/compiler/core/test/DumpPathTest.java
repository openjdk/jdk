/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

/**
 * Check that setting the dump path results in files ending up in the right directory with matching
 * names.
 */
public class DumpPathTest extends GraalCompilerTest {

    public static Object snippet() {
        return new String("snippet");
    }

    @Test
    public void testDump() throws IOException {
        assumeManagementLibraryIsLoadable();
        Path dumpDirectoryPath = Files.createTempDirectory("DumpPathTest");
        String[] extensions = new String[]{".cfg", ".bgv", ".graph-strings"};
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(DebugOptions.DumpPath, dumpDirectoryPath.toString());
        overrides.put(DebugOptions.PrintGraph, PrintGraphTarget.File);
        overrides.put(DebugOptions.PrintCanonicalGraphStrings, true);
        overrides.put(DebugOptions.Dump, "*");

        // Generate dump files.
        test(new OptionValues(getInitialOptions(), overrides), "snippet");
        // Check that Ideal files got created, in the right place.
        checkForFiles(dumpDirectoryPath, extensions);

        // Clean up the generated files.
        scrubDirectory(dumpDirectoryPath);
    }

    /**
     * Check that the given directory contains file or directory names with all the given
     * extensions.
     */
    private static void checkForFiles(Path directoryPath, String[] extensions) throws IOException {
        String[] paths = new String[extensions.length];
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path filePath : stream) {
                String fileName = filePath.getFileName().toString();
                for (int i = 0; i < extensions.length; i++) {
                    String extension = extensions[i];
                    if (fileName.endsWith(extensions[i])) {
                        assertTrue(paths[i] == null, "multiple files found for %s in %s", extension, directoryPath);
                        paths[i] = fileName.replace(extensions[i], "");
                    }
                }
            }
        }
        for (int i = 0; i < paths.length; i++) {
            assertTrue(paths[i] != null, "missing file for extension %s in %s", extensions[i], directoryPath);
        }
        // Ensure that all file names are the same.
        for (int i = 1; i < paths.length; i++) {
            assertTrue(paths[0].equals(paths[i]), paths[0] + " != " + paths[i]);
        }
    }

    /**
     * Remove the temporary directory.
     */
    private static void scrubDirectory(Path directoryPath) {
        try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
                for (Path filePath : stream) {
                    if (Files.isRegularFile(filePath)) {
                        Files.delete(filePath);
                    } else if (Files.isDirectory(filePath)) {
                        scrubDirectory(filePath);
                    }
                }
            }
            Files.delete(directoryPath);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
