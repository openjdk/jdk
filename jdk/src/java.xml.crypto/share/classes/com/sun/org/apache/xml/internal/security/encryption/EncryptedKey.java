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
 * The {@code EncryptedKey} element is used to transport encryption keys
 * from the originator to a known recipient(s). It may be used as a stand-alone
 * XML document, be placed within an application document, or appear inside an
 * {@code EncryptedData} element as a child of a {@code ds:KeyInfo}
 * element. The key value is always encrypted to the recipient(s). When
 * {@code EncryptedKey} is decrypted the resulting octets are made
 * available to the {@code EncryptionMethod} algorithm without any
 * additional processing.
 * <p>
 * Its schema definition is as follows:
 * <pre>{@code
 * <element name='EncryptedKey' type='xenc:EncryptedKeyType'/>
 * <complexType name='EncryptedKeyType'>
 *     <complexContent>
 *         <extension base='xenc:EncryptedType'>
 *             <sequence>
 *                 <element ref='xenc:ReferenceList' minOccurs='0'/>
 *                 <element name='CarriedKeyName' type='string' minOccurs='0'/>
 *             </sequence>
 *             <attribute name='Recipient' type='string' use='optional'/>
 *         </extension>
 *     </complexContent>
 * </complexType>
 * }</pre>
 *
 * @author Axl Mattheus
 */
public interface EncryptedKey extends EncryptedType {

    /**
     * Returns a hint as to which recipient this encrypted key value is intended for.
     *
     * @return the recipient of the {@code EncryptedKey}.
     */
    String getRecipient();

    /**
     * Sets the recipient for this {@code EncryptedKey}.
     *
     * @param recipient the recipient for this {@code EncryptedKey}.
     */
    void setRecipient(String recipient);

    /**
     * Returns pointers to data and keys encrypted using this key. The reference
     * list may contain multiple references to {@code EncryptedKey} and
     * {@code EncryptedData} elements. This is done using
     * {@code KeyReference} and {@code DataReference} elements
     * respectively.
     *
     * @return an {@code Iterator} over all the {@code ReferenceList}s
     *   contained in this {@code EncryptedKey}.
     */
    ReferenceList getReferenceList();

    /**
     * Sets the {@code ReferenceList} to the {@code EncryptedKey}.
     *
     * @param list a list of pointers to data elements encrypted using this key.
     */
    void setReferenceList(ReferenceList list);

    /**
     * Returns a user readable name with the key value. This may then be used to
     * reference the key using the {@code ds:KeyName} element within
     * {@code ds:KeyInfo}. The same {@code CarriedKeyName} label,
     * unlike an ID type, may occur multiple times within a single document. The
     * value of the key is to be the same in all {@code EncryptedKey}
     * elements identified with the same {@code CarriedKeyName} label
     * within a single XML document.
     * <br>
     * <b>Note</b> that because whitespace is significant in the value of
     * the {@code ds:KeyName} element, whitespace is also significant in
     * the value of the {@code CarriedKeyName} element.
     *
     * @return over all the carried names contained in
     *   this {@code EncryptedKey}.
     */
    String getCarriedName();

    /**
     * Sets the carried name.
     *
     * @param name the carried name.
     */
    void setCarriedName(String name);
}

