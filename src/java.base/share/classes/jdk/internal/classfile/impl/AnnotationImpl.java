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

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record AnnotationImpl(Utf8Entry className, List<AnnotationElement> elements)
        implements Annotation {
    public AnnotationImpl {
        requireNonNull(className);
        elements = List.copyOf(elements);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Annotation[");
        sb.append(className().stringValue());
        List<AnnotationElement> evps = elements();
        if (!evps.isEmpty()) {
            sb.append(' ').append(evps);
        }
        sb.append("]");
        return sb.toString();
    }

    public record AnnotationElementImpl(Utf8Entry name,
                                        AnnotationValue value)
            implements AnnotationElement {
        public AnnotationElementImpl {
            requireNonNull(name);
            requireNonNull(value);
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }

    public record OfStringImpl(Utf8Entry constant)
            implements AnnotationValue.OfString {
        @Override
        public int tag() {
            return TAG_STRING;
        }

        @Override
        public String stringValue() {
            return constant().stringValue();
        }
    }

    public record OfDoubleImpl(DoubleEntry constant)
            implements AnnotationValue.OfDouble {
        @Override
        public int tag() {
            return TAG_DOUBLE;
        }

        @Override
        public double doubleValue() {
            return constant().doubleValue();
        }
    }

    public record OfFloatImpl(FloatEntry constant)
            implements AnnotationValue.OfFloat {
        @Override
        public int tag() {
            return TAG_FLOAT;
        }

        @Override
        public float floatValue() {
            return constant().floatValue();
        }
    }

    public record OfLongImpl(LongEntry constant)
            implements AnnotationValue.OfLong {
        @Override
        public int tag() {
            return TAG_LONG;
        }

        @Override
        public long longValue() {
            return constant().longValue();
        }
    }

    public record OfIntImpl(IntegerEntry constant)
            implements AnnotationValue.OfInt {
        @Override
        public int tag() {
            return TAG_INT;
        }

        @Override
        public int intValue() {
            return constant().intValue();
        }
    }

    public record OfShortImpl(IntegerEntry constant)
            implements AnnotationValue.OfShort {
        @Override
        public int tag() {
            return TAG_SHORT;
        }

        @Override
        public short shortValue() {
            return (short) constant().intValue();
        }
    }

    public record OfCharImpl(IntegerEntry constant)
            implements AnnotationValue.OfChar {
        @Override
        public int tag() {
            return TAG_CHAR;
        }

        @Override
        public char charValue() {
            return (char) constant().intValue();
        }
    }

    public record OfByteImpl(IntegerEntry constant)
            implements AnnotationValue.OfByte {
        @Override
        public int tag() {
            return TAG_BYTE;
        }

        @Override
        public byte byteValue() {
            return (byte) constant().intValue();
        }
    }

    public record OfBooleanImpl(IntegerEntry constant)
            implements AnnotationValue.OfBoolean {
        @Override
        public int tag() {
            return TAG_BOOLEAN;
        }

        @Override
        public boolean booleanValue() {
            return constant().intValue() != 0;
        }
    }

    public record OfArrayImpl(List<AnnotationValue> values)
            implements AnnotationValue.OfArray {
        public OfArrayImpl {
            values = List.copyOf(values);
        }

        @Override
        public int tag() {
            return TAG_ARRAY;
        }
    }

    public record OfEnumImpl(Utf8Entry className, Utf8Entry constantName)
            implements AnnotationValue.OfEnum {
        @Override
        public int tag() {
            return TAG_ENUM;
        }
    }

    public record OfAnnotationImpl(Annotation annotation)
            implements AnnotationValue.OfAnnotation {
        @Override
        public int tag() {
            return TAG_ANNOTATION;
        }
    }

    public record OfClassImpl(Utf8Entry className)
            implements AnnotationValue.OfClass {
        @Override
        public int tag() {
            return TAG_CLASS;
        }
    }
}
