/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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
 *
 */

/**
 * @test
 * @bug 8272570
 * @summary crash in PhaseCFG::global_code_motion
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xbatch TestGCMRecalcPressureNodes
 */

public class TestGCMRecalcPressureNodes {
    public boolean bo0;
    public boolean bo1;
    public void foo() {
        int sh12 = 61;
        for (int i = 0; i < 50; i++) {
            sh12 *= 34;
        }
        Math.tan(1.0);
        bo0 = true;
        bo1 = true;
    }
    public static void main(String[] args) {
        TestGCMRecalcPressureNodes instance = new TestGCMRecalcPressureNodes();
        for (int i = 0; i < 50000; i++) {
            instance.foo();
        }
    }
}

