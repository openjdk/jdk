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

package com.sun.org.apache.bcel.internal.generic;

import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.classfile.AnnotationElementValue;
import com.sun.org.apache.bcel.internal.classfile.ElementValue;

/**
 * Generates annotation element values.
 *
 * @since 6.0
 */
public class AnnotationElementValueGen extends ElementValueGen {
    // For annotation element values, this is the annotation
    private final AnnotationEntryGen a;

    /**
     * Constructs an AnnotationElementValueGen.
     *
     * @param value The annotation element value.
     * @param cpool The constant pool generator.
     * @param copyPoolEntries whether to copy pool entries.
     */
    public AnnotationElementValueGen(final AnnotationElementValue value, final ConstantPoolGen cpool, final boolean copyPoolEntries) {
        super(ANNOTATION, cpool);
        a = new AnnotationEntryGen(value.getAnnotationEntry(), cpool, copyPoolEntries);
    }

    /**
     * Constructs an AnnotationElementValueGen.
     *
     * @param a The annotation entry generator.
     * @param cpool The constant pool generator.
     */
    public AnnotationElementValueGen(final AnnotationEntryGen a, final ConstantPoolGen cpool) {
        super(ANNOTATION, cpool);
        this.a = a;
    }

    /**
     * Constructs an AnnotationElementValueGen.
     *
     * @param type The type.
     * @param annotation The annotation.
     * @param cpool The constant pool generator.
     */
    public AnnotationElementValueGen(final int type, final AnnotationEntryGen annotation, final ConstantPoolGen cpool) {
        super(type, cpool);
        if (type != ANNOTATION) {
            throw new IllegalArgumentException("Only element values of type annotation can be built with this ctor - type specified: " + type);
        }
        this.a = annotation;
    }

    @Override
    public void dump(final DataOutputStream dos) throws IOException {
        dos.writeByte(super.getElementValueType()); // u1 type of value (ANNOTATION == '@')
        a.dump(dos);
    }

    /**
     * Gets the annotation.
     *
     * @return The annotation.
     */
    public AnnotationEntryGen getAnnotation() {
        return a;
    }

    /**
     * Returns an immutable variant of this AnnotationElementValueGen.
     *
     * @return An immutable variant of this AnnotationElementValueGen.
     */
    @Override
    public ElementValue getElementValue() {
        return new AnnotationElementValue(super.getElementValueType(), a.getAnnotation(), getConstantPool().getConstantPool());
    }

    @Override
    public String stringifyValue() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
