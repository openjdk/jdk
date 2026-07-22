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
package compiler.controldependency;

/*
 * @test
 * @bug 8385420
 * @summary C2 correctly handles the case when the removed CastPPNode has a CMove use.
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:CompileOnly=${test.main.class}::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM ${test.main.class}
 *
 */
public class TestRemoveCastPPWithCMoveUse {
    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test(null, false);
            test(null, true);
            test("", false);
            test("", true);
        }
    }

    static int test(String a, boolean flag) {
        StringBuilder sb = new StringBuilder();
        if (a == null) {
            sb.append("");
        } else {
            sb.append(flag ? a : "");
        }
        return sb.length();
    }
}
