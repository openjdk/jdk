/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.hotspot.ir_framework.tests;

import jdk.test.lib.hotspot.ir_framework.Test;
import jdk.test.lib.hotspot.ir_framework.TestFramework;

/*
 * @test
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler2.enabled
 * @summary Sanity test remaining framework property flags.
 * @library /test/lib
 * @run main/othervm -DFlipC1C2=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DExcludeRandom=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DVerifyVM=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DDumpReplay=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DVerbose=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DShuffleTests=false jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DReproduce=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DReportStdout=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DGCAfter=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DPrintTimes=true jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 * @run main/othervm -DVerifyIR=false jdk.test.lib.hotspot.ir_framework.tests.TestDFlags
 */

public class TestDFlags {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    public int c1() {
        return 34;
    }


    @Test
    public void c2() {
        for (int i = 0; i < 100; i++) {
        }
    }

    @Test
    public void c2_2() {
        for (int i = 0; i < 100; i++) {
        }
    }

    @Test
    public void c2_3() {
        for (int i = 0; i < 100; i++) {
        }
    }
}

