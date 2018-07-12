/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import org.graalvm.compiler.debug.Versions;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import org.junit.Test;

public class VersionsTest {
    private File temporaryDirectory;

    @After
    public void cleanUp() throws IOException {
        if (temporaryDirectory != null) {
            Files.walkFileTree(temporaryDirectory.toPath(), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        temporaryDirectory = null;
    }

    @Test
    public void emptyProperties() throws URISyntaxException {
        Path root = Paths.get(new URI("file:/"));
        Versions v = new Versions(root);
        assertEmpty(v.withVersions(null));
    }

    @Test
    public void emptyWithNullProperties() throws URISyntaxException {
        Path root = Paths.get(new URI("file:/"));
        Versions v = new Versions(root);
        assertEmpty(v.withVersions(null));
    }

    @Test
    public void readFromSameDirNullProps() throws IOException {
        File dir = prepareReleaseFile();

        Versions v = new Versions(dir.toPath());
        Map<Object, Object> map = v.withVersions(null);
        assertNonModifiable(map);

        assertEquals("16055f1ffaf736b7b86dcfaea53971983cd9ae0a", map.get("version.sdk"));
        assertEquals("7930979c3b0af09a910accaaf3e73b2a55d2bade", map.get("version.truffleruby"));
    }

    @Test
    public void readFromSameDir() throws IOException {
        File dir = prepareReleaseFile();

        Versions v = new Versions(dir.toPath());

        Map<Object, Object> prepared = new HashMap<>();
        prepared.put("test", "best");

        Map<Object, Object> map = v.withVersions(prepared);
        assertSame(prepared, map);

        assertEquals("16055f1ffaf736b7b86dcfaea53971983cd9ae0a", map.get("version.sdk"));
        assertEquals("7930979c3b0af09a910accaaf3e73b2a55d2bade", map.get("version.truffleruby"));
        assertEquals("best", map.get("test"));
    }

    @Test
    public void readFromSubDirNullProps() throws IOException {
        File dir = prepareSubReleaseFile();

        Versions v = new Versions(dir.toPath());
        Map<Object, Object> map = v.withVersions(null);
        assertNonModifiable(map);

        assertEquals("16055f1ffaf736b7b86dcfaea53971983cd9ae0a", map.get("version.sdk"));
        assertEquals("7930979c3b0af09a910accaaf3e73b2a55d2bade", map.get("version.truffleruby"));
    }

    @Test
    public void readFromSubDir() throws IOException {
        File dir = prepareSubReleaseFile();

        Versions v = new Versions(dir.toPath());

        Map<Object, Object> prepared = new HashMap<>();
        prepared.put("test", "best");

        Map<Object, Object> map = v.withVersions(prepared);
        assertSame(prepared, map);

        assertEquals("16055f1ffaf736b7b86dcfaea53971983cd9ae0a", map.get("version.sdk"));
        assertEquals("7930979c3b0af09a910accaaf3e73b2a55d2bade", map.get("version.truffleruby"));
        assertEquals("best", map.get("test"));
    }

    private File prepareReleaseFile() throws IOException {
        if (temporaryDirectory == null) {
            temporaryDirectory = File.createTempFile("versions", ".tmp");
            temporaryDirectory.delete();
            assumeTrue(temporaryDirectory.mkdirs());
            try (FileWriter w = new FileWriter(new File(temporaryDirectory, "release"))) {
// @formatter:off
                w.write(
"OS_NAME=linux\n" +
"OS_ARCH=amd64\n" +
"SOURCE=\" truffle:16055f1ffaf736b7b86dcfaea53971983cd9ae0a sdk:16055f1ffaf736b7b86dcfaea53971983cd9ae0a " +
"tools-enterprise:fcc1292a05e807a63589e24ce6073aafdef45bb9 graal-js:d374a8fd2733487a9f7518be6a55eb6163a779d1 " +
"graal-nodejs:3fcaf6874c9059d5ca5f0615edaa405d66cc1b02 truffleruby:7930979c3b0af09a910accaaf3e73b2a55d2bade " +
"fastr:079c6513b46f36abc24bce8aa6022c90576b3eaf graalpython:4cbee7853d460930c4d693970a21b73f811a4703 " +
"sulong:2c425f92caa004b12f60428a3e7e6e2715b51f87 substratevm:fcc1292a05e807a63589e24ce6073aafdef45bb9 " +
"compiler:16055f1ffaf736b7b86dcfaea53971983cd9ae0a substratevm-enterprise:fcc1292a05e807a63589e24ce6073aafdef45bb9 " +
"vm-enterprise:fcc1292a05e807a63589e24ce6073aafdef45bb9 graal-enterprise:fcc1292a05e807a63589e24ce6073aafdef45bb9 \"\n" +
"COMMIT_INFO={\"vm-enterprise\":{\"commit.rev\":\"fcc1292a05e807a63589e24ce6073aafdef45bb9\"," +
"\"commit.committer\":\"Vojin Jovanovic <vojin.jovanovic@oracle.com>\",}}\n" +
"GRAALVM_VERSION=\"0.29-dev\""
                );
// @formatter:on
            }
        }
        return temporaryDirectory;
    }

    private File prepareSubReleaseFile() throws IOException {
        File subdir = new File(prepareReleaseFile(), "subdir");
        assumeTrue(subdir.mkdirs());
        return subdir;
    }

    private static void assertEmpty(Map<?, ?> map) {
        assertNotNull(map);
        assertTrue(map.isEmpty());
        assertNonModifiable(map);
    }

    private static void assertNonModifiable(Map<?, ?> map) {
        try {
            map.put(null, null);
            fail("Map shall not be modifiable: " + map);
        } catch (UnsupportedOperationException ex) {
            // ok
        }
    }
}
