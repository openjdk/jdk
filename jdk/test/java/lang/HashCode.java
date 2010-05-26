/*
 * Copyright 2009 Google, Inc.  All Rights Reserved.
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
 * @bug 4245470
 * @summary Test the primitive wrappers hashCode()
 */

import java.util.Random;

public class HashCode {

    final Random rnd = new Random();

    void test(String args[]) throws Exception {
        int[] ints = {
            Integer.MIN_VALUE,
            Short.MIN_VALUE,
            Character.MIN_VALUE,
            Byte.MIN_VALUE,
            -1, 0, 1,
            Byte.MAX_VALUE,
            Character.MAX_VALUE,
            Short.MAX_VALUE,
            Integer.MAX_VALUE,
            rnd.nextInt(),
        };

        for (int x : ints) {
            check(    new Long(x).hashCode() == (int)((long)x ^ (long)x>>>32));
            check(Long.valueOf(x).hashCode() == (int)((long)x ^ (long)x>>>32));
            check(    new Integer(x).hashCode() == x);
            check(Integer.valueOf(x).hashCode() == x);
            check(    new Short((short)x).hashCode() == (short) x);
            check(Short.valueOf((short)x).hashCode() == (short) x);
            check(    new Character((char) x).hashCode() == (char) x);
            check(Character.valueOf((char) x).hashCode() == (char) x);
            check(    new Byte((byte) x).hashCode() == (byte) x);
            check(Byte.valueOf((byte) x).hashCode() == (byte) x);
        }
    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new HashCode().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
