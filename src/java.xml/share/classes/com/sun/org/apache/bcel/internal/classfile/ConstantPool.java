/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Iterator;

import com.sun.org.apache.bcel.internal.Const;

/**
 * This class represents the constant pool, that is, a table of constants, of a parsed classfile. It may contain null references, due to the JVM specification that
 * skips an entry after an 8-byte constant (double, long) entry. Those interested in generating constant pools programmatically should see
 * <a href="../generic/ConstantPoolGen.html"> ConstantPoolGen</a>.
 *
 * @see Constant
 * @see com.sun.org.apache.bcel.internal.generic.ConstantPoolGen
 * @LastModified: Nov 2026
 */
public class ConstantPool implements Cloneable, Node, Iterable<Constant> {

    private static String escape(final String str) {
        final int len = str.length();
        final StringBuilder buf = new StringBuilder(len + 5);
        final char[] ch = str.toCharArray();
        for (int i = 0; i < len; i++) {
            switch (ch[i]) {
            case '\n':
                buf.append("\\n");
                break;
            case '\r':
                buf.append("\\r");
                break;
            case '\t':
                buf.append("\\t");
                break;
            case '\b':
                buf.append("\\b");
                break;
            case '"':
                buf.append("\\\"");
                break;
            default:
                buf.append(ch[i]);
            }
        }
        return buf.toString();
    }

    private Constant[] constantPool;

    /**
     * Constructs a ConstantPool.
     *
     * @param constantPool Array of constants.
     */
    public ConstantPool(final Constant... constantPool) {
        setConstantPool(constantPool);
    }

    /**
     * Reads constants from given input stream.
     *
     * @param input Input stream.
     * @throws IOException Thrown if problem in readUnsignedShort or readConstant.
     */
    public ConstantPool(final DataInput input) throws IOException {
        byte tag;
        final int constantPoolCount = input.readUnsignedShort();
        constantPool = new Constant[constantPoolCount];
        /*
         * constantPool[0] is unused by the compiler and may be used freely by the implementation.
         * constantPool[0] is currently unused by the implementation.
         */
        for (int i = 1; i < constantPoolCount; i++) {
            constantPool[i] = Constant.readConstant(input);
            /*
             * Quote from the JVM specification: "All eight byte constants take up two spots in the constant pool. If this is the n'th byte in the constant
             * pool, then the next item will be numbered n+2"
             *
             * Thus we have to increment the index counter.
             */
            tag = constantPool[i].getTag();
            if (tag == Const.CONSTANT_Double || tag == Const.CONSTANT_Long) {
                i++;
            }
        }
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class. I.e., the hierarchy of methods, fields,
     * attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object.
     */
    @Override
    public void accept(final Visitor v) {
        v.visitConstantPool(this);
    }

    /**
     * Resolves constant to a string representation.
     *
     * @param c Constant to be printed.
     * @return String representation.
     * @throws IllegalArgumentException Thrown if c is unknown constant type.
     */
    public String constantToString(Constant c) throws IllegalArgumentException {
        final String str;
        final int i;
        final byte tag = c.getTag();
        switch (tag) {
        case Const.CONSTANT_Class:
            i = ((ConstantClass) c).getNameIndex();
            c = getConstantUtf8(i);
            str = Utility.compactClassName(((ConstantUtf8) c).getBytes(), false);
            break;
        case Const.CONSTANT_String:
            i = ((ConstantString) c).getStringIndex();
            c = getConstantUtf8(i);
            str = "\"" + escape(((ConstantUtf8) c).getBytes()) + "\"";
            break;
        case Const.CONSTANT_Utf8:
            str = ((ConstantUtf8) c).getBytes();
            break;
        case Const.CONSTANT_Double:
            str = String.valueOf(((ConstantDouble) c).getBytes());
            break;
        case Const.CONSTANT_Float:
            str = String.valueOf(((ConstantFloat) c).getBytes());
            break;
        case Const.CONSTANT_Long:
            str = String.valueOf(((ConstantLong) c).getBytes());
            break;
        case Const.CONSTANT_Integer:
            str = String.valueOf(((ConstantInteger) c).getBytes());
            break;
        case Const.CONSTANT_NameAndType:
            str = constantToString(((ConstantNameAndType) c).getNameIndex(), Const.CONSTANT_Utf8) + " "
                    + constantToString(((ConstantNameAndType) c).getSignatureIndex(), Const.CONSTANT_Utf8);
            break;
        case Const.CONSTANT_InterfaceMethodref:
        case Const.CONSTANT_Methodref:
        case Const.CONSTANT_Fieldref:
            str = constantToString(((ConstantCP) c).getClassIndex(), Const.CONSTANT_Class) + "."
                    + constantToString(((ConstantCP) c).getNameAndTypeIndex(), Const.CONSTANT_NameAndType);
            break;
        case Const.CONSTANT_MethodHandle:
            // Note that the ReferenceIndex may point to a Fieldref, Methodref or
            // InterfaceMethodref - so we need to peek ahead to get the actual type.
            final ConstantMethodHandle cmh = (ConstantMethodHandle) c;
            final byte referenceTag = getConstant(cmh.getReferenceIndex()).getTag();
            // JVMS 4.4.8: the reference_index of a CONSTANT_MethodHandle must point to a CONSTANT_Fieldref,
            // CONSTANT_Methodref or CONSTANT_InterfaceMethodref entry. Anything else is malformed; in particular, a
            // CONSTANT_MethodHandle referencing another CONSTANT_MethodHandle (such as itself) would otherwise make
            // this method recurse without bound until a StackOverflowError.
            if (referenceTag != Const.CONSTANT_Fieldref && referenceTag != Const.CONSTANT_Methodref && referenceTag != Const.CONSTANT_InterfaceMethodref) {
                throw new ClassFormatException(
                        "Constant pool at index " + cmh.getReferenceIndex() + " has an invalid tag " + referenceTag + " for a CONSTANT_MethodHandle reference");
            }
            str = Const.getMethodHandleName(cmh.getReferenceKind()) + " " + constantToString(cmh.getReferenceIndex(), referenceTag);
            break;
        case Const.CONSTANT_MethodType:
            final ConstantMethodType cmt = (ConstantMethodType) c;
            str = constantToString(cmt.getDescriptorIndex(), Const.CONSTANT_Utf8);
            break;
        case Const.CONSTANT_InvokeDynamic:
            final ConstantInvokeDynamic cid = (ConstantInvokeDynamic) c;
            str = cid.getBootstrapMethodAttrIndex() + ":" + constantToString(cid.getNameAndTypeIndex(), Const.CONSTANT_NameAndType);
            break;
        case Const.CONSTANT_Dynamic:
            final ConstantDynamic cd = (ConstantDynamic) c;
            str = cd.getBootstrapMethodAttrIndex() + ":" + constantToString(cd.getNameAndTypeIndex(), Const.CONSTANT_NameAndType);
            break;
        case Const.CONSTANT_Module:
            i = ((ConstantModule) c).getNameIndex();
            c = getConstantUtf8(i);
            str = Utility.compactClassName(((ConstantUtf8) c).getBytes(), false);
            break;
        case Const.CONSTANT_Package:
            i = ((ConstantPackage) c).getNameIndex();
            c = getConstantUtf8(i);
            str = Utility.compactClassName(((ConstantUtf8) c).getBytes(), false);
            break;
        default: // Never reached
            throw new IllegalArgumentException("Unknown constant type " + tag);
        }
        return str;
    }

    /**
     * Retrieves constant at 'index' from constant pool and resolve it to a string representation.
     *
     * @param index of constant in constant pool.
     * @param tag   expected type.
     * @return String representation.
     */
    public String constantToString(final int index, final byte tag) {
        return constantToString(getConstant(index, tag));
    }

    /**
     * Creates a deep copy of this constant pool.
     *
     * @return deep copy of this constant pool.
     */
    public ConstantPool copy() {
        ConstantPool c = null;
        try {
            c = (ConstantPool) clone();
            c.constantPool = new Constant[constantPool.length];
            for (int i = 1; i < constantPool.length; i++) {
                if (constantPool[i] != null) {
                    c.constantPool[i] = constantPool[i].copy();
                }
            }
        } catch (final CloneNotSupportedException e) {
            // TODO should this throw?
        }
        return c;
    }

    /**
     * Dumps constant pool to file stream in binary format.
     *
     * @param file Output file stream.
     * @throws IOException Thrown if problem in writeShort or dump
     */
    public void dump(final DataOutputStream file) throws IOException {
        /*
         * Constants over the size of the constant pool shall not be written out.
         * This is a redundant measure as the ConstantPoolGen should have already
         * reported an error back in the situation.
         */
        final int size = Math.min(constantPool.length, Const.MAX_CP_ENTRIES);

        file.writeShort(size);
        for (int i = 1; i < size; i++) {
            if (constantPool[i] != null) {
                constantPool[i].dump(file);
            }
        }
    }

    /**
     * Gets constant from constant pool.
     *
     * @param <T> The type of the constant.
     * @param index Index in constant pool.
     * @return Constant value.
     * @see Constant
     * @throws ClassFormatException Thrown if index is invalid.
     */
    @SuppressWarnings("unchecked")
    public <T extends Constant> T getConstant(final int index) throws ClassFormatException {
        return (T) getConstant(index, Constant.class);
    }

    /**
     * Gets constant from constant pool and check whether it has the expected type.
     *
     * @param <T> The type of the constant.
     * @param index Index in constant pool.
     * @param tag   Tag of expected constant, that is, its type.
     * @return Constant value.
     * @see Constant
     * @throws ClassFormatException Thrown if constant type does not match tag.
     */
    @SuppressWarnings("unchecked")
    public <T extends Constant> T getConstant(final int index, final byte tag) throws ClassFormatException {
        return (T) getConstant(index, tag, Constant.class);
    }

    /**
     * Gets constant from constant pool and check whether it has the expected type.
     *
     * @param <T> The type of the constant.
     * @param index Index in constant pool.
     * @param tag   Tag of expected constant, that is, its type.
     * @param castTo The class to cast to.
     * @return Constant value.
     * @see Constant
     * @throws ClassFormatException Thrown if constant type does not match tag.
     * @since 6.6.0
     */
    public <T extends Constant> T getConstant(final int index, final byte tag, final Class<T> castTo) throws ClassFormatException {
        final T c = getConstant(index);
        if (c == null || c.getTag() != tag) {
            throw new ClassFormatException("Expected class '" + Const.getConstantName(tag) + "' at index " + index + " and got " + c);
        }
        return c;
    }

    /**
     * Gets constant from constant pool.
     *
     * @param <T> A {@link Constant} subclass.
     * @param index Index in constant pool.
     * @param castTo The {@link Constant} subclass to cast to.
     * @return Constant value.
     * @throws ClassFormatException Thrown if index is invalid.
     * @see Constant
     * @since 6.6.0
     */
    public <T extends Constant> T getConstant(final int index, final Class<T> castTo) throws ClassFormatException {
        if (index >= constantPool.length || index < 1) {
            throw new ClassFormatException("Invalid constant pool reference using index: " + index + ". Constant pool size is: " + constantPool.length);
        }
        if (constantPool[index] != null && !castTo.isAssignableFrom(constantPool[index].getClass())) {
            throw new ClassFormatException("Invalid constant pool reference at index: " + index +
                    ". Expected " + castTo + " but was " + constantPool[index].getClass());
        }
        if (index > 1) {
            final Constant prev = constantPool[index - 1];
            if (prev != null && (prev.getTag() == Const.CONSTANT_Double || prev.getTag() == Const.CONSTANT_Long)) {
                throw new ClassFormatException("Constant pool at index " + index + " is invalid. The index is unused due to the preceeding "
                        + Const.getConstantName(prev.getTag()) + ".");
            }
        }
        // Previous check ensures this won't throw a ClassCastException
        final T c = castTo.cast(constantPool[index]);
        if (c == null) {
            throw new ClassFormatException("Constant pool at index " + index + " is null.");
        }
        return c;
    }

    /**
     * Gets constant from constant pool and check whether it has the expected type.
     *
     * @param index Index in constant pool.
     * @return ConstantInteger value.
     * @throws ClassFormatException Thrown if constant type does not match tag.
     * @see ConstantInteger
     */
    public ConstantInteger getConstantInteger(final int index) {
        return getConstant(index, Const.CONSTANT_Integer, ConstantInteger.class);
    }

    /**
     * Gets the array of constants.
     *
     * @return Array of constants.
     * @see Constant
     */
    public Constant[] getConstantPool() {
        return constantPool;
    }

    /**
     * Gets string from constant pool and bypass the indirection of 'ConstantClass' and 'ConstantString' objects. I.e. these classes have an index field that
     * points to another entry of the constant pool of type 'ConstantUtf8' which contains the real data.
     *
     * @param index Index in constant pool.
     * @param tag   Tag of expected constant, either ConstantClass or ConstantString.
     * @return Contents of string reference.
     * @throws IllegalArgumentException Thrown if tag is invalid.
     * @see ConstantClass
     * @see ConstantString
     */
    public String getConstantString(final int index, final byte tag) throws IllegalArgumentException {
        final int i;
        /*
         * This switch() is not that elegant, since the four classes have the same contents, they just differ in the name of the index field variable. But we
         * want to stick to the JVM naming conventions closely though we could have solved these more elegantly by using the same variable name or by
         * subclassing.
         */
        switch (tag) {
        case Const.CONSTANT_Class:
            i = getConstant(index, ConstantClass.class).getNameIndex();
            break;
        case Const.CONSTANT_String:
            i = getConstant(index, ConstantString.class).getStringIndex();
            break;
        case Const.CONSTANT_Module:
            i = getConstant(index, ConstantModule.class).getNameIndex();
            break;
        case Const.CONSTANT_Package:
            i = getConstant(index, ConstantPackage.class).getNameIndex();
            break;
        case Const.CONSTANT_Utf8:
            return getConstantUtf8(index).getBytes();
        default:
            throw new IllegalArgumentException("getConstantString called with illegal tag " + tag);
        }
        // Finally get the string from the constant pool
        return getConstantUtf8(i).getBytes();
    }

    /**
     * Gets constant from constant pool and check whether it has the expected type.
     *
     * @param index Index in constant pool.
     * @return ConstantUtf8 value.
     * @throws ClassFormatException Thrown if constant type does not match tag.
     * @see ConstantUtf8
     */
    public ConstantUtf8 getConstantUtf8(final int index) throws ClassFormatException {
        return getConstant(index, Const.CONSTANT_Utf8, ConstantUtf8.class);
    }

    /**
     * Gets the length of constant pool.
     *
     * @return Length of constant pool.
     */
    public int getLength() {
        return constantPool.length;
    }

    @Override
    public Iterator<Constant> iterator() {
        return Arrays.stream(constantPool).iterator();
    }

    /**
     * Sets a constant at the specified index.
     *
     * @param index The index in the constant pool.
     * @param constant Constant to set.
     */
    public void setConstant(final int index, final Constant constant) {
        constantPool[index] = constant;
    }

    /**
     * Sets the constant pool.
     *
     * @param constantPool The constant pool array.
     */
    public void setConstantPool(final Constant[] constantPool) {
        this.constantPool = constantPool != null ? constantPool : Constant.EMPTY_ARRAY;
    }

    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        for (int i = 1; i < constantPool.length; i++) {
            buf.append(i).append(")").append(constantPool[i]).append("\n");
        }
        return buf.toString();
    }
}
