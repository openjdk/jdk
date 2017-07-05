/*
 * Copyright (c) 1996, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.x509;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateEncodingException;
import java.security.*;
import java.util.Date;

import sun.security.pkcs.PKCS10;


/**
 * Generate a pair of keys, and provide access to them.  This class is
 * provided primarily for ease of use.
 *
 * <P>This provides some simple certificate management functionality.
 * Specifically, it allows you to create self-signed X.509 certificates
 * as well as PKCS 10 based certificate signing requests.
 *
 * <P>Keys for some public key signature algorithms have algorithm
 * parameters, such as DSS/DSA.  Some sites' Certificate Authorities
 * adopt fixed algorithm parameters, which speeds up some operations
 * including key generation and signing.  <em>At this time, this interface
 * does not provide a way to provide such algorithm parameters, e.g.
 * by providing the CA certificate which includes those parameters.</em>
 *
 * <P>Also, note that at this time only signature-capable keys may be
 * acquired through this interface.  Diffie-Hellman keys, used for secure
 * key exchange, may be supported later.
 *
 * @author David Brownell
 * @author Hemma Prafullchandra
 * @see PKCS10
 * @see X509CertImpl
 */
public final class CertAndKeyGen {
    /**
     * Creates a CertAndKeyGen object for a particular key type
     * and signature algorithm.
     *
     * @param keyType type of key, e.g. "RSA", "DSA"
     * @param sigAlg name of the signature algorithm, e.g. "MD5WithRSA",
     *          "MD2WithRSA", "SHAwithDSA".
     * @exception NoSuchAlgorithmException on unrecognized algorithms.
     */
    public CertAndKeyGen (String keyType, String sigAlg)
    throws NoSuchAlgorithmException
    {
        keyGen = KeyPairGenerator.getInstance(keyType);
        this.sigAlg = sigAlg;
    }

    /**
     * Creates a CertAndKeyGen object for a particular key type,
     * signature algorithm, and provider.
     *
     * @param keyType type of key, e.g. "RSA", "DSA"
     * @param sigAlg name of the signature algorithm, e.g. "MD5WithRSA",
     *          "MD2WithRSA", "SHAwithDSA".
     * @param providerName name of the provider
     * @exception NoSuchAlgorithmException on unrecognized algorithms.
     * @exception NoSuchProviderException on unrecognized providers.
     */
    public CertAndKeyGen (String keyType, String sigAlg, String providerName)
    throws NoSuchAlgorithmException, NoSuchProviderException
    {
        if (providerName == null) {
            keyGen = KeyPairGenerator.getInstance(keyType);
        } else {
            try {
                keyGen = KeyPairGenerator.getInstance(keyType, providerName);
            } catch (Exception e) {
                // try first available provider instead
                keyGen = KeyPairGenerator.getInstance(keyType);
            }
        }
        this.sigAlg = sigAlg;
    }

    /**
     * Sets the source of random numbers used when generating keys.
     * If you do not provide one, a system default facility is used.
     * You may wish to provide your own source of random numbers
     * to get a reproducible sequence of keys and signatures, or
     * because you may be able to take advantage of strong sources
     * of randomness/entropy in your environment.
     */
    public void         setRandom (SecureRandom generator)
    {
        prng = generator;
    }

    // want "public void generate (X509Certificate)" ... inherit DSA/D-H param

    /**
     * Generates a random public/private key pair, with a given key
     * size.  Different algorithms provide different degrees of security
     * for the same key size, because of the "work factor" involved in
     * brute force attacks.  As computers become faster, it becomes
     * easier to perform such attacks.  Small keys are to be avoided.
     *
     * <P>Note that not all values of "keyBits" are valid for all
     * algorithms, and not all public key algorithms are currently
     * supported for use in X.509 certificates.  If the algorithm
     * you specified does not produce X.509 compatible keys, an
     * invalid key exception is thrown.
     *
     * @param keyBits the number of bits in the keys.
     * @exception InvalidKeyException if the environment does not
     *  provide X.509 public keys for this signature algorithm.
     */
    public void generate (int keyBits)
    throws InvalidKeyException
    {
        KeyPair pair;

        try {
            if (prng == null) {
                prng = new SecureRandom();
            }
            keyGen.initialize(keyBits, prng);
            pair = keyGen.generateKeyPair();

        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        publicKey = pair.getPublic();
        privateKey = pair.getPrivate();
    }


    /**
     * Returns the public key of the generated key pair if it is of type
     * <code>X509Key</code>, or null if the public key is of a different type.
     *
     * XXX Note: This behaviour is needed for backwards compatibility.
     * What this method really should return is the public key of the
     * generated key pair, regardless of whether or not it is an instance of
     * <code>X509Key</code>. Accordingly, the return type of this method
     * should be <code>PublicKey</code>.
     */
    public X509Key getPublicKey()
    {
        if (!(publicKey instanceof X509Key)) {
            return null;
        }
        return (X509Key)publicKey;
    }


    /**
     * Returns the private key of the generated key pair.
     *
     * <P><STRONG><em>Be extremely careful when handling private keys.
     * When private keys are not kept secret, they lose their ability
     * to securely authenticate specific entities ... that is a huge
     * security risk!</em></STRONG>
     */
    public PrivateKey getPrivateKey ()
    {
        return privateKey;
    }


    /**
     * Returns a self-signed X.509v3 certificate for the public key.
     * The certificate is immediately valid. No extensions.
     *
     * <P>Such certificates normally are used to identify a "Certificate
     * Authority" (CA).  Accordingly, they will not always be accepted by
     * other parties.  However, such certificates are also useful when
     * you are bootstrapping your security infrastructure, or deploying
     * system prototypes.
     *
     * @param myname X.500 name of the subject (who is also the issuer)
     * @param firstDate the issue time of the certificate
     * @param validity how long the certificate should be valid, in seconds
     * @exception CertificateException on certificate handling errors.
     * @exception InvalidKeyException on key handling errors.
     * @exception SignatureException on signature handling errors.
     * @exception NoSuchAlgorithmException on unrecognized algorithms.
     * @exception NoSuchProviderException on unrecognized providers.
     */
    public X509Certificate getSelfCertificate (
            X500Name myname, Date firstDate, long validity)
    throws CertificateException, InvalidKeyException, SignatureException,
        NoSuchAlgorithmException, NoSuchProviderException
    {
        X509CertImpl    cert;
        Date            lastDate;

        try {
            lastDate = new Date ();
            lastDate.setTime (firstDate.getTime () + validity * 1000);

            CertificateValidity interval =
                                   new CertificateValidity(firstDate,lastDate);

            X509CertInfo info = new X509CertInfo();
            // Add all mandatory attributes
            info.set(X509CertInfo.VERSION,
                     new CertificateVersion(CertificateVersion.V3));
            info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(
                    new java.util.Random().nextInt() & 0x7fffffff));
            AlgorithmId algID = AlgorithmId.getAlgorithmId(sigAlg);
            info.set(X509CertInfo.ALGORITHM_ID,
                     new CertificateAlgorithmId(algID));
            info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(myname));
            info.set(X509CertInfo.KEY, new CertificateX509Key(publicKey));
            info.set(X509CertInfo.VALIDITY, interval);
            info.set(X509CertInfo.ISSUER, new CertificateIssuerName(myname));

            cert = new X509CertImpl(info);
            cert.sign(privateKey, this.sigAlg);

            return (X509Certificate)cert;

        } catch (IOException e) {
             throw new CertificateEncodingException("getSelfCert: " +
                                                    e.getMessage());
        }
    }

    // Keep the old method
    public X509Certificate getSelfCertificate (X500Name myname, long validity)
    throws CertificateException, InvalidKeyException, SignatureException,
        NoSuchAlgorithmException, NoSuchProviderException
    {
        return getSelfCertificate(myname, new Date(), validity);
    }

    /**
     * Returns a PKCS #10 certificate request.  The caller uses either
     * <code>PKCS10.print</code> or <code>PKCS10.toByteArray</code>
     * operations on the result, to get the request in an appropriate
     * transmission format.
     *
     * <P>PKCS #10 certificate requests are sent, along with some proof
     * of identity, to Certificate Authorities (CAs) which then issue
     * X.509 public key certificates.
     *
     * @param myname X.500 name of the subject
     * @exception InvalidKeyException on key handling errors.
     * @exception SignatureException on signature handling errors.
     */
    public PKCS10 getCertRequest (X500Name myname)
    throws InvalidKeyException, SignatureException
    {
        PKCS10  req = new PKCS10 (publicKey);

        try {
            Signature signature = Signature.getInstance(sigAlg);
            signature.initSign (privateKey);
            req.encodeAndSign(myname, signature);

        } catch (CertificateException e) {
            throw new SignatureException (sigAlg + " CertificateException");

        } catch (IOException e) {
            throw new SignatureException (sigAlg + " IOException");

        } catch (NoSuchAlgorithmException e) {
            // "can't happen"
            throw new SignatureException (sigAlg + " unavailable?");
        }
        return req;
    }

    private SecureRandom        prng;
    private String              sigAlg;
    private KeyPairGenerator    keyGen;
    private PublicKey           publicKey;
    private PrivateKey          privateKey;
}
