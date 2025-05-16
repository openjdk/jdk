/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


public class ApplicationLayoutTest {

    public void test(boolean move, Path tempDir) throws IOException {
        final var srcAppImageRoot = tempDir.resolve("src");
        Files.createDirectories(srcAppImageRoot);

        final var appImageCopyFiles = List.of("bin/Foo", "lib/app/Foo.cfg", "lib/app/hello.jar", "runtime/bin/java");
        final var appImageCopyDirs = List.of("lib/app/hello");

        final var appImageNoCopyFiles = List.of("lib/Foo.cfg", "Foo");
        final var appImageNoCopyDirs = List.of("lib/hello", "a/b/c");

        for (var path : Stream.concat(appImageCopyFiles.stream(), appImageNoCopyFiles.stream()).map(srcAppImageRoot::resolve).toList()) {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
        }

        for (var path : Stream.concat(appImageCopyDirs.stream(), appImageNoCopyDirs.stream()).map(srcAppImageRoot::resolve).toList()) {
            Files.createDirectories(path);
        }

        final var layout = ApplicationLayout.build()
                .launchersDirectory("bin")
                .appDirectory("lib/app")
                .runtimeDirectory("runtime")
                .appModsDirectory("mods")
                .contentDirectory("content")
                .desktopIntegrationDirectory("lib/apps")
                .create();

        final var dstAppImageRoot = tempDir.resolve("dst");
        Files.createDirectories(dstAppImageRoot);

        final var srcPathGroup = AppImageLayout.toPathGroup(layout.resolveAt(srcAppImageRoot));
        final var dstPathGroup = AppImageLayout.toPathGroup(layout.resolveAt(dstAppImageRoot));
        if (move) {
            srcPathGroup.move(dstPathGroup);
        } else {
            srcPathGroup.copy(dstPathGroup);
        }

        for (var path : Stream.concat(appImageNoCopyDirs.stream(), appImageNoCopyFiles.stream()).map(srcAppImageRoot::resolve).toList()) {
            assertTrue(Files.exists(path));
        }

        for (var path : appImageCopyDirs) {
            var srcPath = srcAppImageRoot.resolve(path);
            if (move) {
                assertFalse(Files.exists(srcPath));
            } else {
                assertTrue(Files.isDirectory(srcPath));
            }
            assertTrue(Files.isDirectory(dstAppImageRoot.resolve(path)));
        }

        for (var path : appImageCopyFiles) {
            var srcPath = srcAppImageRoot.resolve(path);
            if (move) {
                assertFalse(Files.exists(srcPath));
            } else {
                assertTrue(Files.isRegularFile(srcPath));
            }
            assertTrue(Files.isRegularFile(dstAppImageRoot.resolve(path)));
        }
    }

    @Test
    public void testMove(@TempDir Path tempDir) throws IOException {
        test(true, tempDir);
    }

    @Test
    public void testCopy(@TempDir Path tempDir) throws IOException {
        test(false, tempDir);
    }
}
