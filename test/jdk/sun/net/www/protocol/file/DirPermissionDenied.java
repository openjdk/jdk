/*
 * Copyright (c) 2010, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6977851 8385906
 * @summary NPE from FileURLConnection.connect
 * @library /test/lib
 * @build DirPermissionDenied jdk.test.lib.util.FileUtils
 * @run junit ${test.main.class}
 */

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jdk.test.lib.util.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DirPermissionDenied {
    private static final Path TEST_DIR = Paths.get(
            "DirPermissionDeniedDirectory");
    private static URL url;
    private static List<AclEntry> aclEntries;
    private static Set<PosixFilePermission> posixPermissions;

    @Test
    public void connectTest() throws IOException {
        URLConnection uc = url.openConnection();
        assertThrows(IOException.class, ()-> uc.connect());
    }

    @Test
    public void getInputStreamTest() throws IOException {
        URLConnection uc = url.openConnection();
        assertThrows(IOException.class, ()-> uc.getInputStream());
    }

    @Test
    public void getContentLengthLongTest() throws IOException {
        URLConnection uc = url.openConnection();
        assertDoesNotThrow(() -> uc.getContentLengthLong());
    }

    @BeforeAll
    public static void setup() throws Throwable {
        url = new URL(TEST_DIR.toUri().toString());
        Files.createDirectories(TEST_DIR);
        try {
            makeDirectoryUnreadable();
            Assumptions.assumeTrue(TEST_DIR.toFile().list() == null,
                    "Could not make directory inaccessible");
        } catch (Throwable exception) {
            restorePermissions();
            throw exception;
        }
    }

    @AfterAll
    public static void tearDown() throws Throwable {
        restorePermissions();
        FileUtils.deleteFileIfExistsWithRetry(TEST_DIR);
    }

    private static void makeDirectoryUnreadable() throws IOException {
        FileStore store = Files.getFileStore(TEST_DIR);
        if (store.supportsFileAttributeView("posix")) {
            posixPermissions = Files.getPosixFilePermissions(TEST_DIR);
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.GROUP_EXECUTE);
            Files.setPosixFilePermissions(TEST_DIR, perms);
        } else if (store.supportsFileAttributeView("acl")) {
            AclFileAttributeView view = Files.getFileAttributeView(TEST_DIR,
                    AclFileAttributeView.class);
            if (view == null) {
                throw new IOException("ACL view not available");
            }
            aclEntries = new ArrayList<>(view.getAcl());
            List<AclEntry> entries = new ArrayList<>();
            entries.add(AclEntry.newBuilder()
                    .setType(AclEntryType.DENY)
                    .setPrincipal(getCurrentUserPrincipal(view))
                    .setPermissions(AclEntryPermission.READ_DATA,
                            AclEntryPermission.LIST_DIRECTORY)
                    .build());
            entries.addAll(aclEntries);
            view.setAcl(entries);
        } else {
            Assumptions.assumeTrue(false,
                    "Required file attribute view not supported");
        }
    }

    private static UserPrincipal getCurrentUserPrincipal(
            AclFileAttributeView view) throws IOException {
        String userName = System.getProperty("user.name");
        try {
            return TEST_DIR.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(userName);
        } catch (IOException e) {
            for (AclEntry entry : view.getAcl()) {
                UserPrincipal principal = entry.principal();
                String name = principal.getName();
                if (name.equalsIgnoreCase(userName)
                        || name.endsWith("\\" + userName)) {
                    return principal;
                }
            }
            throw e;
        }
    }

    private static void restorePermissions() throws IOException {
        if (Files.notExists(TEST_DIR)) {
            return;
        }

        FileStore store = Files.getFileStore(TEST_DIR);
        if (posixPermissions != null
                && store.supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(TEST_DIR, posixPermissions);
        } else if (aclEntries != null
                && store.supportsFileAttributeView("acl")) {
            AclFileAttributeView view = Files.getFileAttributeView(TEST_DIR,
                    AclFileAttributeView.class);
            view.setAcl(aclEntries);
        }
    }
}
