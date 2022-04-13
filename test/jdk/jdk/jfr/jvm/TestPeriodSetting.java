/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jfr.FlightRecorder;
import jdk.jfr.internal.JVM;
import jdk.test.lib.Asserts;

/**
 * @test TestPeriodSetting
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main/othervm jdk.jfr.jvm.TestPeriodSetting
 */
public class TestPeriodSetting {

    public static void main(String... args) {
        PlatformEventType type = new PlatformEventType(Type.EVENT_NAME_PREFIX + "ExecutionSample", 1, true, true);
        PeriodSetting setting = new PeriodSetting(eventType);

        setting.setValue("0.1ms");
        Asserts.assertEquals(1, type.getPeriod());
        setting.setValue("0ms");
        Asserts.assertEquals(0, type.getPeriod());
        setting.setValue("10001ns");
        Asserts.assertEquals(10, type.getPeriod());
    }
}
