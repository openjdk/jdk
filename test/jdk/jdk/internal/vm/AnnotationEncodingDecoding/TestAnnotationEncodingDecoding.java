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

/*
 * @test
 * @compile AnnotationTestInput.java MemberDeleted.java MemberTypeChanged.java
 * @modules java.base/jdk.internal.vm
 *          java.base/sun.reflect.annotation
 * @clean jdk.internal.vm.test.AnnotationTestInput$Missing
 * @compile alt/MemberDeleted.java alt/MemberTypeChanged.java
 * @run testng/othervm
 *      jdk.internal.vm.test.TestAnnotationEncodingDecoding
 */
package jdk.internal.vm.test;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.testng.Assert;
import org.testng.annotations.Test;

import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.ExceptionProxy;

import jdk.internal.vm.VMSupport;
import jdk.internal.vm.VMSupport.AnnotationDecoder;

public class TestAnnotationEncodingDecoding {

    @Test
    public void encodeDecodeTest() throws Exception {
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredField("annotatedField"));
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("annotatedMethod"));
        checkDecodedEqualsEncoded(AnnotationTestInput.AnnotatedClass.class);

        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("missingAnnotation"));
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("missingNestedAnnotation"), true);
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("missingTypeOfClassMember"), false);
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("missingMember"));
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("changeTypeOfMember"), false);
    }

    private void checkDecodedEqualsEncoded(AnnotatedElement annotated) {
        checkDecodedEqualsEncoded(annotated, false);
    }

    private void checkDecodedEqualsEncoded(AnnotatedElement annotated, boolean expectNCDFE) {
        Annotation[] annotations = getAnnotations(annotated, expectNCDFE);
        if (annotations == null) {
            return;
        }

        byte[] encoded = VMSupport.encodeAnnotations(List.of(annotations));
        MyDecoder decoder = new MyDecoder();
        List<AnnotationConst> decoded = VMSupport.decodeAnnotations(encoded, decoder);
        int i = 0;
        for (AnnotationConst actual : decoded) {
            AnnotationConst expect = new AnnotationConst(annotations[i]);
            checkEquals(actual, expect);
            checkEquals(actual.toString(), expect.toString());
            i++;
        }
    }

    private static Annotation[] getAnnotations(AnnotatedElement annotated, boolean expectNCDFE) throws AssertionError {
        try {
            Annotation[] annotations = annotated.getAnnotations();
            Assert.assertFalse(expectNCDFE, annotated.toString());
            return annotations;
        } catch (NoClassDefFoundError e) {
            if (!expectNCDFE) {
                throw new AssertionError(annotated.toString(), e);
            }
            return null;
        }
    }

    private static void checkEquals(Object actual, Object expect) {
        if (!actual.equals(expect)) {
            throw new AssertionError(String.format("actual != expect%nactual: %s%n%nexpect: %s", actual, expect));
        }
    }

    public static final class AnnotationConst {
        final Class<?> type;
        final Map<String, Object> elements;

        AnnotationConst(Class<?> type, Map.Entry<String, Object>[] elements) {
            this.type = type;
            this.elements = Map.ofEntries(elements);
        }

        AnnotationConst(Annotation a) {
            Map<String, Object> values = AnnotationSupport.memberValues(a);
            this.type = a.annotationType();
            Map.Entry[] elements = new Map.Entry[values.size()];
            int i = 0;
            for (Map.Entry<String, Object> e : values.entrySet()) {
                elements[i++] = Map.entry(e.getKey(), decodeValue(e.getValue()));
            }
            this.elements = Map.ofEntries(elements);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AnnotationConst) {
                AnnotationConst that = (AnnotationConst) obj;
                return this.type.equals(that.type) &&
                        this.elements.equals(that.elements);
            }
            return false;
        }

        @Override
        public String toString() {
            return "@" + type.getName() + "(" + elements + ")";
        }

        private Object decodeValue(Object value) {
            Class<?> valueType = value.getClass();
            if (value instanceof Enum) {
                return new EnumConst(valueType, ((Enum<?>) value).name());
            } else if (value instanceof Annotation) {
                return new AnnotationConst((Annotation) value);
            } else if (valueType.isArray()) {
                int len = Array.getLength(value);
                Object[] arr = new Object[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = decodeValue(Array.get(value, i));
                }
                return List.of(arr);
            } else if (value instanceof ExceptionProxy) {
                return new ErrorConst(value.toString());
            } else {
                return value;
            }
        }

        public Class<?> getType() {
            return type;
        }
    }

    public static final class ErrorConst {
        final String desc;
        public ErrorConst(String desc) {
            this.desc = Objects.requireNonNull(desc);
        }

        @Override
        public String toString() {
            return desc;
        }

        @Override
        public int hashCode() {
            return desc.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ErrorConst) {
                return ((ErrorConst) obj).desc.equals(desc);
            }
            return false;
        }
    }

    public static final class EnumConst {
        final Class<?> type;
        final String name;

        public EnumConst(Class<?> type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof EnumConst) {
                EnumConst that = (EnumConst) obj;
                return this.type.equals(that.type) &&
                        this.name.equals(that.name);
            }
            return false;
        }

        @Override
        public String toString() {
            return type.getName() + "." + name;
        }

        public Class<?> getEnumType() {
            return type;
        }

        public String getName() {
            return name;
        }
    }

    static class MyDecoder implements AnnotationDecoder<Class<?>, AnnotationConst, EnumConst, ErrorConst> {
        @Override
        public Class<?> resolveType(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public AnnotationConst newAnnotation(Class<?> type, Map.Entry<String, Object>[] elements) {
            return new AnnotationConst(type, elements);
        }

        @Override
        public EnumConst newEnumValue(Class<?> enumType, String name) {
            return new EnumConst(enumType, name);
        }

        @Override
        public ErrorConst newErrorValue(String description) {
            return new ErrorConst(description);
        }
    }
}
