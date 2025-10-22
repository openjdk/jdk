/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.org.apache.bcel.internal.Const;
import jdk.xml.internal.Utils;

/**
 * This class represents a bootstrap method attribute, i.e., the bootstrap method ref, the number of bootstrap arguments
 * and an array of the bootstrap arguments.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.23"> The class File Format :
 *      The BootstrapMethods Attribute</a>
 * @since 6.0
 * @LastModified: Sept 2025
 */
public class BootstrapMethod implements Cloneable {

    static final BootstrapMethod[] EMPTY_ARRAY = {};

    /** Index of the CONSTANT_MethodHandle_info structure in the constant_pool table */
    private int bootstrapMethodRef;

    /** Array of references to the constant_pool table */
    private int[] bootstrapArguments;

    /**
     * Initialize from another object.
     *
     * @param c Source to copy.
     */
    public BootstrapMethod(final BootstrapMethod c) {
        this(c.getBootstrapMethodRef(), c.getBootstrapArguments());
    }

    /**
     * Constructs object from input stream.
     *
     * @param input Input stream
     * @throws IOException if an I/O error occurs.
     */
    BootstrapMethod(final DataInput input) throws IOException {
        this(input.readUnsignedShort(), input.readUnsignedShort());

        for (int i = 0; i < bootstrapArguments.length; i++) {
            bootstrapArguments[i] = input.readUnsignedShort();
        }
    }

    // helper method
    private BootstrapMethod(final int bootstrapMethodRef, final int numBootstrapArguments) {
        this(bootstrapMethodRef, new int[numBootstrapArguments]);
    }

    /**
     * @param bootstrapMethodRef int index into constant_pool of CONSTANT_MethodHandle
     * @param bootstrapArguments int[] indices into constant_pool of CONSTANT_[type]_info
     */
    public BootstrapMethod(final int bootstrapMethodRef, final int[] bootstrapArguments) {
        this.bootstrapMethodRef = bootstrapMethodRef;
        setBootstrapArguments(bootstrapArguments);
    }

    /**
     * @return deep copy of this object
     */
    public BootstrapMethod copy() {
        try {
            return (BootstrapMethod) clone();
        } catch (final CloneNotSupportedException ignore) {
            // TODO should this throw?
        }
        return null;
    }

    /**
     * Dump object to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    public final void dump(final DataOutputStream file) throws IOException {
        file.writeShort(bootstrapMethodRef);
        file.writeShort(bootstrapArguments.length);
        for (final int bootstrapArgument : bootstrapArguments) {
            file.writeShort(bootstrapArgument);
        }
    }

    /**
     * @return int[] of bootstrap_method indices into constant_pool of CONSTANT_[type]_info
     */
    public int[] getBootstrapArguments() {
        return bootstrapArguments;
    }

    /**
     * @return index into constant_pool of bootstrap_method
     */
    public int getBootstrapMethodRef() {
        return bootstrapMethodRef;
    }

    /**
     * @return count of number of boostrap arguments
     */
    public int getNumBootstrapArguments() {
        return bootstrapArguments.length;
    }

    /**
     * @param bootstrapArguments int[] indices into constant_pool of CONSTANT_[type]_info
     */
    public void setBootstrapArguments(final int[] bootstrapArguments) {
        this.bootstrapArguments = Utils.createEmptyArrayIfNull(bootstrapArguments);
    }

    /**
     * @param bootstrapMethodRef int index into constant_pool of CONSTANT_MethodHandle
     */
    public void setBootstrapMethodRef(final int bootstrapMethodRef) {
        this.bootstrapMethodRef = bootstrapMethodRef;
    }

    /**
     * @return String representation.
     */
    @Override
    public final String toString() {
        return "BootstrapMethod(" + bootstrapMethodRef + ", " + bootstrapArguments.length + ", " + Arrays.toString(bootstrapArguments) + ")";
    }

    /**
     * @return Resolved string representation
     */
    public final String toString(final ConstantPool constantPool) {
        final StringBuilder buf = new StringBuilder();
        final String bootstrapMethodName = constantPool.constantToString(bootstrapMethodRef, Const.CONSTANT_MethodHandle);
        buf.append(Utility.compactClassName(bootstrapMethodName, false));
        final int bootstrapArgumentsLen = bootstrapArguments.length;
        if (bootstrapArgumentsLen > 0) {
            buf.append("\nMethod Arguments:");
            for (int i = 0; i < bootstrapArgumentsLen; i++) {
                buf.append("\n  ").append(i).append(": ");
                buf.append(constantPool.constantToString(constantPool.getConstant(bootstrapArguments[i])));
            }
        }
        return buf.toString();
    }
}
