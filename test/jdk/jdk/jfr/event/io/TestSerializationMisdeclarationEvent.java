/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.io;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.Events;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import static jdk.test.lib.jfr.EventNames.SerializationMisdeclaration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/*
 * @test
 * @bug 8275338 8324220
 * @summary Check generation of JFR events for misdeclared fields and methods
 *          relevant to serialization
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run junit/othervm jdk.jfr.event.io.TestSerializationMisdeclarationEvent
 */
public class TestSerializationMisdeclarationEvent {

    private static List<RecordedEvent> events;

    @BeforeAll
    static void recordEvents() throws IOException {
        try (Recording r = new Recording()) {
            r.enable(SerializationMisdeclaration);
            r.start();
            doLookups();
            r.stop();
            events = Events.fromRecording(r);
        }
    }

    static Arguments[] testSingleClassMisdeclarations() {
        return new Arguments[] {
                arguments(NoSUID.class, new String[] {"serialVersionUID", "should", "explicitly"}),
                arguments(NoSUID.class, new String[] {"serialPersistentFields", "should", "non-null"}),

                arguments(BadClass.class, new String[] {"serialVersionUID", "should", "private"}),
                arguments(BadClass.class, new String[] {"serialVersionUID", "must", "type", "long"}),
                arguments(BadClass.class, new String[] {"serialVersionUID", "must", "final"}),
                arguments(BadClass.class, new String[] {"serialVersionUID", "must", "static"}),
                arguments(BadClass.class, new String[] {"serialPersistentFields", "must", "private"}),
                arguments(BadClass.class, new String[] {"serialPersistentFields", "must", "static"}),
                arguments(BadClass.class, new String[] {"serialPersistentFields", "must", "final"}),
                arguments(BadClass.class, new String[] {"serialPersistentFields", "should", "type", "ObjectStreamField[]"}),
                arguments(BadClass.class, new String[] {"method", "writeObject", "must", "private"}),
                arguments(BadClass.class, new String[] {"method", "writeObject", "must", "non-static"}),
                arguments(BadClass.class, new String[] {"method", "writeObject", "must", "return"}),
                arguments(BadClass.class, new String[] {"method", "writeObject", "must", "parameter"}),
                arguments(BadClass.class, new String[] {"method", "readObject(", "must", "parameter"}),
                arguments(BadClass.class, new String[] {"method", "readObjectNoData", "must", "parameter"}),

                arguments(EnumClass.class, new String[] {"serialVersionUID", "enum"}),
                arguments(EnumClass.class, new String[] {"serialPersistentFields", "enum"}),
                arguments(EnumClass.class, new String[] {"method", "writeObject", "enum"}),
                arguments(EnumClass.class, new String[] {"method", "readResolve", "enum"}),

                arguments(RecordClass.class, new String[] {"serialPersistentFields", "record"}),
                arguments(RecordClass.class, new String[] {"method", "record"}),

                arguments(C.class, new String[] {"method", "not", "accessible"}),

                arguments(Acc.class, new String[] {"serialPersistentFields", "should", "type", "ObjectStreamField[]"}),
                arguments(Acc.class, new String[] {"serialPersistentFields", "must", "instance", "ObjectStreamField[]"}),
                arguments(Acc.class, new String[] {"method", "readResolve", "must", "non-abstract"}),
                arguments(Acc.class, new String[] {"method", "writeReplace", "must", "non-static"}),
                arguments(Acc.class, new String[] {"method", "writeReplace", "must", "return"}),
                arguments(Acc.class, new String[] {"method", "writeReplace", "must", "parameter"}),
        };
    }

    static Arguments[] testGoodClass() {
        return new Arguments[] {
                arguments(A.class),
                arguments(B.class),
        };
    }

    @ParameterizedTest
    @MethodSource
    public void testSingleClassMisdeclarations(Class<?> cls, String[] keywords) {
        singleClassEvent(cls, keywords);
    }

    @ParameterizedTest
    @MethodSource
    public void testGoodClass(Class<?> cls) {
        assertEquals(0, getEventsFor(cls).size(), cls.getName());
    }

    private static void doLookups() {
        ObjectStreamClass.lookup(NoSUID.class);
        ObjectStreamClass.lookup(BadClass.class);
        ObjectStreamClass.lookup(EnumClass.class);
        ObjectStreamClass.lookup(RecordClass.class);
        ObjectStreamClass.lookup(Acc.class);

        ObjectStreamClass.lookup(A.class);
        ObjectStreamClass.lookup(B.class);
        ObjectStreamClass.lookup(C.class);
    }

    private static void singleClassEvent(Class<?> cls, String[] keywords) {
        assertEquals(1, getEventsFor(cls, keywords).size(), cls.getName());
    }

    private static List<RecordedEvent> getEventsFor(Class<?> cls, String[] keywords) {
        return events.stream()
                .filter(e -> e.getClass("misdeclaredClass").getName().equals(cls.getName())
                        && matchesAllKeywords(e.getString("message"), keywords))
                .toList();
    }

    private static boolean matchesAllKeywords(String msg, String[] keywords) {
        return Arrays.stream(keywords).allMatch(msg::contains);
    }

    private static List<RecordedEvent> getEventsFor(Class<?> cls) {
        return events.stream()
                .filter(e -> e.getClass("misdeclaredClass").getName().equals(cls.getName()))
                .toList();
    }

    private static class A implements Serializable {

        @Serial
        private static final long serialVersionUID = 0xAAAAL;

        @Serial
        private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

        @Serial
        private void writeObject(ObjectOutputStream oos) {
        }

        @Serial
        private void readObject(ObjectInputStream ois) {
        }

        @Serial
        private void readObjectNoData() {
        }

        @Serial
        Object writeReplace() {
            return null;
        }

    }

    private static class B extends A {

        @Serial
        private static final long serialVersionUID = 0xBBBBL;

        @Serial
        private Object readResolve() {
            return null;
        }

    }

    private static final class C extends B {

        @Serial
        private static final long serialVersionUID = 0xCCCCL;

        /*
         * readResolve() in superclass is not accessible
         */

    }

    private static final class NoSUID implements Serializable {

        /*
         * should declare serialVersionUID
         */

        /*
         * value should be non-null
         */
        private static final ObjectStreamField[] serialPersistentFields = null;

    }

    private static final class BadClass implements Serializable {
        /*
         * should be private
         * must be long
         * must be final
         */
        Object serialVersionUID = 1.2;

        /*
         * must be private
         * must be static
         * must be final
         * should be ObjectStreamField[]
         */
        Object serialPersistentFields = new String[0];

        /*
         * must be private
         * must be non-static
         * must return void
         * must accept ObjectOutputStream
         */
        static int writeObject(int i) {
            return 0;
        }

        /*
         * must accept ObjectInputStream
         */
        private void readObject(ObjectOutputStream oos) {
        }

        /*
         * must not accept parameters
         */
        private void readObjectNoData(ObjectInputStream ois) {
        }

    }

    private enum EnumClass implements Serializable {
        __;  // ignored constant

        /*
         * non-effective on enum
         */
        private static final long serialVersionUID = 0xABCDL;

        /*
         * non-effective on enum
         */
        private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

        /*
         * non-effective on enum
         */
        private void writeObject(ObjectOutputStream oos) {
        }

        /*
         * non-effective on enum
         */
        public Object readResolve() {
            return null;
        }

    }

    private record RecordClass() implements Serializable {

        /*
         * allowed on records
         */
        private static final long serialVersionUID = 0x1234L;

        /*
         * non-effective on records
         */
        private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

        /*
         * non-effective on records
         */
        static int writeObject(int i) {
            return 0;
        }

    }

    private abstract static class Acc implements Serializable {

        private static final long serialVersionUID = 0x5678L;

        private static final Object serialPersistentFields = new String[0];
        /*
         * must be non-abstract
         */
        abstract Object readResolve();

        /*
         * must be non-static
         */
        static Object writeReplace() {
            return null;
        }

        /*
         * must return Object
         * must have empty parameter types
         */
        String writeReplace(String s) {
            return null;
        }

    }

}
