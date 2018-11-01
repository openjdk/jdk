/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8206986
 * @summary Check switch expressions embedded in switch expressions.
 * @compile --enable-preview -source 12 ExpressionSwitchInExpressionSwitch.java
 * @run main/othervm --enable-preview ExpressionSwitchInExpressionSwitch
 */

public class ExpressionSwitchInExpressionSwitch {
    public static void main(String[] args) {

        int j = 42;
        int i = switch (j) {
            default -> (switch (j) { default -> 0; } )+1;
        };
        if (i!=1) {
            throw new AssertionError("Unexpected result: " + i);
        }
        i = switch (j) {
            default -> {
                int k = switch (j) {
                    default -> {
                        break 42;
                    }
                };
                System.out.println("didn't break to the top level");
                break 43;
            }
        };
        if (i!=43) {
            throw new AssertionError("Unexpected result: " + i);
        }
    }
}
