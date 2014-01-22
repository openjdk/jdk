/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 * $Id: SignatureMethod.java,v 1.5 2005/05/10 16:03:46 mullan Exp $
 */
package javax.xml.crypto.dsig;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;
import java.security.spec.AlgorithmParameterSpec;

/**
 * A representation of the XML <code>SignatureMethod</code> element
 * as defined in the <a href="http://www.w3.org/TR/xmldsig-core/">
 * W3C Recommendation for XML-Signature Syntax and Processing</a>.
 * The XML Schema Definition is defined as:
 * <pre>
 *   &lt;element name="SignatureMethod" type="ds:SignatureMethodType"/&gt;
 *     &lt;complexType name="SignatureMethodType" mixed="true"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="HMACOutputLength" minOccurs="0" type="ds:HMACOutputLengthType"/&gt;
 *         &lt;any namespace="##any" minOccurs="0" maxOccurs="unbounded"/&gt;
 *           &lt;!-- (0,unbounded) elements from (1,1) namespace --&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="Algorithm" type="anyURI" use="required"/&gt;
 *     &lt;/complexType&gt;
 * </pre>
 *
 * A <code>SignatureMethod</code> instance may be created by invoking the
 * {@link XMLSignatureFactory#newSignatureMethod newSignatureMethod} method
 * of the {@link XMLSignatureFactory} class.
 *
 * @author Sean Mullan
 * @author JSR 105 Expert Group
 * @since 1.6
 * @see XMLSignatureFactory#newSignatureMethod(String, SignatureMethodParameterSpec)
 */
public interface SignatureMethod extends XMLStructure, AlgorithmMethod {

    /**
     * The <a href="http://www.w3.org/2000/09/xmldsig#dsa-sha1">DSAwithSHA1</a>
     * (DSS) signature method algorithm URI.
     */
    static final String DSA_SHA1 =
        "http://www.w3.org/2000/09/xmldsig#dsa-sha1";

    /**
     * The <a href="http://www.w3.org/2000/09/xmldsig#rsa-sha1">RSAwithSHA1</a>
     * (PKCS #1) signature method algorithm URI.
     */
    static final String RSA_SHA1 =
        "http://www.w3.org/2000/09/xmldsig#rsa-sha1";

    /**
     * The <a href="http://www.w3.org/2000/09/xmldsig#hmac-sha1">HMAC-SHA1</a>
     * MAC signature method algorithm URI
     */
    static final String HMAC_SHA1 =
        "http://www.w3.org/2000/09/xmldsig#hmac-sha1";

    /**
     * Returns the algorithm-specific input parameters of this
     * <code>SignatureMethod</code>.
     *
     * <p>The returned parameters can be typecast to a {@link
     * SignatureMethodParameterSpec} object.
     *
     * @return the algorithm-specific input parameters of this
     *    <code>SignatureMethod</code> (may be <code>null</code> if not
     *    specified)
     */
    AlgorithmParameterSpec getParameterSpec();
}
