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

package jdk.jfr.event.io;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static jdk.internal.event.SerializationMisdeclarationEvent.*;
import static jdk.test.lib.jfr.EventNames.SerializationMisdeclaration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/*
 * @test
 * @bug 8275338
 * @summary Check generation of JFR events for misdeclared fields and methods
 *          relevant to serialization
 * @key jfr
 * @requires vm.hasJFR
 * @modules java.base/jdk.internal.event
 * @library /test/lib
 * @run junit jdk.jfr.event.io.TestSerializationMisdeclarationEvent
 */
public class TestSerializationMisdeclarationEvent {

    private static final List<RecordedEvent> events = new ArrayList<>();;

    @BeforeAll
    static void recordEvents() {
        try (var rs = new RecordingStream()) {
            rs.enable(SerializationMisdeclaration)
                    .withoutThreshold()
                    .withoutStackTrace();
            rs.onEvent(SerializationMisdeclaration, events::add);
            rs.startAsync();
            doLookups();
            rs.stop();
        }
    }

    static Arguments[] testSingleClassMisdeclarations() {
        return new Arguments[] {
                arguments(NoSUID.class, SUID_EXPLICIT),
                arguments(NoSUID.class, SER_PERS_NOT_NULL),

                arguments(NonLongSUID.class, SUID_LONG),
                arguments(NonLongSUID.class, SUID_CONVERTIBLE_TO_LONG),

                arguments(EnumClass.class, SUID_INEFFECTIVE_ENUM),
                arguments(EnumClass.class, SUID_PRIVATE),
                arguments(EnumClass.class, SUID_LONG),
                arguments(EnumClass.class, SUID_STATIC),
                arguments(EnumClass.class, SUID_FINAL),
                arguments(EnumClass.class, SER_PERS_INEFFECTIVE_ENUM),
                arguments(EnumClass.class, SER_PERS_PRIVATE),
                arguments(EnumClass.class, SER_PERS_STATIC),
                arguments(EnumClass.class, SER_PERS_FINAL),
                arguments(EnumClass.class, SER_PERS_TYPE_OSF_ARRAY),
                arguments(EnumClass.class, PRIV_METH_PRIV),
                arguments(EnumClass.class, PRIV_METH_NON_STATIC),
                arguments(EnumClass.class, PRIV_METH_RET_TYPE),
                arguments(EnumClass.class, PRIV_METH_PARAM_TYPES),
                arguments(EnumClass.class, PRIV_METH_INEFFECTIVE_ENUM),
                arguments(EnumClass.class, ACC_METH_INEFFECTIVE_ENUM),

                arguments(RecordClass.class, SER_PERS_INEFFECTIVE_RECORD),
                arguments(RecordClass.class, SER_PERS_TYPE_OSF_ARRAY),
                arguments(RecordClass.class, SER_PERS_VALUE_OSF_ARRAY),
                arguments(RecordClass.class, PRIV_METH_INEFFECTIVE_RECORD),

                arguments(C.class, ACC_METH_NON_ACCESSIBLE),

                arguments(Acc.class, ACC_METH_NON_ABSTRACT),
                arguments(Acc.class, ACC_METH_NON_STATIC),
                arguments(Acc.class, ACC_METH_RET_TYPE),
                arguments(Acc.class, ACC_METH_PARAM_TYPES),
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
    public void testSingleClassMisdeclarations(Class<?> cls, int kind) {
        singleClassEvent(cls, kind);
    }

    @ParameterizedTest
    @MethodSource
    public void testGoodClass(Class<?> cls) {
        assertEquals(0, getEventsFor(cls).size(), cls.getName());
    }

    private static void doLookups() {
        ObjectStreamClass.lookup(NoSUID.class);
        ObjectStreamClass.lookup(NonLongSUID.class);
        ObjectStreamClass.lookup(EnumClass.class);
        ObjectStreamClass.lookup(RecordClass.class);
        ObjectStreamClass.lookup(Acc.class);

        ObjectStreamClass.lookup(A.class);
        ObjectStreamClass.lookup(B.class);
        ObjectStreamClass.lookup(C.class);
    }

    private static void singleClassEvent(Class<?> cls, int kind) {
        assertEquals(1, getEventsFor(cls, kind).size(), cls.getName());
    }

    private static List<RecordedEvent> getEventsFor(Class<?> cls, int kind) {
        return events.stream()
                .filter(e -> e.getClass("cls").getName().equals(cls.getName())
                        && e.getInt("kind") == kind)
                .toList();
    }

    private static List<RecordedEvent> getEventsFor(Class<?> cls) {
        return events.stream()
                .filter(e -> e.getClass("cls").getName().equals(cls.getName()))
                .toList();
    }

    private static class A implements Serializable {

        @Serial
        private static final long serialVersionUID = 0xAAAA;

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
        private static final long serialVersionUID = 0xBBBB;

        @Serial
        private Object readResolve() {
            return null;
        }

    }

    private static final class C extends B {

        @Serial
        private static final long serialVersionUID = 0xCCCCL;

    }

    private static final class NoSUID implements Serializable {

        private static final ObjectStreamField[] serialPersistentFields = null;

    }

    private static final class NonLongSUID implements Serializable {
        private static final Object serialVersionUID = 1.2;

    }

    private enum EnumClass implements Serializable {
        __;  // ignored constant

        Object serialVersionUID = 1.2;
        Object serialPersistentFields = new String[0];

        static int writeObject(int i) {
            return 0;
        }

        public Object readResolve() {
            return null;
        }

    }

    private record RecordClass() implements Serializable {

        private static final Object serialPersistentFields = new String[0];

        static int writeObject(int i) {
            return 0;
        }

    }

    private abstract static class Acc implements Serializable {

        @Serial
        private static final long serialVersionUID = 0xAcc;

        @Serial
        abstract Object readResolve();

        @Serial
        static Object writeReplace() {
            return null;
        }

        String writeReplace(String s) {
            return null;
        }

    }

}
