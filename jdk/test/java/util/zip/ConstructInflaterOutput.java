/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4679743 8148624
 * @summary Test parts of InflaterOutputStream code that don't really do I/O.
 */

import java.io.*;
import java.util.zip.*;

public class ConstructInflaterOutput {

    static class MyInflater extends Inflater {
        private boolean ended = false;
        boolean getEnded() { return ended; }
        public void end() {
            fail("MyInflater had end() called");
            super.end();
        }
    }

    private static MyInflater inf = new MyInflater();

    public static void realMain(String[] args) throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InflaterOutputStream ios = null;
        byte[] b = new byte[512];

        // Check construction
        //
        try {
            ios = new InflaterOutputStream(null);
            fail();
        } catch (NullPointerException ex) {
            pass();
        }

        try {
            ios = new InflaterOutputStream(baos, null);
            fail();
        } catch (NullPointerException ex) {
            pass();
        }

        try {
            ios = new InflaterOutputStream(baos, inf, 0);
            fail();
        } catch (IllegalArgumentException ex) {
            pass();
        }

        // Check sanity checks in write methods
        //
        ios = new InflaterOutputStream(baos, inf);

        try {
            ios.write(null, 5, 2);
            fail();
        } catch (NullPointerException ex) {
            pass();
        }

        try {
            ios.write(b, -1, 0);
            fail();
        } catch (IndexOutOfBoundsException ex) {
            pass();
        }

        try {
            ios.write(b, 0, -1);
            fail();
        } catch (IndexOutOfBoundsException ex) {
            pass();
        }

        try {
            ios.write(b, 0, 600);
            fail();
        } catch (IndexOutOfBoundsException ex) {
            pass();
        }

        ios.flush();
        check(!inf.getEnded());
        ios.flush();
        check(!inf.getEnded());
        ios.finish();
        check(!inf.getEnded());
        ios.close();
        check(!inf.getEnded());
        try {
            ios.finish();
            fail();
        } catch (IOException ex) {
            pass();
        }
        try {
            ios.write(13);
            fail();
        } catch (IOException ex) {
            pass();
        }

        ios = new InflaterOutputStream(baos);
        ios.flush();
        ios.finish();
        ios.close();
        try {
            ios.flush();
        } catch (IOException ex) {
            pass();
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
