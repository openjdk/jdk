/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.metadata.annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Name;
import jdk.jfr.SettingDefinition;
import jdk.jfr.SettingDescriptor;
import jdk.jfr.ValueDescriptor;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.SimpleSetting;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.metadata.annotations.TestName
 */
public class TestName {

    @MetadataDefinition
    @Name("com.oracle.TestAnnotation")
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface NamedAnnotation {
    }

    @MetadataDefinition
    @Name("for")
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface ReservedAnnotation {
    }

    @MetadataDefinition
    @Name("Hello World")
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface InvalidAnnotation1 {
    }

    @MetadataDefinition
    @Name("Bad#Name")
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface InvalidAnnotation2 {
    }

    @MetadataDefinition
    @Name("com.example.9thing")
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface InvalidAnnotation3 {
    }

    @MetadataDefinition
    @Name("")
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface EmptyAnnotation {
    }

    @NamedAnnotation
    @Name("com.oracle.TestEvent")
    static class NamedEvent extends Event {
        @Name("testField")
        boolean namedField;

        @SettingDefinition
        @Name("name")
        boolean dummy(SimpleSetting ds) {
            return true;
        }
    }

    @Name("continue")
    static class ReservedEventName extends Event {
    }

    static class ReservedFieldName extends Event {
        @Name("final")
        private int field;
    }

    static class ReservedSettingName extends Event {
        @SettingDefinition
        @Name("true")
        public boolean setting(SimpleSetting s) {
            return true;
        }
    }

    @Name("Hello World")
    static class InvalidEventName1 extends Event {
    }

    @Name("Bad#Name")
    static class InvalidEventName2 extends Event {
    }

    @Name("com.example.9thing")
    static class InvalidEventName3 extends Event {
    }

    static class InvalidFieldName1 extends Event {
        @Name("foo.bar")
        int field;
    }

    static class InvalidFieldName2 extends Event {
        @Name("Hello World")
        int field;
    }

    static class InvalidFieldName3 extends Event {
        @Name("1up")
        int field;
    }

    static class InvalidSettingName1 extends Event {
        @SettingDefinition
        @Name("foo.bar")
        public boolean setting(SimpleSetting s) {
            return true;
        }
    }

    static class InvalidSettingName2 extends Event {
        @SettingDefinition
        @Name("Hello World")
        public boolean setting(SimpleSetting s) {
            return true;
        }
    }

    static class InvalidSettingName3 extends Event {
        @SettingDefinition
        @Name("1up")
        public boolean setting(SimpleSetting s) {
            return true;
        }
    }

    @Name("")
    static class EmptyEventName extends Event {
    }

    static class EmptyFieldName extends Event {
        @Name("")
        private String field;
    }

    static class EmptySettingName extends Event {
        @SettingDefinition
        @Name("")
        public boolean setting(SimpleSetting s) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        EventType t = EventType.getEventType(NamedEvent.class);
        ValueDescriptor testField = t.getField("testField");
        SettingDescriptor setting = getSetting(t, "name");
        AnnotationElement a = Events.getAnnotationByName(t, "com.oracle.TestAnnotation");

        // Check that names are overridden
        Asserts.assertNotNull(testField, "Can't find expected field testField");
        Asserts.assertEquals(t.getName(), "com.oracle.TestEvent", "Incorrect name for event");
        Asserts.assertEquals(a.getTypeName(), "com.oracle.TestAnnotation", "Incorrect name for annotation");
        Asserts.assertEquals(a.getTypeName(), "com.oracle.TestAnnotation", "Incorrect name for annotation");
        Asserts.assertEquals(setting.getName(), "name", "Incorrect name for setting");

        // Check that @Name is persisted
        assertAnnotation(t.getAnnotation(Name.class), "@Name should be persisted on event");
        assertAnnotation(testField.getAnnotation(Name.class), "@Name should be persisted on field");
        assertAnnotation(a.getAnnotation(Name.class), "@Name should be persisted on annotations");
        assertAnnotation(setting.getAnnotation(Name.class), "@Name should be persisted on setting");

        // Check invalid event name
        assertIllegalEventName(ReservedEventName.class);
        assertIllegalEventName(InvalidEventName1.class);
        assertIllegalEventName(InvalidEventName2.class);
        assertIllegalEventName(InvalidEventName3.class);
        assertIllegalEventName(EmptyEventName.class);

        // Check invalid field names
        assertIllegalFieldName(ReservedFieldName.class);
        assertIllegalFieldName(InvalidFieldName1.class);
        assertIllegalFieldName(InvalidFieldName2.class);
        assertIllegalFieldName(InvalidFieldName3.class);
        assertIllegalFieldName(EmptyFieldName.class);

        // Check invalid setting names
        assertIllegalSettingName(ReservedSettingName.class);
        assertIllegalSettingName(InvalidSettingName1.class);
        assertIllegalSettingName(InvalidSettingName1.class);
        assertIllegalSettingName(InvalidSettingName1.class);
        assertIllegalSettingName(EmptySettingName.class);

        // Check invalid value descriptor names
        testIllegalValueDescriptorName("goto");
        testIllegalValueDescriptorName("Hello World");
        testIllegalValueDescriptorName("1up");
        testIllegalValueDescriptorName("foo.bar");
        testIllegalValueDescriptorName("");

        // Check invalid annotation names
        testIllegalAnnotationName(ReservedAnnotation.class);
        testIllegalAnnotationName(InvalidAnnotation1.class);
        testIllegalAnnotationName(InvalidAnnotation2.class);
        testIllegalAnnotationName(InvalidAnnotation3.class);
        testIllegalAnnotationName(EmptyAnnotation.class);
    }

    private static void assertIllegalEventName(Class<? extends Event> eventClass) throws Exception {
        EventType type = EventType.getEventType(eventClass);
        if (!type.getName().equals(eventClass.getName())) {
            throw new Exception("Expected default name " + eventClass.getName() + ", not illegal name " + type.getName());
        }
    }

    private static void assertIllegalSettingName(Class<? extends Event> eventClass) throws Exception {
        EventType type = EventType.getEventType(eventClass);
        for (SettingDescriptor s : type.getSettingDescriptors()) {
            if (s.getName().equals("setting")) {
                return;
            }
            if (!List.of("threshold", "enabled", "stackTrace").contains(s.getName())) {
                throw new Exception("Expected default setting name 'setting' for event " + type.getName() + ", not illegal " + s.getName());
            }
        }
    }

    private static void assertIllegalFieldName(Class<? extends Event> eventClass) throws Exception {
        EventType type = EventType.getEventType(eventClass);
        if (type.getField("field") == null) {
            String illegal = type.getFields().get(type.getFields().size() - 1).getName();
            throw new Exception("Expected default field name 'field' for event " + type.getName() + ", not illegal name " + illegal);
        }
    }

    private static void testIllegalValueDescriptorName(String illegalName) throws Exception {
        try {
            new ValueDescriptor(int.class, illegalName);
        } catch (IllegalArgumentException iae) {
            // OK, as expected
            return;
        }
        throw new Exception("ValueDescriptor should not accept invalid field name '" + illegalName + "'");
    }

    private static void testIllegalAnnotationName(Class<? extends Annotation> annotationClass) throws Exception {
        AnnotationElement ae = new AnnotationElement(annotationClass, Map.of());
        if (!ae.getTypeName().equals(annotationClass.getName())) {
            throw new Exception("AnnotationElement for class  " + annotationClass + " not accept invalid type name '" + ae.getTypeName() + "'");
        }
    }

    // Can't use assert since the use toString on the object which doesn't work well
    // JFR proxies.
    private static void assertAnnotation(Object annotation, String message) throws Exception {
        if (annotation == null) {
            throw new Exception(message);
        }
    }

    private static SettingDescriptor getSetting(EventType t, String name) {
        for (SettingDescriptor v : t.getSettingDescriptors()) {
            if (v.getName().equals(name)) {
                return v;
            }
        }
        Asserts.fail("Could not find setting with name " + name);
        return null;
    }
}
