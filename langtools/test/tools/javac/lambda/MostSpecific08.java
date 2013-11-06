/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8008813
 * @summary Structural most specific fails when method reference is passed to overloaded method
 * @compile/fail/ref=MostSpecific08.out -XDrawDiagnostics MostSpecific08.java
 */
class MostSpecific08 {

    static class C {
        int getInt() { return -1; }
        Integer getInteger() { return -1; }
    }

    interface IntResult { }
    interface ReferenceResult<X> { }

    interface PrimitiveFunction {
        int f(C c);
    }

    interface ReferenceFunction<X> {
        X f(C c);
    }

    interface Tester {
        IntResult apply(PrimitiveFunction p);
        <Z> ReferenceResult<Z> apply(ReferenceFunction<Z> p);
    }

    void testMref(Tester t) {
        IntResult pr = t.apply(C::getInt); //ok - unoverloaded mref
        ReferenceResult<Integer> rr = t.apply(C::getInteger); //ok - unoverloaded mref
    }

    void testLambda(Tester t) {
        IntResult pr1 = t.apply(c->c.getInt()); //ambiguous - implicit
        IntResult pr2 = t.apply((C c)->c.getInt()); //ok
        ReferenceResult<Integer> rr1 = t.apply(c->c.getInteger()); //ambiguous - implicit
        ReferenceResult<Integer> rr2 = t.apply((C c)->c.getInteger()); //ok
    }
}
