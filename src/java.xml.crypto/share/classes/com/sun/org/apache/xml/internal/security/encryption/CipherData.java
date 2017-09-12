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

/**
 * {@code CipherData} provides encrypted data. It must either contain the
 * encrypted octet sequence as base64 encoded text of the
 * {@code CipherValue} element, or provide a reference to an external
 * location containing the encrypted octet sequence via the
 * {@code CipherReference} element.
 * <p>
 * The schema definition is as follows:
 * <pre>{@code
 * <element name='CipherData' type='xenc:CipherDataType'/>
 * <complexType name='CipherDataType'>
 *     <choice>
 *         <element name='CipherValue' type='base64Binary'/>
 *         <element ref='xenc:CipherReference'/>
 *     </choice>
 * </complexType>
 * }</pre>
 *
 * @author Axl Mattheus
 */
public interface CipherData {

    /** VALUE_TYPE ASN */
    int VALUE_TYPE = 0x00000001;

    /** REFERENCE_TYPE ASN */
    int REFERENCE_TYPE = 0x00000002;

    /**
     * Returns the type of encrypted data contained in the
     * {@code CipherData}.
     *
     * @return {@code VALUE_TYPE} if the encrypted data is contained as
     *   {@code CipherValue} or {@code REFERENCE_TYPE} if the
     *   encrypted data is contained as {@code CipherReference}.
     */
    int getDataType();

    /**
     * Returns the cipher value as a base64 encoded {@code byte} array.
     *
     * @return the {@code CipherData}'s value.
     */
    CipherValue getCipherValue();

    /**
     * Sets the {@code CipherData}'s value.
     *
     * @param value the value of the {@code CipherData}.
     * @throws XMLEncryptionException
     */
    void setCipherValue(CipherValue value) throws XMLEncryptionException;

    /**
     * Returns a reference to an external location containing the encrypted
     * octet sequence ({@code byte} array).
     *
     * @return the reference to an external location containing the encrypted
     * octet sequence.
     */
    CipherReference getCipherReference();

    /**
     * Sets the {@code CipherData}'s reference.
     *
     * @param reference an external location containing the encrypted octet sequence.
     * @throws XMLEncryptionException
     */
    void setCipherReference(CipherReference reference) throws XMLEncryptionException;
}

