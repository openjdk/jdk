/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Abstract super class for fields and methods.
 *
 * @LastModified: Jan 2020
 */
public abstract class FieldOrMethod extends AccessFlags implements Cloneable, Node {

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @java.lang.Deprecated
    protected int name_index; // Points to field name in constant pool

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @java.lang.Deprecated
    protected int signature_index; // Points to encoded signature

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @java.lang.Deprecated
    protected Attribute[] attributes; // Collection of attributes

    /**
     * @deprecated (since 6.0) will be removed (not needed)
     */
    @java.lang.Deprecated
    protected int attributes_count; // No. of attributes

    // @since 6.0
    private AnnotationEntry[] annotationEntries; // annotations defined on the field or method

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @java.lang.Deprecated
    protected ConstantPool constant_pool;

    private String signatureAttributeString;
    private boolean searchedForSignatureAttribute;

    FieldOrMethod() {
    }

    /**
     * Construct object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O error occurs.
     */
    protected FieldOrMethod(final DataInput file, final ConstantPool constantPool) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort(), null, constantPool);
        final int attributesCount = file.readUnsignedShort();
        attributes = new Attribute[attributesCount];
        for (int i = 0; i < attributesCount; i++) {
            attributes[i] = Attribute.readAttribute(file, constantPool);
        }
        this.attributes_count = attributesCount; // init deprecated field
    }

    /**
     * Construct object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O error occurs.
     * @deprecated (6.0) Use {@link #FieldOrMethod(java.io.DataInput, ConstantPool)} instead.
     */
    @java.lang.Deprecated
    protected FieldOrMethod(final DataInputStream file, final ConstantPool constantPool) throws IOException {
        this((DataInput) file, constantPool);
    }

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use clone() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    protected FieldOrMethod(final FieldOrMethod c) {
        this(c.getAccessFlags(), c.getNameIndex(), c.getSignatureIndex(), c.getAttributes(), c.getConstantPool());
    }

    /**
     * @param accessFlags Access rights of method
     * @param nameIndex Points to field name in constant pool
     * @param signatureIndex Points to encoded signature
     * @param attributes Collection of attributes
     * @param constantPool Array of constants
     */
    protected FieldOrMethod(final int accessFlags, final int nameIndex, final int signatureIndex, final Attribute[] attributes,
        final ConstantPool constantPool) {
        super(accessFlags);
        this.name_index = nameIndex;
        this.signature_index = signatureIndex;
        this.constant_pool = constantPool;
        setAttributes(attributes);
    }

    /**
     * @return deep copy of this field
     */
    protected FieldOrMethod copy_(final ConstantPool constantPool) {
        try {
            final FieldOrMethod c = (FieldOrMethod) clone();
            c.constant_pool = constantPool;
            c.attributes = new Attribute[attributes.length];
            c.attributes_count = attributes_count; // init deprecated field
            Arrays.setAll(c.attributes, i -> attributes[i].copy(constantPool));
            return c;
        } catch (final CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Dump object to file stream on binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    public final void dump(final DataOutputStream file) throws IOException {
        file.writeShort(super.getAccessFlags());
        file.writeShort(name_index);
        file.writeShort(signature_index);
        file.writeShort(attributes_count);
        if (attributes != null) {
            for (final Attribute attribute : attributes) {
                attribute.dump(file);
            }
        }
    }

    /**
     * @return Annotations on the field or method
     * @since 6.0
     */
    public AnnotationEntry[] getAnnotationEntries() {
        if (annotationEntries == null) {
            annotationEntries = AnnotationEntry.createAnnotationEntries(getAttributes());
        }

        return annotationEntries;
    }

    /**
     * @return Collection of object attributes.
     */
    public final Attribute[] getAttributes() {
        return attributes;
    }

    /**
     * @return Constant pool used by this object.
     */
    public final ConstantPool getConstantPool() {
        return constant_pool;
    }

    /**
     * Hunts for a signature attribute on the member and returns its contents. So where the 'regular' signature may be
     * (Ljava/util/Vector;)V the signature attribute may in fact say 'Ljava/lang/Vector&lt;Ljava/lang/String&gt;;' Coded for
     * performance - searches for the attribute only when requested - only searches for it once.
     *
     * @since 6.0
     */
    public final String getGenericSignature() {
        if (!searchedForSignatureAttribute) {
            boolean found = false;
            for (int i = 0; !found && i < attributes.length; i++) {
                if (attributes[i] instanceof Signature) {
                    signatureAttributeString = ((Signature) attributes[i]).getSignature();
                    found = true;
                }
            }
            searchedForSignatureAttribute = true;
        }
        return signatureAttributeString;
    }

    /**
     * @return Name of object, i.e., method name or field name
     */
    public final String getName() {
        return constant_pool.getConstantUtf8(name_index).getBytes();
    }

    /**
     * @return Index in constant pool of object's name.
     */
    public final int getNameIndex() {
        return name_index;
    }

    /**
     * @return String representation of object's type signature (java style)
     */
    public final String getSignature() {
        return constant_pool.getConstantUtf8(signature_index).getBytes();
    }

    /**
     * @return Index in constant pool of field signature.
     */
    public final int getSignatureIndex() {
        return signature_index;
    }

    /**
     * @param attributes Collection of object attributes.
     */
    public final void setAttributes(final Attribute[] attributes) {
        this.attributes = attributes;
        this.attributes_count = attributes != null ? attributes.length : 0; // init deprecated field
    }

    /**
     * @param constantPool Constant pool to be used for this object.
     */
    public final void setConstantPool(final ConstantPool constantPool) {
        this.constant_pool = constantPool;
    }

    /**
     * @param nameIndex Index in constant pool of object's name.
     */
    public final void setNameIndex(final int nameIndex) {
        this.name_index = nameIndex;
    }

    /**
     * @param signatureIndex Index in constant pool of field signature.
     */
    public final void setSignatureIndex(final int signatureIndex) {
        this.signature_index = signatureIndex;
    }
}
