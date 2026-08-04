/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 4071322
   @summary Basic test for File.listRoots method
   @run junit ListRoots
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ListRoots {

    private static Set<File> expectedSet;
    private static Set<File> actualSet;

    @BeforeAll
    public static void init() throws IOException {
        File[] rs = File.listRoots();
        for (int i = 0; i < rs.length; i++) {
            System.err.println(i + ": " + rs[i]);
        }

        // the list of roots should match FileSystem::getRootDirectories
        FileSystem fs = FileSystems.getDefault();
        expectedSet =
            StreamSupport.stream(fs.getRootDirectories().spliterator(), false)
                         .map(Path::toFile)
                         .collect(Collectors.toSet());
        actualSet = Stream.of(rs).collect(Collectors.toSet());
    }

    @Test
    public void checkRoot() throws IOException {
        File f = new File(System.getProperty("user.dir"));
        String cp = f.getCanonicalPath();
        boolean found = Stream.of(File.listRoots())
                .map(File::getPath)
                .anyMatch(p -> cp.startsWith(p));
        assertTrue(found, cp + " does not have a recognized root");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void listRootsUnix() throws IOException {
        assertEquals(expectedSet, actualSet,
                     "Does not equal FileSystem::getRootDirectories");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void listRootsWindows() throws IOException {
        assertTrue(expectedSet.stream().anyMatch(actualSet::contains),
                   "Does not intersect FileSystem::getRootDirectories");
    }
}
