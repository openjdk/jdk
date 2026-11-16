/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.sun.org.apache.bcel.internal.util.Args;
import jdk.xml.internal.Utils;

/**
 * Represents one annotation in the annotation table
 *
 * @since 6.0
 * @LastModified: Nov 2026
 */
public class AnnotationEntry implements Node {

    /**
     * Empty array of AnnotationEntry objects.
     */
    public static final AnnotationEntry[] EMPTY_ARRAY = {};

    /**
     * Creates annotation entries from attributes.
     *
     * @param attributes The attributes.
     * @return The annotation entries.
     */
    public static AnnotationEntry[] createAnnotationEntries(final Attribute[] attributes) {
        // Find attributes that contain annotation data
        return Utils.streamOfIfNonNull(attributes).filter(Annotations.class::isInstance).flatMap(e -> Stream.of(((Annotations) e).getAnnotationEntries()))
                .toArray(AnnotationEntry[]::new);
    }

    /**
     * Factory method to create an AnnotionEntry from a DataInput.
     *
     * @param input The input stream.
     * @param constantPool The constant pool.
     * @param isRuntimeVisible whether the annotation is runtime visible.
     * @return The entry.
     * @throws IOException Thrown if an I/O error occurs.
     */
    public static AnnotationEntry read(final DataInput input, final ConstantPool constantPool, final boolean isRuntimeVisible) throws IOException {
        return read(input, constantPool, isRuntimeVisible, 0);
    }

    /**
     * Factory method to create an AnnotionEntry from a DataInput, carrying the nesting depth of the enclosing element values so that
     * {@link ElementValue#readElementValue(DataInput, ConstantPool, boolean, int)} can bound the combined annotation/array nesting depth.
     *
     * @param input The input stream.
     * @param constantPool The constant pool.
     * @param isRuntimeVisible whether the annotation is runtime visible.
     * @param nesting the current element value nesting level.
     * @return The entry.
     * @throws IOException Thrown if an I/O error occurs.
     */
    static AnnotationEntry read(final DataInput input, final ConstantPool constantPool, final boolean isRuntimeVisible, final int nesting)
            throws IOException {
        final AnnotationEntry annotationEntry = new AnnotationEntry(input.readUnsignedShort(), constantPool, isRuntimeVisible);
        final int numElementValuePairs = input.readUnsignedShort();
        for (int i = 0; i < numElementValuePairs; i++) {
            annotationEntry.elementValuePairs.add(
                new ElementValuePair(input.readUnsignedShort(), ElementValue.readElementValue(input, constantPool, isRuntimeVisible, nesting), constantPool));
        }
        return annotationEntry;
    }

    private final int typeIndex;

    private final ConstantPool constantPool;

    private final boolean isRuntimeVisible;

    private final List<ElementValuePair> elementValuePairs;

    /**
     * Constructs an AnnotationEntry.
     *
     * @param typeIndex The type index.
     * @param constantPool The constant pool.
     * @param isRuntimeVisible whether the annotation is runtime visible.
     */
    public AnnotationEntry(final int typeIndex, final ConstantPool constantPool, final boolean isRuntimeVisible) {
        this.typeIndex = typeIndex;
        this.constantPool = constantPool;
        this.isRuntimeVisible = isRuntimeVisible;
        this.elementValuePairs = new ArrayList<>();
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object.
     */
    @Override
    public void accept(final Visitor v) {
        v.visitAnnotationEntry(this);
    }

    /**
     * Adds an element name value pair.
     *
     * @param elementNameValuePair The element name value pair.
     */
    public void addElementNameValuePair(final ElementValuePair elementNameValuePair) {
        elementValuePairs.add(elementNameValuePair);
    }

    /**
     * Dumps this annotation entry to a DataOutputStream.
     *
     * @param dos The output stream.
     * @throws IOException Thrown if an I/O error occurs.
     */
    public void dump(final DataOutputStream dos) throws IOException {
        dos.writeShort(typeIndex); // u2 index of type name in cpool
        dos.writeShort(Args.requireU2(elementValuePairs.size(), "elementValuePairs.size()")); // u2 element_value pair
        // count
        for (final ElementValuePair envp : elementValuePairs) {
            envp.dump(dos);
        }
    }

    /**
     * Gets the annotation type name.
     *
     * @return The annotation type name.
     */
    public String getAnnotationType() {
        return constantPool.getConstantUtf8(typeIndex).getBytes();
    }

    /**
     * Gets the annotation type index.
     *
     * @return The annotation type index.
     */
    public int getAnnotationTypeIndex() {
        return typeIndex;
    }

    /**
     * Gets the constant pool.
     *
     * @return The constant pool.
     */
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    /**
     * Gets the element value pairs in this annotation entry.
     *
     * @return The element value pairs in this annotation entry.
     */
    public ElementValuePair[] getElementValuePairs() {
        // TODO return List
        return elementValuePairs.toArray(ElementValuePair.EMPTY_ARRAY);
    }

    /**
     * Gets the number of element value pairs in this annotation entry.
     *
     * @return The number of element value pairs in this annotation entry.
     */
    public final int getNumElementValuePairs() {
        return elementValuePairs.size();
    }

    /**
     * Gets the type index.
     *
     * @return The type index.
     */
    public int getTypeIndex() {
        return typeIndex;
    }

    /**
     * Gets whether this annotation is runtime visible.
     *
     * @return true if this annotation is runtime visible.
     */
    public boolean isRuntimeVisible() {
        return isRuntimeVisible;
    }

    /**
     * Gets a short string representation of this annotation.
     *
     * @return A short string representation of this annotation.
     */
    public String toShortString() {
        final StringBuilder result = new StringBuilder();
        result.append("@");
        result.append(getAnnotationType());
        final ElementValuePair[] evPairs = getElementValuePairs();
        if (evPairs.length > 0) {
            result.append("(");
            for (final ElementValuePair element : evPairs) {
                result.append(element.toShortString());
                result.append(", ");
            }
            // remove last ", "
            result.setLength(result.length() - 2);
            result.append(")");
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return toShortString();
    }
}
