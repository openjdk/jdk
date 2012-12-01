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

/**
 * @test
 * @bug 8003280
 * @summary Add lambda tests
 *  This test is for overloaded methods, verify that the specific method is
             selected when type inference occurs
 * @compile InferenceTest5.java
 * @run main InferenceTest5
 */

import java.util.List;
import java.io.File;

public class InferenceTest5 {

    private static void assertTrue(boolean b) {
        if(!b)
            throw new AssertionError();
    }

    public static void main(String[] args) {
        InferenceTest5 test = new InferenceTest5();
        int n = test.method1((a, b) -> {} );
        assertTrue(n == 1);

        n = test.method1(() -> null);
        assertTrue(n == 2);

        n = test.method1(a -> null);
        assertTrue(n == 3);

        n = test.method1(a -> {});
        assertTrue(n == 4);

        n = test.method1(() -> {});
        assertTrue(n == 5);

        n = test.method1((a, b) -> 0);
        assertTrue(n == 6);

        n = test.method1((a, b) -> null);
        assertTrue(n == 6);

        n = test.method1((a, b) -> null, (a, b) -> null);
        assertTrue(n == 7);
    }

    int method1(SAM1<String> s) {
        return 1;
    }

    int method1(SAM2 s) {
        return 2;
    }

    int method1(SAM3 s) {
        return 3;
    }

    int method1(SAM4 s) {
        return 4;
    }

    int method1(SAM5 s) {
        return 5;
    }

    int method1(SAM6<?, ? super Integer> s) {
        return 6;
    }

    int method1(SAM6<?, ?>... s) {
        return 7;
    }

    static interface SAM1<T> {
        void foo(List<T> a, List<T> b);
    }

    static interface SAM2 {
        List<String> foo();
    }

    static interface SAM3 {
        String foo(int a);
    }

    static interface SAM4 {
        void foo(List<File> a);
    }

    static interface SAM5 {
        void foo();
    }

    static interface SAM6<T, V> {
        V get(T t, T t2);
    }
}
