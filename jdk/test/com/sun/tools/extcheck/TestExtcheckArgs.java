/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6356642
 * @summary Verify that extcheck exits appropriately when invalid args are given.
 * @run shell TestExtcheckArgs.sh
 * @author Dave Bristor
 */

import java.io.File;
import com.sun.tools.extcheck.Main;

/*
 * Test extcheck by using Runtime.exec instead of invoking
 * com.sun.tools.extcheck.Main.main, since the latter does its own
 * System.exit under the conditions checked here.
 */
public class TestExtcheckArgs {
    public static void realMain(String[] args) throws Throwable {
        String testJar = System.getenv("TESTJAVA") + File.separator
            + "lib" + File.separator + "jconsole.jar";

        verify(new String[] {
               }, Main.INSUFFICIENT);
        verify(new String[] {
                   "-verbose"
               }, Main.MISSING);
        verify(new String[] {
                   "-verbose",
                   "foo"
               }, Main.DOES_NOT_EXIST);
        verify(new String[] {
                   testJar,
                   "bar"
               }, Main.EXTRA);
        verify(new String[] {
                   "-verbose",
                   testJar,
                   "bar"
               }, Main.EXTRA);
    }

    static void verify(String[] args, String expected) throws Throwable {
        try {
            Main.realMain(args);
            fail();
        } catch (Exception ex) {
            if (ex.getMessage().startsWith(expected)) {
                pass();
            } else {
                fail("Unexpected message: " + ex.getMessage());
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
