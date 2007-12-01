/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  2003-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sun.org.apache.xml.internal.security.encryption;


import java.util.Iterator;


/**
 * <code>EncryptionProperties</code> can hold additional information concerning
 * the generation of the <code>EncryptedData</code> or
 * <code>EncryptedKey</code>. This information is wraped int an
 * <code>EncryptionProperty</code> element. Examples of additional information
 * is e.g., a date/time stamp or the serial number of cryptographic hardware
 * used during encryption).
 * <p>
 * It is defined as follows:
 * <xmp>
 * <element name='EncryptionProperties' type='xenc:EncryptionPropertiesType'/>
 * <complexType name='EncryptionPropertiesType'>
 *     <sequence>
 *         <element ref='xenc:EncryptionProperty' maxOccurs='unbounded'/>
 *     </sequence>
 *     <attribute name='Id' type='ID' use='optional'/>
 * </complexType>
 * </xmp>
 *
 * @author Axl Mattheus
 */
public interface EncryptionProperties {
    /**
     * Returns the <code>EncryptionProperties</code>' id.
     *
     * @return the id.
     */
    String getId();

    /**
     * Sets the id.
     *
     * @param id the id.
     */
    void setId(String id);

    /**
     * Returns an <code>Iterator</code> over all the
     * <code>EncryptionPropterty</code> elements contained in this
     * <code>EncryptionProperties</code>.
     *
     * @return an <code>Iterator</code> over all the encryption properties.
     */
    Iterator getEncryptionProperties();

    /**
     * Adds an <code>EncryptionProperty</code>.
     *
     * @param property.
     */
    void addEncryptionProperty(EncryptionProperty property);

    /**
     * Removes the specified <code>EncryptionProperty</code>.
     *
     * @param property.
     */
    void removeEncryptionProperty(EncryptionProperty property);
}
