/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jvm;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.Path;

/**
 * @test Tests that the event duration is zero after a chunk rotation
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main/othervm jdk.jfr.jvm.TestEventDuration
 */
public class TestEventDuration {

    static class InstantEvent extends Event {
        long id;
    }

    public static void main(String... args) throws Exception {
        try (Recording r1 = new Recording()) {
            r1.start();
            long counter = 0;
            for (int i = 0; i < 10; i ++) {
                try (Recording r2 = new Recording()) {
                    r2.start();
                    InstantEvent e1 = new InstantEvent();
                    e1.id = counter++;
                    e1.commit();
                    InstantEvent e2 = new InstantEvent();
                    e2.id = counter++;
                    e2.commit();
                }
            }
            Path p = Path.of("dump.jfr");
            r1.dump(p);
            var events = RecordingFile.readAllEvents(p);
            if (events.isEmpty()) {
                throw new AssertionError("Expected at least one event");
            }
            events.forEach(System.out::println);
            for (var event : events) {
                if (event.getDuration().toNanos() != 0) {
                    throw new AssertionError("Expected all events to have zero duration");
                }
            }
        }
    }
}
