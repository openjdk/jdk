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

/**
 * @test
 * @bug 7177216
 * @summary resulting file of native2ascii should have normal permission
 */

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import sun.tools.native2ascii.Main;

public class Permission {

    private static void cleanup(String... fnames) throws Throwable {
        for (String fname : fnames) {
            Files.deleteIfExists(Paths.get(fname));
        }
    }

    public static void realMain(String[] args) throws Throwable {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            String src = "native2ascii_permtest_src";
            String dst = "native2ascii_permtest_dst";

            cleanup(src, dst);
            try {
                try (FileOutputStream fos = new FileOutputStream(src)) {
                    fos.write('a'); fos.write('b'); fos.write('c');
                }
                String[] n2aArgs = new String[] {"-encoding", "utf8", src, dst};
                if (!new Main().convert(n2aArgs)) {
                    fail("n2a failed.");
                }
                equal(Files.getPosixFilePermissions(Paths.get(src)),
                      Files.getPosixFilePermissions(Paths.get(dst)));
                String[] a2nArgs = new String[] {"-reverse", "-encoding", "utf8", dst, src};
                if (!new Main().convert(a2nArgs)) {
                    fail("a2n failed.");
                }
                equal(Files.getPosixFilePermissions(Paths.get(src)),
                      Files.getPosixFilePermissions(Paths.get(dst)));
            } finally {
                cleanup(src, dst);
            }
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
