/*
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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
package jdk.jfr.event.runtime;

import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.assertEquals;

import java.util.List;
import java.time.Duration;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.whitebox.WhiteBox;

/**
 * @test TestTimeToSafepointEvent
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   jdk.jfr.event.runtime.TestTimeToSafepointEvent
 */
public class TestTimeToSafepointEvent {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        Recording recording = new Recording();
        recording.enable(EventNames.TimeToSafepoint)
                 .withThreshold(Duration.ofMillis(200));
        recording.start();

        Thread thread = new Thread(() -> {
            while (true) {
                WB.waitUnsafe(999);
            }
        }, "SafepointTimeout Trigger");
        thread.setDaemon(true);
        thread.start();

        Thread.sleep(1000);

        for (int i = 0; i < 8; i++) {
            WB.forceSafepoint();
        }

        recording.stop();

        List<RecordedEvent> events = Events.fromRecording(recording);
        assertTrue(events.size() > 0);
        for (RecordedEvent event : events) {
            System.out.println("Event: " + event);

            assertTrue(Events.isEventType(event, EventNames.TimeToSafepoint));
            assertEquals(event.getThread().getOSName(), "VM Thread");
            Events.assertEventThread(event, "thread", thread);
            Events.assertTopFrame(event, WhiteBox.class.getName(), "waitUnsafe");
        }
    }
}
