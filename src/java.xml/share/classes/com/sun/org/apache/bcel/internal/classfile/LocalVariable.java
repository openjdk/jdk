/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * This class represents a local variable within a method. It contains its
 * scope, name, signature and index on the method's frame.  It is used both
 * to represent an element of the LocalVariableTable as well as an element
 * of the LocalVariableTypeTable.  The nomenclature used here may be a bit confusing;
 * while the two items have the same layout in a class file, a LocalVariableTable
 * attribute contains a descriptor_index, not a signature_index.  The
 * LocalVariableTypeTable attribute does have a signature_index.
 * @see com.sun.org.apache.bcel.internal.classfile.Utility for more details on the difference.
 *
 * @see     LocalVariableTable
 * @see     LocalVariableTypeTable
 * @LastModified: Jan 2020
 */
public final class LocalVariable implements Cloneable, Node {

    private int start_pc; // Range in which the variable is valid
    private int length;
    private int name_index; // Index in constant pool of variable name
    // Technically, a decscriptor_index for a local variable table entry
    // and a signature_index for a local variable type table entry.
    private int signature_index; // Index of variable signature
    private int index; /* Variable is index'th local variable on
     * this method's frame.
     */
    private ConstantPool constant_pool;
    private int orig_index; // never changes; used to match up with LocalVariableTypeTable entries


    /**
     * Initializes from another LocalVariable. Note that both objects use the same
     * references (shallow copy). Use copy() for a physical copy.
     *
     * @param localVariable Another LocalVariable.
     */
    public LocalVariable(final LocalVariable localVariable) {
        this(localVariable.getStartPC(), localVariable.getLength(), localVariable.getNameIndex(),
                localVariable.getSignatureIndex(), localVariable.getIndex(), localVariable.getConstantPool());
        this.orig_index = localVariable.getOrigIndex();
    }

    /**
     * Constructs object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    LocalVariable(final DataInput file, final ConstantPool constant_pool) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort(), file
                .readUnsignedShort(), file.readUnsignedShort(), constant_pool);
    }


    /**
     * @param start_pc Range in which the variable
     * @param length ... is valid
     * @param name_index Index in constant pool of variable name
     * @param signature_index Index of variable's signature
     * @param index Variable is `index'th local variable on the method's frame
     * @param constant_pool Array of constants
     */
    public LocalVariable(final int start_pc, final int length, final int name_index, final int signature_index, final int index,
            final ConstantPool constant_pool) {
        this.start_pc = start_pc;
        this.length = length;
        this.name_index = name_index;
        this.signature_index = signature_index;
        this.index = index;
        this.constant_pool = constant_pool;
        this.orig_index = index;
    }


    /**
     * @param start_pc Range in which the variable
     * @param length ... is valid
     * @param name_index Index in constant pool of variable name
     * @param signature_index Index of variable's signature
     * @param index Variable is `index'th local variable on the method's frame
     * @param constant_pool Array of constants
     * @param orig_index Variable is `index'th local variable on the method's frame prior to any changes
     */
    public LocalVariable(final int start_pc, final int length, final int name_index, final int signature_index, final int index,
            final ConstantPool constant_pool, final int orig_index) {
        this.start_pc = start_pc;
        this.length = length;
        this.name_index = name_index;
        this.signature_index = signature_index;
        this.index = index;
        this.constant_pool = constant_pool;
        this.orig_index = orig_index;
    }


    /**
     * Called by objects that are traversing the nodes of the tree implicitely
     * defined by the contents of a Java class. I.e., the hierarchy of methods,
     * fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept( final Visitor v ) {
        v.visitLocalVariable(this);
    }


    /**
     * Dumps local variable to file stream in binary format.
     *
     * @param dataOutputStream Output file stream
     * @exception IOException if an I/O error occurs.
     * @see java.io.FilterOutputStream#out
     */
    public void dump(final DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeShort(start_pc);
        dataOutputStream.writeShort(length);
        dataOutputStream.writeShort(name_index);
        dataOutputStream.writeShort(signature_index);
        dataOutputStream.writeShort(index);
    }

    /**
     * @return Constant pool used by this object.
     */
    public ConstantPool getConstantPool() {
        return constant_pool;
    }


    /**
     * @return Variable is valid within getStartPC() .. getStartPC()+getLength()
     */
    public int getLength() {
        return length;
    }


    /**
     * @return Variable name.
     */
    public String getName() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(name_index, Const.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return Index in constant pool of variable name.
     */
    public int getNameIndex() {
        return name_index;
    }


    /**
     * @return Signature.
     */
    public String getSignature() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(signature_index, Const.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return Index in constant pool of variable signature.
     */
    public int getSignatureIndex() {
        return signature_index;
    }


    /**
     * @return index of register where variable is stored
     */
    public int getIndex() {
        return index;
    }


    /**
     * @return index of register where variable was originally stored
     */
    public int getOrigIndex() {
        return orig_index;
    }


    /**
     * @return Start of range where the variable is valid
     */
    public int getStartPC() {
        return start_pc;
    }


    /*
     * Helper method shared with LocalVariableTypeTable
     */
    String toStringShared( final boolean typeTable ) {
        final String name = getName();
        final String signature = Utility.signatureToString(getSignature(), false);
        final String label = "LocalVariable" + (typeTable ? "Types" : "" );
        return label + "(start_pc = " + start_pc + ", length = " + length + ", index = "
                + index + ":" + signature + " " + name + ")";
    }


    /**
     * @param constant_pool Constant pool to be used for this object.
     */
    public void setConstantPool( final ConstantPool constant_pool ) {
        this.constant_pool = constant_pool;
    }


    /**
     * @param length the length of this local variable
     */
    public void setLength( final int length ) {
        this.length = length;
    }


    /**
     * @param name_index the index into the constant pool for the name of this variable
     */
    public void setNameIndex( final int name_index ) { // TODO unused
        this.name_index = name_index;
    }


    /**
     * @param signature_index the index into the constant pool for the signature of this variable
     */
    public void setSignatureIndex( final int signature_index ) { // TODO unused
        this.signature_index = signature_index;
    }


    /**
     * @param index the index in the local variable table of this variable
     */
    public void setIndex( final int index ) { // TODO unused
        this.index = index;
    }


    /**
     * @param start_pc Specify range where the local variable is valid.
     */
    public void setStartPC( final int start_pc ) { // TODO unused
        this.start_pc = start_pc;
    }


    /**
     * @return string representation.
     */
    @Override
    public String toString() {
        return toStringShared(false);
    }


    /**
     * @return deep copy of this object
     */
    public LocalVariable copy() {
        try {
            return (LocalVariable) clone();
        } catch (final CloneNotSupportedException e) {
            // TODO should this throw?
        }
        return null;
    }
}
