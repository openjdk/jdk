/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8271459
 * @requires vm.compiler2.enabled
 * @summary C2 applies string opts to StringBuilder object created with a negative size and misses the NegativeArraySizeException.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.stringopts.TestNegativeArraySize
 */

package compiler.c2.irTests.stringopts;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestNegativeArraySize {

    static int iFld;

    public static void main(String[] args) {
        // Dont inline any StringBuilder methods for this IR test to check if string opts are applied or not.
        TestFramework.runWithFlags("-XX:CompileCommand=dontinline,java.lang.StringBuilder::*");
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString", IRNode.INTRINSIC_TRAP})
    static String positiveConst() {
        // C2 knows that argument is 5 and applies string opts without runtime check.
        StringBuilder sb = new StringBuilder(5); // StringBuilder object optimized away by string opts.
        return sb.toString(); // Call optimized away by string opts.
    }

    @Test
    @IR(counts = {IRNode.ALLOC_OF, "StringBuilder", "1", IRNode.CALL_OF_METHOD, "toString", "1"})
    @IR(failOn = IRNode.INTRINSIC_TRAP) // No runtime check, we bail out of string opts
    static String negativeConst() {
        StringBuilder sb = new StringBuilder(-5); // C2 knows that we always have a negative int -> bail out of string opts
        return sb.toString(); // Call stays due to bailout.
    }

    @Run(test = "negativeConst")
    static void runNegativeConst() {
        try {
            negativeConst();
            Asserts.fail("should have thrown exception");
        } catch (NegativeArraySizeException e) {
            // Expected;
        }
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    @IR(counts = {IRNode.INTRINSIC_TRAP, "1"}) // Uncommon trap of runtime check
    static String positiveFld() {
        // C2 does not know if iFld is positive or negative. It applies string opts but inserts a runtime check to
        // bail out to interpreter. This path, however, is never taken because iFld is always positive.
        StringBuilder sb = new StringBuilder(iFld);
        return sb.toString();
    }

    @Run(test = "positiveFld")
    static void runPositiveFld() {
        iFld = 4;
        positiveFld();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    @IR(counts = {IRNode.INTRINSIC_TRAP, "1"}) // Uncommon trap of runtime check
    static String negativeFld() {
        // C2 does not know if iFld is positive or negative. It applies string opts but inserts a runtime check to
        // bail out to interpreter. This path is always taken because iFld is always negative.
        StringBuilder sb = new StringBuilder(iFld);
        return sb.toString();
    }

    @Run(test = "negativeFld")
    static void runNegativeFld() {
        iFld = -4;
        try {
            negativeFld();
            Asserts.fail("should have thrown exception");
        } catch (NegativeArraySizeException e) {
            // Expected;
        }
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    @IR(counts = {IRNode.INTRINSIC_TRAP, "1"}) // Uncommon trap of runtime check
    static String maybeNegativeConst(boolean flag) {
        // C2 knows that cap is between -5 and 5. It applies string opts but inserts a runtime check to
        // bail out to interpreter. This path is sometimes taken and sometimes not.
        int cap = flag ? 5 : -5;
        StringBuilder sb = new StringBuilder(cap);
        return sb.toString();
    }

    @Run(test = "maybeNegativeConst")
    static void runMaybeNegativeConst() {
        boolean flag = TestFramework.toggleBoolean();
        try {
            maybeNegativeConst(flag);
            Asserts.assertTrue(flag);
        } catch (NegativeArraySizeException e) {
            Asserts.assertFalse(flag);
        }
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString", IRNode.INTRINSIC_TRAP})
    static String alwaysPositiveConst(boolean flag) {
        // C2 knows that cap is between 1 and 100 and applies string opts without runtime check.
        int cap = flag ? 1 : 100;
        StringBuilder sb = new StringBuilder(cap); // Object optimized away.
        return sb.toString(); // Optimized away.
    }

    @Run(test = "alwaysPositiveConst")
    static void runAlwaysPositiveConst() {
        alwaysPositiveConst(TestFramework.toggleBoolean());
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    @IR(counts = {IRNode.INTRINSIC_TRAP, "1"}) // Uncommon trap of runtime check
    static String negativeArg(int cap) {
        // C2 does not know if cap is positive or negative. It applies string opts but inserts a runtime check to
        // bail out to interpreter. This path is always taken because cap is always negative.
        StringBuilder sb = new StringBuilder(cap);
        return sb.toString();
    }

    @Run(test = "negativeArg")
    static void runNegativeArg() {
        try {
            negativeArg(-5);
            Asserts.fail("should have thrown exception");
        } catch (NegativeArraySizeException e) {
            // Expected
        }
    }

    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "StringBuilder", IRNode.CALL_OF_METHOD, "toString"})
    @IR(counts = {IRNode.INTRINSIC_TRAP, "1"}) // Uncommon trap of runtime check
    static String positiveArg(int cap) {
        // C2 does not know if cap is positive or negative. It applies string opts but inserts a runtime check to
        // bail out to interpreter. This path, however, is never taken because cap is always positive.
        StringBuilder sb = new StringBuilder(cap);
        return sb.toString();
    }

    @Run(test = "positiveArg")
    static void runPositiveArg() {
        positiveArg(5);
    }
}
