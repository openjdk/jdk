/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @compile/fail/ref=TargetType02.out -XDrawDiagnostics TargetType02.java
 */

public class TargetType02 {

    interface S1<X extends Number> {
        X m(Integer x);
    }

    interface S2<X extends String> {
        abstract X m(Integer x);
    }

    static <Z extends Number> void call1(S1<Z> s) { }

    static <Z extends String> void call2(S2<Z> s) { }

    static <Z extends Number> void call3(S1<Z> s) { }
    static <Z extends String> void call3(S2<Z> s) { }

    void test() {
        call1(i -> { toString(); return i; });
        call2(i -> { toString(); return i; });
        call3(i -> { toString(); return i; });
    }
}
