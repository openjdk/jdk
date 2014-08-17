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
import com.sun.org.apache.xml.internal.security.keys.KeyInfo;
import org.w3c.dom.Element;

/**
 * A Key Agreement algorithm provides for the derivation of a shared secret key
 * based on a shared secret computed from certain types of compatible public
 * keys from both the sender and the recipient. Information from the originator
 * to determine the secret is indicated by an optional OriginatorKeyInfo
 * parameter child of an <code>AgreementMethod</code> element while that
 * associated with the recipient is indicated by an optional RecipientKeyInfo. A
 * shared key is derived from this shared secret by a method determined by the
 * Key Agreement algorithm.
 * <p>
 * <b>Note:</b> XML Encryption does not provide an on-line key agreement
 * negotiation protocol. The <code>AgreementMethod</code> element can be used by
 * the originator to identify the keys and computational procedure that were
 * used to obtain a shared encryption key. The method used to obtain or select
 * the keys or algorithm used for the agreement computation is beyond the scope
 * of this specification.
 * <p>
 * The <code>AgreementMethod</code> element appears as the content of a
 * <code>ds:KeyInfo</code> since, like other <code>ds:KeyInfo</code> children,
 * it yields a key. This <code>ds:KeyInfo</code> is in turn a child of an
 * <code>EncryptedData</code> or <code>EncryptedKey</code> element. The
 * Algorithm attribute and KeySize child of the <code>EncryptionMethod</code>
 * element under this <code>EncryptedData</code> or <code>EncryptedKey</code>
 * element are implicit parameters to the key agreement computation. In cases
 * where this <code>EncryptionMethod</code> algorithm <code>URI</code> is
 * insufficient to determine the key length, a KeySize MUST have been included.
 * In addition, the sender may place a KA-Nonce element under
 * <code>AgreementMethod</code> to assure that different keying material is
 * generated even for repeated agreements using the same sender and recipient
 * public keys.
 * <p>
 * If the agreed key is being used to wrap a key, then
 * <code>AgreementMethod</code> would appear inside a <code>ds:KeyInfo</code>
 * inside an <code>EncryptedKey</code> element.
 * <p>
 * The Schema for AgreementMethod is as follows:
 * <xmp>
 * <element name="AgreementMethod" type="xenc:AgreementMethodType"/>
 * <complexType name="AgreementMethodType" mixed="true">
 *     <sequence>
 *         <element name="KA-Nonce" minOccurs="0" type="base64Binary"/>
 *         <!-- <element ref="ds:DigestMethod" minOccurs="0"/> -->
 *         <any namespace="##other" minOccurs="0" maxOccurs="unbounded"/>
 *         <element name="OriginatorKeyInfo" minOccurs="0" type="ds:KeyInfoType"/>
 *         <element name="RecipientKeyInfo" minOccurs="0" type="ds:KeyInfoType"/>
 *     </sequence>
 *     <attribute name="Algorithm" type="anyURI" use="required"/>
 * </complexType>
 * </xmp>
 *
 * @author Axl Mattheus
 */
public interface AgreementMethod {

    /**
     * Returns a <code>byte</code> array.
     * @return a <code>byte</code> array.
     */
    byte[] getKANonce();

    /**
     * Sets the KANonce.jj
     * @param kanonce
     */
    void setKANonce(byte[] kanonce);

    /**
     * Returns additional information regarding the <code>AgreementMethod</code>.
     * @return additional information regarding the <code>AgreementMethod</code>.
     */
    Iterator<Element> getAgreementMethodInformation();

    /**
     * Adds additional <code>AgreementMethod</code> information.
     *
     * @param info a <code>Element</code> that represents additional information
     * specified by
     *   <xmp>
     *     <any namespace="##other" minOccurs="0" maxOccurs="unbounded"/>
     *   </xmp>
     */
    void addAgreementMethodInformation(Element info);

    /**
     * Removes additional <code>AgreementMethod</code> information.
     *
     * @param info a <code>Element</code> that represents additional information
     * specified by
     *   <xmp>
     *     <any namespace="##other" minOccurs="0" maxOccurs="unbounded"/>
     *   </xmp>
     */
    void revoveAgreementMethodInformation(Element info);

    /**
     * Returns information relating to the originator's shared secret.
     *
     * @return information relating to the originator's shared secret.
     */
    KeyInfo getOriginatorKeyInfo();

    /**
     * Sets the information relating to the originator's shared secret.
     *
     * @param keyInfo information relating to the originator's shared secret.
     */
    void setOriginatorKeyInfo(KeyInfo keyInfo);

    /**
     * Returns information relating to the recipient's shared secret.
     *
     * @return information relating to the recipient's shared secret.
     */
    KeyInfo getRecipientKeyInfo();

    /**
     * Sets the information relating to the recipient's shared secret.
     *
     * @param keyInfo information relating to the recipient's shared secret.
     */
    void setRecipientKeyInfo(KeyInfo keyInfo);

    /**
     * Returns the algorithm URI of this <code>CryptographicMethod</code>.
     *
     * @return the algorithm URI of this <code>CryptographicMethod</code>
     */
    String getAlgorithm();
}
