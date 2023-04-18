/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
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

/**
 * Represents one annotation in the annotation table
 *
 * @since 6.0
 */
public class AnnotationEntry implements Node {

    public static final AnnotationEntry[] EMPTY_ARRAY = {};

    public static AnnotationEntry[] createAnnotationEntries(final Attribute[] attrs) {
        // Find attributes that contain annotation data
        return Stream.of(attrs).filter(Annotations.class::isInstance).flatMap(e -> Stream.of(((Annotations) e).getAnnotationEntries()))
            .toArray(AnnotationEntry[]::new);
    }

    /**
     * Factory method to create an AnnotionEntry from a DataInput
     *
     * @param input
     * @param constantPool
     * @param isRuntimeVisible
     * @return the entry
     * @throws IOException if an I/O error occurs.
     */
    public static AnnotationEntry read(final DataInput input, final ConstantPool constantPool, final boolean isRuntimeVisible) throws IOException {
        final AnnotationEntry annotationEntry = new AnnotationEntry(input.readUnsignedShort(), constantPool, isRuntimeVisible);
        final int numElementValuePairs = input.readUnsignedShort();
        annotationEntry.elementValuePairs = new ArrayList<>();
        for (int i = 0; i < numElementValuePairs; i++) {
            annotationEntry.elementValuePairs
                .add(new ElementValuePair(input.readUnsignedShort(), ElementValue.readElementValue(input, constantPool), constantPool));
        }
        return annotationEntry;
    }

    private final int typeIndex;

    private final ConstantPool constantPool;

    private final boolean isRuntimeVisible;

    private List<ElementValuePair> elementValuePairs;

    public AnnotationEntry(final int typeIndex, final ConstantPool constantPool, final boolean isRuntimeVisible) {
        this.typeIndex = typeIndex;
        this.constantPool = constantPool;
        this.isRuntimeVisible = isRuntimeVisible;
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitAnnotationEntry(this);
    }

    public void addElementNameValuePair(final ElementValuePair elementNameValuePair) {
        elementValuePairs.add(elementNameValuePair);
    }

    public void dump(final DataOutputStream dos) throws IOException {
        dos.writeShort(typeIndex); // u2 index of type name in cpool
        dos.writeShort(elementValuePairs.size()); // u2 element_value pair
        // count
        for (final ElementValuePair envp : elementValuePairs) {
            envp.dump(dos);
        }
    }

    /**
     * @return the annotation type name
     */
    public String getAnnotationType() {
        return constantPool.getConstantUtf8(typeIndex).getBytes();
    }

    /**
     * @return the annotation type index
     */
    public int getAnnotationTypeIndex() {
        return typeIndex;
    }

    public ConstantPool getConstantPool() {
        return constantPool;
    }

    /**
     * @return the element value pairs in this annotation entry
     */
    public ElementValuePair[] getElementValuePairs() {
        // TODO return List
        return elementValuePairs.toArray(ElementValuePair.EMPTY_ARRAY);
    }

    /**
     * @return the number of element value pairs in this annotation entry
     */
    public final int getNumElementValuePairs() {
        return elementValuePairs.size();
    }

    public int getTypeIndex() {
        return typeIndex;
    }

    public boolean isRuntimeVisible() {
        return isRuntimeVisible;
    }

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
