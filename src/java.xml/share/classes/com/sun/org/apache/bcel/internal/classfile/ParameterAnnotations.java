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

/**
 * base class for parameter annotations
 *
 * @since 6.0
 */
public abstract class ParameterAnnotations extends Attribute {

    /** Table of parameter annotations */
    private ParameterAnnotationEntry[] parameter_annotation_table;

    /**
     * @param parameter_annotation_type the subclass type of the parameter annotation
     * @param name_index Index pointing to the name <em>Code</em>
     * @param length Content length in bytes
     * @param input Input stream
     * @param constant_pool Array of constants
     */
    ParameterAnnotations(final byte parameter_annotation_type, final int name_index, final int length,
            final DataInput input, final ConstantPool constant_pool) throws IOException {
        this(parameter_annotation_type, name_index, length, (ParameterAnnotationEntry[]) null,
                constant_pool);
        final int num_parameters = input.readUnsignedByte();
        parameter_annotation_table = new ParameterAnnotationEntry[num_parameters];
        for (int i = 0; i < num_parameters; i++) {
            parameter_annotation_table[i] = new ParameterAnnotationEntry(input, constant_pool);
        }
    }


    /**
     * @param parameter_annotation_type the subclass type of the parameter annotation
     * @param name_index Index pointing to the name <em>Code</em>
     * @param length Content length in bytes
     * @param parameter_annotation_table the actual parameter annotations
     * @param constant_pool Array of constants
     */
    public ParameterAnnotations(final byte parameter_annotation_type, final int name_index, final int length,
            final ParameterAnnotationEntry[] parameter_annotation_table, final ConstantPool constant_pool) {
        super(parameter_annotation_type, name_index, length, constant_pool);
        this.parameter_annotation_table = parameter_annotation_table;
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
        v.visitParameterAnnotation(this);
    }


    /**
     * @param parameter_annotation_table the entries to set in this parameter annotation
     */
    public final void setParameterAnnotationTable(final ParameterAnnotationEntry[] parameter_annotation_table ) {
        this.parameter_annotation_table = parameter_annotation_table;
    }


    /**
     * @return the parameter annotation entry table
     */
    public final ParameterAnnotationEntry[] getParameterAnnotationTable() {
        return parameter_annotation_table;
    }


    /**
     * returns the array of parameter annotation entries in this parameter annotation
     */
    public ParameterAnnotationEntry[] getParameterAnnotationEntries() {
        return parameter_annotation_table;
    }

    @Override
    public void dump(final DataOutputStream dos) throws IOException
    {
        super.dump(dos);
        dos.writeByte(parameter_annotation_table.length);

        for (final ParameterAnnotationEntry element : parameter_annotation_table) {
            element.dump(dos);
        }

    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( final ConstantPool constant_pool ) {
        return (Attribute) clone();
    }
}
