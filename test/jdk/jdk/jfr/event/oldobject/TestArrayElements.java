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

package jdk.jfr.event.oldobject;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @bug 8313394
 * @summary test Array Elements in OldObjectSample event
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @run main/othervm -Xms128m -Xmx128m -XX:TLABSize=2k jdk.jfr.event.oldobject.TestArrayElements
 */
public class TestArrayElements {

    private static final int ARRAY_SIZE = 1024;
    private static final int MAX_RETRY = 3;

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);

        for (int i = 0; i < MAX_RETRY; i++) {
            try (Recording recording = new Recording()) {
                recording.enable(EventNames.OldObjectSample).withoutStackTrace().with("cutoff", "infinity");
                recording.start();
                for (int j = 0; j <1024 * 1000; j++) {
                  new String("aaaaaaaa"); // not array object
                  long a[] = new long[ARRAY_SIZE]; // array object
                }
                recording.stop();
                List<RecordedEvent> events = Events.fromRecording(recording);
                Events.hasEvents(events);
                if (verifyArrayElements(events)) {
                    return;
                }
            }
            System.out.println("Retrying...");
        }
        throw new Exception("Could not find OldObjectSample event with array object or other object");

    }

    private static boolean verifyArrayElements(List<RecordedEvent> events) throws Exception {
        boolean hasLongArray = false;
        boolean hasString = false;
        for (RecordedEvent e : events) {
            System.out.println(e);
            RecordedObject object = e.getValue("object");
            RecordedClass objectType = object.getValue("type");
            if (objectType.getName().equals(long[].class.getName())) {
                int size = e.getValue("arrayElements");
                if (size != ARRAY_SIZE) {
                    throw new Exception("Expected array size: " + ARRAY_SIZE + ", but got " + size);
                }
                hasLongArray = true;
            }
            if (objectType.getName().equals(String.class.getName())) {
                int size = e.getValue("arrayElements");
                if (size != -1) {
                    throw new Exception("Expected array size: -1, but got " + size);
                }
                hasString = true;
            }
        }
        if (!hasLongArray) {
            System.out.println("Could not find event with " + long[].class + " as object");
        }
        if (!hasString) {
            System.out.println("Could not find event with " + String.class + " as object");
        }
        return (hasLongArray && hasString);
    }

}

