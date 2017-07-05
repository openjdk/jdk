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
 * The <code>EncryptedData</code> element is the core element in the syntax. Not
 * only does its <code>CipherData</code> child contain the encrypted data, but
 * it's also the element that replaces the encrypted element, or serves as the
 * new document root.
 * <p>
 * It's schema definition is as follows:
 * <p>
 * <xmp>
 * <element name='EncryptedData' type='xenc:EncryptedDataType'/>
 * <complexType name='EncryptedDataType'>
 *     <complexContent>
 *         <extension base='xenc:EncryptedType'/>
 *     </complexContent>
 * </complexType>
 * </xmp>
 *
 * @author Axl Mattheus
 */
public interface EncryptedData extends EncryptedType {
}
