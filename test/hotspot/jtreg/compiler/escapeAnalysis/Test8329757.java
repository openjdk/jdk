/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8329757
 * @summary Deoptimization with nested eliminated and not eliminated locks
 *          caused reordered lock stacks. This can be handled by the interpreter
 *          but when a frame is migrated back to compiled code via OSR the C2
 *          assumption about balanced monitorenter-monitorexit is broken.
 *
 * @requires vm.compMode != "Xint"
 *
 * @run main/othervm compiler.escapeAnalysis.Test8329757
 */

package compiler.escapeAnalysis;

public class Test8329757 {

    int a = 400;
    Double ddd;

    void q() {
        int e;
        synchronized (new Double(1.1f)) {
        int[] f = new int[a];
        synchronized (Test8329757.class) {
            for (int d = 4; d < 127; d++) {
            e = 13;
            do switch (d * 5) {
                case 0:
                case 42:
                case 29:
                e = d;
                default:
                f[1] = e;
            } while (--e > 0);
            }
        }
        }
    }

    void n() {
        for (int j = 6; j < 274; ++j) q();
    }

    public static void main(String[] args) {
        Test8329757 r = new Test8329757();
        for (int i = 0; i < 1000; i++) r.n();
    }
}
