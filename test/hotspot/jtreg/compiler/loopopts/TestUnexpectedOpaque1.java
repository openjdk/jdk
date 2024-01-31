/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress randomness
 * @bug 8298520
 * @requires vm.compiler2.enabled
 * @summary Dying subgraph confuses logic that tries to find the OpaqueZeroTripGuard.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.loopopts.TestUnexpectedOpaque1::* -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=1642564308 compiler.loopopts.TestUnexpectedOpaque1
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.loopopts.TestUnexpectedOpaque1::* -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN compiler.loopopts.TestUnexpectedOpaque1
 */

package compiler.loopopts;

public class TestUnexpectedOpaque1 {

    static int cnt = 0;

    static int test() {
        int res = 42;
        for (int i = 500; i > 0; --i) {
            res |= 1;
            if (res != 0) {
                return 43;
            }
            cnt++;
        }
        return Float.floatToIntBits(44F);
    }

    public static void main(String[] args) {
        test();
    }
}
