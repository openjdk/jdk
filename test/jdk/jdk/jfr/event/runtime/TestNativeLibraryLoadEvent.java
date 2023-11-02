/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertNotNull;
import static jdk.test.lib.Asserts.assertNull;
import static jdk.test.lib.Asserts.assertTrue;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.test.lib.Platform;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @bug 8313251
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.runtime.TestNativeLibraryLoadEvent
 */
public class TestNativeLibraryLoadEvent {

    private final static String EVENT_NAME = EventNames.NativeLibraryLoad;
    private final static String LOAD_CLASS_NAME = "java.lang.System";
    private final static String LOAD_METHOD_NAME = "loadLibrary";
    private final static String LIBRARY = "instrument";
    private final static String PLATFORM_LIBRARY_NAME = Platform.buildSharedLibraryName(LIBRARY);

    public static void main(String[] args) throws Throwable {
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            System.loadLibrary(LIBRARY);
            recording.stop();

            for (RecordedEvent event : Events.fromRecording(recording)) {
                if (validate(event)) {
                    return;
                }
            }
            assertTrue(false, "Missing library " + PLATFORM_LIBRARY_NAME);
        }
    }

    private static boolean validate(RecordedEvent event) {
        assertTrue(event.getEventType().getName().equals(EVENT_NAME));
        String lib = Events.assertField(event, "name").notEmpty().getValue();
        System.out.println(lib);
        if (!lib.endsWith(PLATFORM_LIBRARY_NAME)) {
            return false;
        }
        assertTrue(Events.assertField(event, "success").getValue());
        assertNull(Events.assertField(event, "errorMessage").getValue());
        RecordedStackTrace stacktrace = event.getStackTrace();
        assertNotNull(stacktrace);
        for (RecordedFrame f : stacktrace.getFrames()) {
            if (match(f.getMethod())) {
                return true;
            }
        }
        return false;
    }

    private static boolean match(RecordedMethod method) {
        assertNotNull(method);
        System.out.println(method.getType().getName() + "." + method.getName());
        return method.getName().equals(LOAD_METHOD_NAME) && method.getType().getName().equals(LOAD_CLASS_NAME);
    }
}
