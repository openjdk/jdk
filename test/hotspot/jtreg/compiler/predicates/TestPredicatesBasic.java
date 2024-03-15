/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package compiler.predicates;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8325451
 * @summary Test basic loop predicates
 * @library /test/lib /
 * @run driver compiler.predicates.TestPredicatesBasic
 */
public class TestPredicatesBasic {
    public static final int size = 100;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    private void blackhole(int i) {}

    @DontInline
    private int[] getArr() {
        int[] arr = new int[size];
        for (int i = 0; i < size; ++i) {
            arr[i] = i;
        }
        return arr;
    }

    @Test
    // Null check, loop entrance check, array upper bound check
    @IR(counts = {IRNode.IF, "3"})
    public void basic() {
        int[] arr = getArr();
        for (int i = 0; i < arr.length; ++i) {
            blackhole(arr[i]);
        }
    }

    @Test
    // Null check, loop entrance check, array upper bound check
    @IR(counts = {IRNode.IF, "4"})
    public void basicMinus() {
        int[] arr = getArr();
        for (int i = 0; i < arr.length - 1; ++i) {
            blackhole(arr[i]);
        }
    }

    @Test
    // Null check, loop entrance check, array lower/upper bound check
    @IR(counts = {IRNode.IF, "4"})
    public void basicNeg() {
        int[] arr = getArr();
        for (int i = arr.length - 1; i >= 0; --i) {
            blackhole(arr[i]);
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    // Null check, loop entrance check, array lower/upper bound check
    @IR(counts = {IRNode.IF, "4"})
    public void basicLimit(int limit) {
        int[] arr = getArr();
        for (int i = 0; i < limit; ++i) {
            blackhole(arr[i]);
        }
    }
}

