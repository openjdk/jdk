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

package jdk.jfr.jvm;

import jdk.jfr.Recording;
import jdk.test.lib.jfr.EventNames;

/**
 * @test
 * @bug 8316271
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -Xverify:all jdk.jfr.jvm.TestVerifyInstrumentation
 */
public class TestVerifyInstrumentation {
    private final static String EVENT_NAME = EventNames.ThreadSleep;

    public static void main(String[] args) {
        // This thread sleep wil load the jdk.internal.event.ThreadSleepEvent
        // before JFR has started recording. This will set the type of the static
        // field EventConfiguration to become untyped, i.e. java.lang.Object.
        // Before issuing an invokevirtual instructions for this receiver, we must perform
        // the proper type conversion, i.e. a downcast to type EventConfiguration using
        // a conditional checkcast. -Xverify:all asserts this is ok.
        //
        // If not ok, the following exception is thrown as part of retransformation:
        //
        // java.lang.RuntimeException: JfrJvmtiAgent::retransformClasses failed: JVMTI_ERROR_FAILS_VERIFICATION.
        //
        try {
            Thread.sleep(1);
        } catch (InterruptedException ie) {}
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME).withoutThreshold().withStackTrace();
            recording.start();
            recording.stop();
        }
    }
}
