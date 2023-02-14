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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.testng.Assert;
import org.testng.annotations.Test;

import sun.reflect.annotation.AnnotationParser;

import jdk.internal.vm.VMSupport;
import jdk.internal.vm.VMSupport.AnnotationDecoder;

public class TestAnnotationEncodingDecoding {

    @Test
    public void encodeDecodeTest() throws Exception {
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredField("annotatedField"));
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("annotatedMethod"));
        checkDecodedEqualsEncoded(AnnotationTestInput.AnnotatedClass.class);

        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("missingAnnotation"));
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("missingNestedAnnotation"), true, true);
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("missingTypeOfClassMember"), false, true);
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("missingMember"));
        checkDecodedEqualsEncoded(AnnotationTestInput.class.getDeclaredMethod("changeTypeOfMember"), false, true);
    }

    private void checkDecodedEqualsEncoded(AnnotatedElement annotated) throws ClassNotFoundException {
        checkDecodedEqualsEncoded(annotated, false, false);
    }

    private void checkDecodedEqualsEncoded(AnnotatedElement annotated, boolean expectNCDFE, boolean onlyStringEquality) throws ClassNotFoundException {
        Annotation[] annotations = getAnnotations(annotated, expectNCDFE);
        if (annotations == null) {
            return;
        }

        byte[] encoded = VMSupport.encodeAnnotations(List.of(annotations));
        MyDecoder decoder = new MyDecoder();
        AnnotationConst[] decoded = VMSupport.decodeAnnotations(encoded, decoder);
        int i = 0;
        for (AnnotationConst ac : decoded) {
            Class<? extends Annotation> type = (Class<? extends Annotation>) ac.getType();
            Map<String, Object> memberValues = new LinkedHashMap<>(ac.names.length);
            decodeAnnotation(ac, memberValues);
            Annotation expect = annotations[i];
            Annotation actual = AnnotationParser.annotationForMap(type, memberValues);
            if (!onlyStringEquality) {
                checkEquals(actual, expect);
            }
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
        final String[] names;
        final Object[] values;

        AnnotationConst(Class<?> type, String[] names, Object[] values) {
            this.type = type;
            this.names = names;
            this.values = values;
        }

        public Class<?> getType() {
            return type;
        }
    }

    public static final class EnumConst {
        final Class<?> type;
        final String name;

        public EnumConst(Class<?> type, String name) {
            this.type = type;
            this.name = name;
        }

        public Class<?> getEnumType() {
            return type;
        }

        public String getName() {
            return name;
        }
    }

    static class MyDecoder implements AnnotationDecoder<Class<?>, AnnotationConst, EnumConst, StringBuilder> {
        @Override
        public Class<?> resolveType(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public AnnotationConst newAnnotation(Class<?> type, String[] names, Object[] values) {
            return new AnnotationConst(type, names, values);
        }

        @Override
        public EnumConst newEnumValue(Class<?> enumType, String name) {
            return new EnumConst(enumType, name);
        }

        @Override
        public Class<?>[] newClassArray(int length) {
            return new Class<?>[length];
        }

        @Override
        public AnnotationConst[] newAnnotationArray(int length) {
            return new AnnotationConst[length];
        }

        @Override
        public EnumConst[] newEnumValues(int length) {
            return new EnumConst[length];
        }

        @Override
        public StringBuilder newErrorValue(String description) {
            return new StringBuilder(description);
        }
    }

    @SuppressWarnings("unchecked")
    private void decodeAnnotation(AnnotationConst ac, Map<String, Object> memberValues) throws ClassNotFoundException {
        for (int i = 0; i < ac.names.length; i++) {
            String name = ac.names[i];
            Object value = ac.values[i];
            Class<?> valueType = value.getClass();
            if (valueType == EnumConst.class) {
                EnumConst enumConst = (EnumConst) value;
                String enumName = enumConst.getName();
                Class<? extends Enum> enumType = (Class<? extends Enum>) enumConst.getEnumType();
                memberValues.put(name, asEnum(enumType, enumName));
            } else if (valueType == AnnotationConst.class) {
                AnnotationConst innerAc = (AnnotationConst) value;
                Map<String, Object> innerAcMemberValues = new LinkedHashMap<>(innerAc.names.length);
                decodeAnnotation(innerAc, innerAcMemberValues);
                Class<? extends Annotation> innerAcType = (Class<? extends Annotation>) innerAc.getType();
                Annotation innerA = AnnotationParser.annotationForMap(innerAcType, innerAcMemberValues);
                memberValues.put(name, innerA);
            } else if (valueType.isArray()) {
                Class<?> componentType = valueType.getComponentType();
                if (componentType == AnnotationConst.class) {
                    AnnotationConst[] array = (AnnotationConst[]) value;
                    Annotation[] dst = new Annotation[array.length];
                    for (int j = 0; j < array.length; j++) {
                        AnnotationConst e = array[j];
                        Class<? extends Annotation> type = (Class<? extends Annotation>) e.getType();
                        Map<String, Object> eValues = new LinkedHashMap<>(e.names.length);
                        decodeAnnotation(e, eValues);
                        dst[j] = AnnotationParser.annotationForMap(type, eValues);
                    }
                    memberValues.put(name, dst);
                } else if (componentType == EnumConst.class) {
                    EnumConst[] array = (EnumConst[]) value;
                    if (array.length == 0) {
                        Object[] dst = {};
                        memberValues.put(name, dst);
                    } else {
                        EnumConst ec = array[0];
                        Class<? extends Enum> enumType = (Class<? extends Enum>) ec.getEnumType();
                        Object[] dst = (Object[]) Array.newInstance(enumType, array.length);
                        for (int j = 0; j < array.length; j++) {
                            ec = array[j];
                            dst[j] = asEnum(enumType, ec.getName());
                        }
                        memberValues.put(name, dst);
                    }
                } else {
                    memberValues.put(name, value);
                }
            } else {
                memberValues.put(name, value);
            }
        }
    }

    private static Object asEnum(Class<? extends Enum> enumType, String enumName) {
        return Enum.valueOf(enumType, enumName);
    }
}
