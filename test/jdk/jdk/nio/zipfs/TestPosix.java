/*
 * Copyright (c) 2019, 2024, SAP SE. All rights reserved.
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @test
 * @bug 8213031 8273935 8324635
 * @summary Test POSIX ZIP file operations.
 * @modules jdk.zipfs
 *          jdk.jartool
 * @run junit TestPosix
 * @run junit/othervm/java.security.policy=test.policy.posix TestPosix
 */
public class TestPosix {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(()->new RuntimeException("jar tool not found"));

    // files and directories
    private static final Path ZIP_FILE = Paths.get("testPosix.zip");
    private static final Path JAR_FILE = Paths.get("testPosix.jar");
    private static final Path ZIP_FILE_COPY = Paths.get("testPosixCopy.zip");
    private static final Path UNZIP_DIR = Paths.get("unzip/");

    // permission sets
    private static final Set<PosixFilePermission> ALLPERMS =
        PosixFilePermissions.fromString("rwxrwxrwx");
    private static final Set<PosixFilePermission> EMPTYPERMS =
        Collections.<PosixFilePermission>emptySet();
    private static final Set<PosixFilePermission> UR = Set.of(OWNER_READ);
    private static final Set<PosixFilePermission> UW = Set.of(OWNER_WRITE);
    private static final Set<PosixFilePermission> UE = Set.of(OWNER_EXECUTE);
    private static final Set<PosixFilePermission> GR = Set.of(GROUP_READ);
    private static final Set<PosixFilePermission> GW = Set.of(GROUP_WRITE);
    private static final Set<PosixFilePermission> GE = Set.of(GROUP_EXECUTE);
    private static final Set<PosixFilePermission> OR = Set.of(OTHERS_READ);
    private static final Set<PosixFilePermission> OW = Set.of(OTHERS_WRITE);
    private static final Set<PosixFilePermission> OE = Set.of(OTHERS_EXECUTE);

    // principals
    private static final UserPrincipal DUMMY_USER = ()->"defusr";
    private static final GroupPrincipal DUMMY_GROUP = ()->"defgrp";

    // FS open options
    private static final Map<String, Object> ENV_DEFAULT = Collections.<String, Object>emptyMap();
    private static final Map<String, Object> ENV_POSIX = Map.of("enablePosixFileAttributes", true);

    // misc
    private static final CopyOption[] COPY_ATTRIBUTES = {StandardCopyOption.COPY_ATTRIBUTES};
    private static final Map<String, ZipFileEntryInfo> ENTRIES = new HashMap<>();

    private int entriesCreated;

    static enum checkExpects {
        contentOnly,
        noPermDataInZip,
        permsInZip,
        permsPosix
    }

    static class ZipFileEntryInfo {
        // permissions to set initially
        private final Set<PosixFilePermission> intialPerms;
        // permissions to set in a later call
        private final Set<PosixFilePermission> laterPerms;
        // permissions that should be effective in the zip file
        private final Set<PosixFilePermission> permsInZip;
        // permissions that should be returned by zipfs w/Posix support
        private final Set<PosixFilePermission> permsPosix;
        // entry is a directory
        private final boolean isDir;
        // need additional read flag in copy test
        private final boolean setReadFlag;

        private ZipFileEntryInfo(Set<PosixFilePermission> initialPerms, Set<PosixFilePermission> laterPerms,
            Set<PosixFilePermission> permsInZip, Set<PosixFilePermission> permsZipPosix, boolean isDir, boolean setReadFlag)
        {
            this.intialPerms = initialPerms;
            this.laterPerms = laterPerms;
            this.permsInZip = permsInZip;
            this.permsPosix = permsZipPosix;
            this.isDir = isDir;
            this.setReadFlag = setReadFlag;
        }
    }

    static class CopyVisitor extends SimpleFileVisitor<Path> {
        private Path from, to;
        private boolean copyPerms;

        CopyVisitor(Path from, Path to) {
            this.from = from;
            this.to = to;
        }

        CopyVisitor(Path from, Path to, boolean copyPerms) {
            this.from = from;
            this.to = to;
            this.copyPerms = copyPerms;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            FileVisitResult rc = super.preVisitDirectory(dir, attrs);
            Path target = to.resolve(from.relativize(dir).toString());
            if (!Files.exists(target)) {
                Files.copy(dir, target, COPY_ATTRIBUTES);
                if (copyPerms) {
                    Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(dir));
                }
            }
            return rc;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            FileVisitResult rc = super.visitFile(file, attrs);
            Path target = to.resolve(from.relativize(file).toString());
            Files.copy(file, target, COPY_ATTRIBUTES);
            if (copyPerms) {
                Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(file));
            }
            return rc;
        }
    }

    static class DeleteVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            FileVisitResult rc = super.postVisitDirectory(dir, exc);
            Files.delete(dir);
            return rc;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            FileVisitResult rc = super.visitFile(file, attrs);
            Files.delete(file);
            return rc;
        }
    }

    @FunctionalInterface
    static interface Executor {
        void doIt() throws IOException;
    }

    static {
        ENTRIES.put("dir",        new ZipFileEntryInfo(ALLPERMS,   null, ALLPERMS,   ALLPERMS,   true,  false));
        ENTRIES.put("uread",      new ZipFileEntryInfo(UR,         null, UR,         UR,         false, false));
        ENTRIES.put("uwrite",     new ZipFileEntryInfo(UW,         null, UW,         UW,         false, true));
        ENTRIES.put("uexec",      new ZipFileEntryInfo(UE,         null, UE,         UE,         false, true));
        ENTRIES.put("gread",      new ZipFileEntryInfo(GR,         null, GR,         GR,         false, true));
        ENTRIES.put("gwrite",     new ZipFileEntryInfo(GW,         null, GW,         GW,         false, true));
        ENTRIES.put("gexec",      new ZipFileEntryInfo(GE,         null, GE,         GE,         false, true));
        ENTRIES.put("oread",      new ZipFileEntryInfo(OR,         null, OR,         OR,         false, true));
        ENTRIES.put("owrite",     new ZipFileEntryInfo(OW,         null, OW,         OW,         false, true));
        ENTRIES.put("oexec",      new ZipFileEntryInfo(OE,         null, OE,         OE,         false, true));
        ENTRIES.put("emptyperms", new ZipFileEntryInfo(EMPTYPERMS, null, EMPTYPERMS, EMPTYPERMS, false, true));
        ENTRIES.put("noperms",    new ZipFileEntryInfo(null,       null, null,       ALLPERMS,   false, false));
        ENTRIES.put("permslater", new ZipFileEntryInfo(null,       UR,   UR,         UR,         false, false));
    }

    private static String expectedDefaultOwner(Path zf) {
        try {
            try {
                PrivilegedExceptionAction<String> pa = ()->Files.getOwner(zf).getName();
                return AccessController.doPrivileged(pa);
            } catch (UnsupportedOperationException e) {
                // if we can't get the owner of the file, we fall back to system property user.name
                PrivilegedAction<String> pa = ()->System.getProperty("user.name");
                return AccessController.doPrivileged(pa);
            }
        } catch (PrivilegedActionException | SecurityException e) {
            System.out.println("Caught " + e.getClass().getName() + "(" + e.getMessage() +
                ") when running a privileged operation to get the default owner.");
            return null;
        }
    }

    private static String expectedDefaultGroup(Path zf, String defaultOwner) {
        try {
            try {
                PosixFileAttributeView zfpv = Files.getFileAttributeView(zf, PosixFileAttributeView.class);
                if (zfpv == null) {
                    return defaultOwner;
                }
                PrivilegedExceptionAction<String> pa = ()->zfpv.readAttributes().group().getName();
                return AccessController.doPrivileged(pa);
            } catch (UnsupportedOperationException e) {
                return defaultOwner;
            }
        } catch (PrivilegedActionException | SecurityException e) {
            System.out.println("Caught an exception when running a privileged operation to get the default group.");
            e.printStackTrace();
            return null;
        }
    }

    private void putEntry(FileSystem fs, String name, ZipFileEntryInfo entry) throws IOException {
        if (entry.isDir) {
            if (entry.intialPerms == null) {
                Files.createDirectory(fs.getPath(name));
            } else {
                Files.createDirectory(fs.getPath(name), PosixFilePermissions.asFileAttribute(entry.intialPerms));
            }

        } else {
            if (entry.intialPerms == null) {
                Files.createFile(fs.getPath(name));
            } else {
                Files.createFile(fs.getPath(name), PosixFilePermissions.asFileAttribute(entry.intialPerms));
            }
        }
        if (entry.laterPerms != null) {
            Files.setAttribute(fs.getPath(name), "zip:permissions", entry.laterPerms);
        }
        entriesCreated++;
    }

    private FileSystem createTestZipFile(Path zpath, Map<String, Object> env) throws IOException {
        if (Files.exists(zpath)) {
            System.out.println("Deleting old " + zpath + "...");
            Files.delete(zpath);
        }
        System.out.println("Creating " + zpath + "...");
        entriesCreated = 0;
        var opts = new HashMap<String, Object>();
        opts.putAll(env);
        opts.put("create", true);
        FileSystem fs = FileSystems.newFileSystem(zpath, opts);
        for (String name : ENTRIES.keySet()) {
            putEntry(fs, name, ENTRIES.get(name));
        }
        return fs;
    }

    // The caller is responsible for closing the FileSystem returned by this method
    private FileSystem createEmptyZipFileSystem(Path zpath, Map<String, Object> env) throws IOException {
        if (Files.exists(zpath)) {
            System.out.println("Deleting old " + zpath + "...");
            Files.delete(zpath);
        }
        System.out.println("Creating " + zpath + "...");
        var opts = new HashMap<String, Object>();
        opts.putAll(env);
        opts.put("create", true);
        return FileSystems.newFileSystem(zpath, opts);
    }

    private void delTree(Path p) throws IOException {
        if (Files.exists(p)) {
            Files.walkFileTree(p, new DeleteVisitor());
        }
    }

    private void addOwnerRead(Path root) throws IOException {
        for (String name : ENTRIES.keySet()) {
            ZipFileEntryInfo ei = ENTRIES.get(name);
            if (!ei.setReadFlag) {
                continue;
            }
            Path setReadOn = root.resolve(name);
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(setReadOn);
            perms.add(OWNER_READ);
            Files.setPosixFilePermissions(setReadOn, perms);
        }
    }

    private void removeOwnerRead(Path root) throws IOException {
        for (String name : ENTRIES.keySet()) {
            ZipFileEntryInfo ei = ENTRIES.get(name);
            if (!ei.setReadFlag) {
                continue;
            }
            Path removeReadFrom = root.resolve(name);
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(removeReadFrom);
            perms.remove(OWNER_READ);
            Files.setPosixFilePermissions(removeReadFrom, perms);
        }
    }

    @SuppressWarnings("unchecked")
    private void checkEntry(Path file, checkExpects expected) {
        System.out.println("Checking " + file + "...");
        String name = file.getFileName().toString();
        ZipFileEntryInfo ei = ENTRIES.get(name);
        assertNotNull(ei, "Found unknown entry " + name + ".");
        BasicFileAttributes attrs = null;
        if (expected == checkExpects.permsPosix) {
            try {
                attrs = Files.readAttributes(file, PosixFileAttributes.class);
            } catch (IOException e) {
                e.printStackTrace();
                fail("Caught IOException reading file attributes (posix) for " + name + ": " + e.getMessage());
            }
        } else {
            try {
                attrs = Files.readAttributes(file, BasicFileAttributes.class);
            } catch (IOException e) {
                e.printStackTrace();
                fail("Caught IOException reading file attributes (basic) " + name + ": " + e.getMessage());
            }
        }
        assertEquals(ei.isDir, Files.isDirectory(file), "Unexpected directory attribute for:" + System.lineSeparator() + attrs);

        if (expected == checkExpects.contentOnly) {
            return;
        }

        Set<PosixFilePermission> permissions;
        if (expected == checkExpects.permsPosix) {
            try {
                permissions = Files.getPosixFilePermissions(file);
            } catch (IOException e) {
                e.printStackTrace();
                fail("Caught IOException getting permission attribute for:" + System.lineSeparator() + attrs);
                return;
            }
            comparePermissions(ei.permsPosix, permissions);
        } else if (expected == checkExpects.permsInZip || expected == checkExpects.noPermDataInZip) {
            try {
                permissions = (Set<PosixFilePermission>)Files.getAttribute(file, "zip:permissions");
            } catch (IOException e) {
                e.printStackTrace();
                fail("Caught IOException getting permission attribute for:" + System.lineSeparator() + attrs);
                return;
            }
            comparePermissions(expected == checkExpects.noPermDataInZip ? null : ei.permsInZip, permissions);
        }
    }

    private void doCheckEntries(Path path, checkExpects expected) throws IOException {
        AtomicInteger entries = new AtomicInteger();

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(path)) {
            paths.forEach(file -> {
                entries.getAndIncrement();
                checkEntry(file, expected);
            });
        }
        System.out.println("Number of entries: " + entries.get() + ".");
        assertEquals(entriesCreated, entries.get(), "File contained wrong number of entries.");
    }

    private void checkEntries(FileSystem fs, checkExpects expected) throws IOException {
        System.out.println("Checking permissions on file system " + fs + "...");
        doCheckEntries(fs.getPath("/"), expected);
    }

    private void checkEntries(Path path, checkExpects expected) throws IOException {
        System.out.println("Checking permissions on path " + path + "...");
        doCheckEntries(path, expected);
    }

    private boolean throwsUOE(Executor e) throws IOException {
        try {
            e.doIt();
            return false;
        } catch (UnsupportedOperationException exc) {
            return true;
        }
    }

    private void comparePermissions(Set<PosixFilePermission> expected, Set<PosixFilePermission> actual) {
        if (expected == null) {
            assertNull(actual, "Permissions are not null");
        } else {
            assertNotNull(actual, "Permissions are null.");
            assertEquals(expected.size(), actual.size(), "Unexpected number of permissions (" +
                actual.size() + " received vs " + expected.size() + " expected).");
            for (PosixFilePermission p : expected) {
                assertTrue(actual.contains(p), "Posix permission " + p + " missing.");
            }
        }
    }

    /**
     * This tests whether the entries in a zip file created w/o
     * Posix support are correct.
     *
     * @throws IOException
     */
    @Test
    public void testDefault() throws IOException {
        // create zip file using zipfs with default options
        createTestZipFile(ZIP_FILE, ENV_DEFAULT).close();
        // check entries on zipfs with default options
        try (FileSystem zip = FileSystems.newFileSystem(ZIP_FILE, ENV_DEFAULT)) {
            checkEntries(zip, checkExpects.permsInZip);
        }
        // check entries on zipfs with posix options
        try (FileSystem zip = FileSystems.newFileSystem(ZIP_FILE, ENV_POSIX)) {
            checkEntries(zip, checkExpects.permsPosix);
        }
    }

    /**
     * This tests whether the entries in a zip file created w/
     * Posix support are correct.
     *
     * @throws IOException
     */
    @Test
    public void testPosix() throws IOException {
        // create zip file using zipfs with posix option
        createTestZipFile(ZIP_FILE, ENV_POSIX).close();
        // check entries on zipfs with default options
        try (FileSystem zip = FileSystems.newFileSystem(ZIP_FILE, ENV_DEFAULT)) {
            checkEntries(zip, checkExpects.permsInZip);
        }
        // check entries on zipfs with posix options
        try (FileSystem zip = FileSystems.newFileSystem(ZIP_FILE, ENV_POSIX)) {
            checkEntries(zip, checkExpects.permsPosix);
        }
    }

    /**
     * This tests whether the entries in a zip file copied from another
     * are correct.
     *
     * @throws IOException
     */
    @Test
    public void testCopy() throws IOException {
        // copy zip to zip with default options
        try (FileSystem zipIn = createTestZipFile(ZIP_FILE, ENV_DEFAULT);
             FileSystem zipOut = createEmptyZipFileSystem(ZIP_FILE_COPY, ENV_DEFAULT)) {
            Path from = zipIn.getPath("/");
            Files.walkFileTree(from, new CopyVisitor(from, zipOut.getPath("/")));
        }
        // check entries on copied zipfs with default options
        try (FileSystem zip = FileSystems.newFileSystem(ZIP_FILE_COPY, ENV_DEFAULT)) {
            checkEntries(zip, checkExpects.permsInZip);
        }
        // check entries on copied zipfs with posix options
        try (FileSystem zip = FileSystems.newFileSystem(ZIP_FILE_COPY, ENV_POSIX)) {
            checkEntries(zip, checkExpects.permsPosix);
        }
    }

    /**
     * This tests whether the entries of a zip file look correct after extraction
     * and re-packing. When not using zipfs with Posix support, we expect the
     * effective permissions in the resulting zip file to be empty.
     *
     * @throws IOException
     */
    @Test
    public void testUnzipDefault() throws IOException {
        delTree(UNZIP_DIR);
        Files.createDirectory(UNZIP_DIR);

        try (FileSystem srcZip = createTestZipFile(ZIP_FILE, ENV_DEFAULT)) {
            Path from = srcZip.getPath("/");
            Files.walkFileTree(from, new CopyVisitor(from, UNZIP_DIR));
        }

        // we just check that the entries got extracted to file system
        checkEntries(UNZIP_DIR, checkExpects.contentOnly);

        // the target zip file is opened with Posix support
        // but we expect no permission data to be copied using the default copy method
        try (FileSystem tgtZip = createEmptyZipFileSystem(ZIP_FILE_COPY, ENV_POSIX)) {
            Files.walkFileTree(UNZIP_DIR, new CopyVisitor(UNZIP_DIR, tgtZip.getPath("/")));
        }

        // check entries on copied zipfs - no permission data should exist
        if (System.getProperty("os.name").toLowerCase().contains("windows"))
            try (FileSystem zip = FileSystems.newFileSystem(ZIP_FILE_COPY,
                ENV_DEFAULT)) {
                checkEntries(zip, checkExpects.noPermDataInZip);
            }
    }

    /**
     * This tests whether the entries of a zip file look correct after extraction
     * and re-packing. If the default file system supports Posix, we test whether we
     * correctly carry the Posix permissions. Otherwise there's not much to test in
     * this method.
     *
     * @throws IOException
     */
    @Test
    public void testUnzipPosix() throws IOException {
        delTree(UNZIP_DIR);
        Files.createDirectory(UNZIP_DIR);

        try {
            Files.getPosixFilePermissions(UNZIP_DIR);
        } catch (Exception e) {
            // if we run into any exception here, be it because of the fact that the file system
            // is not Posix or if we have insufficient security permissions, we can't do this test.
            System.out.println("This can't be tested here because of " + e);
            return;
        }

        try (FileSystem srcZip = createTestZipFile(ZIP_FILE, ENV_POSIX)) {
            Path from = srcZip.getPath("/");
            // copy permissions as well
            Files.walkFileTree(from, new CopyVisitor(from, UNZIP_DIR, true));
        }

        // permissions should have been propagated to file system
        checkEntries(UNZIP_DIR, checkExpects.permsPosix);

        try (FileSystem tgtZip = createEmptyZipFileSystem(ZIP_FILE_COPY, ENV_POSIX)) {
            // Make some files owner readable to be able to copy them into the zipfs
            addOwnerRead(UNZIP_DIR);

            // copy permissions as well
            Files.walkFileTree(UNZIP_DIR, new CopyVisitor(UNZIP_DIR, tgtZip.getPath("/"), true));

            // Fix back all the files in the target zip file which have been made readable before
            removeOwnerRead(tgtZip.getPath("/"));
        }

        // check entries on copied zipfs - permission data should have been propagated
        try (FileSystem zip = FileSystems.newFileSystem(ZIP_FILE_COPY, ENV_POSIX)) {
            checkEntries(zip, checkExpects.permsPosix);
        }
    }

    /**
     * Tests POSIX default behavior.
     *
     * @throws IOException
     */
    @Test
    public void testPosixDefaults() throws IOException {
        // test with posix = false, expect UnsupportedOperationException
        try (FileSystem zipIn = createTestZipFile(ZIP_FILE, ENV_DEFAULT)) {
            var entry = zipIn.getPath("/dir");
            assertTrue(throwsUOE(()->Files.getPosixFilePermissions(entry)));
            assertTrue(throwsUOE(()->Files.setPosixFilePermissions(entry, UW)));
            assertTrue(throwsUOE(()->Files.getOwner(entry)));
            assertTrue(throwsUOE(()->Files.setOwner(entry, DUMMY_USER)));
            assertNull(Files.getFileAttributeView(entry, PosixFileAttributeView.class));
        }

        // test with posix = true -> default values
        try (FileSystem zipIn = FileSystems.newFileSystem(ZIP_FILE, ENV_POSIX)) {
            String defaultOwner = expectedDefaultOwner(ZIP_FILE);
            String defaultGroup = expectedDefaultGroup(ZIP_FILE, defaultOwner);
            var entry = zipIn.getPath("/noperms");
            comparePermissions(ALLPERMS, Files.getPosixFilePermissions(entry));
            var owner = Files.getOwner(entry);
            assertNotNull(owner, "owner should not be null");
            if (defaultOwner != null) {
                assertEquals(defaultOwner, owner.getName());
            }
            Files.setOwner(entry, DUMMY_USER);
            assertEquals(DUMMY_USER, Files.getOwner(entry));
            var view = Files.getFileAttributeView(entry, PosixFileAttributeView.class);
            var group = view.readAttributes().group();
            assertNotNull(group, "group must not be null");
            if (defaultGroup != null) {
                assertEquals(defaultGroup, group.getName());
            }
            view.setGroup(DUMMY_GROUP);
            assertEquals(DUMMY_GROUP, view.readAttributes().group());
            entry = zipIn.getPath("/uexec");
            Files.setPosixFilePermissions(entry, GR); // will be persisted
            comparePermissions(GR, Files.getPosixFilePermissions(entry));
        }

        // test with posix = true + custom defaults of type String
        try (FileSystem zipIn = FileSystems.newFileSystem(ZIP_FILE, Map.of("enablePosixFileAttributes", true,
            "defaultOwner", "auser", "defaultGroup", "agroup", "defaultPermissions", "r--------")))
        {
            var entry = zipIn.getPath("/noperms");
            comparePermissions(UR, Files.getPosixFilePermissions(entry));
            assertEquals("auser", Files.getOwner(entry).getName());
            var view = Files.getFileAttributeView(entry, PosixFileAttributeView.class);
            assertEquals("agroup", view.readAttributes().group().getName());
            // check if the change to permissions of /uexec was persisted
            comparePermissions(GR, Files.getPosixFilePermissions(zipIn.getPath("/uexec")));
        }

        // test with posix = true + custom defaults as Objects
        try (FileSystem zipIn = FileSystems.newFileSystem(ZIP_FILE, Map.of("enablePosixFileAttributes", true,
            "defaultOwner", DUMMY_USER, "defaultGroup", DUMMY_GROUP, "defaultPermissions", UR)))
        {
            var entry = zipIn.getPath("/noperms");
            comparePermissions(UR, Files.getPosixFilePermissions(entry));
            assertEquals(DUMMY_USER, Files.getOwner(entry));
            var view = Files.getFileAttributeView(entry, PosixFileAttributeView.class);
            assertEquals(DUMMY_GROUP, view.readAttributes().group());
        }
    }

    /**
     * Sanity check to test whether the zip file can be unzipped with the java.util.zip API.
     *
     * @throws IOException
     */
    @Test
    public void testUnzipWithJavaUtilZip() throws IOException {
        createTestZipFile(ZIP_FILE, ENV_DEFAULT).close();
        delTree(UNZIP_DIR);
        Files.createDirectory(UNZIP_DIR);
        File targetDir = UNZIP_DIR.toFile();
        try (ZipFile zf = new ZipFile(ZIP_FILE.toFile())) {
            Enumeration<? extends ZipEntry> zenum = zf.entries();
            while (zenum.hasMoreElements()) {
                ZipEntry ze = zenum.nextElement();
                File target = new File(targetDir + File.separator + ze.getName());
                if (ze.isDirectory()) {
                    target.mkdir();
                    continue;
                }
                try (InputStream is = zf.getInputStream(ze);
                     FileOutputStream fos = new FileOutputStream(target))
                {
                    while (is.available() > 0) {
                        fos.write(is.read());
                    }
                }
            }
        }
    }

    /**
     * Sanity check to test whether a jar file created with zipfs can be
     * extracted with the java.util.jar API.
     *
     * @throws IOException
     */
    @Test
    public void testJarFile() throws IOException {
        // create jar file using zipfs with default options
        createTestZipFile(JAR_FILE, ENV_DEFAULT).close();

        // extract it using java.util.jar.JarFile
        delTree(UNZIP_DIR);
        Files.createDirectory(UNZIP_DIR);
        File targetDir = UNZIP_DIR.toFile();
        try (JarFile jf = new JarFile(ZIP_FILE.toFile())) {
            Enumeration<? extends JarEntry> zenum = jf.entries();
            while (zenum.hasMoreElements()) {
                JarEntry ze = zenum.nextElement();
                File target = new File(targetDir + File.separator + ze.getName());
                if (ze.isDirectory()) {
                    target.mkdir();
                    continue;
                }
                try (InputStream is = jf.getInputStream(ze);
                     FileOutputStream fos = new FileOutputStream(target))
                {
                    while (is.available() > 0) {
                        fos.write(is.read());
                    }
                }
            }
        }

        // extract it using the jar tool
        delTree(UNZIP_DIR);
        System.out.println("jar xvf " + JAR_FILE);

        // the run method catches IOExceptions, we need to expose them
        int rc = JAR_TOOL.run(System.out, System.err, "xvf", JAR_FILE.toString());
        assertEquals(0, rc, "Return code of jar call is " + rc + " but expected 0");
    }

    /**
     * Verify that calling Files.setPosixFilePermissions with the current
     * permission set does not change the 'external file attributes' field.
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void setPermissionsShouldPreserveRemainingBits() throws IOException {
        assertExternalFileAttributeUnchanged(fs -> {
            Path path = fs.getPath("hello.txt");
            // Set permissions to their current value
            Files.setPosixFilePermissions(path, Files.getPosixFilePermissions(path));
        });
    }

    /**
     * Verify that calling Files.setPosixFilePermissions on an MS-DOS entry
     * results in only the expected permission bits being set
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void setPermissionsShouldConvertToUnix() throws IOException {
        // The default environment creates MS-DOS entries, with zero 'external file attributes'
        try (FileSystem fs = createEmptyZipFileSystem(ZIP_FILE, ENV_DEFAULT)) {
            Path path = fs.getPath("hello.txt");
            Files.createFile(path);
        }
        // The CEN header is now as follows:
        //
        //   004A CENTRAL HEADER #1     02014B50
        //   004E Created Zip Spec      14 '2.0'
        //   004F Created OS            00 'MS-DOS'
        //   0050 Extract Zip Spec      14 '2.0'
        //   0051 Extract OS            00 'MS-DOS'
        //   [...]
        //   0070 Ext File Attributes   00000000

        // Sanity check that all 'external file attributes' bits are all zero
        verifyExternalFileAttribute(Files.readAllBytes(ZIP_FILE), "0");

        // Convert to a UNIX entry by calling Files.setPosixFilePermissions
        try (FileSystem fs = FileSystems.newFileSystem(ZIP_FILE, ENV_POSIX)) {
            Path path = fs.getPath("hello.txt");
            Files.setPosixFilePermissions(path, EnumSet.of(OWNER_READ));
        }

        // The CEN header should now be as follows:
        //
        // 004A CENTRAL HEADER #1     02014B50
        // 004E Created Zip Spec      14 '2.0'
        // 004F Created OS            03 'Unix'
        // 0050 Extract Zip Spec      14 '2.0'
        // 0051 Extract OS            00 'MS-DOS'
        // [...]
        // 0070 Ext File Attributes   01000000

        // The first of the nine trailing permission bits should be set
        verifyExternalFileAttribute(Files.readAllBytes(ZIP_FILE), "100000000");
    }

    /**
     * Verify that a non-POSIX operation such as Files.setLastModifiedTime
     * does not change the 'external file attributes' field.
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void setLastModifiedTimeShouldNotChangeExternalFileAttribute() throws IOException {
        assertExternalFileAttributeUnchanged(fs -> {
            Path path = fs.getPath("hello.txt");
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        });
    }

    // Represents an operation performed on a FileSystem
    static interface FileSystemOperation {
        void accept(FileSystem fileSystem) throws IOException;
    }

    /**
     * Assert that running the given operation on a ZipFileSystem does not
     * change the 'external file attributes' value of the 'hello.txt' entry
     * @param action the action to run on the file system
     *
     * @throws IOException if an unexpected IOException occurs
     */
    private void assertExternalFileAttributeUnchanged(FileSystemOperation action) throws IOException {
        /*
         * The ZIP test vector used here is created using:
         * % touch hello.txt
         * % chmod u+s hello.txt     # setuid
         * % chmod g+s hello.txt     # setgid
         * % chmod +t hello.txt      # sticky
         * % zip hello.zip hello.txt
         * % cat hello.zip | xxd -ps
         */
        byte[] zip = HexFormat.of().parseHex("""
                504b03040a0000000000d994945700000000000000000000000009001c00
                68656c6c6f2e7478745554090003aa268365aa26836575780b000104f501
                00000414000000504b01021e030a0000000000d994945700000000000000
                0000000000090018000000000000000000a48f0000000068656c6c6f2e74
                78745554050003aa26836575780b000104f50100000414000000504b0506
                00000000010001004f000000430000000000
                """.replaceAll("\n",""));

        // Expected bit values of the 'external file attributes' CEN field in the ZIP above
        String expectedBits = "1000111110100100";
                            // ^^^^             file type: 1000 (regular file)
                            //     ^            setuid: ON
                            //      ^           setgid: ON
                            //       ^          sticky: ON
                            //        ^^^^^^^^^ rwxr--r--  (9 bits)

        // Sanity check that 'external file attributes' has the expected value
        verifyExternalFileAttribute(zip, expectedBits);


        Path zipFile = Path.of("preserve-external-file-attrs.zip");
        Files.write(zipFile, zip);

        // Run the provided action on the ZipFileSystem
        try (FileSystem fs = FileSystems.newFileSystem(zipFile, ENV_POSIX)) {
            action.accept(fs);
        }
        // Running the action should not change the 'external file attributes' value
        verifyExternalFileAttribute(Files.readAllBytes(zipFile), expectedBits);
    }

    /**
     * Verify that the first 16 bits of the CEN field 'external file attributes' matches
     * a given bit string
     * @param zip the ZIP file to parse
     * @param expectedBits a string of '0' or '1' representing the expected bits
     */
    private void verifyExternalFileAttribute(byte[] zip, String expectedBits) {
        // Buffer to help parse the ZIP
        ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        // Look up offset of first CEN header from the END header
        int cenOff = buffer.getInt(buffer.capacity() - ZipFile.ENDHDR + ZipFile.ENDOFF);
        // We're interested in the first 16 'unix' bits of the 32-bit 'external file attributes' field
        int externalFileAttr = (buffer.getInt(cenOff + ZipFile.CENATX) >> 16) & 0xFFFF;

        // Verify that the expected bits are set
        assertEquals(expectedBits, Integer.toBinaryString(externalFileAttr),
                "The 'external file attributes' field does not match the expected value:");
    }
}
