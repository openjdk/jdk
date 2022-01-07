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
package jdk.jfr.api.consumer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.SimpleEvent;
import sun.hotspot.WhiteBox;

/**
 * @test
 * @summary Test jdk.jfr.consumer.RecordedFrame::getType()
 * @key jfr
 * @requires vm.hasJFR & vm.compiler1.enabled
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *      -XX:+WhiteBoxAPI jdk.jfr.api.consumer.TestRecordedFrameType
 */
public final class TestRecordedFrameType {

    public static void main(String[] args) throws Exception {
        WhiteBox WB = WhiteBox.getWhiteBox();
        String directive =
            """
            [
              {
                match: "jdk/jfr/api/consumer/TestRecordedFrameType.interpreted()",
                Exclude: true,
              },
              {
                match: "jdk/jfr/api/consumer/TestRecordedFrameType.compiled()",
                BackgroundCompilation: false,
              },
            ]
            """;
        WB.addCompilerDirective(directive);
        while (true) { // Retry if method is being deoptimized
            int count = 0;
            try (Recording recording = new Recording()) {
                recording.start();
                Method mtd = TestRecordedFrameType.class.getMethod("compiled", new Class[0]);
                if (!WB.enqueueMethodForCompilation(mtd, 1)) {
                    throw new Exception("Could not enqueue method for CompLevel_simple");
                }
                Utils.waitForCondition(() -> WB.isMethodCompiled(mtd));

                interpreted();
                compiled();

                List<RecordedEvent> events = Events.fromRecording(recording);

                RecordedFrame interpreted = findFrame(events, "interpreted");
                System.out.println(interpreted);
                String iType = interpreted.getType();

                RecordedFrame compiled = findFrame(events, "compiled");
                System.out.println(compiled);
                String cType = compiled.getType(); // Can be "JIT compiled" or "Inlined"
                if (iType.equals("Interpreted") && !cType.equals("Interpreted"))  {
                    return; // OK
                }
                count++;
                System.out.println("Incorrect frame type. Retry " + count);
            }
        }
    }

    private static RecordedFrame findFrame(List<RecordedEvent> events, String methodName) throws Exception {
        for (RecordedEvent event : events) {
            for (RecordedFrame frame : event.getStackTrace().getFrames()) {
                if (frame.getMethod().getName().equals(methodName)) {
                    System.out.println("Found frame with method named: " + methodName);
                    return frame;
                }
            }
        }
        throw new Exception("Could not find frame with method named: " + methodName);
    }

    public static void interpreted() {
        SimpleEvent event = new SimpleEvent();
        event.id = 1;
        event.commit();
    }

    public static void compiled() {
        SimpleEvent event = new SimpleEvent();
        event.id = 2;
        event.commit();
    }
}
