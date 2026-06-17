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

package compiler.loopopts;

/*
 * @test
 * @bug 8386830
 * @summary Test for CountedLoopConverter::filtered_type, where we wrongly
 *          ignored a nullptr type, and returned a type that was too narrow,
 *          which led us to wrongly ignore wrapping in
 *          CountedLoopConverter::has_truncation_wrap
 * @library /test/lib /
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */
public class TestTruncationWrapPhiTypeUnion {

    static final int EXPECTED = 11111;

    interface TestMethod {
        int call();
    }

    public static void main(String[] args) {
        int failures = 0;

        failures += run("test1", () -> test1(-1), EXPECTED);
        failures += run("test2", () -> test2(-1), EXPECTED);

        if (failures > 0) {
            throw new RuntimeException("failures: " + failures);
        }
    }

    static int run(String name, TestMethod t, int expected) {
        for (int i = 0; i < 20; i++) {
            int result = t.call();
            if (result != expected) {
                System.out.println(name + " wrong result: " + result + " vs " + expected);
                return 1;
            }
        }
        return 0;
    }

    static int test1(int limit) {
        int x = 0;
        int sum = 0;

        limit = (byte) limit; // type BYTE = [-128..127], at runtime: -1

        int i = 510;
        while (limit < i) {
            // Exit check checks for positive values, but with
            // entry 510 and unsigned truncation, that can never fail.

            sum++;

            // Secondary exit check, to make sure we exit eventually.
            if (++x >= EXPECTED) {
                break;
            }

            // Unsigned 15-bit truncation.
            // We check for wrap/truncation/underflow:
            //
            //   } else if (stride_con < 0) {
            //     if (truncation.trunc_type()->lo_as_long() - phi_ft->lo_as_long() > stride_con ||
            //         truncation.trunc_type()->hi_as_long() < phi_ft->hi_as_long()) {
            //       return true;  // truncation may occur
            //     }
            //   }
            //
            // The lo of truncation is 0, and also the phi type should have a lo of 0,
            // but it was wrongly determined to be 510.
            // So, whereas "0 - 0 > -10" would have given us the required "true",
            // we now checked "0 - 510 > -10", which was wrongly "false".
            //
            // The reason is that the phi_ft has been wrongly determined to be 510,
            // so only considering the entry value. This is determined inside filtered_type:
            // - entry: filtered_type_from_dominators discovers entry value 510.
            // - backedge: filtered_type_from_dominators discovers no dominating-if, returns nullptr.
            // But filtered_type skips nullptr results, as in "no extra filter". But
            // we should be accumulating the entry and backedge type here!
            //
            // The only if on the backedge-path would have been the exit
            // check: limit < i. But filtered_int_type finds nothing, returns nullptr.
            i = (i - 10) & 0x7fff;
        }

        return sum;
    }

    // Note: this case was first discovered during JDK-8386591, which enabled 0xffff masking.
    //       Without allowing 0xffff masking, this did not fail. But it was the way I
    //       first discovered the bug, and so I wanted to add this as a test anyway.
    static int test2(int limit) {
        int x = 0;
        int sum = 0;

        limit = (short) limit; // type SHORT = [-32768..32767], at runtime: -1

        int i = 510;
        while (limit < i) {
            // Exit check checks for positive values, but with
            // entry 510 and unsigned truncation, that can never fial.

            sum++;

            // Secondary exit check, to make sure we exit eventually.
            if (++x >= 11111) {
                break;
            }

            // CHAR truncation: 0..0xffff = 0..65535
            //
            // i iterates: 510, 500, ... 10, 0
            // And then, it shshould ould underflows: (0 - 10) & 0xffff = 65526
            //
            // But in CountedLoopConverter::has_truncation_wrap, we wrongly
            // decide there cannot be overflow.
            // truncation: [0..65535]
            // stride_con: -10
            //
            // Accordingly, phi_ft should be in [0..65535], and so when we check
            // for underflow, we check:
            //
            //   } else if (stride_con < 0) {
            //     if (truncation.trunc_type()->lo_as_long() - phi_ft->lo_as_long() > stride_con ||
            //         truncation.trunc_type()->hi_as_long() < phi_ft->hi_as_long()) {
            //       return true;  // truncation may occur
            //     }
            //   }
            //
            // So we should check: 0 - 0 > -10, and we would see that truncation could occur.
            // But instead, we checked 0 - 510 > -10, which wronly lead to "no truncation".
            //
            // The reason is that the phi_ft has been wrongly determined to be 510,
            // so only considering the entry value. This is determined inside filtered_type:
            // - entry: filtered_type_from_dominators discovers entry value 510.
            // - backedge: filtered_type_from_dominators discovers no dominating-if, returns nullptr.
            // But filtered_type skips nullptr results, as in "no extra filter". But
            // we should be accumulating the entry and backedge type here!
            //
            // The only if on the backedge-path would have been the exit
            // check: limit < i. But filtered_int_type finds nothing, returns nullptr.
            i = (i - 10) & 0xffff;
        }

        return sum;
    }
}
