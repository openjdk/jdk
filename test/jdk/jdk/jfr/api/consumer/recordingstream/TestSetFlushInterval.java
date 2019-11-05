/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.api.consumer.recordingstream;

import java.time.Duration;

import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.EventNames;

/**
 * @test
 * @summary Tests RecordingStream::setFlushInterval
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.recordingstream.TestSetFlushInterval
 */
public class TestSetFlushInterval {

    public static void main(String... args) throws Exception {
        Duration expectedDuration = Duration.ofMillis(1001);
        try (RecordingStream r = new RecordingStream()) {
            r.setFlushInterval(expectedDuration);
            r.enable(EventNames.ActiveRecording);
            r.onEvent(e -> {
                System.out.println(e);
                Duration duration = e.getDuration("flushInterval");
                if (expectedDuration.equals(duration)) {
                    System.out.println("Closing recording");
                    r.close();
                    return;
                }
                System.out.println("Flush interval not set, was " + duration +
                                   ", but expected " + expectedDuration);
            });
            r.start();
        }
    }
}
