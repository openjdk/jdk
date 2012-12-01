/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003280
 * @summary Add lambda tests
 *  check overload resolution and target type inference w.r.t. generic methods
 * @author  Maurizio Cimadamore
 * @run main TargetType02
 */

public class TargetType02 {

    static int assertionCount = 0;

    static void assertTrue(boolean cond) {
        assertionCount++;
        if (!cond)
            throw new AssertionError();
    }

    interface S1<X extends Number> {
        X m(Integer x);
    }

    interface S2<X extends String> {
        abstract X m(Integer x);
    }

    static <Z extends Number> void call(S1<Z> s) { s.m(1); assertTrue(true); }
    static <Z extends String> void call(S2<Z> s) { s.m(2); assertTrue(false); }

    void test() {
        call(i -> { toString(); return i; });
    }

    public static void main(String[] args) {
        new TargetType02().test();
        assertTrue(assertionCount == 1);
    }
}
