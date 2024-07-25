/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import jdk.internal.classfile.impl.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AnnotationDefaultVerifier {

    private final Map<Integer, TestElementValue> verifiers;

    public AnnotationDefaultVerifier() {
        this.verifiers = new HashMap<>();
        verifiers.put((int) 'B', new TestIntegerElementValue());
        verifiers.put((int) 'C', new TestIntegerElementValue());
        verifiers.put((int) 'D', new TestDoubleElementValue());
        verifiers.put((int) 'F', new TestFloatElementValue());
        verifiers.put((int) 'I', new TestIntegerElementValue());
        verifiers.put((int) 'J', new TestLongElementValue());
        verifiers.put((int) 'S', new TestIntegerElementValue());
        verifiers.put((int) 'Z', new TestIntegerElementValue());
        verifiers.put((int) 's', new TestStringElementValue());
        verifiers.put((int) 'e', new TestEnumElementValue());
        verifiers.put((int) 'c', new TestClassElementValue());
        verifiers.put((int) '[', new TestArrayElementValue());
        verifiers.put((int) '@', new TestAnnotationElementValue());
    }

    public void testLength(int tag, TestResult testResult, AnnotationDefaultAttribute attr) {
        verifiers.get(tag).testLength(testResult, attr);
    }

    public void testElementValue(int tag, TestResult testResult, ClassModel classFile,
                                 AnnotationValue element_value, String[] values) {
        get(tag).testElementValue(testResult, classFile, element_value, values);
    }

    private TestElementValue get(int tag) {
        TestElementValue ev = verifiers.get(tag);
        if (ev == null) {
            throw new IllegalArgumentException("Unknown tag : " + (char) tag);
        }
        return ev;
    }

    private abstract class TestElementValue {
        public void testLength(TestResult testCase, AnnotationDefaultAttribute attr) {
            var buf = new BufWriterImpl(ConstantPoolBuilder.of(), (ClassFileImpl) ClassFile.of());
            AnnotationReader.writeAnnotationValue(buf, attr.defaultValue());
            testCase.checkEquals(((BoundAttribute<?>)attr).payloadLen(), buf.size(),
                    "attribute_length");
        }

        public String[] getValues(String[] values, int index, int length) {
            return Arrays.copyOfRange(values, index, index + length);
        }

        public int getLength() {
            return 1;
        }

        public abstract void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values);
    }

    private class TestIntegerElementValue extends TestElementValue {

        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            switch (element_value) {
                case AnnotationValue.OfByte ev -> {
                    testCase.checkEquals((int)ev.byteValue(), Integer.parseInt(values[0]), "const_value_index");
                }
                case AnnotationValue.OfCharacter ev -> {
                    testCase.checkEquals((int)ev.charValue(), Integer.parseInt(values[0]), "const_value_index");
                }
                case AnnotationValue.OfShort ev -> {
                    testCase.checkEquals((int)ev.shortValue(), Integer.parseInt(values[0]), "const_value_index");
                }
                case AnnotationValue.OfBoolean ev -> {
                    testCase.checkEquals(ev.booleanValue()? 1: 0, Integer.parseInt(values[0]), "const_value_index");
                }
                default -> {
                    testCase.checkEquals(((AnnotationValue.OfInteger) element_value).intValue(), Integer.parseInt(values[0]), "const_value_index");
                }
            }
        }
    }

    private class TestLongElementValue extends TestElementValue {
        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            AnnotationValue.OfLong ev =
                    (AnnotationValue.OfLong) element_value;
            testCase.checkEquals(ev.longValue(), Long.parseLong(values[0]), "const_value_index");
        }
    }

    private class TestFloatElementValue extends TestElementValue {
        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            AnnotationValue.OfFloat ev =
                    (AnnotationValue.OfFloat) element_value;
            testCase.checkEquals(ev.floatValue(), Float.parseFloat(values[0]), "const_value_index");
        }
    }

    private class TestDoubleElementValue extends TestElementValue {
        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            AnnotationValue.OfDouble ev =
                    (AnnotationValue.OfDouble) element_value;
            testCase.checkEquals(ev.doubleValue(), Double.parseDouble(values[0]), "const_value_index");
        }
    }

    private class TestStringElementValue extends TestElementValue {
        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            AnnotationValue.OfString ev =
                    (AnnotationValue.OfString) element_value;
            testCase.checkEquals(ev.stringValue(), values[0], "const_value_index");
        }
    }

    private class TestEnumElementValue extends TestElementValue {

        @Override
        public int getLength() {
            return 2;
        }

        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            AnnotationValue.OfEnum ev = (AnnotationValue.OfEnum) element_value;
            testCase.checkEquals(ev.classSymbol().descriptorString(),
                    values[0], "type_name_index");
            testCase.checkEquals(ev.constantName().stringValue(),
                    values[1], "const_name_index");
        }
    }

    private class TestClassElementValue extends TestElementValue {
        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            AnnotationValue.OfClass ev = (AnnotationValue.OfClass) element_value;
            testCase.checkEquals(
                    ev.classSymbol().descriptorString(),
                    values[0], "class_info_index");
        }
    }

    private class TestAnnotationElementValue extends TestElementValue {
        @Override
        public void testLength(TestResult testCase, AnnotationDefaultAttribute attr) {
            // Suppress, since it is hard to test the length of this kind of element values.
        }

        @Override
        public int getLength() {
            // Expected that the test uses DefaultAnnotation
            // tag (1 byte) + annotation_value (2 bytes) which contains const_value
            return 3;
        }

        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            Annotation ev = ((AnnotationValue.OfAnnotation) element_value)
                    .annotation();
            testCase.checkEquals(
                    ev.classSymbol().descriptorString(),
                    values[0],
                    "type_index");
            for (int i = 0; i < ev.elements().size(); ++i) {
                AnnotationElement pair = ev.elements().get(i);
                testCase.checkEquals(
                        pair.name().stringValue(),
                        values[2 * i + 1],
                        "element_name_index");
                TestElementValue testElementValue = verifiers.get((int)pair.value().tag());
                testElementValue.testElementValue(
                        testCase,
                        classFile,
                        pair.value(),
                        new String[]{values[2 * i + 2]});
            }
        }
    }

    private class TestArrayElementValue extends TestElementValue {
        // testLength method is the same as in TestElementValue class
        @Override
        public void testElementValue(
                TestResult testCase,
                ClassModel classFile,
                AnnotationValue element_value,
                String[] values) {
            AnnotationValue.OfArray ev =
                    (AnnotationValue.OfArray) element_value;
            int index = 0;
            for (int i = 0; i < ev.values().size(); ++i) {
                TestElementValue testElementValue = verifiers.get((int)ev.values().get(i).tag());
                int length = testElementValue.getLength();
                testElementValue.testElementValue(
                        testCase,
                        classFile,
                        ev.values().get(i),
                        testElementValue.getValues(values, index, length));
                index += length;
            }
        }
    }
}
