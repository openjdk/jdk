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




/**
 * A container for <code>ds:Transform</code>s.
 * <p>
 * It is defined as follows:
 * <xmp>
 * <complexType name='TransformsType'>
 *     <sequence>
 *         <element ref='ds:Transform' maxOccurs='unbounded'/>
 *     </sequence>
 * </complexType>
 * </xmp>
 *
 * @author Axl Mattheus
 * @see com.sun.org.apache.xml.internal.security.encryption.CipherReference
 */
public interface Transforms {
    /**
     * Returns an <code>Iterator</code> over all the transforms contained in
     * this transform list.
     *
     * @return all transforms.
     */
    /* Iterator getTransforms(); */

    /**
     * Adds a <code>ds:Transform</code> to the list of transforms.
     *
     * @param transform.
     */
    /* void addTransform(Transform transform); */

    /**
     * Removes the specified transform.
     *
     * @param transform.
     */
        /*    void removeTransform(Transform transform); */

        /**
         * Temporary method to turn the XMLEncryption Transforms class
         * into a DS class.  The main logic is currently implemented in the
         * DS class, so we need to get to get the base class.
         * <p>
         * <b>Note</b> This will be removed in future versions
     * @return
         */

        com.sun.org.apache.xml.internal.security.transforms.Transforms getDSTransforms();

}
