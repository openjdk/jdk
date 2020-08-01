/*
 * Copyright (c) 2005, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4057701 6286712 6364377
 * @run build GetXSpace
 * @run shell GetXSpace.sh
 * @summary Basic functionality of File.get-X-Space methods.
 * @key randomness
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FilePermission;
import java.io.InputStreamReader;
import java.io.IOException;
import java.security.Permission;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.out;

public class GetXSpace {

    private static SecurityManager [] sma = { null, new Allow(), new DenyFSA(),
                                              new DenyRead() };

    private static final String osName = System.getProperty("os.name");
    // FileSystem Total Used Available Use% MountedOn
    private static final Pattern dfPattern = Pattern.compile("([^\\s]+)\\s+(\\d+)\\s+\\d+\\s+(\\d+)\\s+\\d+%\\s+([^\\s].*)\n");

    private static int fail = 0;
    private static int pass = 0;
    private static Throwable first;

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
        private static final long KSIZE = 1024;
        private final String name;
        private final long total;
        private final long free;

        Space(String total, String free, String name) {
            try {
                this.total = Long.valueOf(total) * KSIZE;
                this.free = Long.valueOf(free) * KSIZE;
            } catch (NumberFormatException x) {
                throw new RuntimeException("the regex should have caught this", x);
            }
            this.name = name;
        }

        String name() { return name; }
        long total() { return total; }
        long free() { return free; }
        boolean woomFree(long freeSpace) {
            return ((freeSpace >= (free / 10)) && (freeSpace <= (free * 10)));
        }
        public String toString() {
            return String.format("%s (%d/%d)", name, free, total);
        }
    }

    private static ArrayList<Space> space(String f) throws IOException {
        ArrayList<Space> al = new ArrayList<>();

        String cmd = "df -k -P" + (f == null ? "" : " " + f);
        StringBuilder sb = new StringBuilder();
        Process p = Runtime.getRuntime().exec(cmd);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String s;
            int i = 0;
            while ((s = in.readLine()) != null) {
                // skip header
                if (i++ == 0) continue;
                sb.append(s).append("\n");
            }
        }
        out.println(sb);

        Matcher m = dfPattern.matcher(sb);
        int j = 0;
        while (j < sb.length()) {
            if (m.find(j)) {
                // swap can change while this test is running
                if (!m.group(1).equals("swap")) {
                    String name = f;
                    if (name == null) {
                        // cygwin's df lists windows path as FileSystem (1st group)
                        name = osName.startsWith("Windows") ? m.group(1) : m.group(4);
                    }
                    al.add(new Space(m.group(2), m.group(3), name));;
                }
                j = m.end() + 1;
            } else {
                throw new RuntimeException("unrecognized df output format: "
                                           + "charAt(" + j + ") = '"
                                           + sb.charAt(j) + "'");
            }
        }

        if (al.size() == 0) {
            // df did not produce output
            String name = (f == null ? "" : f);
            al.add(new Space("0", "0", name));
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

        out.format("%s:%n", s.name());
        String fmt = "  %-4s total= %12d free = %12d usable = %12d%n";
        out.format(fmt, "df", s.total(), 0, s.free());
        out.format(fmt, "getX", ts, fs, us);

        // if the file system can dynamically change size, this check will fail
        if (ts != s.total()) {
            fail(s.name(), s.total(), "!=", ts);
        } else {
            pass();
        }

        // unix df returns statvfs.f_bavail
        long tsp = (!osName.startsWith("Windows") ? us : fs);
        if (!s.woomFree(tsp)) {
            fail(s.name(), s.free(), "??", tsp);
        } else {
            pass();
        }

        if (fs > s.total()) {
            fail(s.name(), s.total(), ">", fs);
        } else {
            pass();
        }

        if (us > s.total()) {
            fail(s.name(), s.total(), ">", us);
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

    private static void testFile(String dirName) {
        out.format("--- Testing %s%n", dirName);
        ArrayList<Space> l;
        try {
            l = space(dirName);
        } catch (IOException x) {
            throw new RuntimeException(dirName + " can't get file system information", x);
        }
        compare(l.get(0));
    }

    private static void testDF() {
        out.println("--- Testing df");
        // Find all of the partitions on the machine and verify that the size
        // returned by "df" is equivalent to File.getXSpace() values.
        ArrayList<Space> l;
        try {
            l = space(null);
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
            for (var s : l) {
                if (sm instanceof Deny) {
                    tryCatch(s);
                } else {
                    compare(s);
                    compareZeroNonExist();
                    compareZeroExist();
                }
            }
        }
    }

    public static void main(String [] args) {
        if (args.length > 0) {
            testFile(args[0]);
        } else {
            testDF();
        }

        if (fail != 0) {
            throw new RuntimeException((fail + pass) + " tests: "
                                       + fail + " failure(s), first", first);
        } else {
            out.format("all %d tests passed%n", fail + pass);
        }
    }
}
