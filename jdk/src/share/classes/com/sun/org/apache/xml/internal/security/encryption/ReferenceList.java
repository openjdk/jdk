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
 * <code>ReferenceList</code> is an element that contains pointers from a key
 * value of an <code>EncryptedKey</code> to items encrypted by that key value
 * (<code>EncryptedData</code> or <code>EncryptedKey</code> elements).
 * <p>
 * It is defined as follows:
 * <xmp>
 * <element name='ReferenceList'>
 *     <complexType>
 *         <choice minOccurs='1' maxOccurs='unbounded'>
 *             <element name='DataReference' type='xenc:ReferenceType'/>
 *             <element name='KeyReference' type='xenc:ReferenceType'/>
 *         </choice>
 *     </complexType>
 * </element>
 * </xmp>
 *
 * @author Axl Mattheus
 * @see Reference
 */
public interface ReferenceList {
        /** DATA TAG */
    public static final int DATA_REFERENCE = 0x00000001;
    /** KEY TAG */
    public static final int KEY_REFERENCE  = 0x00000002;

    /**
     * Adds a reference to this reference list.
     *
     * @param reference the reference to add.
     * @throws IllegalAccessException if the <code>Reference</code> is not an
     *   instance of <code>DataReference</code> or <code>KeyReference</code>.
     */
    public void add(Reference reference);

    /**
     * Removes a reference from the <code>ReferenceList</code>.
     *
     * @param reference the reference to remove.
     */
    public void remove(Reference reference);

    /**
     * Returns the size of the <code>ReferenceList</code>.
     *
     * @return the size of the <code>ReferenceList</code>.
     */
    public int size();

    /**
     * Indicates if the <code>ReferenceList</code> is empty.
     *
     * @return <code><b>true</b></code> if the <code>ReferenceList</code> is
     *     empty, else <code><b>false</b></code>.
     */
    public boolean isEmpty();

    /**
     * Returns an <code>Iterator</code> over all the <code>Reference</code>s
     * contatined in this <code>ReferenceList</code>.
     *
     * @return Iterator.
     */
    public Iterator getReferences();

    /**
     * <code>DataReference</code> factory method. Returns a
     * <code>DataReference</code>.
     * @param uri
     * @return
     */
    public Reference newDataReference(String uri);

    /**
     * <code>KeyReference</code> factory method. Returns a
     * <code>KeyReference</code>.
     * @param uri
     * @return
     */
    public Reference newKeyReference(String uri);
}
