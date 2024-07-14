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
package java.lang.classfile;

import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.AnnotationImpl;
import jdk.internal.classfile.impl.TemporaryConstantPool;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;
import jdk.internal.javac.PreviewFeature;

/**
 * Models the value of a key-value pair of an annotation.
 *
 * @see Annotation
 * @see AnnotationElement
 *
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface AnnotationValue extends WritableElement<AnnotationValue>
        permits AnnotationValue.OfAnnotation, AnnotationValue.OfArray,
                AnnotationValue.OfString, AnnotationValue.OfDouble,
                AnnotationValue.OfFloat, AnnotationValue.OfLong,
                AnnotationValue.OfInteger, AnnotationValue.OfShort,
                AnnotationValue.OfCharacter, AnnotationValue.OfByte,
                AnnotationValue.OfBoolean, AnnotationValue.OfClass,
                AnnotationValue.OfEnum, AnnotationImpl.OfConstantImpl {

    /**
     * Models an annotation-valued element.
     * The tag of this element is {@value ClassFile#AEV_ANNOTATION}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfAnnotation extends AnnotationValue
            permits AnnotationImpl.OfAnnotationImpl {
        /** {@return the annotation} */
        Annotation annotation();
    }

    /**
     * Models an array-valued element.
     * The tag of this element is {@value ClassFile#AEV_ARRAY}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfArray extends AnnotationValue
            permits AnnotationImpl.OfArrayImpl {
        /** {@return the values} */
        List<AnnotationValue> values();
    }

    /**
     * Models a string-valued element.
     * The tag of this element is {@value ClassFile#AEV_STRING}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfString extends AnnotationValue
            permits AnnotationImpl.OfStringImpl {
        /** {@return the constant} */
        String stringValue();

        /** {@return the constant pool entry} */
        Utf8Entry poolEntry();
    }

    /**
     * Models a double-valued element.
     * The tag of this element is {@value ClassFile#AEV_DOUBLE}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfDouble extends AnnotationValue
            permits AnnotationImpl.OfDoubleImpl {
        /** {@return the constant} */
        double doubleValue();

        /** {@return the constant pool entry} */
        DoubleEntry poolEntry();
    }

    /**
     * Models a float-valued element.
     * The tag of this element is {@value ClassFile#AEV_FLOAT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfFloat extends AnnotationValue
            permits AnnotationImpl.OfFloatImpl {
        /** {@return the constant} */
        float floatValue();

        /** {@return the constant pool entry} */
        FloatEntry poolEntry();
    }

    /**
     * Models a long-valued element.
     * The tag of this element is {@value ClassFile#AEV_LONG}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfLong extends AnnotationValue
            permits AnnotationImpl.OfLongImpl {
        /** {@return the constant} */
        long longValue();

        /** {@return the constant pool entry} */
        LongEntry poolEntry();
    }

    /**
     * Models an integer-valued element.
     * The tag of this element is {@value ClassFile#AEV_INT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfInteger extends AnnotationValue
            permits AnnotationImpl.OfIntegerImpl {
        /** {@return the constant} */
        int intValue();

        /** {@return the constant pool entry} */
        IntegerEntry poolEntry();
    }

    /**
     * Models a short-valued element.
     * The tag of this element is {@value ClassFile#AEV_SHORT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfShort extends AnnotationValue
            permits AnnotationImpl.OfShortImpl {
        /** {@return the constant} */
        short shortValue();

        /** {@return the constant pool entry} */
        IntegerEntry poolEntry();
    }

    /**
     * Models a character-valued element.
     * The tag of this element is {@value ClassFile#AEV_CHAR}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfCharacter extends AnnotationValue
            permits AnnotationImpl.OfCharacterImpl {
        /** {@return the constant} */
        char charValue();

        /** {@return the constant pool entry} */
        IntegerEntry poolEntry();
    }

    /**
     * Models a byte-valued element.
     * The tag of this element is {@value ClassFile#AEV_BYTE}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfByte extends AnnotationValue
            permits AnnotationImpl.OfByteImpl {
        /** {@return the constant} */
        byte byteValue();

        /** {@return the constant pool entry} */
        IntegerEntry poolEntry();
    }

    /**
     * Models a boolean-valued element.
     * The tag of this element is {@value ClassFile#AEV_BOOLEAN}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfBoolean extends AnnotationValue
            permits AnnotationImpl.OfBooleanImpl {
        /** {@return the constant} */
        boolean booleanValue();

        /** {@return the constant pool entry} */
        IntegerEntry poolEntry();
    }

    /**
     * Models a class-valued element.
     * The tag of this element is {@value ClassFile#AEV_CLASS}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfClass extends AnnotationValue
            permits AnnotationImpl.OfClassImpl {
        /** {@return the class name} */
        Utf8Entry className();

        /** {@return the class symbol} */
        default ClassDesc classSymbol() {
            return ClassDesc.ofDescriptor(className().stringValue());
        }
    }

    /**
     * Models an enum-valued element.
     * The tag of this element is {@value ClassFile#AEV_ENUM}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfEnum extends AnnotationValue
            permits AnnotationImpl.OfEnumImpl {
        /** {@return the enum class name} */
        Utf8Entry className();

        /** {@return the enum class symbol} */
        default ClassDesc classSymbol() {
            return ClassDesc.ofDescriptor(className().stringValue());
        }

        /** {@return the enum constant name} */
        Utf8Entry constantName();
    }

    /**
     * {@return the tag character for this type as per {@jvms 4.7.16.1}}
     */
    char tag();

    /**
     * {@return an annotation element for a enum-valued element}
     * @param className the name of the enum class
     * @param constantName the name of the enum constant
     */
    static OfEnum ofEnum(Utf8Entry className,
                         Utf8Entry constantName) {
        return new AnnotationImpl.OfEnumImpl(className, constantName);
    }

    /**
     * {@return an annotation element for a enum-valued element}
     * @param className the name of the enum class
     * @param constantName the name of the enum constant
     */
    static OfEnum ofEnum(ClassDesc className, String constantName) {
        return ofEnum(TemporaryConstantPool.INSTANCE.utf8Entry(className.descriptorString()),
                      TemporaryConstantPool.INSTANCE.utf8Entry(constantName));
    }

    /**
     * {@return an annotation element for a class-valued element}
     * @param className the name of the enum class
     */
    static OfClass ofClass(Utf8Entry className) {
        return new AnnotationImpl.OfClassImpl(className);
    }

    /**
     * {@return an annotation element for a class-valued element}
     * @param className the name of the enum class
     */
    static OfClass ofClass(ClassDesc className) {
        return ofClass(TemporaryConstantPool.INSTANCE.utf8Entry(className.descriptorString()));
    }

    /**
     * {@return an annotation element for a string-valued element}
     * @param value the string
     * @since 24
     */
    static OfString ofString(Utf8Entry value) {
        return new AnnotationImpl.OfStringImpl(value);
    }

    /**
     * {@return an annotation element for a string-valued element}
     * @param value the string
     * @since 24
     */
    static OfString ofString(String value) {
        return ofString(TemporaryConstantPool.INSTANCE.utf8Entry(value));
    }

    /**
     * {@return an annotation element for a double-valued element}
     * @param value the double value
     * @since 24
     */
    static OfDouble ofDouble(DoubleEntry value) {
        return new AnnotationImpl.OfDoubleImpl(value);
    }

    /**
     * {@return an annotation element for a double-valued element}
     * @param value the double value
     * @since 24
     */
    static OfDouble ofDouble(double value) {
        return ofDouble(TemporaryConstantPool.INSTANCE.doubleEntry(value));
    }

    /**
     * {@return an annotation element for a float-valued element}
     * @param value the float value
     * @since 24
     */
    static OfFloat ofFloat(FloatEntry value) {
        return new AnnotationImpl.OfFloatImpl(value);
    }

    /**
     * {@return an annotation element for a float-valued element}
     * @param value the float value
     * @since 24
     */
    static OfFloat ofFloat(float value) {
        return ofFloat(TemporaryConstantPool.INSTANCE.floatEntry(value));
    }

    /**
     * {@return an annotation element for a long-valued element}
     * @param value the long value
     * @since 24
     */
    static OfLong ofLong(LongEntry value) {
        return new AnnotationImpl.OfLongImpl(value);
    }

    /**
     * {@return an annotation element for a long-valued element}
     * @param value the long value
     * @since 24
     */
    static OfLong ofLong(long value) {
        return ofLong(TemporaryConstantPool.INSTANCE.longEntry(value));
    }

    /**
     * {@return an annotation element for an int-valued element}
     * @param value the int value
     * @since 24
     */
    static OfInteger ofInt(IntegerEntry value) {
        return new AnnotationImpl.OfIntegerImpl(value);
    }

    /**
     * {@return an annotation element for an int-valued element}
     * @param value the int value
     * @since 24
     */
    static OfInteger ofInt(int value) {
        return ofInt(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return an annotation element for a short-valued element}
     * @param value the short value
     * @since 24
     */
    static OfShort ofShort(IntegerEntry value) {
        return new AnnotationImpl.OfShortImpl(value);
    }

    /**
     * {@return an annotation element for a short-valued element}
     * @param value the short value
     * @since 24
     */
    static OfShort ofShort(short value) {
        return ofShort(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return an annotation element for a char-valued element}
     * @param value the char value
     * @since 24
     */
    static OfCharacter ofChar(IntegerEntry value) {
        return new AnnotationImpl.OfCharacterImpl(value);
    }

    /**
     * {@return an annotation element for a char-valued element}
     * @param value the char value
     * @since 24
     */
    static OfCharacter ofChar(char value) {
        return ofChar(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return an annotation element for a byte-valued element}
     * @param value the byte value
     * @since 24
     */
    static OfByte ofByte(IntegerEntry value) {
        return new AnnotationImpl.OfByteImpl(value);
    }

    /**
     * {@return an annotation element for a byte-valued element}
     * @param value the byte value
     * @since 24
     */
    static OfByte ofByte(byte value) {
        return ofByte(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return an annotation element for a boolean-valued element}
     * @param value the boolean value
     * @since 24
     */
    static OfBoolean ofBoolean(IntegerEntry value) {
        return new AnnotationImpl.OfBooleanImpl(value);
    }

    /**
     * {@return an annotation element for a boolean-valued element}
     * @param value the boolean value
     * @since 24
     */
    static OfBoolean ofBoolean(boolean value) {
        int i = value ? 1 : 0;
        return ofBoolean(TemporaryConstantPool.INSTANCE.intEntry(i));
    }

    /**
     * {@return an annotation element for an annotation-valued element}
     * @param value the annotation
     */
    static OfAnnotation ofAnnotation(Annotation value) {
        return new AnnotationImpl.OfAnnotationImpl(value);
    }

    /**
     * {@return an annotation element for an array-valued element}
     * @param values the values
     */
    static OfArray ofArray(List<AnnotationValue> values) {
        return new AnnotationImpl.OfArrayImpl(values);
    }

    /**
     * {@return an annotation element for an array-valued element}
     * @param values the values
     */
    static OfArray ofArray(AnnotationValue... values) {
        return ofArray(List.of(values));
    }

    /**
     * {@return an annotation element}  The {@code value} parameter must be
     * a primitive, a wrapper of primitive, a String, a ClassDesc, an enum
     * constant, or an array of one of these.
     *
     * @param value the annotation value
     * @throws IllegalArgumentException when the {@code value} parameter is not
     *         a primitive, a wrapper of primitive, a String, a ClassDesc,
     *         an enum constant, or an array of one of these.
     */
    static AnnotationValue of(Object value) {
        if (value instanceof String s) {
            return ofString(s);
        } else if (value instanceof Byte b) {
            return ofByte(b);
        } else if (value instanceof Boolean b) {
            return ofBoolean(b);
        } else if (value instanceof Short s) {
            return ofShort(s);
        } else if (value instanceof Character c) {
            return ofChar(c);
        } else if (value instanceof Integer i) {
            return ofInt(i);
        } else if (value instanceof Long l) {
            return ofLong(l);
        } else if (value instanceof Float f) {
            return ofFloat(f);
        } else if (value instanceof Double d) {
            return ofDouble(d);
        } else if (value instanceof ClassDesc clsDesc) {
            return ofClass(clsDesc);
        } else if (value instanceof byte[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(ofByte(el));
            }
            return ofArray(els);
        } else if (value instanceof boolean[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(ofBoolean(el));
            }
            return ofArray(els);
        } else if (value instanceof short[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(ofShort(el));
            }
            return ofArray(els);
        } else if (value instanceof char[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(ofChar(el));
            }
            return ofArray(els);
        } else if (value instanceof int[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(ofInt(el));
            }
            return ofArray(els);
        } else if (value instanceof long[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(ofLong(el));
            }
            return ofArray(els);
        } else if (value instanceof float[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(ofFloat(el));
            }
            return ofArray(els);
        } else if (value instanceof double[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(ofDouble(el));
            }
            return ofArray(els);
        } else if (value instanceof Object[] arr) {
            var els = new ArrayList<AnnotationValue>(arr.length);
            for (var el : arr) {
                els.add(of(el));
            }
            return ofArray(els);
        } else if (value instanceof Enum<?> e) {
            return ofEnum(ClassDesc.ofDescriptor(e.getDeclaringClass().descriptorString()), e.name());
        }
        throw new IllegalArgumentException("Illegal annotation constant value type " + (value == null ? null : value.getClass()));
    }
}
