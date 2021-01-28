/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Alibaba designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

/**
 * @test TestBasicTypes
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.runtime.TestBasicTypes
 */

package jdk.jfr.event.runtime;

import java.lang.reflect.Modifier;
import java.util.List;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

public class TestBasicTypes {

    private static class MyEvent extends Event {
        Class<?> booleanClass = boolean.class;
        Class<?> charClass = char.class;
        Class<?> floatClass = float.class;
        Class<?> doubleClass = double.class;
        Class<?> byteClass = byte.class;
        Class<?> shortClass = short.class;
        Class<?> intClass = int.class;
        Class<?> longClass = long.class;
        Class<?> voidClass = void.class;
    }

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(MyEvent.class);
            r.enable(MyEvent.class);
            r.start();
            MyEvent myEvent = new MyEvent();
            myEvent.commit();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
            RecordedEvent event = events.get(0);
            System.out.println(event);
            testField(event, "booleanClass", boolean.class);
            testField(event, "charClass", char.class);
            testField(event, "floatClass", float.class);
            testField(event, "doubleClass", double.class);
            testField(event, "byteClass", byte.class);
            testField(event, "shortClass", short.class);
            testField(event, "intClass", int.class);
            testField(event, "longClass", long.class);
            testField(event, "voidClass", void.class);
        }
    }

    private static void testField(RecordedEvent event, String fieldName, Class<?> excepted) {
        Asserts.assertTrue(event.hasField(fieldName));
        RecordedClass classField = event.getValue(fieldName);
        Asserts.assertEquals(classField.getName(), excepted.getName());
        Asserts.assertNull(classField.getClassLoader());
        Asserts.assertEquals(classField.getModifiers(), Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC);
    }
}
