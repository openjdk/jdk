/*
 * Copyright (c) 2024, SAP SE. All rights reserved.
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

package jdk.jfr.event.profiling;

import java.time.Duration;
import java.util.List;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.classloader.ClassUnloadCommon;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.RecurseThread;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @requires vm.hasJFR & os.family == "linux" & vm.debug
 * @library /test/lib
 * @library classes
 * @modules jdk.jfr/jdk.jfr.internal
 * @build test.RecursiveMethods
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -XX:ErrorFile=/tmp/bla.log -Xbootclasspath/a:.
 *    --enable-native-access=ALL-UNNAMED -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *    jdk.jfr.event.profiling.TestCPUTimeClassUnloading
 */
public class TestCPUTimeClassUnloading {

    private static WhiteBox wb = WhiteBox.getWhiteBox();

    // The period is set to 1100 ms to provoke the 1000 ms
    // threshold in the JVM for os::naked_short_sleep().
    public static void main(String[] args) throws Exception {

        try(Recording recording = new Recording()) {
            recording.enable(EventNames.CPUTimeSample)
                    .with("throttle", "1us");
            recording.start();

            Thread.sleep(100);

            loadAndUnload();
            // stop processing the queue
            wb.setCPUTimeSamplerProcessQueue(false);
            // load a class, then call a method on it for a second
            // then unload the class, this should create methods and
            // classes in the queue that are not present any more
            loadAndUnload();
            // resume processing the queue
            wb.setCPUTimeSamplerProcessQueue(true);
            Thread.sleep(1000);

            recording.stop();

            long count = Events.fromRecording(recording).stream()
                    .filter(e -> e.getEventType().getName().equals(EventNames.CPUTimeSample))
                    .count();
            if (count < 10) {
                throw new AssertionError("Not enough events");
            }
        }
    }

    private static void loadAndUnload() throws Exception {
        ClassLoader cl = ClassUnloadCommon.newClassLoader();
        Class<?> c = cl.loadClass("test.RecursiveMethods");
        var entryMethod = c.getMethod("entry", int.class);
        // call entry method for 1s
        long start = System.currentTimeMillis();
        while (start + 1000 < System.currentTimeMillis()) {
            entryMethod.invoke(null, 100);
        }
        ClassUnloadCommon.triggerUnloading(List.of("test.RecursiveMethods"));
    }
}
