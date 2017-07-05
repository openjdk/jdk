/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 6334003 6440786
 * @summary Test ability to write and read zip files that have no entries.
 * @author Dave Bristor
 */

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class TestEmptyZip {
    public static void realMain(String[] args) throws Throwable {
        String zipName = "foo.zip";
        File f = new File(System.getProperty("test.scratch", "."), zipName);
        if (f.exists() && !f.delete()) {
            throw new Exception("failed to delete " + zipName);
        }

        // Verify 0-length file cannot be read
        f.createNewFile();
        ZipFile zf = null;
        try {
            zf = new ZipFile(f);
            fail();
        } catch (Exception ex) {
            check(ex.getMessage().contains("zip file is empty"));
        } finally {
            if (zf != null) {
                zf.close();
            }
        }

        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(f));
            ZipEntry ze = zis.getNextEntry();
            check(ze == null);
        } catch (Exception ex) {
            unexpected(ex);
        } finally {
            if (zis != null) {
                zis.close();
            }
        }

        f.delete();

        // Verify 0-entries file can be written
        write(f);

        // Verify 0-entries file can be read
        readFile(f);
        readStream(f);

        f.delete();
    }

    static void write(File f) throws Exception {
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new FileOutputStream(f));
            zos.finish();
            zos.close();
            pass();
        } catch (Exception ex) {
            unexpected(ex);
        } finally {
            if (zos != null) {
                zos.close();
            }
        }
    }

    static void readFile(File f) throws Exception {
        ZipFile zf = null;
        try {
            zf = new ZipFile(f);

            Enumeration e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) e.nextElement();
                fail();
            }
            zf.close();
            pass();
        } catch (Exception ex) {
            unexpected(ex);
        } finally {
            if (zf != null) {
                zf.close();
            }
        }
    }

    static void readStream(File f) throws Exception {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(f));
            ZipEntry ze = zis.getNextEntry();
            check(ze == null);
            byte[] buf = new byte[1024];
            check(zis.read(buf, 0, 1024) == -1);
        } finally {
            if (zis != null) {
                zis.close();
            }
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static boolean pass() {passed++; return true;}
    static boolean fail() {failed++; Thread.dumpStack(); return false;}
    static boolean fail(String msg) {System.out.println(msg); return fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static boolean check(boolean cond) {if (cond) pass(); else fail(); return cond;}
    static boolean equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) return pass();
        else return fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
