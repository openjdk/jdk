/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;

import java.lang.constant.Constable;
import java.util.List;

import static java.lang.classfile.ClassFile.*;

public record AnnotationImpl(Utf8Entry className, List<AnnotationElement> elements)
        implements Annotation, Util.Writable {
    public AnnotationImpl {
        elements = List.copyOf(elements);
    }

    @Override
    public void writeTo(BufWriterImpl buf) {
        buf.writeIndex(className());
        buf.writeU2(elements().size());
        for (var e : elements) {
            buf.writeIndex(e.name());
            AnnotationReader.writeAnnotationValue(buf, e.value());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Annotation[");
        sb.append(className().stringValue());
        List<AnnotationElement> evps = elements();
        if (!evps.isEmpty())
            sb.append(" [");
        for (AnnotationElement evp : evps) {
            sb.append(evp.name().stringValue())
                    .append("=")
                    .append(evp.value().toString())
                    .append(", ");
        }
        if (!evps.isEmpty()) {
            sb.delete(sb.length()-1, sb.length());
            sb.append("]");
        }
        sb.append("]");
        return sb.toString();
    }

    public record AnnotationElementImpl(Utf8Entry name,
                                        AnnotationValue value)
            implements AnnotationElement, Util.Writable {

        @Override
        public void writeTo(BufWriterImpl buf) {
            buf.writeIndex(name());
            AnnotationReader.writeAnnotationValue(buf, value());
        }
    }

    public sealed interface OfConstantImpl<C extends AnnotationConstantValueEntry, T extends Constable & Comparable<T>>
            extends AnnotationValue.OfConstant<C, T>, Util.Writable {

        @Override
        default void writeTo(BufWriterImpl buf) {
            buf.writeU1(tag());
            buf.writeIndex(poolEntry());
        }

    }

    public record OfStringImpl(Utf8Entry poolEntry)
            implements OfConstantImpl<Utf8Entry, String>, AnnotationValue.OfString {

        @Override
        public char tag() {
            return AEV_STRING;
        }

        @Override
        public String stringValue() {
            return poolEntry().stringValue();
        }
    }

    public record OfDoubleImpl(DoubleEntry poolEntry)
            implements OfConstantImpl<DoubleEntry, Double>, AnnotationValue.OfDouble {

        @Override
        public char tag() {
            return AEV_DOUBLE;
        }

        @Override
        public double doubleValue() {
            return poolEntry().doubleValue();
        }
    }

    public record OfFloatImpl(FloatEntry poolEntry)
            implements OfConstantImpl<FloatEntry, Float>, AnnotationValue.OfFloat {

        @Override
        public char tag() {
            return AEV_FLOAT;
        }

        @Override
        public float floatValue() {
            return poolEntry().floatValue();
        }
    }

    public record OfLongImpl(LongEntry poolEntry)
            implements OfConstantImpl<LongEntry, Long>, AnnotationValue.OfLong {

        @Override
        public char tag() {
            return AEV_LONG;
        }

        @Override
        public long longValue() {
            return poolEntry().longValue();
        }
    }

    public record OfIntegerImpl(IntegerEntry poolEntry)
            implements OfConstantImpl<IntegerEntry, Integer>, AnnotationValue.OfInteger {

        @Override
        public char tag() {
            return AEV_INT;
        }

        @Override
        public int intValue() {
            return poolEntry().intValue();
        }
    }

    public record OfShortImpl(IntegerEntry poolEntry)
            implements OfConstantImpl<IntegerEntry, Short>, AnnotationValue.OfShort {

        @Override
        public char tag() {
            return AEV_SHORT;
        }

        @Override
        public short shortValue() {
            return (short) poolEntry().intValue();
        }
    }

    public record OfCharacterImpl(IntegerEntry poolEntry)
            implements OfConstantImpl<IntegerEntry, Character>, AnnotationValue.OfCharacter {

        @Override
        public char tag() {
            return AEV_CHAR;
        }

        @Override
        public char charValue() {
            return (char) poolEntry().intValue();
        }
    }

    public record OfByteImpl(IntegerEntry poolEntry)
            implements OfConstantImpl<IntegerEntry, Byte>, AnnotationValue.OfByte {

        @Override
        public char tag() {
            return AEV_BYTE;
        }

        @Override
        public byte byteValue() {
            return (byte) poolEntry().intValue();
        }
    }

    public record OfBooleanImpl(IntegerEntry poolEntry)
            implements OfConstantImpl<IntegerEntry, Boolean>, AnnotationValue.OfBoolean {

        @Override
        public char tag() {
            return AEV_BOOLEAN;
        }

        @Override
        public boolean booleanValue() {
            return poolEntry().intValue() == 1;
        }
    }

    public record OfArrayImpl(List<AnnotationValue> values)
            implements AnnotationValue.OfArray, Util.Writable {

        public OfArrayImpl(List<AnnotationValue> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public char tag() {
            return AEV_ARRAY;
        }

        @Override
        public void writeTo(BufWriterImpl buf) {
            buf.writeU1(tag());
            buf.writeU2(values.size());
            for (var e : values) {
                AnnotationReader.writeAnnotationValue(buf, e);
            }
        }

    }

    public record OfEnumImpl(Utf8Entry className, Utf8Entry constantName)
            implements AnnotationValue.OfEnum, Util.Writable {
        @Override
        public char tag() {
            return AEV_ENUM;
        }

        @Override
        public void writeTo(BufWriterImpl buf) {
            buf.writeU1(tag());
            buf.writeIndex(className);
            buf.writeIndex(constantName);
        }

    }

    public record OfAnnotationImpl(Annotation annotation)
            implements AnnotationValue.OfAnnotation, Util.Writable {
        @Override
        public char tag() {
            return AEV_ANNOTATION;
        }

        @Override
        public void writeTo(BufWriterImpl buf) {
            buf.writeU1(tag());
            AnnotationReader.writeAnnotation(buf, annotation);
        }

    }

    public record OfClassImpl(Utf8Entry className)
            implements AnnotationValue.OfClass, Util.Writable {
        @Override
        public char tag() {
            return AEV_CLASS;
        }

        @Override
        public void writeTo(BufWriterImpl buf) {
            buf.writeU1(tag());
            buf.writeIndex(className);
        }

    }
}
