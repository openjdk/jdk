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

import com.sun.org.apache.bcel.internal.classfile.ConstantUtf8;
import com.sun.org.apache.bcel.internal.classfile.ElementValue;
import com.sun.org.apache.bcel.internal.classfile.ElementValuePair;

/**
 * Generates element value pairs in annotations.
 *
 * @since 6.0
 */
public class ElementValuePairGen {
    private final int nameIdx;

    private final ElementValueGen value;

    private final ConstantPoolGen constantPoolGen;

    /**
     * Constructs an ElementValuePairGen from an ElementValuePair.
     *
     * @param nvp The element value pair.
     * @param cpool The constant pool.
     * @param copyPoolEntries whether to copy pool entries.
     */
    public ElementValuePairGen(final ElementValuePair nvp, final ConstantPoolGen cpool, final boolean copyPoolEntries) {
        this.constantPoolGen = cpool;
        // J5ASSERT:
        // Could assert nvp.getNameString() points to the same thing as
        // constantPoolGen.getConstant(nvp.getNameIndex())
        // if
        // (!nvp.getNameString().equals(((ConstantUtf8) constantPoolGen.getConstant(nvp.getNameIndex())).getBytes()))
        // {
        // throw new IllegalArgumentException("envp buggered");
        // }
        if (copyPoolEntries) {
            nameIdx = cpool.addUtf8(nvp.getNameString());
        } else {
            nameIdx = nvp.getNameIndex();
        }
        value = ElementValueGen.copy(nvp.getValue(), cpool, copyPoolEntries);
    }

    /**
     * Constructs an ElementValuePairGen.
     *
     * @param idx The name index.
     * @param value The element value.
     * @param cpool The constant pool.
     */
    protected ElementValuePairGen(final int idx, final ElementValueGen value, final ConstantPoolGen cpool) {
        this.nameIdx = idx;
        this.value = value;
        this.constantPoolGen = cpool;
    }

    /**
     * Constructs an ElementValuePairGen.
     *
     * @param name The name.
     * @param value The element value.
     * @param cpool The constant pool.
     */
    public ElementValuePairGen(final String name, final ElementValueGen value, final ConstantPoolGen cpool) {
        this.nameIdx = cpool.addUtf8(name);
        this.value = value;
        this.constantPoolGen = cpool;
    }

    /**
     * Dumps this element value pair to a DataOutputStream.
     *
     * @param dos The output stream.
     * @throws IOException Thrown if an I/O error occurs.
     */
    protected void dump(final DataOutputStream dos) throws IOException {
        dos.writeShort(nameIdx); // u2 name of the element
        value.dump(dos);
    }

    /**
     * Retrieves an immutable version of this ElementValuePairGen.
     *
     * @return An immutable ElementValuePair.
     */
    public ElementValuePair getElementNameValuePair() {
        final ElementValue immutableValue = value.getElementValue();
        return new ElementValuePair(nameIdx, immutableValue, constantPoolGen.getConstantPool());
    }

    /**
     * Gets the name index.
     *
     * @return The name index.
     */
    public int getNameIndex() {
        return nameIdx;
    }

    /**
     * Gets the name string.
     *
     * @return The name string.
     */
    public final String getNameString() {
        // ConstantString cu8 = (ConstantString) constantPoolGen.getConstant(nameIdx);
        return ((ConstantUtf8) constantPoolGen.getConstant(nameIdx)).getBytes();
    }

    /**
     * Gets the value.
     *
     * @return The element value.
     */
    public final ElementValueGen getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "ElementValuePair:[" + getNameString() + "=" + value.stringifyValue() + "]";
    }
}
