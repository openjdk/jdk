/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.cert.internal.x509;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.Signature;
import javax.security.cert.*;
import java.security.*;
import java.util.Date;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Vector;

/**
 * The X509V1CertImpl class is used as a conversion wrapper around
 * sun.security.x509.X509Cert certificates when running under JDK1.1.x.
 *
 * @deprecated This is the implementation class for the deprecated
 *  {@code javax.security.cert.X509Certificate} class. The classes in the
 *  {@code java.security.cert} package should be used instead.
 *
 * @author Jeff Nisewanger
 */
@Deprecated
public class X509V1CertImpl extends X509Certificate implements Serializable {
    static final long serialVersionUID = -2048442350420423405L;
    private java.security.cert.X509Certificate wrappedCert;

    private static synchronized java.security.cert.CertificateFactory
    getFactory()
    throws java.security.cert.CertificateException
    {
        return java.security.cert.CertificateFactory.getInstance("X.509");
    }

    /**
     * Default constructor.
     */
    public X509V1CertImpl() { }

    /**
     * Unmarshals a certificate from its encoded form, parsing the
     * encoded bytes.  This form of constructor is used by agents which
     * need to examine and use certificate contents.  That is, this is
     * one of the more commonly used constructors.  Note that the buffer
     * must include only a certificate, and no "garbage" may be left at
     * the end.  If you need to ignore data at the end of a certificate,
     * use another constructor.
     *
     * @param certData the encoded bytes, with no trailing padding.
     * @exception CertificateException on parsing errors.
     */
    public X509V1CertImpl(byte[] certData)
    throws CertificateException {
        try {
            ByteArrayInputStream bs;

            bs = new ByteArrayInputStream(certData);
            wrappedCert = (java.security.cert.X509Certificate)
                getFactory().generateCertificate(bs);
        } catch (java.security.cert.CertificateException e) {
            throw new CertificateException(e.getMessage());
        }
    }

    /**
     * unmarshals an X.509 certificate from an input stream.
     *
     * @param in an input stream holding at least one certificate
     * @exception CertificateException on parsing errors.
     */
    public X509V1CertImpl(InputStream in)
    throws CertificateException {
        try {
            wrappedCert = (java.security.cert.X509Certificate)
                getFactory().generateCertificate(in);
        } catch (java.security.cert.CertificateException e) {
            throw new CertificateException(e.getMessage());
        }
    }

    /**
     * Returns the encoded form of this certificate. It is
     * assumed that each certificate type would have only a single
     * form of encoding; for example, X.509 certificates would
     * be encoded as ASN.1 DER.
     */
    public byte[] getEncoded() throws CertificateEncodingException {
        try {
            return wrappedCert.getEncoded();
        } catch (java.security.cert.CertificateEncodingException e) {
            throw new CertificateEncodingException(e.getMessage());
        }
    }

    /**
     * Throws an exception if the certificate was not signed using the
     * verification key provided.  Successfully verifying a certificate
     * does <em>not</em> indicate that one should trust the entity which
     * it represents.
     *
     * @param key the public key used for verification.
     */
    public void verify(PublicKey key)
        throws CertificateException, NoSuchAlgorithmException,
        InvalidKeyException, NoSuchProviderException,
        SignatureException
    {
        try {
            wrappedCert.verify(key);
        } catch (java.security.cert.CertificateException e) {
            throw new CertificateException(e.getMessage());
        }
    }

    /**
     * Throws an exception if the certificate was not signed using the
     * verification key provided.  Successfully verifying a certificate
     * does <em>not</em> indicate that one should trust the entity which
     * it represents.
     *
     * @param key the public key used for verification.
     * @param sigProvider the name of the provider.
     */
    public void verify(PublicKey key, String sigProvider)
        throws CertificateException, NoSuchAlgorithmException,
        InvalidKeyException, NoSuchProviderException,
        SignatureException
    {
        try {
            wrappedCert.verify(key, sigProvider);
        } catch (java.security.cert.CertificateException e) {
            throw new CertificateException(e.getMessage());
        }
    }

    /**
     * Checks that the certificate is currently valid, i.e. the current
     * time is within the specified validity period.
     */
    public void checkValidity() throws
      CertificateExpiredException, CertificateNotYetValidException {
        checkValidity(new Date());
    }

    /**
     * Checks that the specified date is within the certificate's
     * validity period, or basically if the certificate would be
     * valid at the specified date/time.
     *
     * @param date the Date to check against to see if this certificate
     *        is valid at that date/time.
     */
    public void checkValidity(Date date) throws
      CertificateExpiredException, CertificateNotYetValidException {
        try {
            wrappedCert.checkValidity(date);
        } catch (java.security.cert.CertificateNotYetValidException e) {
            throw new CertificateNotYetValidException(e.getMessage());
        } catch (java.security.cert.CertificateExpiredException e) {
            throw new CertificateExpiredException(e.getMessage());
        }
    }


    /**
     * Returns a printable representation of the certificate.  This does not
     * contain all the information available to distinguish this from any
     * other certificate.  The certificate must be fully constructed
     * before this function may be called.
     */
    public String toString() {
        return wrappedCert.toString();
    }

    /**
     * Gets the publickey from this certificate.
     *
     * @return the publickey.
     */
    public PublicKey getPublicKey() {
        PublicKey key = wrappedCert.getPublicKey();
        return key;
    }

    /*
     * Gets the version number from the certificate.
     *
     * @return the version number.
     */
    public int getVersion() {
        return wrappedCert.getVersion() - 1;
    }

    /**
     * Gets the serial number from the certificate.
     *
     * @return the serial number.
     */
    public BigInteger getSerialNumber() {
        return wrappedCert.getSerialNumber();
    }

    /**
     * Gets the subject distinguished name from the certificate.
     *
     * @return the subject name.
     * @exception CertificateException if a parsing error occurs.
     */
    public Principal getSubjectDN() {
        return wrappedCert.getSubjectDN();
    }

    /**
     * Gets the issuer distinguished name from the certificate.
     *
     * @return the issuer name.
     * @exception CertificateException if a parsing error occurs.
     */
    public Principal getIssuerDN() {
        return wrappedCert.getIssuerDN();
    }

    /**
     * Gets the notBefore date from the validity period of the certificate.
     *
     * @return the start date of the validity period.
     * @exception CertificateException if a parsing error occurs.
     */
    public Date getNotBefore() {
        return wrappedCert.getNotBefore();
    }

    /**
     * Gets the notAfter date from the validity period of the certificate.
     *
     * @return the end date of the validity period.
     * @exception CertificateException if a parsing error occurs.
     */
    public Date getNotAfter() {
        return wrappedCert.getNotAfter();
    }

    /**
     * Gets the signature algorithm name for the certificate
     * signature algorithm.
     * For example, the string "SHA1/DSA".
     *
     * @return the signature algorithm name.
     * @exception CertificateException if a parsing error occurs.
     */
    public String getSigAlgName() {
        return wrappedCert.getSigAlgName();
    }

    /**
     * Gets the signature algorithm OID string from the certificate.
     * For example, the string "1.2.840.10040.4.3"
     *
     * @return the signature algorithm oid string.
     * @exception CertificateException if a parsing error occurs.
     */
    public String getSigAlgOID() {
        return wrappedCert.getSigAlgOID();
    }

    /**
     * Gets the DER encoded signature algorithm parameters from this
     * certificate's signature algorithm.
     *
     * @return the DER encoded signature algorithm parameters, or
     *         null if no parameters are present.
     * @exception CertificateException if a parsing error occurs.
     */
    public byte[] getSigAlgParams() {
        return wrappedCert.getSigAlgParams();
    }

    private synchronized void writeObject(ObjectOutputStream stream)
        throws IOException {
        try {
            stream.write(getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IOException("getEncoded failed: " + e.getMessage());
        }
    }

    private synchronized void readObject(ObjectInputStream stream)
        throws IOException {
        try {
            wrappedCert = (java.security.cert.X509Certificate)
                getFactory().generateCertificate(stream);
        } catch (java.security.cert.CertificateException e) {
            throw new IOException("generateCertificate failed: " + e.getMessage());
        }
    }

    public java.security.cert.X509Certificate getX509Certificate() {
        return wrappedCert;
    }
}
