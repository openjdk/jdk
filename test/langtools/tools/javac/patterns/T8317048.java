/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8317048
 * @summary VerifyError with unnamed pattern variable and more than one components
 * @enablePreview
 * @compile T8317048.java
 * @run main T8317048
 */

public class T8317048 {
    record Tuple<T1, T2>(T1 first, T2 second) {}
    record R1<T>(Integer value) implements R<T> {}
    record R2<T>(Integer value) implements R<T> {}
    sealed interface R<T> {}

    static <T1 extends Comparable, T2 extends Comparable> int meth1(Tuple<R<T1>, R<T2>> o) {
        return switch (o) {
            case Tuple<R<T1>, R<T2>>(R1<T1> _, R1<T2> _) -> -1;
            case Tuple<R<T1>, R<T2>>(R1<T1> _, R2<T2> _) -> -1;
            case Tuple<R<T1>, R<T2>>(R2<T1> _, R1<T2> _) -> -1;
            case Tuple<R<T1>, R<T2>>(R2<T1> fst, R2<T2> snd) -> fst.value().compareTo(snd.value());
        };
    }

    static <T1 extends Comparable, T2 extends Comparable> int meth2(Tuple<R<T1>, R<T2>> o) {
        return switch (o) {
            case Tuple<R<T1>, R<T2>>(R1<T1> _, R1<T2> _) -> -1;
            case Tuple<R<T1>, R<T2>>(R1<T1> fst, R2<T2> snd) -> fst.value().compareTo(snd.value());
            case Tuple<R<T1>, R<T2>>(R2<T1> _, R1<T2> _) -> -1;
            case Tuple<R<T1>, R<T2>>(R2<T1> fst, R2<T2> snd) -> fst.value().compareTo(snd.value());
        };
    }

    static <T1 extends Comparable, T2 extends Comparable> int meth3(Tuple<R<T1>, R<T2>> o) {
        return switch (o) {
            case Tuple<R<T1>, R<T2>>(R1<T1> fst, R1<T2> _) -> fst.value();
            case Tuple<R<T1>, R<T2>>(R1<T1> _, R2<T2> snd) -> snd.value();
            case Tuple<R<T1>, R<T2>>(R2<T1> _, R1<T2> _) -> -1;
            case Tuple<R<T1>, R<T2>>(R2<T1> fst, R2<T2> snd) -> fst.value().compareTo(snd.value());
        };
    }

    static <T1 extends Comparable, T2 extends Comparable> int meth4(Tuple<R<T1>, R<T2>> o) {
        return switch (o) {
            case Tuple<R<T1>, R<T2>>(R1<T1> _, R1<T2> _) -> -1;
            case Tuple<R<T1>, R<T2>>(R1<T1> _, R2<T2> _) -> -1;
            case Tuple<R<T1>, R<T2>>(R2<T1> fst, R2<T2> snd) -> fst.value().compareTo(snd.value());
            case Tuple<R<T1>, R<T2>>(R2<T1> _, R1<T2> _) -> -1;
        };
    }

    public static void main(String[] args) {
        assertEquals(1, meth1(new Tuple<R<Integer>, R<Integer>>(new R2<>(2), new R2<>(1))));
        assertEquals(1, meth2(new Tuple<R<Integer>, R<Integer>>(new R1<>(2), new R2<>(1))));
        assertEquals(0, meth2(new Tuple<R<Integer>, R<Integer>>(new R2<>(1), new R2<>(1))));
        assertEquals(2, meth3(new Tuple<R<Integer>, R<Integer>>(new R1<>(2), new R1<>(1))));
        assertEquals(3, meth3(new Tuple<R<Integer>, R<Integer>>(new R1<>(2), new R2<>(3))));
        assertEquals(1, meth4(new Tuple<R<Integer>, R<Integer>>(new R2<>(2), new R2<>(1))));
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}