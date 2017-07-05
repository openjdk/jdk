/*
 * Portions Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
 * Copyright  1999-2004 The Apache Software Foundation.
 */
/*
 * $Id: DOMSignatureMethod.java,v 1.20.4.1 2005/08/12 14:23:49 mullan Exp $
 */
package org.jcp.xml.dsig.internal.dom;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMCryptoContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;

import java.io.IOException;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.jcp.xml.dsig.internal.SignerOutputStream;

/**
 * DOM-based abstract implementation of SignatureMethod.
 *
 * @author Sean Mullan
 */
public abstract class DOMSignatureMethod extends DOMStructure
    implements SignatureMethod {

    private static Logger log =
        Logger.getLogger("org.jcp.xml.dsig.internal.dom");

    // see RFC 4051 for these algorithm definitions
    final static String RSA_SHA256 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";
    final static String RSA_SHA384 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384";
    final static String RSA_SHA512 =
        "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512";
    final static String HMAC_SHA256 =
        "http://www.w3.org/2001/04/xmldsig-more#hmac-sha256";
    final static String HMAC_SHA384 =
        "http://www.w3.org/2001/04/xmldsig-more#hmac-sha384";
    final static String HMAC_SHA512 =
        "http://www.w3.org/2001/04/xmldsig-more#hmac-sha512";

    private SignatureMethodParameterSpec params;
    private Signature signature;

    /**
     * Creates a <code>DOMSignatureMethod</code>.
     *
     * @param params the algorithm-specific params (may be <code>null</code>)
     * @throws InvalidAlgorithmParameterException if the parameters are not
     *    appropriate for this signature method
     */
    DOMSignatureMethod(AlgorithmParameterSpec params)
        throws InvalidAlgorithmParameterException {
        if (params != null &&
            !(params instanceof SignatureMethodParameterSpec)) {
            throw new InvalidAlgorithmParameterException
                ("params must be of type SignatureMethodParameterSpec");
        }
        checkParams((SignatureMethodParameterSpec) params);
        this.params = (SignatureMethodParameterSpec) params;
    }

    /**
     * Creates a <code>DOMSignatureMethod</code> from an element. This ctor
     * invokes the abstract {@link #unmarshalParams unmarshalParams} method to
     * unmarshal any algorithm-specific input parameters.
     *
     * @param smElem a SignatureMethod element
     */
    DOMSignatureMethod(Element smElem) throws MarshalException {
        Element paramsElem = DOMUtils.getFirstChildElement(smElem);
        if (paramsElem != null) {
            params = unmarshalParams(paramsElem);
        }
        try {
            checkParams(params);
        } catch (InvalidAlgorithmParameterException iape) {
            throw new MarshalException(iape);
        }
    }

    static SignatureMethod unmarshal(Element smElem) throws MarshalException {
        String alg = DOMUtils.getAttributeValue(smElem, "Algorithm");
        if (alg.equals(SignatureMethod.RSA_SHA1)) {
            return new SHA1withRSA(smElem);
        } else if (alg.equals(RSA_SHA256)) {
            return new SHA256withRSA(smElem);
        } else if (alg.equals(RSA_SHA384)) {
            return new SHA384withRSA(smElem);
        } else if (alg.equals(RSA_SHA512)) {
            return new SHA512withRSA(smElem);
        } else if (alg.equals(SignatureMethod.DSA_SHA1)) {
            return new SHA1withDSA(smElem);
        } else if (alg.equals(SignatureMethod.HMAC_SHA1)) {
            return new DOMHMACSignatureMethod.SHA1(smElem);
        } else if (alg.equals(HMAC_SHA256)) {
            return new DOMHMACSignatureMethod.SHA256(smElem);
        } else if (alg.equals(HMAC_SHA384)) {
            return new DOMHMACSignatureMethod.SHA384(smElem);
        } else if (alg.equals(HMAC_SHA512)) {
            return new DOMHMACSignatureMethod.SHA512(smElem);
        } else {
            throw new MarshalException
                ("unsupported SignatureMethod algorithm: " + alg);
        }
    }

    /**
     * Checks if the specified parameters are valid for this algorithm. By
     * default, this method throws an exception if parameters are specified
     * since most SignatureMethod algorithms do not have parameters. Subclasses
     * should override it if they have parameters.
     *
     * @param params the algorithm-specific params (may be <code>null</code>)
     * @throws InvalidAlgorithmParameterException if the parameters are not
     *    appropriate for this signature method
     */
    void checkParams(SignatureMethodParameterSpec params)
        throws InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException("no parameters " +
                "should be specified for the " + getSignatureAlgorithm()
                 + " SignatureMethod algorithm");
        }
    }

    public final AlgorithmParameterSpec getParameterSpec() {
        return params;
    }

    /**
     * Unmarshals <code>SignatureMethodParameterSpec</code> from the specified
     * <code>Element</code>. By default, this method throws an exception since
     * most SignatureMethod algorithms do not have parameters. Subclasses should
     * override it if they have parameters.
     *
     * @param paramsElem the <code>Element</code> holding the input params
     * @return the algorithm-specific <code>SignatureMethodParameterSpec</code>
     * @throws MarshalException if the parameters cannot be unmarshalled
     */
    SignatureMethodParameterSpec
        unmarshalParams(Element paramsElem) throws MarshalException {
        throw new MarshalException("no parameters should " +
            "be specified for the " + getSignatureAlgorithm() +
            " SignatureMethod algorithm");
    }

    /**
     * This method invokes the abstract {@link #marshalParams marshalParams}
     * method to marshal any algorithm-specific parameters.
     */
    public void marshal(Node parent, String dsPrefix, DOMCryptoContext context)
        throws MarshalException {
        Document ownerDoc = DOMUtils.getOwnerDocument(parent);

        Element smElem = DOMUtils.createElement
            (ownerDoc, "SignatureMethod", XMLSignature.XMLNS, dsPrefix);
        DOMUtils.setAttribute(smElem, "Algorithm", getAlgorithm());

        if (params != null) {
            marshalParams(smElem, dsPrefix);
        }

        parent.appendChild(smElem);
    }

    /**
     * Verifies the passed-in signature with the specified key, using the
     * underlying signature or MAC algorithm.
     *
     * @param key the verification key
     * @param si the DOMSignedInfo
     * @param signature the signature bytes to be verified
     * @param context the XMLValidateContext
     * @return <code>true</code> if the signature verified successfully,
     *    <code>false</code> if not
     * @throws NullPointerException if <code>key</code>, <code>si</code> or
     *    <code>signature</code> are <code>null</code>
     * @throws InvalidKeyException if the key is improperly encoded, of
     *    the wrong type, or parameters are missing, etc
     * @throws SignatureException if an unexpected error occurs, such
     *    as the passed in signature is improperly encoded
     * @throws XMLSignatureException if an unexpected error occurs
     */
    boolean verify(Key key, DOMSignedInfo si, byte[] sig,
        XMLValidateContext context) throws InvalidKeyException,
        SignatureException, XMLSignatureException {
        if (key == null || si == null || sig == null) {
            throw new NullPointerException();
        }

        if (!(key instanceof PublicKey)) {
            throw new InvalidKeyException("key must be PublicKey");
        }
        if (signature == null) {
            try {
                signature = Signature.getInstance(getSignatureAlgorithm());
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initVerify((PublicKey) key);
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Signature provider:"+ signature.getProvider());
            log.log(Level.FINE, "verifying with key: " + key);
        }
        si.canonicalize(context, new SignerOutputStream(signature));

        if (getAlgorithm().equals(SignatureMethod.DSA_SHA1)) {
            try {
                return signature.verify(convertXMLDSIGtoASN1(sig));
            } catch (IOException ioe) {
                throw new XMLSignatureException(ioe);
            }
        } else {
            return signature.verify(sig);
        }
    }

    /**
     * Signs the bytes with the specified key, using the underlying
     * signature or MAC algorithm.
     *
     * @param key the signing key
     * @param si the DOMSignedInfo
     * @param context the XMLSignContext
     * @return the signature
     * @throws NullPointerException if <code>key</code> or
     *    <code>si</code> are <code>null</code>
     * @throws InvalidKeyException if the key is improperly encoded, of
     *    the wrong type, or parameters are missing, etc
     * @throws XMLSignatureException if an unexpected error occurs
     */
    byte[] sign(Key key, DOMSignedInfo si, XMLSignContext context)
        throws InvalidKeyException, XMLSignatureException {
        if (key == null || si == null) {
            throw new NullPointerException();
        }

        if (!(key instanceof PrivateKey)) {
            throw new InvalidKeyException("key must be PrivateKey");
        }
        if (signature == null) {
            try {
                signature = Signature.getInstance(getSignatureAlgorithm());
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLSignatureException(nsae);
            }
        }
        signature.initSign((PrivateKey) key);
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Signature provider:" +signature.getProvider());
            log.log(Level.FINE, "Signing with key: " + key);
        }

        si.canonicalize(context, new SignerOutputStream(signature));

        try {
            if (getAlgorithm().equals(SignatureMethod.DSA_SHA1)) {
                return convertASN1toXMLDSIG(signature.sign());
            } else {
                return signature.sign();
            }
        } catch (SignatureException se) {
            throw new XMLSignatureException(se);
        } catch (IOException ioe) {
            throw new XMLSignatureException(ioe);
        }
    }

    /**
     * Marshals the algorithm-specific parameters to an Element and
     * appends it to the specified parent element.  By default, this method
     * throws an exception since most SignatureMethod algorithms do not have
     * parameters. Subclasses should override it if they have parameters.
     *
     * @param parent the parent element to append the parameters to
     * @param paramsPrefix the algorithm parameters prefix to use
     * @throws MarshalException if the parameters cannot be marshalled
     */
    void marshalParams(Element parent, String paramsPrefix)
        throws MarshalException {
        throw new MarshalException("no parameters should " +
            "be specified for the " + getSignatureAlgorithm() +
            " SignatureMethod algorithm");
    }

    /**
     * Returns the java.security.Signature standard algorithm name.
     */
    abstract String getSignatureAlgorithm();

    /**
     * Returns true if parameters are equal; false otherwise.
     *
     * Subclasses should override this method to compare algorithm-specific
     * parameters.
     */
    boolean paramsEqual(AlgorithmParameterSpec spec) {
        return (getParameterSpec() == spec);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof SignatureMethod)) {
            return false;
        }
        SignatureMethod osm = (SignatureMethod) o;

        return (getAlgorithm().equals(osm.getAlgorithm()) &&
            paramsEqual(osm.getParameterSpec()));
    }

    /**
     * Converts an ASN.1 DSA value to a XML Signature DSA Value.
     *
     * The JAVA JCE DSA Signature algorithm creates ASN.1 encoded (r,s) value
     * pairs; the XML Signature requires the core BigInteger values.
     *
     * @param asn1Bytes
     *
     * @throws IOException
     * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#dsa-sha1">6.4.1 DSA</A>
     */
    private static byte[] convertASN1toXMLDSIG(byte asn1Bytes[])
        throws IOException {

        // THIS CODE IS COPIED FROM APACHE (see copyright at top of file)
        byte rLength = asn1Bytes[3];
        int i;

        for (i = rLength; (i > 0) && (asn1Bytes[(4 + rLength) - i] == 0); i--);

        byte sLength = asn1Bytes[5 + rLength];
        int j;

        for (j = sLength;
            (j > 0) && (asn1Bytes[(6 + rLength + sLength) - j] == 0); j--);

        if ((asn1Bytes[0] != 48) || (asn1Bytes[1] != asn1Bytes.length - 2)
            || (asn1Bytes[2] != 2) || (i > 20)
            || (asn1Bytes[4 + rLength] != 2) || (j > 20)) {
            throw new IOException("Invalid ASN.1 format of DSA signature");
        } else {
            byte xmldsigBytes[] = new byte[40];

            System.arraycopy(asn1Bytes, (4+rLength)-i, xmldsigBytes, 20-i, i);
            System.arraycopy(asn1Bytes, (6+rLength+sLength)-j, xmldsigBytes,
                          40 - j, j);

            return xmldsigBytes;
        }
    }

    /**
     * Converts a XML Signature DSA Value to an ASN.1 DSA value.
     *
     * The JAVA JCE DSA Signature algorithm creates ASN.1 encoded (r,s) value
     * pairs; the XML Signature requires the core BigInteger values.
     *
     * @param xmldsigBytes
     *
     * @throws IOException
     * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#dsa-sha1">6.4.1 DSA</A>
     */
    private static byte[] convertXMLDSIGtoASN1(byte xmldsigBytes[])
        throws IOException {

        // THIS CODE IS COPIED FROM APACHE (see copyright at top of file)
        if (xmldsigBytes.length != 40) {
            throw new IOException("Invalid XMLDSIG format of DSA signature");
        }

        int i;

        for (i = 20; (i > 0) && (xmldsigBytes[20 - i] == 0); i--);

        int j = i;

        if (xmldsigBytes[20 - i] < 0) {
            j += 1;
        }

        int k;

        for (k = 20; (k > 0) && (xmldsigBytes[40 - k] == 0); k--);

        int l = k;

        if (xmldsigBytes[40 - k] < 0) {
            l += 1;
        }

        byte asn1Bytes[] = new byte[6 + j + l];

        asn1Bytes[0] = 48;
        asn1Bytes[1] = (byte) (4 + j + l);
        asn1Bytes[2] = 2;
        asn1Bytes[3] = (byte) j;

        System.arraycopy(xmldsigBytes, 20 - i, asn1Bytes, (4 + j) - i, i);

        asn1Bytes[4 + j] = 2;
        asn1Bytes[5 + j] = (byte) l;

        System.arraycopy(xmldsigBytes, 40 - k, asn1Bytes, (6 + j + l) - k, k);

        return asn1Bytes;
    }

    static final class SHA1withRSA extends DOMSignatureMethod {
        SHA1withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA1withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return SignatureMethod.RSA_SHA1;
        }
        String getSignatureAlgorithm() {
            return "SHA1withRSA";
        }
    }

    static final class SHA256withRSA extends DOMSignatureMethod {
        SHA256withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA256withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return RSA_SHA256;
        }
        String getSignatureAlgorithm() {
            return "SHA256withRSA";
        }
    }

    static final class SHA384withRSA extends DOMSignatureMethod {
        SHA384withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA384withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return RSA_SHA384;
        }
        String getSignatureAlgorithm() {
            return "SHA384withRSA";
        }
    }

    static final class SHA512withRSA extends DOMSignatureMethod {
        SHA512withRSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA512withRSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return RSA_SHA512;
        }
        String getSignatureAlgorithm() {
            return "SHA512withRSA";
        }
    }

    static final class SHA1withDSA extends DOMSignatureMethod {
        SHA1withDSA(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
            super(params);
        }
        SHA1withDSA(Element dmElem) throws MarshalException {
            super(dmElem);
        }
        public String getAlgorithm() {
            return SignatureMethod.DSA_SHA1;
        }
        String getSignatureAlgorithm() {
            return "SHA1withDSA";
        }
    }
}
