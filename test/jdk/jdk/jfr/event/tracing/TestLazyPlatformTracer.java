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
package jdk.jfr.event.tracing;

import jdk.internal.misc.Unsafe;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
/**
* @test
* @summary Tests that PlatformTracer is not initialized if a method filter has not been set.
* @requires vm.flagless
* @requires vm.hasJFR
* @modules java.base/jdk.internal.misc jdk.jfr/jdk.jfr.internal.tracing
* @library /test/lib
* @run main/othervm -XX:StartFlightRecording jdk.jfr.event.tracing.TestLazyPlatformTracer
*/
public class TestLazyPlatformTracer {

    public static void main(String... args) throws Exception {
        // Stop recording so end chunk events are emitted
        FlightRecorder.getFlightRecorder().getRecordings().getFirst().stop();
        if (!Unsafe.getUnsafe().shouldBeInitialized(jdk.jfr.internal.tracing.PlatformTracer.class)) {
            throw new AssertionError("PlatformTracer should not have been initialized");
        }
    }
}
