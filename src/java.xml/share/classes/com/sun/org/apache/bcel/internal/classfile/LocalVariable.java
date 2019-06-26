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
 * scope, name, signature and index on the method's frame.
 *
 * @version $Id$
 * @see     LocalVariableTable
 * @LastModified: Jun 2019
 */
public final class LocalVariable implements Cloneable, Node {

    private int start_pc; // Range in which the variable is valid
    private int length;
    private int name_index; // Index in constant pool of variable name
    private int signature_index; // Index of variable signature
    private int index; /* Variable is `index'th local variable on
     * this method's frame.
     */
    private ConstantPool constant_pool;
    private int orig_index; // never changes; used to match up with LocalVariableTypeTable entries


    /**
     * Initialize from another object. Note that both objects use the same
     * references (shallow copy). Use copy() for a physical copy.
     */
    public LocalVariable(final LocalVariable c) {
        this(c.getStartPC(), c.getLength(), c.getNameIndex(), c.getSignatureIndex(), c.getIndex(),
                c.getConstantPool());
        this.orig_index = c.getOrigIndex();
    }


    /**
     * Construct object from file stream.
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
     * Dump local variable to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    public final void dump( final DataOutputStream file ) throws IOException {
        file.writeShort(start_pc);
        file.writeShort(length);
        file.writeShort(name_index);
        file.writeShort(signature_index);
        file.writeShort(index);
    }


    /**
     * @return Constant pool used by this object.
     */
    public final ConstantPool getConstantPool() {
        return constant_pool;
    }


    /**
     * @return Variable is valid within getStartPC() .. getStartPC()+getLength()
     */
    public final int getLength() {
        return length;
    }


    /**
     * @return Variable name.
     */
    public final String getName() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(name_index, Const.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return Index in constant pool of variable name.
     */
    public final int getNameIndex() {
        return name_index;
    }


    /**
     * @return Signature.
     */
    public final String getSignature() {
        ConstantUtf8 c;
        c = (ConstantUtf8) constant_pool.getConstant(signature_index, Const.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return Index in constant pool of variable signature.
     */
    public final int getSignatureIndex() {
        return signature_index;
    }


    /**
     * @return index of register where variable is stored
     */
    public final int getIndex() {
        return index;
    }


    /**
     * @return index of register where variable was originally stored
     */
    public final int getOrigIndex() {
        return orig_index;
    }


    /**
     * @return Start of range where he variable is valid
     */
    public final int getStartPC() {
        return start_pc;
    }


    /*
     * Helper method shared with LocalVariableTypeTable
     */
    final String toStringShared( final boolean typeTable ) {
        final String name = getName();
        final String signature = Utility.signatureToString(getSignature(), false);
        final String label = "LocalVariable" + (typeTable ? "Types" : "" );
        return label + "(start_pc = " + start_pc + ", length = " + length + ", index = "
                + index + ":" + signature + " " + name + ")";
    }


    /**
     * @param constant_pool Constant pool to be used for this object.
     */
    public final void setConstantPool( final ConstantPool constant_pool ) {
        this.constant_pool = constant_pool;
    }


    /**
     * @param length the length of this local variable
     */
    public final void setLength( final int length ) {
        this.length = length;
    }


    /**
     * @param name_index the index into the constant pool for the name of this variable
     */
    public final void setNameIndex( final int name_index ) { // TODO unused
        this.name_index = name_index;
    }


    /**
     * @param signature_index the index into the constant pool for the signature of this variable
     */
    public final void setSignatureIndex( final int signature_index ) { // TODO unused
        this.signature_index = signature_index;
    }


    /**
     * @param index the index in the local variable table of this variable
     */
    public final void setIndex( final int index ) { // TODO unused
        this.index = index;
    }


    /**
     * @param start_pc Specify range where the local variable is valid.
     */
    public final void setStartPC( final int start_pc ) { // TODO unused
        this.start_pc = start_pc;
    }


    /**
     * @return string representation.
     */
    @Override
    public final String toString() {
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
