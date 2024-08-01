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

import java.lang.classfile.constantpool.AnnotationConstantValueEntry;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.DynamicConstantPoolEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.AnnotationImpl;
import jdk.internal.classfile.impl.TemporaryConstantPool;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
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
public sealed interface AnnotationValue {

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
     * Models a constant-valued element.
     *
     * @param <C> the constant pool entry type
     * @param <R> the resolved live constant type
     * @sealedGraph
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfConstant<C extends AnnotationConstantValueEntry, R extends Comparable<R> & Constable>
            extends AnnotationValue
            permits OfString, OfDouble, OfFloat, OfLong, OfInteger, OfShort, OfCharacter, OfByte,
                    OfBoolean, AnnotationImpl.OfConstantImpl {
        /**
         * {@return the constant pool entry backing this constant element}
         *
         * @apiNote
         * Different types of constant values may share the same type of entry.
         * For example, {@link OfInteger} and {@link OfCharacter} are both
         * backed by {@link IntegerEntry}. Use {@link #resolvedValue
         * resolvedValue()} for a value of accurate type.
         */
        C poolEntry();

        /**
         * {@return the resolved live constant value, as an object} The type of
         * the returned value may be a wrapper class or {@link String}.
         *
         * @apiNote
         * The returned object, despite being {@link Constable}, may not
         * {@linkplain Constable#describeConstable() describe} the right constant
         * pool entry for encoding the annotation value in a class file. For example,
         * {@link OfCharacter} describes itself as a {@link DynamicConstantPoolEntry},
         * but it is actually backed by {@link IntegerEntry} in annotation format.
         * Use {@link #poolEntry poolEntry()} for a correct constant pool representation.
         */
        R resolvedValue();
    }

    /**
     * Models a string-valued element.
     * The tag of this element is {@value ClassFile#AEV_STRING}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfString extends OfConstant<Utf8Entry, String>
            permits AnnotationImpl.OfStringImpl {
        /** {@return the constant} */
        String stringValue();

        @Override
        default String resolvedValue() {
            return stringValue();
        }
    }

    /**
     * Models a double-valued element.
     * The tag of this element is {@value ClassFile#AEV_DOUBLE}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfDouble extends OfConstant<DoubleEntry, Double>
            permits AnnotationImpl.OfDoubleImpl {
        /** {@return the constant} */
        double doubleValue();

        @Override
        default Double resolvedValue() {
            return doubleValue();
        }
    }

    /**
     * Models a float-valued element.
     * The tag of this element is {@value ClassFile#AEV_FLOAT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfFloat extends OfConstant<FloatEntry, Float>
            permits AnnotationImpl.OfFloatImpl {
        /** {@return the constant} */
        float floatValue();

        @Override
        default Float resolvedValue() {
            return floatValue();
        }
    }

    /**
     * Models a long-valued element.
     * The tag of this element is {@value ClassFile#AEV_LONG}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfLong extends OfConstant<LongEntry, Long>
            permits AnnotationImpl.OfLongImpl {
        /** {@return the constant} */
        long longValue();

        @Override
        default Long resolvedValue() {
            return longValue();
        }
    }

    /**
     * Models an int-valued element.
     * The tag of this element is {@value ClassFile#AEV_INT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfInteger extends OfConstant<IntegerEntry, Integer>
            permits AnnotationImpl.OfIntegerImpl {
        /** {@return the constant} */
        int intValue();

        @Override
        default Integer resolvedValue() {
            return intValue();
        }
    }

    /**
     * Models a short-valued element.
     * The tag of this element is {@value ClassFile#AEV_SHORT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfShort extends OfConstant<IntegerEntry, Short>
            permits AnnotationImpl.OfShortImpl {
        /** {@return the constant} */
        short shortValue();

        @Override
        default Short resolvedValue() {
            return shortValue();
        }
    }

    /**
     * Models a char-valued element.
     * The tag of this element is {@value ClassFile#AEV_CHAR}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfCharacter extends OfConstant<IntegerEntry, Character>
            permits AnnotationImpl.OfCharacterImpl {
        /** {@return the constant} */
        char charValue();

        @Override
        default Character resolvedValue() {
            return charValue();
        }
    }

    /**
     * Models a byte-valued element.
     * The tag of this element is {@value ClassFile#AEV_BYTE}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfByte extends OfConstant<IntegerEntry, Byte>
            permits AnnotationImpl.OfByteImpl {
        /** {@return the constant} */
        byte byteValue();

        @Override
        default Byte resolvedValue() {
            return byteValue();
        }
    }

    /**
     * Models a boolean-valued element.
     * The tag of this element is {@value ClassFile#AEV_BOOLEAN}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfBoolean extends OfConstant<IntegerEntry, Boolean>
            permits AnnotationImpl.OfBooleanImpl {
        /** {@return the constant} */
        boolean booleanValue();

        @Override
        default Boolean resolvedValue() {
            return booleanValue();
        }
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
     */
    static OfString ofString(Utf8Entry value) {
        return new AnnotationImpl.OfStringImpl(value);
    }

    /**
     * {@return an annotation element for a string-valued element}
     * @param value the string
     */
    static OfString ofString(String value) {
        return ofString(TemporaryConstantPool.INSTANCE.utf8Entry(value));
    }

    /**
     * {@return an annotation element for a double-valued element}
     * @param value the double value
     */
    static OfDouble ofDouble(DoubleEntry value) {
        return new AnnotationImpl.OfDoubleImpl(value);
    }

    /**
     * {@return an annotation element for a double-valued element}
     * @param value the double value
     */
    static OfDouble ofDouble(double value) {
        return ofDouble(TemporaryConstantPool.INSTANCE.doubleEntry(value));
    }

    /**
     * {@return an annotation element for a float-valued element}
     * @param value the float value
     */
    static OfFloat ofFloat(FloatEntry value) {
        return new AnnotationImpl.OfFloatImpl(value);
    }

    /**
     * {@return an annotation element for a float-valued element}
     * @param value the float value
     */
    static OfFloat ofFloat(float value) {
        return ofFloat(TemporaryConstantPool.INSTANCE.floatEntry(value));
    }

    /**
     * {@return an annotation element for a long-valued element}
     * @param value the long value
     */
    static OfLong ofLong(LongEntry value) {
        return new AnnotationImpl.OfLongImpl(value);
    }

    /**
     * {@return an annotation element for a long-valued element}
     * @param value the long value
     */
    static OfLong ofLong(long value) {
        return ofLong(TemporaryConstantPool.INSTANCE.longEntry(value));
    }

    /**
     * {@return an annotation element for an int-valued element}
     * @param value the int value
     */
    static OfInteger ofInt(IntegerEntry value) {
        return new AnnotationImpl.OfIntegerImpl(value);
    }

    /**
     * {@return an annotation element for an int-valued element}
     * @param value the int value
     */
    static OfInteger ofInt(int value) {
        return ofInt(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return an annotation element for a short-valued element}
     * @param value the short value
     */
    static OfShort ofShort(IntegerEntry value) {
        return new AnnotationImpl.OfShortImpl(value);
    }

    /**
     * {@return an annotation element for a short-valued element}
     * @param value the short value
     */
    static OfShort ofShort(short value) {
        return ofShort(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return an annotation element for a char-valued element}
     * @param value the char value
     */
    static OfCharacter ofChar(IntegerEntry value) {
        return new AnnotationImpl.OfCharacterImpl(value);
    }

    /**
     * {@return an annotation element for a char-valued element}
     * @param value the char value
     */
    static OfCharacter ofChar(char value) {
        return ofChar(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return an annotation element for a byte-valued element}
     * @param value the byte value
     */
    static OfByte ofByte(IntegerEntry value) {
        return new AnnotationImpl.OfByteImpl(value);
    }

    /**
     * {@return an annotation element for a byte-valued element}
     * @param value the byte value
     */
    static OfByte ofByte(byte value) {
        return ofByte(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return an annotation element for a boolean-valued element}
     * @param value the boolean value
     */
    static OfBoolean ofBoolean(IntegerEntry value) {
        return new AnnotationImpl.OfBooleanImpl(value);
    }

    /**
     * {@return an annotation element for a boolean-valued element}
     * @param value the boolean value
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
