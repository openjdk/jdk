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
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Stream;

import com.sun.org.apache.bcel.internal.Const;

/**
 * This class represents a MethodParameters attribute.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.24"> The class File Format :
 *      The MethodParameters Attribute</a>
 * @since 6.0
 */
public class MethodParameters extends Attribute implements Iterable<MethodParameter> {

    /**
     * Empty array.
     */
    private static final MethodParameter[] EMPTY_METHOD_PARAMETER_ARRAY = {};

    private MethodParameter[] parameters = EMPTY_METHOD_PARAMETER_ARRAY;

    MethodParameters(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        super(Const.ATTR_METHOD_PARAMETERS, nameIndex, length, constantPool);

        final int parameterCount = input.readUnsignedByte();
        parameters = new MethodParameter[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            parameters[i] = new MethodParameter(input);
        }
    }

    @Override
    public void accept(final Visitor v) {
        v.visitMethodParameters(this);
    }

    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final MethodParameters c = (MethodParameters) clone();
        c.parameters = new MethodParameter[parameters.length];

        Arrays.setAll(c.parameters, i -> parameters[i].copy());
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump method parameters attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeByte(parameters.length);
        for (final MethodParameter parameter : parameters) {
            parameter.dump(file);
        }
    }

    public MethodParameter[] getParameters() {
        return parameters;
    }

    @Override
    public Iterator<MethodParameter> iterator() {
        return Stream.of(parameters).iterator();
    }

    public void setParameters(final MethodParameter[] parameters) {
        this.parameters = parameters;
    }
}
