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

import com.sun.org.apache.bcel.internal.Const;

/**
 * Entry of the parameters table.
 * <p>
 * Implements {@link Node} as of 6.7.0.
 * </p>
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.24"> The class File Format :
 *      The MethodParameters Attribute</a>
 * @since 6.0
 */
public class MethodParameter implements Cloneable, Node {

    /** Index of the CONSTANT_Utf8_info structure in the constant_pool table representing the name of the parameter */
    private int nameIndex;

    /** The access flags */
    private int accessFlags;

    /**
     * Constructs a MethodParameter.
     */
    public MethodParameter() {
    }

    /**
     * Constructs an instance from a DataInput.
     *
     * @param input Input stream.
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file
     */
    MethodParameter(final DataInput input) throws IOException {
        nameIndex = input.readUnsignedShort();
        accessFlags = input.readUnsignedShort();
    }

    @Override
    public void accept(final Visitor v) {
        v.visitMethodParameter(this);
    }

    /**
     * Creates a deep copy of this object.
     *
     * @return deep copy of this object.
     */
    public MethodParameter copy() {
        try {
            return (MethodParameter) clone();
        } catch (final CloneNotSupportedException e) {
            // TODO should this throw?
        }
        return null;
    }

    /**
     * Dumps object to file stream on binary format.
     *
     * @param file Output file stream.
     * @throws IOException Thrown if an I/O error occurs.
     */
    public final void dump(final DataOutputStream file) throws IOException {
        file.writeShort(nameIndex);
        file.writeShort(accessFlags);
    }

    /**
     * Gets the access flags.
     *
     * @return The access flags.
     */
    public int getAccessFlags() {
        return accessFlags;
    }

    /**
     * Gets the name index.
     *
     * @return The name index.
     */
    public int getNameIndex() {
        return nameIndex;
    }

    /**
     * Gets the name of the parameter.
     *
     * @param constantPool The pool to query.
     * @return Constant from the given pool.
     */
    public String getParameterName(final ConstantPool constantPool) {
        if (nameIndex == 0) {
            return null;
        }
        return constantPool.getConstantUtf8(nameIndex).getBytes();
    }

    /**
     * Checks if this parameter is final.
     *
     * @return true if this parameter is final.
     */
    public boolean isFinal() {
        return (accessFlags & Const.ACC_FINAL) != 0;
    }

    /**
     * Checks if this parameter is mandated.
     *
     * @return true if this parameter is mandated.
     */
    public boolean isMandated() {
        return (accessFlags & Const.ACC_MANDATED) != 0;
    }

    /**
     * Checks if this parameter is synthetic.
     *
     * @return true if this parameter is synthetic.
     */
    public boolean isSynthetic() {
        return (accessFlags & Const.ACC_SYNTHETIC) != 0;
    }

    /**
     * Sets the access flags.
     *
     * @param accessFlags The access flags.
     */
    public void setAccessFlags(final int accessFlags) {
        this.accessFlags = accessFlags;
    }

    /**
     * Sets the name index.
     *
     * @param nameIndex The name index.
     */
    public void setNameIndex(final int nameIndex) {
        this.nameIndex = nameIndex;
    }
}
