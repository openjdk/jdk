/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4057701 6286712 6364377 8181919
 * @requires (os.family == "linux" | os.family == "mac" |
 *            os.family == "windows")
 * @summary Basic functionality of File.get-X-Space methods.
 * @library .. /test/lib
 * @build jdk.test.lib.Platform
 * @run main/othervm/native -Djava.security.manager=allow GetXSpace
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.Permission;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.Platform;
import jdk.test.lib.Platform;

import static java.lang.System.err;
import static java.lang.System.out;

@SuppressWarnings("removal")
public class GetXSpace {
    static {
        System.loadLibrary("GetXSpace");
    }

    private static SecurityManager [] sma = { null, new Allow(), new DenyFSA(),
                                              new DenyRead() };

    private static int fail = 0;
    private static int pass = 0;
    private static Throwable first;

    static void reset() {
        fail = 0;
        pass = 0;
        first = null;
    }

    static void pass() {
        pass++;
    }

    static void fail(String p) {
        setFirst(p);
        System.err.format("FAILED: %s%n", p);
        fail++;
    }

    static void fail(String p, long exp, String cmp, long got) {
        String s = String.format("'%s': %d %s %d", p, exp, cmp, got);
        setFirst(s);
        System.err.format("FAILED: %s%n", s);
        fail++;
    }

    private static void fail(String p, Class ex) {
        String s = String.format("'%s': expected %s - FAILED%n", p, ex.getName());
        setFirst(s);
        System.err.format("FAILED: %s%n", s);
        fail++;
    }

    private static void setFirst(String s) {
        if (first == null) {
            first = new RuntimeException(s);
        }
    }

    private static class Space {
        private final String name;
        private final long size;
        private final long total;
        private final long free;
        private final long available;

        Space(String name) {
            this.name = name;
            long[] sizes = new long[4];
            if (getSpace0(name, sizes))
                System.err.println("WARNING: total space is estimated");
            this.size = sizes[0];
            this.total = sizes[1];
            this.free = sizes[2];
            this.available = sizes[3];
        }

        String name() { return name; }
        long size() { return size; }
        long total() { return total; }
        long available() { return available; }
        long free() { return free; }

        boolean woomFree(long freeSpace) {
            return ((freeSpace >= (available / 10)) &&
                    (freeSpace <= (available * 10)));
        }

        public String toString() {
            return String.format("%s (%d/%d/%d)", name, total, free, available);
        }
    }

    private static void diskFree() throws IOException {
        ArrayList<Space> al = new ArrayList<>();

        String cmd = "fsutil volume diskFree C:\\";
        StringBuilder sb = new StringBuilder();
        Process p = Runtime.getRuntime().exec(cmd);
        try (BufferedReader in = p.inputReader()) {
            String s;
            int i = 0;
            while ((s = in.readLine()) != null) {
                // skip header
                if (i++ == 0) continue;
                sb.append(s).append("\n");
            }
        }
        out.println(sb);
    }

    private static ArrayList<String> paths() throws IOException {
        ArrayList<String> al = new ArrayList<>();

        File[] roots = File.listRoots();
        long[] space = new long[4];
        for (File root : roots) {
            String path = root.toString();
            al.add(path);
        }

        return al;
    }

    private static void tryCatch(Space s) {
        out.format("%s:%n", s.name());
        File f = new File(s.name());
        SecurityManager sm = System.getSecurityManager();
        if (sm instanceof Deny) {
            String fmt = "  %14s: \"%s\" thrown as expected%n";
            try {
                f.getTotalSpace();
                fail(s.name(), SecurityException.class);
            } catch (SecurityException x) {
                out.format(fmt, "getTotalSpace", x);
                pass();
            }
            try {
                f.getFreeSpace();
                fail(s.name(), SecurityException.class);
            } catch (SecurityException x) {
                out.format(fmt, "getFreeSpace", x);
                pass();
            }
            try {
                f.getUsableSpace();
                fail(s.name(), SecurityException.class);
            } catch (SecurityException x) {
                out.format(fmt, "getUsableSpace", x);
                pass();
            }
        }
    }

    private static void compare(Space s) {
        File f = new File(s.name());
        long ts = f.getTotalSpace();
        long fs = f.getFreeSpace();
        long us = f.getUsableSpace();

        out.format("%s (%d):%n", s.name(), s.size());
        String fmt = "  %-4s total = %12d free = %12d usable = %12d%n";
        out.format(fmt, "getSpace0", s.total(), s.free(), s.available());
        out.format(fmt, "getXSpace", ts, fs, us);

        // If the file system can dynamically change size, this check will fail.
        // This can happen on macOS for the /dev files system.
        if (ts != s.total()
            && (!Platform.isOSX() || !s.name().equals("/dev"))) {
            long blockSize = 1;
            long numBlocks = 0;
            try {
                FileStore fileStore = Files.getFileStore(f.toPath());
                blockSize = fileStore.getBlockSize();
                numBlocks = fileStore.getTotalSpace()/blockSize;
            } catch (NoSuchFileException nsfe) {
                // On Linux, ignore the NSFE if the path is one of the
                // /run/user/$UID mounts created by pam_systemd(8) as it
                // might be deleted during the test
                if (!Platform.isLinux() || !s.name().contains("/run/user"))
                    throw new RuntimeException(nsfe);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (Platform.isWindows()) {
                if (ts > s.total()) {
                    fail(s.name() + " total space", ts, ">", s.total());
                }
            } else if (ts != s.total()) {
                fail(s.name() + " total space", ts, "!=", s.total());
            }
        } else {
            pass();
        }

        // unix usable space is from statvfs.f_bavail
        long tsp = (!Platform.isWindows() ? us : fs);
        if (!s.woomFree(tsp)) {
            fail(s.name(), s.available(), "??", tsp);
        } else {
            pass();
        }

        //
        // Invariants are:
        // total space <= size
        // total space == size (Unix)
        // free space <= total space (if no quotas in effect) (Windows)
        // free space < size (if quotas in effect) (Windows)
        // usable space <= total space
        // usable space <= free space
        //

        // total space <= size
        if (ts > s.size()) {
            fail(s.name() + " size", ts, ">", s.size());
        } else {
            pass();
        }

        // On Unix the total space should always be the volume size
        if (Platform.isWindows()) {
            // ts != s.size() indicates that quotas are in effect
            if (ts == s.size() && fs > s.total()) {
                fail(s.name() + " free space", fs, ">", s.total());
            } else if (ts < s.size() && fs > s.size()) {
                fail(s.name() + " free space (quota)", fs, ">", s.size());
            } else {
                pass();
            }
        } else { // not Windows
            if (ts != s.size()) {
                fail(s.name() + " total space", ts, "!=", s.size());
            } else {
                pass();
            }
        }

        // usable space <= total space
        if (us > s.total()) {
            fail(s.name() + " usable space", us, ">", s.total());
        } else {
            pass();
        }

        // usable space <= free space
        if (us > s.free()) {
            // free and usable change dynamically
            System.err.println("Warning: us > s.free()");
            if (1.0 - Math.abs((double)s.free()/(double)us) > 0.01) {
                fail(s.name() + " usable vs. free space", us, ">", s.free());
            } else {
                pass();
            }
        } else {
            pass();
        }
    }

    private static String FILE_PREFIX = "/getSpace.";
    private static void compareZeroNonExist() {
        File f;
        while (true) {
            f = new File(FILE_PREFIX + Math.random());
            if (f.exists()) {
                continue;
            }
            break;
        }

        long [] s = { f.getTotalSpace(), f.getFreeSpace(), f.getUsableSpace() };

        for (int i = 0; i < s.length; i++) {
            if (s[i] != 0L) {
                fail(f.getName(), s[i], "!=", 0L);
            } else {
                pass();
            }
        }
    }

    private static void compareZeroExist() {
        try {
            File f = File.createTempFile("tmp", null, new File("."));

            long [] s = { f.getTotalSpace(), f.getFreeSpace(), f.getUsableSpace() };

            for (int i = 0; i < s.length; i++) {
                if (s[i] == 0L) {
                    fail(f.getName(), s[i], "==", 0L);
                } else {
                    pass();
                }
            }
        } catch (IOException x) {
            x.printStackTrace();
            fail("Couldn't create temp file for test");
        }
    }

    private static class Allow extends SecurityManager {
        public void checkRead(String file) {}
        public void checkPermission(Permission p) {}
        public void checkPermission(Permission p, Object context) {}
    }

    private static class Deny extends SecurityManager {
        public void checkPermission(Permission p) {
            if (p.implies(new RuntimePermission("setSecurityManager"))
                || p.implies(new RuntimePermission("getProtectionDomain")))
                return;
            super.checkPermission(p);
        }

        public void checkPermission(Permission p, Object context) {
            if (p.implies(new RuntimePermission("setSecurityManager"))
                || p.implies(new RuntimePermission("getProtectionDomain")))
                return;
            super.checkPermission(p, context);
        }
    }

    private static class DenyFSA extends Deny {
        private String err = "sorry - getFileSystemAttributes";

        public void checkPermission(Permission p) {
            if (p.implies(new RuntimePermission("getFileSystemAttributes")))
                throw new SecurityException(err);
            super.checkPermission(p);
        }

        public void checkPermission(Permission p, Object context) {
            if (p.implies(new RuntimePermission("getFileSystemAttributes")))
                throw new SecurityException(err);
            super.checkPermission(p, context);
        }
    }

    private static class DenyRead extends Deny {
        private String err = "sorry - checkRead()";

        public void checkRead(String file) {
            throw new SecurityException(err);
        }
    }

    private static int testFile(Path dir) {
        String dirName = dir.toString();
        out.format("--- Testing %s%n", dirName);
        compare(new Space(dir.getRoot().toString()));

        if (fail != 0) {
            err.format("%d tests: %d failure(s); first: %s%n",
                       fail + pass, fail, first);
        } else {
            out.format("all %d tests passed%n", fail + pass);
        }

        return fail != 0 ? 1 : 0;
    }

    private static int testVolumes() {
        out.println("--- Testing volumes");
        // Find all of the partitions on the machine and verify that the sizes
        // returned by File::getXSpace are equivalent to those from getSpace0
        ArrayList<String> l;
        try {
            l = paths();
            if (Platform.isWindows()) {
                diskFree();
            }
        } catch (IOException x) {
            throw new RuntimeException("can't get file system information", x);
        }
        if (l.size() == 0)
            throw new RuntimeException("no partitions?");

        for (int i = 0; i < sma.length; i++) {
            System.setSecurityManager(sma[i]);
            SecurityManager sm = System.getSecurityManager();
            if (sma[i] != null && sm == null)
                throw new RuntimeException("Test configuration error "
                                           + " - can't set security manager");

            out.format("%nSecurityManager = %s%n" ,
                       (sm == null ? "null" : sm.getClass().getName()));
            for (var p : l) {
                Space s = new Space(p);
                if (sm instanceof Deny) {
                    tryCatch(s);
                } else {
                    compare(s);
                    compareZeroNonExist();
                    compareZeroExist();
                }
            }
        }

        System.setSecurityManager(null);

        if (fail != 0) {
            err.format("%d tests: %d failure(s); first: %s%n",
                       fail + pass, fail, first);
        } else {
            out.format("all %d tests passed%n", fail + pass);
        }

        return fail != 0 ? 1 : 0;
    }

    private static void perms(File file, boolean allow) throws IOException {
        file.setExecutable(allow, false);
        file.setReadable(allow, false);
        file.setWritable(allow, false);
    }

    private static void deny(Path path) throws IOException {
        perms(path.toFile(), false);
    }

    private static void allow(Path path) throws IOException {
        perms(path.toFile(), true);
    }

    public static void main(String[] args) throws Exception {
        int failedTests = testVolumes();
        reset();

        Path tmpDir = Files.createTempDirectory(null);
        Path tmpSubdir = Files.createTempDirectory(tmpDir, null);
        Path tmpFile = Files.createTempFile(tmpSubdir, "foo", null);

        deny(tmpSubdir);
        failedTests += testFile(tmpFile);

        allow(tmpSubdir);
        Files.delete(tmpFile);
        Files.delete(tmpSubdir);
        Files.delete(tmpDir);

        if (failedTests > 0) {
            throw new RuntimeException(failedTests + " test(s) failed");
        }
    }

    //
    // root     the root of the volume
    // size[0]  total size:   number of bytes in the volume
    // size[1]  total space:  number of bytes visible to the caller
    // size[2]  free space:   number of free bytes in the volume
    // size[3]  usable space: number of bytes available to the caller
    //
    private static native boolean getSpace0(String root, long[] space);
}
