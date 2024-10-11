/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309841
 * @summary Unit Test for a common Test API in jdk.test.lib.util.JarUtils
 * @library /test/lib
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.util.JarUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class JarUtilsTest {
    public static void main(String[] args) throws Exception {
        Files.createDirectory(Path.of("bx"));
        JarUtils.createJarFile(Path.of("a.jar"),
                Path.of("."),
                Files.writeString(Path.of("a"), ""),
                Files.writeString(Path.of("b1"), ""),
                Files.writeString(Path.of("b2"), ""),
                Files.writeString(Path.of("bx/x"), ""),
                Files.writeString(Path.of("c"), ""),
                Files.writeString(Path.of("e1"), ""),
                Files.writeString(Path.of("e2"), ""));
        checkContent("a", "b1", "b2", "bx/x", "c", "e1", "e2");

        JarUtils.deleteEntries(Path.of("a.jar"), "a");
        checkContent("b1", "b2", "bx/x", "c", "e1", "e2");

        // Note: b* covers everything starting with b, even bx/x
        JarUtils.deleteEntries(Path.of("a.jar"), "b*");
        checkContent("c", "e1", "e2");

        // d* does not match
        JarUtils.deleteEntries(Path.of("a.jar"), "d*");
        checkContent("c", "e1", "e2");

        // multiple patterns
        JarUtils.deleteEntries(Path.of("a.jar"), "d*", "e*");
        checkContent("c");
    }

    static void checkContent(String... expected) throws IOException {
        try (var jf = new JarFile("a.jar")) {
            Asserts.assertEquals(Set.of(expected),
                    jf.stream().map(JarEntry::getName).collect(Collectors.toSet()));
        }
    }
}
