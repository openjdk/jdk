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

import jdk.internal.classfile.impl.Util;
import jdk.internal.javac.PreviewFeature;

/**
 * Models an {@code element_value} structure, or a value of an element-value
 * pair of an annotation, as defined in JVMS {@jvms 4.7.16.1}.
 * <p>
 * Two {@code AnnotationValue} objects should be compared using the {@link
 * Object#equals(Object) equals} method.
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
     * Models an annotation value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_ANNOTATION}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfAnnotation extends AnnotationValue
            permits AnnotationImpl.OfAnnotationImpl {
        /** {@return the annotation value} */
        Annotation annotation();
    }

    /**
     * Models an array value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_ARRAY}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfArray extends AnnotationValue
            permits AnnotationImpl.OfArrayImpl {
        /**
         * {@return the array elements of the array value}
         *
         * @apiNote
         * All array elements derived from Java source code have the same type,
         * which must not be an array type. (JLS {@jls 9.6.1}) If such elements are
         * annotations, they have the same annotation interface; if such elements
         * are enum, they belong to the same enum class.
         */
        List<AnnotationValue> values();
    }

    /**
     * Models a constant value of an element-value pair.
     *
     * @sealedGraph
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfConstant extends AnnotationValue {
        /**
         * {@return the constant pool entry backing this constant element}
         *
         * @apiNote
         * Different types of constant values may share the same type of entry
         * because they have the same {@linkplain TypeKind##computational-type
         * computational type}.
         * For example, {@link OfInt} and {@link OfChar} are both
         * backed by {@link IntegerEntry}. Use {@link #resolvedValue
         * resolvedValue()} for a value of accurate type.
         */
        AnnotationConstantValueEntry constant();

        /**
         * {@return the resolved live constant value, as an object} The type of
         * the returned value may be a wrapper class or {@link String}.
         *
         * @apiNote
         * The returned object, despite being {@link Constable}, may not
         * {@linkplain Constable#describeConstable() describe} the right constant
         * for encoding the annotation value in a class file. For example,
         * {@link Character} returned by {@link OfChar} describes itself as a
         * {@link DynamicConstantPoolEntry}, but it is actually backed by
         * {@link IntegerEntry} in annotation format.
         * Use {@link #constant constant()} for a correct constant pool representation.
         */
        Constable resolvedValue();
    }

    /**
     * Models a string value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_STRING}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfString extends OfConstant
            permits AnnotationImpl.OfStringImpl {
        /** {@return the backing UTF8 entry} */
        @Override
        Utf8Entry constant();

        /** {@return the constant string value} */
        String stringValue();

        /**
         * {@return the resolved string value}
         *
         * @implSpec
         * This method returns the same as {@link #stringValue()}.
         */
        @Override
        default String resolvedValue() {
            return stringValue();
        }
    }

    /**
     * Models a double value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_DOUBLE}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfDouble extends OfConstant
            permits AnnotationImpl.OfDoubleImpl {
        /** {@return the backing double entry} */
        @Override
        DoubleEntry constant();

        /** {@return the constant double value} */
        double doubleValue();

        /**
         * {@return the resolved double value}
         *
         * @implSpec
         * This method returns the same as {@link #doubleValue()}.
         */
        @Override
        default Double resolvedValue() {
            return doubleValue();
        }
    }

    /**
     * Models a float value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_FLOAT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfFloat extends OfConstant
            permits AnnotationImpl.OfFloatImpl {
        /** {@return the backing float entry} */
        @Override
        FloatEntry constant();

        /** {@return the constant float value} */
        float floatValue();

        /**
         * {@return the resolved float value}
         *
         * @implSpec
         * This method returns the same as {@link #floatValue()}.
         */
        @Override
        default Float resolvedValue() {
            return floatValue();
        }
    }

    /**
     * Models a long value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_LONG}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfLong extends OfConstant
            permits AnnotationImpl.OfLongImpl {
        /** {@return the backing long entry} */
        @Override
        LongEntry constant();

        /** {@return the constant long value} */
        long longValue();

        /**
         * {@return the resolved long value}
         *
         * @implSpec
         * This method returns the same as {@link #longValue()}.
         */
        @Override
        default Long resolvedValue() {
            return longValue();
        }
    }

    /**
     * Models an int value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_INT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfInt extends OfConstant
            permits AnnotationImpl.OfIntImpl {
        /** {@return the backing integer entry} */
        @Override
        IntegerEntry constant();

        /** {@return the constant int value} */
        int intValue();

        /**
         * {@return the resolved int value}
         *
         * @implSpec
         * This method returns the same as {@link #intValue()}.
         */
        @Override
        default Integer resolvedValue() {
            return intValue();
        }
    }

    /**
     * Models a short value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_SHORT}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfShort extends OfConstant
            permits AnnotationImpl.OfShortImpl {
        /** {@return the backing integer entry} */
        @Override
        IntegerEntry constant();

        /**
         * {@return the constant short value}
         * @jvms 2.11.1 Types and the Java Virtual Machine
         */
        short shortValue();

        /**
         * {@return the resolved short value}
         *
         * @implSpec
         * This method returns the same as {@link #shortValue()}.
         */
        @Override
        default Short resolvedValue() {
            return shortValue();
        }
    }

    /**
     * Models a char value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_CHAR}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfChar extends OfConstant
            permits AnnotationImpl.OfCharImpl {
        /** {@return the backing integer entry} */
        @Override
        IntegerEntry constant();

        /**
         * {@return the constant char value}
         * @jvms 2.11.1 Types and the Java Virtual Machine
         */
        char charValue();

        /**
         * {@return the resolved char value}
         *
         * @implSpec
         * This method returns the same as {@link #charValue()}.
         */
        @Override
        default Character resolvedValue() {
            return charValue();
        }
    }

    /**
     * Models a byte value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_BYTE}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfByte extends OfConstant
            permits AnnotationImpl.OfByteImpl {
        /** {@return the backing integer entry} */
        @Override
        IntegerEntry constant();

        /**
         * {@return the constant byte value}
         * @jvms 2.11.1 Types and the Java Virtual Machine
         */
        byte byteValue();

        /**
         * {@return the resolved byte value}
         *
         * @implSpec
         * This method returns the same as {@link #byteValue()}.
         */
        @Override
        default Byte resolvedValue() {
            return byteValue();
        }
    }

    /**
     * Models a boolean value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_BOOLEAN}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfBoolean extends OfConstant
            permits AnnotationImpl.OfBooleanImpl {
        /** {@return the backing integer entry} */
        @Override
        IntegerEntry constant();

        /**
         * {@return the constant boolean value}
         * @jvms 2.3.4 The <i>boolean</i> Type
         */
        boolean booleanValue();

        /**
         * {@return the resolved boolean value}
         *
         * @implSpec
         * This method returns the same as {@link #booleanValue()}.
         */
        @Override
        default Boolean resolvedValue() {
            return booleanValue();
        }
    }

    /**
     * Models a class value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_CLASS}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfClass extends AnnotationValue
            permits AnnotationImpl.OfClassImpl {
        /** {@return the class descriptor string} */
        Utf8Entry className();

        /** {@return the class descriptor} */
        default ClassDesc classSymbol() {
            return Util.fieldTypeSymbol(className());
        }
    }

    /**
     * Models an enum value of an element-value pair.
     * The {@linkplain #tag tag} of this value is {@value ClassFile#AEV_ENUM}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OfEnum extends AnnotationValue
            permits AnnotationImpl.OfEnumImpl {
        /** {@return the enum class descriptor string} */
        Utf8Entry className();

        /** {@return the enum class descriptor} */
        default ClassDesc classSymbol() {
            return Util.fieldTypeSymbol(className());
        }

        /** {@return the enum constant name} */
        Utf8Entry constantName();
    }

    /**
     * {@return the tag character for this value as per JVMS {@jvms 4.7.16.1}}
     * The tag characters have a one-to-one mapping to the types of annotation element values.
     */
    char tag();

    /**
     * {@return an enum value for an element-value pair}
     * @param className the descriptor string of the enum class
     * @param constantName the name of the enum constant
     */
    static OfEnum ofEnum(Utf8Entry className,
                         Utf8Entry constantName) {
        return new AnnotationImpl.OfEnumImpl(className, constantName);
    }

    /**
     * {@return an enum value for an element-value pair}
     * @param className the descriptor of the enum class
     * @param constantName the name of the enum constant
     */
    static OfEnum ofEnum(ClassDesc className, String constantName) {
        return ofEnum(TemporaryConstantPool.INSTANCE.utf8Entry(className),
                      TemporaryConstantPool.INSTANCE.utf8Entry(constantName));
    }

    /**
     * {@return a class value for an element-value pair}
     * @param className the descriptor string of the class
     */
    static OfClass ofClass(Utf8Entry className) {
        return new AnnotationImpl.OfClassImpl(className);
    }

    /**
     * {@return a class value for an element-value pair}
     * @param className the descriptor of the class
     */
    static OfClass ofClass(ClassDesc className) {
        return ofClass(TemporaryConstantPool.INSTANCE.utf8Entry(className));
    }

    /**
     * {@return a string value for an element-value pair}
     * @param value the string
     */
    static OfString ofString(Utf8Entry value) {
        return new AnnotationImpl.OfStringImpl(value);
    }

    /**
     * {@return a string value for an element-value pair}
     * @param value the string
     */
    static OfString ofString(String value) {
        return ofString(TemporaryConstantPool.INSTANCE.utf8Entry(value));
    }

    /**
     * {@return a double value for an element-value pair}
     * @param value the double value
     */
    static OfDouble ofDouble(DoubleEntry value) {
        return new AnnotationImpl.OfDoubleImpl(value);
    }

    /**
     * {@return a double value for an element-value pair}
     * @param value the double value
     */
    static OfDouble ofDouble(double value) {
        return ofDouble(TemporaryConstantPool.INSTANCE.doubleEntry(value));
    }

    /**
     * {@return a float value for an element-value pair}
     * @param value the float value
     */
    static OfFloat ofFloat(FloatEntry value) {
        return new AnnotationImpl.OfFloatImpl(value);
    }

    /**
     * {@return a float value for an element-value pair}
     * @param value the float value
     */
    static OfFloat ofFloat(float value) {
        return ofFloat(TemporaryConstantPool.INSTANCE.floatEntry(value));
    }

    /**
     * {@return a long value for an element-value pair}
     * @param value the long value
     */
    static OfLong ofLong(LongEntry value) {
        return new AnnotationImpl.OfLongImpl(value);
    }

    /**
     * {@return a long value for an element-value pair}
     * @param value the long value
     */
    static OfLong ofLong(long value) {
        return ofLong(TemporaryConstantPool.INSTANCE.longEntry(value));
    }

    /**
     * {@return an int value for an element-value pair}
     * @param value the int value
     */
    static OfInt ofInt(IntegerEntry value) {
        return new AnnotationImpl.OfIntImpl(value);
    }

    /**
     * {@return an int value for an element-value pair}
     * @param value the int value
     */
    static OfInt ofInt(int value) {
        return ofInt(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return a short value for an element-value pair}
     * @param value the short value
     */
    static OfShort ofShort(IntegerEntry value) {
        return new AnnotationImpl.OfShortImpl(value);
    }

    /**
     * {@return a short value for an element-value pair}
     * @param value the short value
     */
    static OfShort ofShort(short value) {
        return ofShort(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return a char value for an element-value pair}
     * @param value the char value
     */
    static OfChar ofChar(IntegerEntry value) {
        return new AnnotationImpl.OfCharImpl(value);
    }

    /**
     * {@return a char value for an element-value pair}
     * @param value the char value
     */
    static OfChar ofChar(char value) {
        return ofChar(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return a byte value for an element-value pair}
     * @param value the byte value
     */
    static OfByte ofByte(IntegerEntry value) {
        return new AnnotationImpl.OfByteImpl(value);
    }

    /**
     * {@return a byte value for an element-value pair}
     * @param value the byte value
     */
    static OfByte ofByte(byte value) {
        return ofByte(TemporaryConstantPool.INSTANCE.intEntry(value));
    }

    /**
     * {@return a boolean value for an element-value pair}
     * @param value the boolean value
     */
    static OfBoolean ofBoolean(IntegerEntry value) {
        return new AnnotationImpl.OfBooleanImpl(value);
    }

    /**
     * {@return a boolean value for an element-value pair}
     * @param value the boolean value
     */
    static OfBoolean ofBoolean(boolean value) {
        int i = value ? 1 : 0;
        return ofBoolean(TemporaryConstantPool.INSTANCE.intEntry(i));
    }

    /**
     * {@return an annotation value for an element-value pair}
     * @param value the annotation
     */
    static OfAnnotation ofAnnotation(Annotation value) {
        return new AnnotationImpl.OfAnnotationImpl(value);
    }

    /**
     * {@return an array value for an element-value pair}
     *
     * @apiNote
     * See {@link AnnotationValue.OfArray#values() values()} for conventions
     * on array values derived from Java source code.
     *
     * @param values the array elements
     */
    static OfArray ofArray(List<AnnotationValue> values) {
        return new AnnotationImpl.OfArrayImpl(values);
    }

    /**
     * {@return an array value for an element-value pair}
     *
     * @apiNote
     * See {@link AnnotationValue.OfArray#values() values()} for conventions
     * on array values derived from Java source code.
     *
     * @param values the array elements
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
