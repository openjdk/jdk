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

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An annotation's element value pair.
 *
 * @since 6.0
 */
public class ElementValuePair {

    static final ElementValuePair[] EMPTY_ARRAY = {};

    private final ElementValue elementValue;

    private final ConstantPool constantPool;

    private final int elementNameIndex;

    /**
     * Constructs an ElementValuePair.
     *
     * @param elementNameIndex The element name index.
     * @param elementValue The element value.
     * @param constantPool The constant pool.
     */
    public ElementValuePair(final int elementNameIndex, final ElementValue elementValue, final ConstantPool constantPool) {
        this.elementValue = elementValue;
        this.elementNameIndex = elementNameIndex;
        this.constantPool = constantPool;
    }

    /**
     * Dumps this element value pair to a DataOutputStream.
     *
     * @param dos The output stream.
     * @throws IOException Thrown if an I/O error occurs.
     */
    protected void dump(final DataOutputStream dos) throws IOException {
        dos.writeShort(elementNameIndex); // u2 name of the element
        elementValue.dump(dos);
    }

    /**
     * Gets the name index.
     *
     * @return The name index.
     */
    public int getNameIndex() {
        return elementNameIndex;
    }

    /**
     * Gets the name string.
     *
     * @return The name string.
     */
    public String getNameString() {
        return constantPool.getConstantUtf8(elementNameIndex).getBytes();
    }

    /**
     * Gets the value.
     *
     * @return The element value.
     */
    public final ElementValue getValue() {
        return elementValue;
    }

    /**
     * Gets a short string representation.
     *
     * @return A short string representation.
     */
    public String toShortString() {
        final StringBuilder result = new StringBuilder();
        result.append(getNameString()).append("=").append(getValue().toShortString());
        return result.toString();
    }
}
