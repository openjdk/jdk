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
package jdk.jfr.event.gc.detailed;

import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.util.List;
import java.util.ArrayList;

import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.EventNames;
import jdk.test.whitebox.WhiteBox;

/**
 * @test id=Serial
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires vm.gc.Serial
 * @library /test/lib /test/jdk
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. --add-opens=java.base/java.lang=ALL-UNNAMED
 *                                       -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                                       -ea
 *                                       -XX:+UseSerialGC
 *                                       -XX:+UseStringDeduplication
 *                                       -XX:StringDeduplicationAgeThreshold=1
 *                                       -Xlog:stringdedup*=debug
 *                                       jdk.jfr.event.gc.detailed.TestStringDeduplicationEvent
 */

/**
 * @test id=Parallel
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires vm.gc.Parallel
 * @library /test/lib /test/jdk
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. --add-opens=java.base/java.lang=ALL-UNNAMED
 *                                       -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                                       -ea
 *                                       -XX:+UseParallelGC
 *                                       -XX:+UseStringDeduplication
 *                                       -XX:StringDeduplicationAgeThreshold=1
 *                                       -Xlog:stringdedup*=debug
 *                                       jdk.jfr.event.gc.detailed.TestStringDeduplicationEvent
 */

/**
 * @test id=G1
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires vm.gc.G1
 * @library /test/lib /test/jdk
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. --add-opens=java.base/java.lang=ALL-UNNAMED
 *                                       -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                                       -ea
 *                                       -XX:+UseG1GC
 *                                       -XX:+UseStringDeduplication
 *                                       -XX:StringDeduplicationAgeThreshold=1
 *                                       -Xlog:stringdedup*=debug
 *                                       jdk.jfr.event.gc.detailed.TestStringDeduplicationEvent
 */

/**
 * @test id=Z
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires vm.gc.Z
 * @library /test/lib /test/jdk
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. --add-opens=java.base/java.lang=ALL-UNNAMED
 *                                       -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                                       -ea
 *                                       -XX:+UseZGC
 *                                       -XX:+UseStringDeduplication
 *                                       -XX:StringDeduplicationAgeThreshold=1
 *                                       -Xlog:stringdedup*=debug
 *                                       jdk.jfr.event.gc.detailed.TestStringDeduplicationEvent
 */

/**
 * @test id=Shenandoah
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires vm.gc.Shenandoah
 * @library /test/lib /test/jdk
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. --add-opens=java.base/java.lang=ALL-UNNAMED
 *                                       -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                                       -ea
 *                                       -XX:+UseShenandoahGC
 *                                       -XX:+UseStringDeduplication
 *                                       -XX:StringDeduplicationAgeThreshold=1
 *                                       -Xlog:stringdedup*=debug
 *                                       jdk.jfr.event.gc.detailed.TestStringDeduplicationEvent
 */

public class TestStringDeduplicationEvent {
    private static Field valueField;

    static {
        try {
            valueField = String.class.getDeclaredField("value");
            valueField.setAccessible(true);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public static void main(String[] args) throws Exception {
        boolean zgc = isZgc();

        try (RecordingStream recording = new RecordingStream()) {
            recording.enable(EventNames.StringDeduplication);
            recording.onEvent(EventNames.StringDeduplication, e -> recording.close());
            recording.startAsync();

            String base = TestStringDeduplicationEvent.class.getSimpleName();
            String duplicate = new StringBuilder(base).toString();
            assert(getValue(base) != getValue(duplicate));

            if (zgc) {
                // ZGC only triggers string deduplications from major collections
                WhiteBox.getWhiteBox().fullGC();
            } else {
                WhiteBox.getWhiteBox().youngGC();
            }

            recording.awaitTermination();
        }
    }

    private static Object getValue(String string) {
        try {
            return valueField.get(string);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isZgc() {
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        return gcs.getFirst().getName().contains("ZGC");
    }
}
