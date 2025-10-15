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
import java.util.Collections;
import java.util.List;

/**
 * represents one parameter annotation in the parameter annotation table
 *
 * @since 6.0
 */
public class ParameterAnnotationEntry implements Node {

    static final ParameterAnnotationEntry[] EMPTY_ARRAY = {};

    public static ParameterAnnotationEntry[] createParameterAnnotationEntries(final Attribute[] attributes) {
        if (attributes == null) {
            return EMPTY_ARRAY;
        }
        // Find attributes that contain parameter annotation data
        final List<ParameterAnnotationEntry> accumulatedAnnotations = new ArrayList<>(attributes.length);
        for (final Attribute attribute : attributes) {
            if (attribute instanceof ParameterAnnotations) {
                final ParameterAnnotations runtimeAnnotations = (ParameterAnnotations) attribute;
                final ParameterAnnotationEntry[] parameterAnnotationEntries = runtimeAnnotations.getParameterAnnotationEntries();
                if (parameterAnnotationEntries != null) {
                    Collections.addAll(accumulatedAnnotations, parameterAnnotationEntries);
                }
            }
        }
        return accumulatedAnnotations.toArray(EMPTY_ARRAY);
    }

    private final AnnotationEntry[] annotationTable;

    /**
     * Constructs object from input stream.
     *
     * @param input Input stream
     * @throws IOException if an I/O error occurs.
     */
    ParameterAnnotationEntry(final DataInput input, final ConstantPool constantPool) throws IOException {
        final int annotationTableLength = input.readUnsignedShort();
        annotationTable = new AnnotationEntry[annotationTableLength];
        for (int i = 0; i < annotationTableLength; i++) {
            // TODO isRuntimeVisible
            annotationTable[i] = AnnotationEntry.read(input, constantPool, false);
        }
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitParameterAnnotationEntry(this);
    }

    public void dump(final DataOutputStream dos) throws IOException {
        dos.writeShort(annotationTable.length);
        for (final AnnotationEntry entry : annotationTable) {
            entry.dump(dos);
        }
    }

    /**
     * returns the array of annotation entries in this annotation
     */
    public AnnotationEntry[] getAnnotationEntries() {
        return annotationTable;
    }
}
