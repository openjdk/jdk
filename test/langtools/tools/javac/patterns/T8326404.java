/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8326404
 * @summary Assertion error when trying to compile switch with fallthrough with pattern
 * @compile T8326404.java
 * @run main T8326404
 */
public class T8326404 {
    private static final record R<T>(T a) {}

    public static void main(String[] args) {
        assertEquals(4, run1(""));
        assertEquals(3, run1(new R("")));
        assertEquals(2, run1(new R(42)));

        assertEquals(2, run1_break1(""));
        assertEquals(1, run1_break1(new R("")));
        assertEquals(2, run1_break1(new R(42)));

        assertEquals(3, run2(""));
        assertEquals(4, run2(new R("")));
        assertEquals(2, run2(new R(42)));

        assertEquals(1, run2_break1(""));
        assertEquals(2, run2_break1(new R("")));
        assertEquals(2, run2_break1(new R(42)));

        assertEquals(2, run3(""));
        assertEquals(4, run3(new R("")));
        assertEquals(3, run3(new R(42)));

        assertEquals(2, run3_break1(""));
        assertEquals(2, run3_break1(new R("")));
        assertEquals(1, run3_break1(new R(42)));
    }

    private static int run1(Object o) {
        int i = 0;
        switch (o) {
            case String _:
                i++;
            case R(String _):
                i++;
            case R(Integer _):
                i++;
            default:
                i++;
        }
        return i;
    }

    private static int run1_break1(Object o) {
        int i = 0;
        switch (o) {
            case String s:
                i++;
            case R(String _):
                i++;
                break;
            case R(Integer _):
                i++;
            default:
                i++;
        }
        return i;
    }

    private static int run2(Object o) {
        int i = 0;
        switch (o) {
            case R(String _):
                i++;
            case String _:
                i++;
            case R(Integer _):
                i++;
            default:
                i++;
        }
        return i;
    }

    private static int run2_break1(Object o) {
        int i = 0;
        switch (o) {
            case R(String _):
                i++;
            case String _:
                i++;
                break;
            case R(Integer _):
                i++;
            default:
                i++;
        }
        return i;
    }

    private static int run3(Object o) {
        int i = 0;
        switch (o) {
            case R(String _):
                i++;
            case R(Integer _):
                i++;
            case String _:
                i++;
            default:
                i++;
        }
        return i;
    }

    private static int run3_break1(Object o) {
        int i = 0;
        switch (o) {
            case R(String _):
                i++;
            case R(Integer _):
                i++;
                break;
            case String _:
                i++;
            default:
                i++;
        }
        return i;
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}