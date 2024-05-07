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
 * @bug 8315735
 * @summary Verify valid classfile is produced when synchronized block is used
 *          inside a switch expression.
 * @compile ExpressionSwitchSynchronized.java
 * @run main ExpressionSwitchSynchronized
 */
public class ExpressionSwitchSynchronized {

    public static void main(String... args) {
        int i1 = 2 + switch (args.length) {
            default -> {
                synchronized (args) {
                    yield 1;
                }
            }
        };
        if (i1 != 3) {
            throw new AssertionError("Incorrect value, got: " + i1 +
                                     ", expected: " + 3);
        }
        int i2 = 2 + switch (args) {
            case String[] a -> {
                synchronized (args) {
                    yield a.length + 1;
                }
            }
        };
        if (i2 != 3) {
            throw new AssertionError("Incorrect value, got: " + i2 +
                                     ", expected: " + 3);
        }
    }

}