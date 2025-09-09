/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8356989
 * @summary Unexpected null in C2 compiled code
 * @run main/othervm -XX:-BackgroundCompilation TestArrayCopySameSrcDstInitializesNonEscapingArray
 * @run main TestArrayCopySameSrcDstInitializesNonEscapingArray
 */

 public class TestArrayCopySameSrcDstInitializesNonEscapingArray {
    private static volatile int volatileField;

    public static void main(String[] args) {
        Object obj = new Object();
        for (int i = 0; i < 20_000; i++) {
            test1(obj);
        }
    }

    private static void test1(Object obj) {
        A a = new A();
        Object[] array = new Object[2];
        array[0] = obj;
        a.field = array;
        System.arraycopy(array, 0, array, 1, 1);
        if (a.field[1] == null) {
            throw new RuntimeException("Can't be null");
        }
    }

    private static class A {
        Object[] field;

        public A() {
            field = null;
        }
    }
}
