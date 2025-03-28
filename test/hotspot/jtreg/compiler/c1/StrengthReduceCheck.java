/*
 * Copyright (c) 2024 IBM Corporation. All rights reserved.
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
 * @author Amit Kumar
 * @bug 8344026
 * @library /test/lib
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xcomp -Xbatch -XX:TieredStopAtLevel=1 compiler.c1.StrengthReduceCheck
 */

package compiler.c1;

import jdk.test.lib.Asserts;

public class StrengthReduceCheck {

    static int test1(int x) {
        // Multiply by 2 ^ 30 - 1
        x = x * 1073741823;
        return x;
    }

    static int test2(int x) {
        // Multiply by 2 ^ 30 + 1
        x = x * 1073741825;
        return x;
    }

    static int test3(int x) {
        // Multiply by INT_MIN
        x = x * -2147483648;
        return x;
    }

    static int test4(int x) {
        x = x * -1;
        return x;
    }

    public static void main(String[] args) {
        for (int i =0; i < 1000; ++i) {
            Asserts.assertEQ(test1(26071999), -1099813823);
            Asserts.assertEQ(test2(26071999), -1047669825);
            Asserts.assertEQ(test3(26071999), -2147483648);
            Asserts.assertEQ(test4(26071999), -26071999);
        }
    }
}

