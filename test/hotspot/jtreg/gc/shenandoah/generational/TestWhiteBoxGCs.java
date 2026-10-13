/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test id=old
 * @requires vm.gc.Shenandoah
 * @summary Test that WhiteBox::shenandoahOldMarkGC is robust
 * @requires vm.flagless
 * @bug 8373203
 * @library /test/lib /
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.shenandoah.generational.TestWhiteBoxGCs old
 */

/*
 * @test id=mixed
 * @requires vm.gc.Shenandoah
 * @summary Test that WhiteBox::shenandoahOldMarkGC is robust
 * @requires vm.flagless
 * @bug 8373203
 * @library /test/lib /
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.shenandoah.generational.TestWhiteBoxGCs mixed
 */

/*
 * @test id=cancel
 * @requires vm.gc.Shenandoah
 * @summary Test that WhiteBox::shenandoahOldMarkGC is robust
 * @requires vm.flagless
 * @bug 8373203
 * @library /test/lib /
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.shenandoah.generational.TestWhiteBoxGCs cancel
 */
package gc.shenandoah.generational;

import com.sun.management.GarbageCollectionNotificationInfo;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static gc.shenandoah.generational.TestHelpers.promote;

public class TestWhiteBoxGCs {
    public static final int OBJECT_COUNT = 2048;

    public static void main(String[] argv) throws Exception {
        assert argv.length == 1;
        switch (argv[0]) {
            case "old"    -> Old.runTest();
            case "mixed"  -> Mixed.runTest();
            case "cancel" -> Cancelled.runTest();
        }
    }

    public static class Old {
        private static void runTest() throws Exception {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xbootclasspath/a:.",
                    "-XX:+IgnoreUnrecognizedVMOptions",
                    "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC", "-XX:ShenandoahGCMode=generational", "-Xlog:gc",
                    "-Xmx128M", "-ea",
                    TestWhiteBoxGCs.Old.class.getName()
            );
            output.shouldContain("WhiteBox Old Mark");
            output.shouldNotContain("WhiteBox Old Expedite Mixed");
        }

        public static void main(String[] argv) {
            testConsecutiveOld();
        }

        private static void testConsecutiveOld() {
            final WhiteBox WB = WhiteBox.getWhiteBox();
            long one = WB.shenandoahOldMarkGC();
            long two = WB.shenandoahOldMarkGC();
            if (one == two) {
                throw new RuntimeException("Old GC did not advance GC ID");
            }
        }
    }

    public static class Mixed {
        private static void runTest() throws Exception {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xbootclasspath/a:.",
                    "-XX:+IgnoreUnrecognizedVMOptions",
                    "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC", "-XX:ShenandoahGCMode=generational", "-Xlog:gc",
                    "-XX:ShenandoahGenerationalMinTenuringAge=1", "-XX:ShenandoahGenerationalMaxTenuringAge=1",
                    "-Xmx128M", "-ea",
                    TestWhiteBoxGCs.Mixed.class.getName()
            );

            for (var line : output.asLines()) {
                if (line.contains("Trigger")) {
                    System.out.println(line);
                }
            }
            output.shouldContain("WhiteBox Old Mark");
            output.shouldContain("WhiteBox Old Expedite Mixed");
        }

        public static void main(String[] argv) {
            testMixed();
        }

        private static void testMixed() {
            final WhiteBox WB = WhiteBox.getWhiteBox();

            var objects = new ArrayList<>(OBJECT_COUNT);
            for (int i = 0; i < OBJECT_COUNT; ++i) {
                objects.add(new Object());
            }

            promote(objects);

            // Release half the objects in a way that avoids 100% free regions
            for (int i = 0; i < OBJECT_COUNT; ++i) {
                if (i % 2 == 0) {
                    objects.set(i, null);
                }
            }

            long one = WB.shenandoahOldMarkGC();
            long two = WB.shenandoahOldMarkGC();
            if (one == two) {
                throw new RuntimeException("Old GC did not advance GC ID");
            }
        }
    }

    public static class Cancelled {
        private static void runTest() throws Exception {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                    "-Xbootclasspath/a:.",
                    "-XX:+IgnoreUnrecognizedVMOptions",
                    "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseShenandoahGC", "-XX:ShenandoahGCMode=generational", "-Xlog:gc",
                    "-XX:ShenandoahGenerationalMinTenuringAge=1", "-XX:ShenandoahGenerationalMaxTenuringAge=1",
                    "-Xmx128M", "-ea",
                    TestWhiteBoxGCs.Cancelled.class.getName()
            );

            for (var line : output.asLines()) {
                if (line.contains("Trigger")) {
                    System.out.println(line);
                }
            }
        }

        public static void main(String[] argv) throws Exception {
            testCancelledOldGc();
        }

        private static void testCancelledOldGc() throws InterruptedException {
            final WhiteBox WB = WhiteBox.getWhiteBox();

            // Using a linked list here to intentionally slow down old marking
            var objects = new LinkedList<>();
            for (int i = 0; i < OBJECT_COUNT * 100; ++i) {
                objects.add(new Object());
            }

            promote(objects);

            int i = 0;
            for (var iter = objects.listIterator(); iter.hasNext(); iter.next()) {
                if (++i % 2 == 0) {
                    iter.remove();
                }
            }

            final var gate = new CountDownLatch(1);
            TestHelpers.GcNotificationListener startGlobal = (GarbageCollectionNotificationInfo event) -> {
                if (event.getGcAction().contains("end of Young GC cycle")) {
                    WB.fullGC();
                    gate.countDown();
                }
            };

            try (var _ = new TestHelpers.ScopedListener(startGlobal)) {
                WB.shenandoahOldMarkGC();

                gate.await(5, TimeUnit.SECONDS);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
 }
