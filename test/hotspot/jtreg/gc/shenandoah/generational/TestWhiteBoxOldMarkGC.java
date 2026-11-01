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
 * @bug 8394388
 * @summary Test that WhiteBox::shenandoahOldMarkGC is robust
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib /
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.shenandoah.generational.TestWhiteBoxOldMarkGC old
 */

/*
 * @test id=mixed
 * @bug 8394388
 * @summary Test that WhiteBox::shenandoahOldMarkGC will expedite mixed
 *          evacuations to finish an extent OldGC.
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib /
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.shenandoah.generational.TestWhiteBoxOldMarkGC mixed
 */

/*
 * @test id=cancel
 * @bug 8394388
 * @summary Test that WhiteBox::shenandoahOldMarkGC will timeout and fail when
 *          the old mark is canceled by a full GC.
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib /
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.shenandoah.generational.TestWhiteBoxOldMarkGC cancel
 */
package gc.shenandoah.generational;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

import com.sun.management.GarbageCollectionNotificationInfo;

import static gc.shenandoah.generational.TestHelpers.promote;

public class TestWhiteBoxOldMarkGC {
    public static final int OBJECT_COUNT = 2048;

    public static void main(String[] argv) throws Exception {
        switch (argv[0]) {
            case "old"    -> Old.runTest();
            case "mixed"  -> Mixed.runTest();
            case "cancel" -> Cancelled.runTest();
            default -> throw new IllegalArgumentException("Argument should be: old|mixed|cancel");
        }
    }

    public static class Old {
        private static void runTest() throws Exception {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                getCommandLineArguments(Old.class.getName())
            );

            output.shouldContain("WhiteBox Old Mark");
            output.shouldNotContain("WhiteBox Old Expedite Mixed");
            output.shouldHaveExitValue(0);
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
                    getCommandLineArguments(Mixed.class.getName())
            );

            output.shouldContain("WhiteBox Old Mark");
            output.shouldContain("WhiteBox Old Expedite Mixed");
            output.shouldHaveExitValue(0);
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

    // TODO: Old mark cancellation is broken. Fixed by JDK-8387790.
    // The test makes no assertions because we cannot readily test the old
    // mark cancellation case.
    public static class Cancelled {
        private static void runTest() throws Exception {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                getCommandLineArguments(
                        TestWhiteBoxOldMarkGC.Cancelled.class.getName()
                )
            );
            output.shouldHaveExitValue(0);
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

            final var fullGC = new Thread(WB::fullGC);
            try (var _ = new TestHelpers.ScopedListener()) {
                fullGC.start();
                WB.shenandoahOldMarkGC(5);

                fullGC.join(10000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String[] getCommandLineArguments(String... additional) {
        var defaultArguments = new String[] {
            "-Xbootclasspath/a:.",
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseShenandoahGC", "-XX:ShenandoahGCMode=generational",
            "-Xlog:gc*", "-Xmx128M", "-ea",
            "-XX:ShenandoahGenerationalMinTenuringAge=1",
            "-XX:ShenandoahGenerationalMaxTenuringAge=1",
        };
        var arguments = new String[defaultArguments.length + additional.length];
        System.arraycopy(defaultArguments, 0, arguments, 0, defaultArguments.length);
        System.arraycopy(additional, 0, arguments, defaultArguments.length, additional.length);
        return arguments;
    }
}
