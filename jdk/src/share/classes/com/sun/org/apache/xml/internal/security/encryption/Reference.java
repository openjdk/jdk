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
import org.w3c.dom.Element;


/**
 * A wrapper for a pointer from a key value of an <code>EncryptedKey</code> to
 * items encrypted by that key value (<code>EncryptedData</code> or
 * <code>EncryptedKey</code> elements).
 * <p>
 * It is defined as follows:
 * <xmp>
 * <complexType name='ReferenceType'>
 *     <sequence>
 *         <any namespace='##other' minOccurs='0' maxOccurs='unbounded'/>
 *     </sequence>
 *     <attribute name='URI' type='anyURI' use='required'/>
 * </complexType>
 * </xmp>
 *
 * @author Axl Mattheus
 * @see ReferenceList
 */
public interface Reference {
    /**
     * Returns a <code>URI</code> that points to an <code>Element</code> that
     * were encrypted using the key defined in the enclosing
     * <code>EncryptedKey</code> element.
     *
     * @return an Uniform Resource Identifier that qualifies an
     *   <code>EncryptedType</code>.
     */
    String getURI();

    /**
     * Sets a <code>URI</code> that points to an <code>Element</code> that
     * were encrypted using the key defined in the enclosing
     * <code>EncryptedKey</code> element.
     *
     * @param uri the Uniform Resource Identifier that qualifies an
     *   <code>EncryptedType</code>.
     */
    void setURI(String uri);

    /**
     * Returns an <code>Iterator</code> over all the child elements contained in
     * this <code>Reference</code> that will aid the recipient in retrieving the
     * <code>EncryptedKey</code> and/or <code>EncryptedData</code> elements.
     * These could include information such as XPath transforms, decompression
     * transforms, or information on how to retrieve the elements from a
     * document storage facility.
     *
     * @return child elements.
     */
    Iterator getElementRetrievalInformation();

    /**
     * Adds retrieval information.
     *
     * @param info.
     */
    void addElementRetrievalInformation(Element info);

    /**
     * Removes the specified retrieval information.
     *
     * @param info.
     */
    void removeElementRetrievalInformation(Element info);
}
