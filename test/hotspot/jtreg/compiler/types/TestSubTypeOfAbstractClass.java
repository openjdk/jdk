/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8316533
 * @summary Oop of abstract class A with no subclass is subtype checked after null-check
 * @run driver compiler.types.TestSubTypeOfAbstractClass
 */

/**
 * @test
 * @bug 8316533
 * @summary Oop of abstract class A is subtype checked after null-check
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:CompileCommand=compileonly,*A::test
 *                   -Xcomp -XX:+IgnoreUnrecognizedVMOptions -XX:+StressReflectiveCode
 *                   compiler.types.TestSubTypeOfAbstractClass
 */

package compiler.types;

public class TestSubTypeOfAbstractClass {

    abstract class A {
        public static A get_null() {
            return null;
        }

        public static boolean test() {
            // NullCheck -> CastPP with type A:NotNull
            // But A is abstract with no subclass, hence this type is impossible
            return get_null() instanceof A;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++ ) {
            A.test();
        }
    }
}
