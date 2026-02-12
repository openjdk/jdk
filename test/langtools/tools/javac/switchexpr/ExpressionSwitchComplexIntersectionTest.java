/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8369654
 * @summary javac OutOfMemoryError for complex intersection type
 * @compile ExpressionSwitchComplexIntersectionTest.java
 */

public class ExpressionSwitchComplexIntersectionTest {
    interface WithMixin01<T> {}
    interface WithMixin02<T> {}
    interface WithMixin03<T> {}
    interface WithMixin04<T> {}
    interface WithMixin05<T> {}
    interface WithMixin06<T> {}
    interface WithMixin07<T> {}
    interface WithMixin08<T> {}
    interface WithMixin09<T> {}
    interface WithMixin10<T> {}
    interface WithMixin11<T> {}
    interface WithMixin12<T> {}
    interface WithMixin13<T> {}
    interface WithMixin14<T> {}
    interface WithMixin15<T> {}
    interface WithMixin16<T> {}
    interface WithMixin17<T> {}
    interface WithMixin18<T> {}
    interface WithMixin19<T> {}
    interface WithMixin20<T> {}

    interface ClientA extends
            WithMixin01<ClientA>,
            WithMixin02<ClientA>,
            WithMixin03<ClientA>,
            WithMixin04<ClientA>,
            WithMixin05<ClientA>,
            WithMixin06<ClientA>,
            WithMixin07<ClientA>,
            WithMixin08<ClientA>,
            WithMixin09<ClientA>,
            WithMixin10<ClientA>,
            WithMixin11<ClientA>,
            WithMixin12<ClientA>,
            WithMixin13<ClientA>,
            WithMixin14<ClientA>,
            WithMixin15<ClientA>,
            WithMixin16<ClientA>,
            WithMixin17<ClientA>,
            WithMixin18<ClientA>,
            WithMixin19<ClientA>,
            WithMixin20<ClientA> {
    }

    interface ClientB extends
            WithMixin01<ClientB>,
            WithMixin02<ClientB>,
            WithMixin03<ClientB>,
            WithMixin04<ClientB>,
            WithMixin05<ClientB>,
            WithMixin06<ClientB>,
            WithMixin07<ClientB>,
            WithMixin08<ClientB>,
            WithMixin09<ClientB>,
            WithMixin10<ClientB>,
            WithMixin11<ClientB>,
            WithMixin12<ClientB>,
            WithMixin13<ClientB>,
            WithMixin14<ClientB>,
            WithMixin15<ClientB>,
            WithMixin16<ClientB>,
            WithMixin17<ClientB>,
            WithMixin18<ClientB>,
            WithMixin19<ClientB>,
            WithMixin20<ClientB> {
    }

    Object f1(boolean b, ClientA c1, ClientB c2) {
        return b ? c1 : c2;
    }

    Object f2(boolean b, ClientA[] array1, ClientB[] array2) {
        return b ? array1 : array2;
    }

    <TA extends ClientA, TB extends ClientB> Object f3(boolean b, TA[] array1, TB[] array2) {
        return b ? array1 : array2;
    }

    <TA extends ClientA, TB extends ClientB, TAA extends TA, TBB extends TB> Object f4(boolean b, TAA[] array1, TBB[] array2) {
        return b ? array1 : array2;
    }
}
