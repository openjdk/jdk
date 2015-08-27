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

import org.w3c.dom.Attr;

/**
 * {@code CipherReference} identifies a source which, when processed,
 * yields the encrypted octet sequence.
 * <p>
 * The actual value is obtained as follows. The {@code CipherReference URI}
 * contains an identifier that is dereferenced. Should the
 * Transforms, the data resulting from dereferencing the {@code URI} is
 * transformed as specified so as to yield the intended cipher value. For
 * example, if the value is base64 encoded within an XML document; the
 * transforms could specify an XPath expression followed by a base64 decoding so
 * as to extract the octets.
 * <p>
 * The syntax of the {@code URI} and Transforms is similar to that of
 * [XML-DSIG]. However, there is a difference between signature and encryption
 * processing. In [XML-DSIG] both generation and validation processing start
 * with the same source data and perform that transform in the same order. In
 * encryption, the decryptor has only the cipher data and the specified
 * transforms are enumerated for the decryptor, in the order necessary to obtain
 * the octets. Consequently, because it has different semantics Transforms is in
 * the &xenc; namespace.
 * <p>
 * The schema definition is as follows:
 * <pre>{@code
 * <element name='CipherReference' type='xenc:CipherReferenceType'/>
 * <complexType name='CipherReferenceType'>
 *     <sequence>
 *         <element name='Transforms' type='xenc:TransformsType' minOccurs='0'/>
 *     </sequence>
 *     <attribute name='URI' type='anyURI' use='required'/>
 * </complexType>
 * }</pre>
 *
 * @author Axl Mattheus
 */
public interface CipherReference {
    /**
     * Returns an {@code URI} that contains an identifier that should be
     * dereferenced.
     * @return an {@code URI} that contains an identifier that should be
     * dereferenced.
     */
    String getURI();

    /**
     * Gets the URI as an Attribute node.  Used to meld the CipherReference
     * with the XMLSignature ResourceResolvers
     * @return the URI as an Attribute node
     */
    Attr getURIAsAttr();

    /**
     * Returns the {@code Transforms} that specifies how to transform the
     * {@code URI} to yield the appropriate cipher value.
     *
     * @return the transform that specifies how to transform the reference to
     *   yield the intended cipher value.
     */
    Transforms getTransforms();

    /**
     * Sets the {@code Transforms} that specifies how to transform the
     * {@code URI} to yield the appropriate cipher value.
     *
     * @param transforms the set of {@code Transforms} that specifies how
     *   to transform the reference to yield the intended cipher value.
     */
    void setTransforms(Transforms transforms);
}

