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

/*
 * @test
 * @bug 8003280
 * @summary Add lambda tests
 *  Structural most specific doesn't handle cases with wildcards in functional interfaces
 */
public class MostSpecific04 {

    static int assertionCount = 0;

    static void assertTrue(boolean cond) {
        assertionCount++;
        if (!cond)
            throw new AssertionError();
    }

    interface DoubleMapper<T> {
        double map(T t);
    }

    interface LongMapper<T> {
        long map(T t);
    }

    static class MyList<E> {
        void map(DoubleMapper<? super E> m) { assertTrue(false); }
        void map(LongMapper<? super E> m) { assertTrue(true); }
    }

    public static void main(String[] args) {
        MyList<String> ls = new MyList<String>();
        ls.map(e->e.length());
        assertTrue(assertionCount == 1);
    }
}
