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

package compiler.ccp;


/*
 * @test
 * @bug 8379555
 * @summary Test that there are no crashed in CCP with DivL.
 * @library /test/lib /
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,${test.main.class}::* -XX:CompileCommand=dontinline,${test.main.class}::test
 *                   ${test.main.class}
 */
public class TestIntegerDivCCP {
    public static void main(String[] strArr) throws Exception {
        for (int i = 0; i < 10; i++) {
            test(10, 20, 30);
        }
    }


    public static long test(long arg0_2173, long arg1_2173, int arg2_2173) {
        arg0_2173 = arg0_2173 | -8796093022215L;
        arg1_2173 = Math.min(arg1_2173, -4398046511102L);
        var div = arg0_2173 / arg1_2173;
        var val = Long.compress(div, Double.doubleToLongBits(arg2_2173));
        return val;
    }
}
