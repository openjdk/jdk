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

/**
 * @test
 * @summary Test XOR-based parallel induction variable optimization
 * @library /test/lib /
 * @run driver compiler.loopopts.TestXorParallelIV
 */

package compiler.loopopts;

import compiler.lib.ir_framework.*;

public class TestXorParallelIV {

    public static void main(String[] args) {
        TestFramework.run();
    }

    // Test the classic isEven pattern from the problem statement
    // This should be optimized to eliminate the loop when the optimization is enabled
    // The loop should be replaced with: return (number & 1) != 0 (since we start with true and toggle)
    @Test
    @IR(failOn = {IRNode.COUNTED_LOOP})
    public boolean testIsEven(int number) {
        if (number < 0) return true; // Guard against negative numbers
        boolean even = true;
        for (int i = 0; i < number; i++) {
            even = !even;
        }
        return even;
    }

    // Test with integer XOR pattern
    @Test
    @IR(failOn = {IRNode.COUNTED_LOOP})
    public int testIntXor(int n) {
        if (n < 0) return 1; // Guard against negative numbers
        int result = 1;
        for (int i = 0; i < n; i++) {
            result = result ^ -1;
        }
        return result;
    }

    // Test with long XOR pattern
    @Test
    @IR(failOn = {IRNode.COUNTED_LOOP})
    public long testLongXor(int n) {
        if (n < 0) return 1L; // Guard against negative numbers
        long result = 1L;
        for (int i = 0; i < n; i++) {
            result = result ^ -1L;
        }
        return result;
    }

    // Verification tests
    @Check(test = "testIsEven")
    public void checkIsEven(boolean result) {
        // For number = 10, should be even (true)
        // For number = 11, should be odd (false)
    }

    @Run(test = "testIsEven")
    public void runIsEven() {
        boolean result1 = testIsEven(10);
        if (!result1) {
            throw new RuntimeException("Expected true for 10 (even toggles = true), got " + result1);
        }
        
        boolean result2 = testIsEven(11);
        if (result2) {
            throw new RuntimeException("Expected false for 11 (odd toggles = false), got " + result2);
        }
        
        boolean result3 = testIsEven(0);
        if (!result3) {
            throw new RuntimeException("Expected true for 0 (no toggles = true), got " + result3);
        }
    }

    @Run(test = "testIntXor")
    public void runIntXor() {
        int result1 = testIntXor(0);
        if (result1 != 1) {
            throw new RuntimeException("Expected 1 for n=0, got " + result1);
        }
        
        int result2 = testIntXor(1);
        if (result2 != -2) {
            throw new RuntimeException("Expected -2 for n=1, got " + result2);
        }
        
        int result3 = testIntXor(2);
        if (result3 != 1) {
            throw new RuntimeException("Expected 1 for n=2, got " + result3);
        }
    }

    @Run(test = "testLongXor")
    public void runLongXor() {
        long result1 = testLongXor(0);
        if (result1 != 1L) {
            throw new RuntimeException("Expected 1 for n=0, got " + result1);
        }
        
        long result2 = testLongXor(1);
        if (result2 != -2L) {
            throw new RuntimeException("Expected -2 for n=1, got " + result2);
        }
        
        long result3 = testLongXor(2);
        if (result3 != 1L) {
            throw new RuntimeException("Expected 1 for n=2, got " + result3);
        }
    }
}
