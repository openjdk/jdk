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
 * @bug 8313345
 * @summary Test SuperWord with a CMove that does not have a matching bool pack.
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-TieredCompilation -XX:CompileCommand=compileonly,*TestCMoveWithoutBoolPack*::fill -Xbatch
 *                   compiler.vectorization.TestCMoveWithoutBoolPack
 * @run main/othervm -XX:-TieredCompilation -XX:CompileCommand=compileonly,*TestCMoveWithoutBoolPack*::fill -Xbatch
 *                   -XX:+UseCMoveUnconditionally
 *                   compiler.vectorization.TestCMoveWithoutBoolPack
 */

package compiler.vectorization;

public class TestCMoveWithoutBoolPack {

    public static void main(String[] args) {
        A a = new A();
        B b = new B();
        double[] c = new double[1000];
        for (int i = 0; i < 1000; i++) {
            a.fill(c);
            b.fill(c);
        }
    }

    public static class A {
        void fill(double[] array) {
            for (int i = 0; i < array.length; ++i) {
                array[i] = this.transform(array[i]);
            }
        }

        public double transform(double value) {
            return value * value;
        }
    }

    public static class B extends A {
        public double transform(double value) {
            return value;
        }
    }
}
