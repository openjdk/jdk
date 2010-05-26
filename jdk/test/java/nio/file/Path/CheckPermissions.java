/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6866804
 * @summary Unit test for java.nio.file.Path
 * @library ..
 * @build CheckPermissions
 * @run main/othervm CheckPermissions
 */

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.channels.SeekableByteChannel;
import java.security.Permission;
import java.io.*;
import java.util.*;

/**
 * Checks each method that accesses the file system does the right permission
 * check when there is a security manager set.
 */

public class CheckPermissions {

    static class Checks {
        private List<Permission> permissionsChecked = new ArrayList<Permission>();
        private Set<String>  propertiesChecked = new HashSet<String>();
        private List<String> readsChecked   = new ArrayList<String>();
        private List<String> writesChecked  = new ArrayList<String>();
        private List<String> deletesChecked = new ArrayList<String>();
        private List<String> execsChecked   = new ArrayList<String>();

        List<Permission> permissionsChecked()  { return permissionsChecked; }
        Set<String> propertiesChecked()        { return propertiesChecked; }
        List<String> readsChecked()            { return readsChecked; }
        List<String> writesChecked()           { return writesChecked; }
        List<String> deletesChecked()          { return deletesChecked; }
        List<String> execsChecked()            { return execsChecked; }
    }

    static ThreadLocal<Checks> myChecks =
        new ThreadLocal<Checks>() {
            @Override protected Checks initialValue() {
                return null;
            }
        };

    static void prepare() {
        myChecks.set(new Checks());
    }

    static void assertCheckPermission(Class<? extends Permission> type,
                                      String name)
    {
        for (Permission perm: myChecks.get().permissionsChecked()) {
            if (type.isInstance(perm) && perm.getName().equals(name))
                return;
        }
        throw new RuntimeException(type.getName() + "\"" + name + "\") not checked");
    }

    static void assertCheckPropertyAccess(String key) {
        if (!myChecks.get().propertiesChecked().contains(key))
            throw new RuntimeException("Property " + key + " not checked");
    }

    static void assertChecked(Path file, List<String> list) {
        String s = file.toString();
        for (String f: list) {
            if (f.endsWith(s))
                return;
        }
        throw new RuntimeException("Access not checked");
    }

    static void assertCheckRead(Path file) {
        assertChecked(file, myChecks.get().readsChecked());
    }

    static void assertCheckWrite(Path file) {
        assertChecked(file, myChecks.get().writesChecked());
    }

    static void assertCheckDelete(Path file) {
        assertChecked(file, myChecks.get().deletesChecked());
    }

    static void assertCheckExec(Path file) {
        assertChecked(file, myChecks.get().execsChecked());
    }

    static class LoggingSecurityManager extends SecurityManager {
        static void install() {
            System.setSecurityManager(new LoggingSecurityManager());
        }

        @Override
        public void checkPermission(Permission perm) {
            Checks checks = myChecks.get();
            if (checks != null)
                checks.permissionsChecked().add(perm);
        }

        @Override
        public void checkPropertyAccess(String key) {
            Checks checks = myChecks.get();
            if (checks != null)
                checks.propertiesChecked().add(key);
        }

        @Override
        public void checkRead(String file) {
            Checks checks = myChecks.get();
            if (checks != null)
                checks.readsChecked().add(file);
        }

        @Override
        public void checkWrite(String file) {
            Checks checks = myChecks.get();
            if (checks != null)
                checks.writesChecked().add(file);
        }

        @Override
        public void checkDelete(String file) {
            Checks checks = myChecks.get();
            if (checks != null)
                checks.deletesChecked().add(file);
        }

        @Override
        public void checkExec(String file) {
            Checks checks = myChecks.get();
            if (checks != null)
                checks.execsChecked().add(file);
        }
    }

    static void testBasicFileAttributeView(BasicFileAttributeView view, Path file)
        throws IOException
    {
        prepare();
        view.readAttributes();
        assertCheckRead(file);

        prepare();
        FileTime now = FileTime.fromMillis(System.currentTimeMillis());
        view.setTimes(null, now, now);
        assertCheckWrite(file);
    }

    static void testPosixFileAttributeView(PosixFileAttributeView view, Path file)
        throws IOException
    {
        prepare();
        PosixFileAttributes attrs = view.readAttributes();
        assertCheckRead(file);
        assertCheckPermission(RuntimePermission.class, "accessUserInformation");

        prepare();
        view.setPermissions(attrs.permissions());
        assertCheckWrite(file);
        assertCheckPermission(RuntimePermission.class, "accessUserInformation");

        prepare();
        view.setOwner(attrs.owner());
        assertCheckWrite(file);
        assertCheckPermission(RuntimePermission.class, "accessUserInformation");

        prepare();
        view.setOwner(attrs.owner());
        assertCheckWrite(file);
        assertCheckPermission(RuntimePermission.class, "accessUserInformation");
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(System.getProperty("test.dir", "."));
        Path file = dir.resolve("file1234").createFile();
        try {
            LoggingSecurityManager.install();

            // -- checkAccess --

            prepare();
            file.checkAccess();
            assertCheckRead(file);

            prepare();
            file.checkAccess(AccessMode.READ);
            assertCheckRead(file);

            prepare();
            file.checkAccess(AccessMode.WRITE);
            assertCheckWrite(file);

            prepare();
            try {
                file.checkAccess(AccessMode.EXECUTE);
            } catch (AccessDeniedException x) { }
            assertCheckExec(file);

            prepare();
            try {
                file.checkAccess(AccessMode.READ, AccessMode.WRITE, AccessMode.EXECUTE);
            } catch (AccessDeniedException x) { }
            assertCheckRead(file);
            assertCheckWrite(file);
            assertCheckExec(file);

            // -- copyTo --

            Path target = dir.resolve("target1234");
            prepare();
            file.copyTo(target);
            try {
                assertCheckRead(file);
                assertCheckWrite(target);
            } finally {
                target.delete();
            }

            if (TestUtil.supportsLinks(dir)) {
                Path link = dir.resolve("link1234").createSymbolicLink(file);
                try {
                    prepare();
                    link.copyTo(target, LinkOption.NOFOLLOW_LINKS);
                    try {
                        assertCheckRead(link);
                        assertCheckWrite(target);
                        assertCheckPermission(LinkPermission.class, "symbolic");
                    } finally {
                        target.delete();
                    }
                } finally {
                    link.delete();
                }
            }

            // -- createDirectory --

            Path subdir = dir.resolve("subdir1234");
            prepare();
            subdir.createDirectory();
            try {
                assertCheckWrite(subdir);
            } finally {
                subdir.delete();
            }

            // -- createFile --

            Path fileToCreate = dir.resolve("file7890");
            prepare();
            try {
                fileToCreate.createFile();
                assertCheckWrite(fileToCreate);
            } finally {
                fileToCreate.delete();
            }

            // -- createSymbolicLink --

            if (TestUtil.supportsLinks(dir)) {
                prepare();
                Path link = dir.resolve("link1234").createSymbolicLink(file);
                try {
                    assertCheckWrite(link);
                    assertCheckPermission(LinkPermission.class, "symbolic");
                } finally {
                    link.delete();
                }
            }

            // -- delete/deleteIfExists --

            Path fileToDelete = dir.resolve("file7890");

            fileToDelete.createFile();
            prepare();
            fileToDelete.delete();
            assertCheckDelete(fileToDelete);

            fileToDelete.createFile();
            prepare();
            fileToDelete.deleteIfExists();
            assertCheckDelete(fileToDelete);

            // -- exists/notExists --

            prepare();
            file.exists();
            assertCheckRead(file);

            prepare();
            file.notExists();
            assertCheckRead(file);

            // -- getFileStore --

            prepare();
            file.getFileStore();
            assertCheckRead(file);
            assertCheckPermission(RuntimePermission.class, "getFileStoreAttributes");

            // -- isSameFile --

            prepare();
            file.isSameFile(dir);
            assertCheckRead(file);
            assertCheckRead(dir);

            // -- moveTo --

            Path target2 = dir.resolve("target1234");
            prepare();
            file.moveTo(target2);
            try {
                assertCheckWrite(file);
                assertCheckWrite(target2);
            } finally {
                // restore file
                target2.moveTo(file);
            }

            // -- newByteChannel --

            SeekableByteChannel sbc;

            prepare();
            sbc = file.newByteChannel();
            try {
                assertCheckRead(file);
            } finally {
                sbc.close();
            }
            prepare();
            sbc = file.newByteChannel(StandardOpenOption.WRITE);
            try {
                assertCheckWrite(file);
            } finally {
                sbc.close();
            }
            prepare();
            sbc = file.newByteChannel(StandardOpenOption.READ, StandardOpenOption.WRITE);
            try {
                assertCheckRead(file);
                assertCheckWrite(file);
            } finally {
                sbc.close();
            }

            prepare();
            sbc = file.newByteChannel(StandardOpenOption.DELETE_ON_CLOSE);
            try {
                assertCheckRead(file);
                assertCheckDelete(file);
            } finally {
                sbc.close();
            }
            file.createFile(); // restore file


            // -- newInputStream/newOutptuStream --

            prepare();
            InputStream in = file.newInputStream();
            try {
                assertCheckRead(file);
            } finally {
                in.close();
            }
            prepare();
            OutputStream out = file.newOutputStream();
            try {
                assertCheckWrite(file);
            } finally {
                out.close();
            }

            // -- newDirectoryStream --

            prepare();
            DirectoryStream<Path> stream = dir.newDirectoryStream();
            try {
                assertCheckRead(dir);

                if (stream instanceof SecureDirectoryStream<?>) {
                    Path entry;
                    SecureDirectoryStream<Path> sds =
                        (SecureDirectoryStream<Path>)stream;

                    // newByteChannel
                    entry = file.getName();
                    prepare();
                    sbc = sds.newByteChannel(entry, EnumSet.of(StandardOpenOption.READ));
                    try {
                        assertCheckRead(file);
                    } finally {
                        sbc.close();
                    }
                    prepare();
                    sbc = sds.newByteChannel(entry, EnumSet.of(StandardOpenOption.WRITE));
                    try {
                        assertCheckWrite(file);
                    } finally {
                        sbc.close();
                    }

                    // deleteFile
                    entry = file.getName();
                    prepare();
                    sds.deleteFile(entry);
                    assertCheckDelete(file);
                    dir.resolve(entry).createFile();  // restore file

                    // deleteDirectory
                    entry = Paths.get("subdir1234");
                    dir.resolve(entry).createDirectory();
                    prepare();
                    sds.deleteDirectory(entry);
                    assertCheckDelete(dir.resolve(entry));

                    // move
                    entry = Paths.get("tempname1234");
                    prepare();
                    sds.move(file.getName(), sds, entry);
                    assertCheckWrite(file);
                    assertCheckWrite(dir.resolve(entry));
                    sds.move(entry, sds, file.getName());  // restore file

                    // newDirectoryStream
                    entry = Paths.get("subdir1234");
                    dir.resolve(entry).createDirectory();
                    try {
                        prepare();
                        sds.newDirectoryStream(entry).close();
                        assertCheckRead(dir.resolve(entry));
                    } finally {
                        dir.resolve(entry).delete();
                    }

                    // getFileAttributeView to access attributes of directory
                    testBasicFileAttributeView(sds
                        .getFileAttributeView(BasicFileAttributeView.class), dir);
                    testPosixFileAttributeView(sds
                        .getFileAttributeView(PosixFileAttributeView.class), dir);

                    // getFileAttributeView to access attributes of entry
                    entry = file.getName();
                    testBasicFileAttributeView(sds
                        .getFileAttributeView(entry, BasicFileAttributeView.class), file);
                    testPosixFileAttributeView(sds
                        .getFileAttributeView(entry, PosixFileAttributeView.class), file);

                } else {
                    System.out.println("SecureDirectoryStream not tested");
                }

            } finally {
                stream.close();
            }

            // -- toAbsolutePath --

            prepare();
            file.getName().toAbsolutePath();
            assertCheckPropertyAccess("user.dir");

            // -- toRealPath --

            prepare();
            file.toRealPath(true);
            assertCheckRead(file);

            prepare();
            file.toRealPath(false);
            assertCheckRead(file);

            prepare();
            Paths.get(".").toRealPath(true);
            assertCheckPropertyAccess("user.dir");

            prepare();
            Paths.get(".").toRealPath(false);
            assertCheckPropertyAccess("user.dir");

            // -- register --

            WatchService watcher = FileSystems.getDefault().newWatchService();
            try {
                prepare();
                dir.register(watcher, StandardWatchEventKind.ENTRY_DELETE);
                assertCheckRead(dir);
            } finally {
                watcher.close();
            }

            // -- getAttribute/setAttribute/readAttributes --

            prepare();
            file.getAttribute("size");
            assertCheckRead(file);

            prepare();
            file.setAttribute("lastModifiedTime",
                FileTime.fromMillis(System.currentTimeMillis()));
            assertCheckWrite(file);

            prepare();
            file.readAttributes("*");
            assertCheckRead(file);

            // -- BasicFileAttributeView --
            testBasicFileAttributeView(file
                .getFileAttributeView(BasicFileAttributeView.class), file);

            // -- PosixFileAttributeView --

            {
                PosixFileAttributeView view =
                    file.getFileAttributeView(PosixFileAttributeView.class);
                if (view != null &&
                    file.getFileStore().supportsFileAttributeView(PosixFileAttributeView.class))
                {
                    testPosixFileAttributeView(view, file);
                } else {
                    System.out.println("PosixFileAttributeView not tested");
                }
            }

            // -- DosFileAttributeView --

            {
                DosFileAttributeView view =
                    file.getFileAttributeView(DosFileAttributeView.class);
                if (view != null &&
                    file.getFileStore().supportsFileAttributeView(DosFileAttributeView.class))
                {
                    prepare();
                    view.readAttributes();
                    assertCheckRead(file);

                    prepare();
                    view.setArchive(false);
                    assertCheckWrite(file);

                    prepare();
                    view.setHidden(false);
                    assertCheckWrite(file);

                    prepare();
                    view.setReadOnly(false);
                    assertCheckWrite(file);

                    prepare();
                    view.setSystem(false);
                    assertCheckWrite(file);
                } else {
                    System.out.println("DosFileAttributeView not tested");
                }
            }

            // -- FileOwnerAttributeView --

            {
                FileOwnerAttributeView view =
                    file.getFileAttributeView(FileOwnerAttributeView.class);
                if (view != null &&
                    file.getFileStore().supportsFileAttributeView(FileOwnerAttributeView.class))
                {
                    prepare();
                    UserPrincipal owner = view.getOwner();
                    assertCheckRead(file);
                    assertCheckPermission(RuntimePermission.class, "accessUserInformation");

                    prepare();
                    view.setOwner(owner);
                    assertCheckWrite(file);
                    assertCheckPermission(RuntimePermission.class, "accessUserInformation");

                } else {
                    System.out.println("FileOwnerAttributeView not tested");
                }
            }

            // -- UserDefinedFileAttributeView --

            {
                UserDefinedFileAttributeView view =
                    file.getFileAttributeView(UserDefinedFileAttributeView.class);
                if (view != null &&
                    file.getFileStore().supportsFileAttributeView(UserDefinedFileAttributeView.class))
                {
                    prepare();
                    view.write("test", ByteBuffer.wrap(new byte[100]));
                    assertCheckWrite(file);
                    assertCheckPermission(RuntimePermission.class,
                                               "accessUserDefinedAttributes");

                    prepare();
                    view.read("test", ByteBuffer.allocate(100));
                    assertCheckRead(file);
                    assertCheckPermission(RuntimePermission.class,
                                               "accessUserDefinedAttributes");

                    prepare();
                    view.size("test");
                    assertCheckRead(file);
                    assertCheckPermission(RuntimePermission.class,
                                               "accessUserDefinedAttributes");

                    prepare();
                    view.list();
                    assertCheckRead(file);
                    assertCheckPermission(RuntimePermission.class,
                                               "accessUserDefinedAttributes");

                    prepare();
                    view.delete("test");
                    assertCheckWrite(file);
                    assertCheckPermission(RuntimePermission.class,
                                               "accessUserDefinedAttributes");
                } else {
                    System.out.println("UserDefinedFileAttributeView not tested");
                }
            }

            // -- AclFileAttributeView --
            {
                AclFileAttributeView view =
                    file.getFileAttributeView(AclFileAttributeView.class);
                if (view != null &&
                    file.getFileStore().supportsFileAttributeView(AclFileAttributeView.class))
                {
                    prepare();
                    List<AclEntry> acl = view.getAcl();
                    assertCheckRead(file);
                    assertCheckPermission(RuntimePermission.class, "accessUserInformation");
                    prepare();
                    view.setAcl(acl);
                    assertCheckWrite(file);
                    assertCheckPermission(RuntimePermission.class, "accessUserInformation");
                } else {
                    System.out.println("AclFileAttributeView not tested");
                }
            }

            // -- UserPrincipalLookupService

            UserPrincipalLookupService lookupService =
                FileSystems.getDefault().getUserPrincipalLookupService();
            UserPrincipal owner = Attributes.getOwner(file);

            prepare();
            lookupService.lookupPrincipalByName(owner.getName());
            assertCheckPermission(RuntimePermission.class,
                                       "lookupUserInformation");

            try {
                UserPrincipal group = Attributes.readPosixFileAttributes(file).group();
                prepare();
                lookupService.lookupPrincipalByGroupName(group.getName());
                assertCheckPermission(RuntimePermission.class,
                                           "lookupUserInformation");
            } catch (UnsupportedOperationException ignore) {
                System.out.println("lookupPrincipalByGroupName not tested");
            }


        } finally {
            file.deleteIfExists();
        }
    }
}
