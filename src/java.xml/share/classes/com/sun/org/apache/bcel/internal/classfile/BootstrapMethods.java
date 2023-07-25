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
import java.util.Iterator;
import java.util.stream.Stream;

import com.sun.org.apache.bcel.internal.Const;

/**
 * This class represents a BootstrapMethods attribute.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.23"> The class File Format :
 *      The BootstrapMethods Attribute</a>
 * @since 6.0
 */
public class BootstrapMethods extends Attribute implements Iterable<BootstrapMethod> {

    private BootstrapMethod[] bootstrapMethods; // TODO this could be made final (setter is not used)

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use clone() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    public BootstrapMethods(final BootstrapMethods c) {
        this(c.getNameIndex(), c.getLength(), c.getBootstrapMethods(), c.getConstantPool());
    }

    /**
     * @param nameIndex Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param bootstrapMethods array of bootstrap methods
     * @param constantPool Array of constants
     */
    public BootstrapMethods(final int nameIndex, final int length, final BootstrapMethod[] bootstrapMethods, final ConstantPool constantPool) {
        super(Const.ATTR_BOOTSTRAP_METHODS, nameIndex, length, constantPool);
        this.bootstrapMethods = bootstrapMethods;
    }

    /**
     * Construct object from Input stream.
     *
     * @param nameIndex Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    BootstrapMethods(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (BootstrapMethod[]) null, constantPool);

        final int numBootstrapMethods = input.readUnsignedShort();
        bootstrapMethods = new BootstrapMethod[numBootstrapMethods];
        for (int i = 0; i < numBootstrapMethods; i++) {
            bootstrapMethods[i] = new BootstrapMethod(input);
        }
    }

    /**
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitBootstrapMethods(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public BootstrapMethods copy(final ConstantPool constantPool) {
        final BootstrapMethods c = (BootstrapMethods) clone();
        c.bootstrapMethods = new BootstrapMethod[bootstrapMethods.length];

        for (int i = 0; i < bootstrapMethods.length; i++) {
            c.bootstrapMethods[i] = bootstrapMethods[i].copy();
        }
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump bootstrap methods attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public final void dump(final DataOutputStream file) throws IOException {
        super.dump(file);

        file.writeShort(bootstrapMethods.length);
        for (final BootstrapMethod bootstrapMethod : bootstrapMethods) {
            bootstrapMethod.dump(file);
        }
    }

    /**
     * @return array of bootstrap method "records"
     */
    public final BootstrapMethod[] getBootstrapMethods() {
        return bootstrapMethods;
    }

    @Override
    public Iterator<BootstrapMethod> iterator() {
        return Stream.of(bootstrapMethods).iterator();
    }

    /**
     * @param bootstrapMethods the array of bootstrap methods
     */
    public final void setBootstrapMethods(final BootstrapMethod[] bootstrapMethods) {
        this.bootstrapMethods = bootstrapMethods;
    }

    /**
     * @return String representation.
     */
    @Override
    public final String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("BootstrapMethods(");
        buf.append(bootstrapMethods.length);
        buf.append("):");
        for (int i = 0; i < bootstrapMethods.length; i++) {
            buf.append("\n");
            final int start = buf.length();
            buf.append("  ").append(i).append(": ");
            final int indentCount = buf.length() - start;
            final String[] lines = bootstrapMethods[i].toString(super.getConstantPool()).split("\\r?\\n");
            buf.append(lines[0]);
            for (int j = 1; j < lines.length; j++) {
                buf.append("\n").append("          ", 0, indentCount).append(lines[j]);
            }
        }
        return buf.toString();
    }
}
