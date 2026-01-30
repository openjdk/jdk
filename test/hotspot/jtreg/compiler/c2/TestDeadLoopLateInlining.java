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
 * @bug 8375694
 * @summary C2: Dead loop constructed with CastPP in late inlining
 * @requires vm.debug
 * @run main TestDeadLoopLateInlining
 * @run main/othervm -XX:+AlwaysIncrementalInline -XX:CompileOnly=TestDeadLoopLateInlining::test1 -Xcomp TestDeadLoopLateInlining
 */
public class TestDeadLoopLateInlining {
    private static Object fieldObject;
    private static int field;

    public static void main(String[] args) {
        Object o = new Object();
        test1(0, true);
    }

    private static Object test1(int j, boolean flag) {
        if (j < 42) {
            if (flag) {
                field = 42;
            }
            Object o = fieldObject;
            if (j >= 42) {
                for (int i = 1; i < 10; ) {
                    boolean boolRes = lateInlined2();
                    if (boolRes) {
                        i *= 2;
                        o = lateInlined1(o);
                        if (o == null) {
                            throw new RuntimeException();
                        }
                    } else {
                        i++;
                    }
                }
            }
            return o;
        }
        return null;
    }

    private static boolean lateInlined2() {
        return true;
    }

    private static Object lateInlined1(Object o) {
        return o;
    }
}
