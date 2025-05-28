/*
 * Copyright (c) 2014, 2024, Alibaba Group Holding Limited. All rights reserved.
 * Copyright (c) 2024, 2025, Red Hat Inc.
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

package gc;

/**
 * @test id=ParallelCollector
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Parallel
 * @requires os.maxMemory > 2G
 * @requires !vm.debug
 * @requires os.family == "linux"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx256m -Xms256m -XX:+UseParallelGC -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=SerialCollector
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Serial
 * @requires os.maxMemory > 2G
 * @requires !vm.debug
 * @requires os.family == "linux"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx256m -Xms256m -XX:+UseSerialGC -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=Shenandoah
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Shenandoah
 * @requires os.maxMemory > 2G
 * @requires !vm.debug
 * @requires os.family == "linux"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx256m -Xms256m -XX:+UseShenandoahGC  -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=G1
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.G1
 * @requires os.maxMemory > 2G
 * @requires !vm.debug
 * @requires os.family == "linux"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xmx256m -Xms256m -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=Z
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Z
 * @requires os.maxMemory > 2G
 * @requires !vm.debug
 * @requires os.family == "linux"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseZGC -Xmx256m -Xms256m -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

/**
 * @test id=Epsilon
 * @summary tests AlwaysPreTouch
 * @requires vm.gc.Epsilon
 * @requires os.maxMemory > 2G
 * @requires !vm.debug
 * @requires os.family == "linux"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xmx256m -Xms256m -XX:+AlwaysPreTouch gc.TestAlwaysPreTouchBehavior
 */

import jdk.test.lib.Asserts;

import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

public class TestAlwaysPreTouchBehavior {
    //
    //    This test tests the ability of the JVM to pretouch its java heap for test purposes (AlwaysPreTouch). We start a
    //    JVM with -XX:+AlwaysPreTouch, then observe RSS and expect to see RSS covering the entirety of the java heap,
    //    since it should all be pre-touched now.
    //
    //    This test is important (we had pretouching break before) but very shaky since RSS of the JVM process is subject to
    //    host machine conditions. If there is memory pressure, we may swap parts of the heap out after pretouching and
    //    before measuring RSS, thus tainting the result.
    //
    //    This test attempts to minimize the risk of false positives stemming from memory pressure by:
    //    - specifying @requires os.maxMemory > 2G
    //    - checking  if the memory still available on the host machine after starting the process is lower than a
    //      certain required threshold; if it is, we take this as a sign of memory pressure and disregard test errors.
    //    - we only run this test with release JVMs - debug JVMs have a higher and more unpredictable RSS footprint
    //      and therefore would require a larger heap for a clear difference between non-pretouch and pretouch; also,
    //      there should not be much difference between debug and release code.
    //
    //    Obviously, all of this is not bulletproof and only useful on Linux:
    //    - On MacOS, os::available_memory() drastically underreports available memory, so this technique would almost
    //      always fail to function
    //    - On AIX, we dont have a way to measure rss yet.
    //

    public static void main(String [] args) {
        long rss = WhiteBox.getWhiteBox().rss();
        System.out.println("RSS: " + rss);
        long available = WhiteBox.getWhiteBox().hostAvailableMemory();
        System.out.println("Host available memory: " + available);

        long heapSize = 256 * 1024 * 1024;

        // On Linux, a JVM that runs with 256M pre-committed heap will use about 60MB (release JVM) RSS. Barring
        // memory pressure that causes us to lose RSS, pretouching should increase RSS to >256MB. So there should be a
        // clear distinction between non-pretouched and pretouched.
        long minRequiredRss = heapSize;

        // The minimum required available memory size to count test errors as errors (to somewhat safely disregard
        // outside memory pressure as the culprit). We are over-cautious and require at least 1G free. We rather err
        // on the side of disregarding true errors than to produce false positives (if pretouching is broken, at least
        // some of the runs of this test will run on beefy enough machines and show the test as failed).
        long requiredAvailable = 1024 * 1024 * 1024;
        if (rss == 0) {
            throw new SkippedException("cannot get RSS?");
        }
        if (available > requiredAvailable) {
            Asserts.assertGreaterThan(rss, minRequiredRss, "RSS of this process(" + rss + "b) should be bigger " +
                                      "than or equal to heap size(" + heapSize + "b) (available memory: " + available + ")");
        }
    }
}
