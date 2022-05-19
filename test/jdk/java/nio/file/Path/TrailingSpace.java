/*
 * Copyright 2022 JetBrains s.r.o.
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
 * @bug 8190546
 * @summary Verifies that a path name that ends with a space can be
 *          successfully created and deleted.
 */

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

public class TrailingSpace {
    static void testResolve(String parent, String... paths) {
        final Path p = Path.of(parent, paths);
        System.out.println("Path successfully created: " + p);
    }

    static void testResolve(String path) {
        final Path p = Path.of(path);
        System.out.println("Path successfully created: " + p);
    }

    static void testDelete(String path) throws IOException {
        final Path p = Files.createDirectories(Path.of(path));
        Files.delete(p);
    }

    public static void main(String args[]) throws IOException {
        testResolve("./", "ends-with-space ");
        testResolve("ends-with-space ");
        testResolve("1", "2", "ends-with-space ", "3");
        testResolve("1\\2\\ends-with-space \\3");
        testResolve("1/2/ends-with-space /3");
        testResolve("1/2/ends-with-space \\3");
        testResolve("ends-with-space /");
        testResolve("ends-with-space ///");
        testResolve("ends-with-space \\");
        testResolve("ends-with-space \\\\\\");

        testDelete("ends-with-space ");
        testDelete("ends-with-space-1 \\");
    }
}
