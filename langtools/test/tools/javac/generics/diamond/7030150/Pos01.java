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

/*
 * @test
 * @bug 7030150
 * @summary Type inference for generic instance creation failed for formal type parameter
 *          check that redundant type-arguments on non-generic constructor are accepted
 * @compile Pos01.java
 */

class Pos01 {

    static class Foo<X> {
        Foo(X t) {}
    }

    Foo<Integer> fi1 = new Foo<>(1);
    Foo<Integer> fi2 = new Foo<Integer>(1);
    Foo<Integer> fi3 = new <String> Foo<>(1);
    Foo<Integer> fi4 = new <String> Foo<Integer>(1);
    Foo<Integer> fi5 = new <String, String> Foo<>(1);
    Foo<Integer> fi6 = new <String, String> Foo<Integer>(1);
}
