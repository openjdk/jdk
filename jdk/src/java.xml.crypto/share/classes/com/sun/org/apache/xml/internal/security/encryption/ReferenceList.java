/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.encryption;

import java.util.Iterator;

/**
 * {@code ReferenceList} is an element that contains pointers from a key
 * value of an {@code EncryptedKey} to items encrypted by that key value
 * ({@code EncryptedData} or {@code EncryptedKey} elements).
 * <p>
 * It is defined as follows:
 * <pre>{@code
 * <element name='ReferenceList'>
 *     <complexType>
 *         <choice minOccurs='1' maxOccurs='unbounded'>
 *             <element name='DataReference' type='xenc:ReferenceType'/>
 *             <element name='KeyReference' type='xenc:ReferenceType'/>
 *         </choice>
 *     </complexType>
 * </element>
 * }</pre>
 *
 * @author Axl Mattheus
 * @see Reference
 */
public interface ReferenceList {

    /** DATA TAG */
    int DATA_REFERENCE = 0x00000001;

    /** KEY TAG */
    int KEY_REFERENCE  = 0x00000002;

    /**
     * Adds a reference to this reference list.
     *
     * @param reference the reference to add.
     * @throws IllegalAccessException if the {@code Reference} is not an
     *   instance of {@code DataReference} or {@code KeyReference}.
     */
    void add(Reference reference);

    /**
     * Removes a reference from the {@code ReferenceList}.
     *
     * @param reference the reference to remove.
     */
    void remove(Reference reference);

    /**
     * Returns the size of the {@code ReferenceList}.
     *
     * @return the size of the {@code ReferenceList}.
     */
    int size();

    /**
     * Indicates if the {@code ReferenceList} is empty.
     *
     * @return <b>{@code true}</b> if the {@code ReferenceList} is
     *     empty, else <b>{@code false}</b>.
     */
    boolean isEmpty();

    /**
     * Returns an {@code Iterator} over all the {@code Reference}s
     * contained in this {@code ReferenceList}.
     *
     * @return Iterator.
     */
    Iterator<Reference> getReferences();

    /**
     * {@code DataReference} factory method. Returns a
     * {@code DataReference}.
     * @param uri
     * @return a {@code DataReference}.
     */
    Reference newDataReference(String uri);

    /**
     * {@code KeyReference} factory method. Returns a
     * {@code KeyReference}.
     * @param uri
     * @return a {@code KeyReference}.
     */
    Reference newKeyReference(String uri);
}
