/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8389071
 * @summary javac OutOfMemoryError when computing lub for types with self-referential
 *          witness-typed interfaces
 * @compile T8389071.java
 */

import java.util.Arrays;

class T8389071 {
    private interface I<X, IWitness extends I<?, IWitness>> {}
    private interface J<X, JWitness extends J<?, JWitness>> {}
    private interface K<X, KWitness extends K<?, KWitness>> {}
    private interface L<X, LWitness extends L<?, LWitness>> {}
    private interface M<X, MWitness extends M<?, MWitness>> {}
    private interface N<X, NWitness extends N<?, NWitness>> {}

    private static class ConsStruct {
        private static class Empty extends ConsStruct {}
        private static class Cons<X, Y extends ConsStruct> extends ConsStruct {}
    }

    private static class A6<X> extends ConsStruct.Cons<X, ConsStruct.Empty> implements
            I<X, A6<?>>, J<X, A6<?>>, K<X, A6<?>>, L<X, A6<?>>, M<X, A6<?>>, N<X, A6<?>> {}
    private static class B6<X, Y> extends ConsStruct.Cons<X, A6<Y>> implements
            I<Y, B6<X, ?>>, J<Y, B6<X, ?>>, K<Y, B6<X, ?>>, L<Y, B6<X, ?>>, M<Y, B6<X, ?>>, N<Y, B6<X, Y>> {}
    private static class C6<X, Y, Z> extends ConsStruct.Cons<X, B6<Y, Z>> implements
            I<Z, C6<X, Y, ?>>, J<Z, C6<X, Y, ?>>, K<Z, C6<X, Y, ?>>, L<Z, C6<X, Y, ?>>, M<Z, C6<X, Y, ?>>, N<Z, C6<X, Y, ?>> {}

    void foo() {
        A6<Boolean> a = new A6<>();
        B6<Boolean, Integer> b = new B6<>();
        C6<Boolean, Integer, String> c = new C6<>();
        java.util.List<ConsStruct.Cons<Boolean, ? extends ConsStruct>> list =
                Arrays.asList(a, b, c);
    }
}
