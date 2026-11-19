/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.classfile;

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.Const;

/**
 * The element_value structure is documented at https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-4.html#jvms-4.7.16.1
 *
 * <pre>
 * element_value {
 *    u1 tag;
 *    union {
 *        u2 const_value_index;
 *
 *        {   u2 type_name_index;
 *            u2 const_name_index;
 *        } enum_const_value;
 *
 *        u2 class_info_index;
 *
 *        annotation annotation_value;
 *
 *        {   u2            num_values;
 *            element_value values[num_values];
 *        } array_value;
 *    } value;
 *}
 *</pre>
 *
 * @since 6.0
 */
public abstract class ElementValue {

    /** Element value type: string. */
    public static final byte STRING = 's';

    /** Element value type: enum constant. */
    public static final byte ENUM_CONSTANT = 'e';

    /** Element value type: class. */
    public static final byte CLASS = 'c';

    /** Element value type: annotation. */
    public static final byte ANNOTATION = '@';

    /** Element value type: array. */
    public static final byte ARRAY = '[';

    /** Element value type: primitive int. */
    public static final byte PRIMITIVE_INT = 'I';

    /** Element value type: primitive byte. */
    public static final byte PRIMITIVE_BYTE = 'B';

    /** Element value type: primitive char. */
    public static final byte PRIMITIVE_CHAR = 'C';

    /** Element value type: primitive double. */
    public static final byte PRIMITIVE_DOUBLE = 'D';

    /** Element value type: primitive float. */
    public static final byte PRIMITIVE_FLOAT = 'F';

    /** Element value type: primitive long. */
    public static final byte PRIMITIVE_LONG = 'J';

    /** Element value type: primitive short. */
    public static final byte PRIMITIVE_SHORT = 'S';

    /** Element value type: primitive boolean. */
    public static final byte PRIMITIVE_BOOLEAN = 'Z';

    /** Empty array constant. */
    static final ElementValue[] EMPTY_ARRAY = {};

    /**
     * Reads an {@code element_value} as an {@code ElementValue}.
     *
     * @param input Raw data input.
     * @param cpool Constant pool.
     * @return A new ElementValue.
     * @throws IOException Thrown if an I/O error occurs.
     */
    public static ElementValue readElementValue(final DataInput input, final ConstantPool cpool) throws IOException {
        return readElementValue(input, cpool, 0);
    }

    static ElementValue readElementValue(final DataInput input, final ConstantPool cpool, final boolean isRuntimeVisible, int arrayNesting)
            throws IOException {
        final byte tag = input.readByte();
        switch (tag) {
        case PRIMITIVE_BYTE:
        case PRIMITIVE_CHAR:
        case PRIMITIVE_DOUBLE:
        case PRIMITIVE_FLOAT:
        case PRIMITIVE_INT:
        case PRIMITIVE_LONG:
        case PRIMITIVE_SHORT:
        case PRIMITIVE_BOOLEAN:
        case STRING:
            return new SimpleElementValue(tag, input.readUnsignedShort(), cpool);

        case ENUM_CONSTANT:
            return new EnumElementValue(ENUM_CONSTANT, input.readUnsignedShort(), input.readUnsignedShort(), cpool);

        case CLASS:
            return new ClassElementValue(CLASS, input.readUnsignedShort(), cpool);

        case ANNOTATION:
            arrayNesting++;
            if (arrayNesting > Const.MAX_ARRAY_DIMENSIONS) {
                // Annotation element values may legitimately nest (annotations whose members are annotations or arrays thereof), but a malicious class file
                // can alternate annotation and array nesting to recurse without limit. Count both kinds of nesting against the same JVM spec 4.4.1 bound so
                // the depth cannot be reset by wrapping an array in an annotation (CWE-674).
                throw new ClassFormatException(
                    String.format("Annotation element values are only valid if they nest %,d or fewer levels.", Const.MAX_ARRAY_DIMENSIONS));
            }
            return new AnnotationElementValue(ANNOTATION, AnnotationEntry.read(input, cpool, isRuntimeVisible, arrayNesting), cpool);

        case ARRAY:
            arrayNesting++;
            if (arrayNesting > Const.MAX_ARRAY_DIMENSIONS) {
                // JVM spec 4.4.1
                throw new ClassFormatException(String.format("Arrays are only valid if they represent %,d or fewer dimensions.", Const.MAX_ARRAY_DIMENSIONS));
            }
            final int numArrayVals = input.readUnsignedShort();
            final ElementValue[] evalues = new ElementValue[numArrayVals];
            for (int j = 0; j < numArrayVals; j++) {
                evalues[j] = readElementValue(input, cpool, isRuntimeVisible, arrayNesting);
            }
            return new ArrayElementValue(ARRAY, evalues, cpool);

        default:
            throw new ClassFormatException("Unexpected element value tag in annotation: " + tag);
        }
    }

    /**
     * Reads an {@code element_value} as an {@code ElementValue}.
     *
     * @param input Raw data input.
     * @param cpool Constant pool.
     * @param arrayNesting level of current array nesting.
     * @return A new ElementValue.
     * @throws IOException Thrown if an I/O error occurs.
     * @since 6.7.0
     */
    public static ElementValue readElementValue(final DataInput input, final ConstantPool cpool, final int arrayNesting) throws IOException {
        return readElementValue(input, cpool, false, arrayNesting);
    }

    /**
     * @deprecated (since 6.0) will be made private and final; do not access directly, use getter.
     */
    @java.lang.Deprecated
    protected int type; // TODO should be final

    /**
     * @deprecated (since 6.0) will be made private and final; do not access directly, use getter.
     */
    @java.lang.Deprecated
    protected ConstantPool cpool; // TODO should be final

    /**
     * Constructs an ElementValue.
     *
     * @param type The element value type.
     * @param cpool The constant pool.
     */
    protected ElementValue(final int type, final ConstantPool cpool) {
        this.type = type;
        this.cpool = cpool;
    }

    /**
     * Dumps this element value to a DataOutputStream.
     *
     * @param dos The output stream.
     * @throws IOException Thrown if an I/O error occurs.
     */
    public abstract void dump(DataOutputStream dos) throws IOException;

    /**
     * Gets the constant pool.
     *
     * @return The constant pool.
     * @since 6.0
     */
    final ConstantPool getConstantPool() {
        return cpool;
    }

    /**
     * Gets the element value type.
     *
     * @return The element value type.
     */
    public int getElementValueType() {
        return type;
    }

    /**
     * Gets the type.
     *
     * @return The type.
     * @since 6.0
     */
    final int getType() {
        return type;
    }

    /**
     * Returns a string representation of the element value.
     *
     * @return A string representation of the element value.
     */
    public abstract String stringifyValue();

    /**
     * Returns a short string representation of the element value.
     *
     * @return A short string representation of the element value.
     */
    public String toShortString() {
        return stringifyValue();
    }

    /**
     * Returns a string representation of the element value.
     *
     * @return A string representation of the element value.
     */
    @Override
    public String toString() {
        return stringifyValue();
    }
}
