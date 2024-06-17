/*
 * Copyright (c) 2014, 2024, Alibaba Group Holding Limited. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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

package gc;

/**
 * @test id=ParallelCollector
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Parallel
 * @requires os.maxMemory > 2G
 * @requires os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx512m -Xms512m -XX:+UseParallelGC -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

 /**
 * @test id=SerialCollector
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Serial
 * @requires os.maxMemory > 2G
 * @requires os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx512m -Xms512m -XX:+UseSerialGC -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=Shenandoah
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Shenandoah
 * @requires os.maxMemory > 2G
 * @requires os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx512m -Xms512m -XX:+UseShenandoahGC  -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=G1
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.G1
 * @requires os.maxMemory > 2G
 * @requires os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx512m -Xms512m -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=ZGenerational
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.ZGenerational
 * @requires os.maxMemory > 2G
 * @requires os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseZGC -XX:+ZGenerational -Xmx512m -Xms512m -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=ZSinglegen
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.ZSinglegen
 * @requires os.maxMemory > 2G
 * @requires os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseZGC -XX:-ZGenerational -Xmx512m -Xms512m -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=Epsilon
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Epsilon
 * @requires os.maxMemory > 2G
 * @requires os.family != "aix"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xmx512m -Xms512m -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */


import jdk.test.lib.Asserts;

import jdk.test.whitebox.WhiteBox;

public class TestAlwaysPreTouchBehavior {

    public static void main(String [] args) {
    long rss = WhiteBox.getWhiteBox().rss();
    System.out.println("RSS: " + rss);
    if (rss == 0) {
        System.out.println("cannot get RSS, just skip");
        return; // Did not get available RSS, just ignore this test.
    }
    Runtime runtime = Runtime.getRuntime();
    long committedMemory = runtime.totalMemory();
    Asserts.assertGreaterThan(rss, committedMemory, "RSS of this process(" + rss + "b) should be bigger than or equal to committed heap mem(" + committedMemory + "b)");
   }
}

