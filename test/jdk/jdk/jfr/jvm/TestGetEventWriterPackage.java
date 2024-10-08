/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.Registered;
/**
 * @test Tests that a module can't execute code in jdk.jfr.internal.event unless an event has been registered.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm
 *   --add-opens jdk.jfr/jdk.jfr.events=ALL-UNNAMED
 *    jdk.jfr.jvm.TestGetEventWriterPackage
 */
public class TestGetEventWriterPackage {

    @Registered(false)
    static class PackageUnlockEvent extends Event {
    }
     public static void main(String... args) throws Throwable {
        // --add-opens jdk.jfr/jdk.jfr.events=ALL-UNNAMED gives access to
        // the FileReadEvent class in the jdk.jfr module.
        // When JFR is initialized the DirectBufferStatisticsEvent is registered and an EventConfiguration object
        // assigned to its static field eventConfiguration
        try (Recording r = new Recording()) {
            r.start();
        }
        // The tests gets the EventConfiguration object from the class
        Class<?>c = Class.forName("jdk.jfr.events.DirectBufferStatisticsEvent");
        Field f = c.getDeclaredField("eventConfiguration");
        f.setAccessible(true);
        Object o = f.get(null);
        Class<?> clazz = Class.forName("jdk.jfr.internal.event.EventConfiguration");
        Method m = clazz.getDeclaredMethod("isRegistered", new Class[0]);
        // it then tries to execute a method on the object from the unnamed module
        try {
            System.out.println("Is registered: " +  m.invoke(o, new Object[0]));
            throw new Exception("Did NOT expect unnamed module to be able to execute method in EventConfiguration object before event registration");
        }  catch (IllegalAccessException iae) {
            // OK, as expected
        }
        // The registration makes the jdk.jfr.internal.event accessible
        FlightRecorder.register(PackageUnlockEvent.class);
        try {
            System.out.println("Is registered: " +  m.invoke(o, new Object[0]));
        }  catch (IllegalAccessException iae) {
            throw new Exception("Did expect unnamed module to be able to execute method in EventConfiguration object efter event registration", iae);
        }
        // If a Security Manager would be present, the caller would need
        // to have FlightRecorderPermission("registerEvent")
    }
}
