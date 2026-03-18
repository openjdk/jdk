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
   @modules java.base/jdk.internal.util
   @summary Basic test for File.listRoots method
 */

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jdk.internal.util.OperatingSystem;

public class ListRoots {

    public static void main(String[] args) throws Exception {
        File[] rs = File.listRoots();
        for (int i = 0; i < rs.length; i++) {
            System.out.println(i + ": " + rs[i]);
        }

        File f = new File(System.getProperty("test.src", "."), "ListRoots.java");
        String cp = f.getCanonicalPath();
        boolean found = Stream.of(rs)
                .map(File::getPath)
                .anyMatch(p -> cp.startsWith(p));
        if (!found) {
            throw new RuntimeException(cp + " does not have a recognized root");
        }

        // the list of roots should match FileSystem::getRootDirectories
        Set<File> actualSet = Stream.of(rs).collect(Collectors.toSet());
        FileSystem fs = FileSystems.getDefault();
        Stream<File> expectedStream =
            StreamSupport.stream(fs.getRootDirectories().spliterator(), false)
                .map(Path::toFile);

        if (OperatingSystem.isWindows()) {
            if (!expectedStream.anyMatch(actualSet::contains)) {
                System.err.println(actualSet);
                throw new RuntimeException("Does not intersect FileSystem::getRootDirectories");
            }
        } else { // Unix
            Set<File> expectedSet = expectedStream.collect(Collectors.toSet());
            if (!actualSet.equals(expectedSet)) {
                System.err.println(actualSet);
                throw new RuntimeException("Does not equal FileSystem::getRootDirectories");
            }
        }
    }

}
