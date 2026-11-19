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

/*
 * @test
 * @bug 4847239
 * @summary Verify directory parameter behavior in File.createTempFile(String,String,File)
 * @library /test/lib
 * @run junit TargetDirectory
 */
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.condition.DisabledIf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jdk.test.lib.Platform;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetDirectory {

    @TempDir(cleanup = CleanupMode.ALWAYS)
    Path tempDir;

    @Test
    void testWritableDirectory() throws Exception {
        Path dir = tempDir.resolve("target");
        File target = Files.createDirectory(dir).toFile();
        File tmp = File.createTempFile("passes", null, target);
        assertTrue(Files.exists(tmp.toPath()), "Temp file not created");
    }

    @Test
    @DisabledIf("jdk.test.lib.Platform#isRoot")
    void testReadOnlyDirectory() throws Exception {
        Path dir = tempDir.resolve("target");
        File target = Files.createDirectory(dir).toFile();

        // Make 'target' read-only
        if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
            PosixFileAttributeView view =
                Files.getFileAttributeView(dir, PosixFileAttributeView.class);
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            view.setPermissions(perms);
        } else if (Files.getFileStore(dir).supportsFileAttributeView("acl")) {
            AclFileAttributeView view = Files.getFileAttributeView(dir,
                AclFileAttributeView.class);
            List<AclEntry> entries = new ArrayList<>();
            for (AclEntry entry : view.getAcl()) {
                Set<AclEntryPermission> perms =
                    new HashSet<>(entry.permissions());
                perms.remove(AclEntryPermission.ADD_FILE);
                entries.add(AclEntry.newBuilder().setType(entry.type())
                    .setPrincipal(entry.principal()).setPermissions(perms)
                    .build());
            }
            view.setAcl(entries);
        } else {
            throw new RuntimeException("Required attribute view not supported");
        }

        assertThrows(IOException.class,
            () -> File.createTempFile("readonly", null, target));
    }

    @Test
    void testNonExistentDirectory() {
        assertThrows(IOException.class,
            () -> File.createTempFile("nonexistent", null, new File(tempDir.toFile(), "void")));
    }

    @Test
    void testTargetIsFile() throws Exception {
        File target = Files.createFile(tempDir.resolve("file")).toFile();
        assertThrows(IOException.class,
            () -> File.createTempFile("file", null, target));
    }
}
