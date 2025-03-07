/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ApplicationLayoutTest {

    private Path newFolder(Path folderName, String ... extraFolderNames) throws IOException {
        var path = tempFolder.resolve(folderName);
        Files.createDirectories(path);
        for (var extraFolderName : extraFolderNames) {
            path = path.resolve(extraFolderName);
            Files.createDirectories(path);
        }
        return path;
    }

    private Path newFile(Path fileName) throws IOException {
        var path = tempFolder.resolve(fileName);
        Files.createDirectories(path.getParent());
        Files.createFile(path);
        return path;
    }

    private void fillLinuxAppImage() throws IOException {
        appImage = newFolder(Path.of("Foo"));

        Path base = appImage.getFileName();

        newFolder(base, "bin");
        newFolder(base, "lib", "app", "mods");
        newFolder(base, "lib", "runtime", "bin");
        newFile(base.resolve("bin/Foo"));
        newFile(base.resolve("lib/app/Foo.cfg"));
        newFile(base.resolve("lib/app/hello.jar"));
        newFile(base.resolve("lib/Foo.png"));
        newFile(base.resolve("lib/libapplauncher.so"));
        newFile(base.resolve("lib/runtime/bin/java"));
    }

    @Test
    public void testLinux() throws IOException {
        fillLinuxAppImage();
        testApplicationLayout(ApplicationLayout.linuxAppImage());
    }

    private void testApplicationLayout(ApplicationLayout layout) throws IOException {
        ApplicationLayout srcLayout = layout.resolveAt(appImage);
        assertApplicationLayout(srcLayout);

        ApplicationLayout dstLayout = layout.resolveAt(
                appImage.getParent().resolve(
                        "Copy" + appImage.getFileName().toString()));
        srcLayout.move(dstLayout);
        Files.deleteIfExists(appImage);
        assertApplicationLayout(dstLayout);

        dstLayout.copy(srcLayout);
        assertApplicationLayout(srcLayout);
        assertApplicationLayout(dstLayout);
    }

    private void assertApplicationLayout(ApplicationLayout layout) throws IOException {
        assertTrue(Files.isRegularFile(layout.appDirectory().resolve("Foo.cfg")));
        assertTrue(Files.isRegularFile(layout.appDirectory().resolve("hello.jar")));
        assertTrue(Files.isDirectory(layout.appModsDirectory()));
        assertTrue(Files.isRegularFile(layout.launchersDirectory().resolve("Foo")));
        assertTrue(Files.isRegularFile(layout.destktopIntegrationDirectory().resolve("Foo.png")));
        assertTrue(Files.isRegularFile(layout.runtimeDirectory().resolve("bin/java")));
    }

    @TempDir
    private Path tempFolder;
    private Path appImage;
}
