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
 * @bug 8343932
 * @summary Error when parsing qualified generic type test pattern in switch
 * @compile T8343932.java
 */
public class T8343932 {
    abstract sealed class J<T1, T2> permits X.S, A {}
    final class A extends J<Integer, Integer> {}

    public class X<T> {
        final class S<U> extends J<T, U> {
            abstract sealed class J<T1, T2> permits XX.SS, AA {}
            final class AA extends J<Integer, Integer> {}

            public class XX<T> {
                final class SS<U> extends J<T, U> {}
            }
        }

        static int test(J<Integer, Integer> ji) {
            return switch (ji) {
                case A a -> 42;
                case X<Integer>.S<Integer> e -> 4200; // level 1
            };
        }

        static int test(X<Integer>.S<Integer>.J<Integer, Integer> ji) {
            return switch (ji) {
                case X<Integer>.S<Integer>.AA a -> 42;
                case X<Integer>.S<Integer>.XX<Integer>.SS<Integer> e -> 4200; // level 2
            };
        }
    }
}