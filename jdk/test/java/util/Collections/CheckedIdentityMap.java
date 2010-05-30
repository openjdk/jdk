/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6585904
 * @summary Checked collections with underlying maps with identity comparisons
 */

import java.util.*;
import static java.util.Collections.*;

public class CheckedIdentityMap {
    void test(String[] args) throws Throwable {
        Map<Integer, Integer> m1 = checkedMap(
            new IdentityHashMap<Integer, Integer>(),
            Integer.class, Integer.class);
        Map<Integer, Integer> m2 = checkedMap(
            new IdentityHashMap<Integer, Integer>(),
            Integer.class, Integer.class);
        m1.put(new Integer(1), new Integer(1));
        m2.put(new Integer(1), new Integer(1));

        Map.Entry<Integer, Integer> e1 = m1.entrySet().iterator().next();
        Map.Entry<Integer, Integer> e2 = m2.entrySet().iterator().next();
        check(! e1.equals(e2));
        check(e1.hashCode() == hashCode(e1));
        check(e2.hashCode() == hashCode(e2));
    }

    int hashCode(Map.Entry<?,?> e) {
        return (System.identityHashCode(e.getKey()) ^
                System.identityHashCode(e.getValue()));
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
        new CheckedIdentityMap().instanceMain(args);}
    void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
    abstract class F {abstract void f() throws Throwable;}
    void THROWS(Class<? extends Throwable> k, F... fs) {
        for (F f : fs)
            try {f.f(); fail("Expected " + k.getName() + " not thrown");}
            catch (Throwable t) {
                if (k.isAssignableFrom(t.getClass())) pass();
                else unexpected(t);}}
    Thread checkedThread(final Runnable r) {
        return new Thread() {public void run() {
            try {r.run();} catch (Throwable t) {unexpected(t);}}};}
}
