/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7156873 8040059
 * @summary ZipFileSystem regression tests
 *
 * @run main ZFSTests
 * @run main/othervm/java.security.policy=test.policy ZFSTests
 */


import java.net.URI;
import java.nio.file.*;
import java.util.Map;
import java.util.HashMap;

public class ZFSTests {

    public static void main(String[] args) throws Throwable {
        test7156873();
    }

    static void test7156873() throws Throwable {
        String DIRWITHSPACE = "testdir with spaces";
        Path dir = Paths.get(DIRWITHSPACE);
        Path path = Paths.get(DIRWITHSPACE, "file.zip");
        try {
            Files.createDirectory(dir);
            URI uri = URI.create("jar:" + path.toUri());
            Map<String, Object> env = new HashMap<String, Object>();
            env.put("create", "true");
            try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {}
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(dir);
        }
    }
}
