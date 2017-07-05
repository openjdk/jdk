/*
 * Copyright (c) 1996, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs;

import java.io.OutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.*;
import java.util.ArrayList;

import sun.security.util.*;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;
import sun.security.x509.KeyUsageExtension;
import sun.security.x509.PKIXExtensions;
import sun.misc.HexDumpEncoder;

/**
 * A SignerInfo, as defined in PKCS#7's signedData type.
 *
 * @author Benjamin Renaud
 */
public class SignerInfo implements DerEncoder {

    BigInteger version;
    X500Name issuerName;
    BigInteger certificateSerialNumber;
    AlgorithmId digestAlgorithmId;
    AlgorithmId digestEncryptionAlgorithmId;
    byte[] encryptedDigest;

    PKCS9Attributes authenticatedAttributes;
    PKCS9Attributes unauthenticatedAttributes;

    public SignerInfo(X500Name  issuerName,
                      BigInteger serial,
                      AlgorithmId digestAlgorithmId,
                      AlgorithmId digestEncryptionAlgorithmId,
                      byte[] encryptedDigest) {
        this.version = BigInteger.ONE;
        this.issuerName = issuerName;
        this.certificateSerialNumber = serial;
        this.digestAlgorithmId = digestAlgorithmId;
        this.digestEncryptionAlgorithmId = digestEncryptionAlgorithmId;
        this.encryptedDigest = encryptedDigest;
    }

    public SignerInfo(X500Name  issuerName,
                      BigInteger serial,
                      AlgorithmId digestAlgorithmId,
                      PKCS9Attributes authenticatedAttributes,
                      AlgorithmId digestEncryptionAlgorithmId,
                      byte[] encryptedDigest,
                      PKCS9Attributes unauthenticatedAttributes) {
        this.version = BigInteger.ONE;
        this.issuerName = issuerName;
        this.certificateSerialNumber = serial;
        this.digestAlgorithmId = digestAlgorithmId;
        this.authenticatedAttributes = authenticatedAttributes;
        this.digestEncryptionAlgorithmId = digestEncryptionAlgorithmId;
        this.encryptedDigest = encryptedDigest;
        this.unauthenticatedAttributes = unauthenticatedAttributes;
    }

    /**
     * Parses a PKCS#7 signer info.
     */
    public SignerInfo(DerInputStream derin)
        throws IOException, ParsingException
    {
        this(derin, false);
    }

    /**
     * Parses a PKCS#7 signer info.
     *
     * <p>This constructor is used only for backwards compatibility with
     * PKCS#7 blocks that were generated using JDK1.1.x.
     *
     * @param derin the ASN.1 encoding of the signer info.
     * @param oldStyle flag indicating whether or not the given signer info
     * is encoded according to JDK1.1.x.
     */
    public SignerInfo(DerInputStream derin, boolean oldStyle)
        throws IOException, ParsingException
    {
        // version
        version = derin.getBigInteger();

        // issuerAndSerialNumber
        DerValue[] issuerAndSerialNumber = derin.getSequence(2);
        byte[] issuerBytes = issuerAndSerialNumber[0].toByteArray();
        issuerName = new X500Name(new DerValue(DerValue.tag_Sequence,
                                               issuerBytes));
        certificateSerialNumber = issuerAndSerialNumber[1].getBigInteger();

        // digestAlgorithmId
        DerValue tmp = derin.getDerValue();

        digestAlgorithmId = AlgorithmId.parse(tmp);

        // authenticatedAttributes
        if (oldStyle) {
            // In JDK1.1.x, the authenticatedAttributes are always present,
            // encoded as an empty Set (Set of length zero)
            derin.getSet(0);
        } else {
            // check if set of auth attributes (implicit tag) is provided
            // (auth attributes are OPTIONAL)
            if ((byte)(derin.peekByte()) == (byte)0xA0) {
                authenticatedAttributes = new PKCS9Attributes(derin);
            }
        }

        // digestEncryptionAlgorithmId - little RSA naming scheme -
        // signature == encryption...
        tmp = derin.getDerValue();

        digestEncryptionAlgorithmId = AlgorithmId.parse(tmp);

        // encryptedDigest
        encryptedDigest = derin.getOctetString();

        // unauthenticatedAttributes
        if (oldStyle) {
            // In JDK1.1.x, the unauthenticatedAttributes are always present,
            // encoded as an empty Set (Set of length zero)
            derin.getSet(0);
        } else {
            // check if set of unauth attributes (implicit tag) is provided
            // (unauth attributes are OPTIONAL)
            if (derin.available() != 0
                && (byte)(derin.peekByte()) == (byte)0xA1) {
                unauthenticatedAttributes =
                    new PKCS9Attributes(derin, true);// ignore unsupported attrs
            }
        }

        // all done
        if (derin.available() != 0) {
            throw new ParsingException("extra data at the end");
        }
    }

    public void encode(DerOutputStream out) throws IOException {

        derEncode(out);
    }

    /**
     * DER encode this object onto an output stream.
     * Implements the <code>DerEncoder</code> interface.
     *
     * @param out
     * the output stream on which to write the DER encoding.
     *
     * @exception IOException on encoding error.
     */
    public void derEncode(OutputStream out) throws IOException {
        DerOutputStream seq = new DerOutputStream();
        seq.putInteger(version);
        DerOutputStream issuerAndSerialNumber = new DerOutputStream();
        issuerName.encode(issuerAndSerialNumber);
        issuerAndSerialNumber.putInteger(certificateSerialNumber);
        seq.write(DerValue.tag_Sequence, issuerAndSerialNumber);

        digestAlgorithmId.encode(seq);

        // encode authenticated attributes if there are any
        if (authenticatedAttributes != null)
            authenticatedAttributes.encode((byte)0xA0, seq);

        digestEncryptionAlgorithmId.encode(seq);

        seq.putOctetString(encryptedDigest);

        // encode unauthenticated attributes if there are any
        if (unauthenticatedAttributes != null)
            unauthenticatedAttributes.encode((byte)0xA1, seq);

        DerOutputStream tmp = new DerOutputStream();
        tmp.write(DerValue.tag_Sequence, seq);

        out.write(tmp.toByteArray());
    }



    /*
     * Returns the (user) certificate pertaining to this SignerInfo.
     */
    public X509Certificate getCertificate(PKCS7 block)
        throws IOException
    {
        return block.getCertificate(certificateSerialNumber, issuerName);
    }

    /*
     * Returns the certificate chain pertaining to this SignerInfo.
     */
    public ArrayList<X509Certificate> getCertificateChain(PKCS7 block)
        throws IOException
    {
        X509Certificate userCert;
        userCert = block.getCertificate(certificateSerialNumber, issuerName);
        if (userCert == null)
            return null;

        ArrayList<X509Certificate> certList = new ArrayList<>();
        certList.add(userCert);

        X509Certificate[] pkcsCerts = block.getCertificates();
        if (pkcsCerts == null
            || userCert.getSubjectDN().equals(userCert.getIssuerDN())) {
            return certList;
        }

        Principal issuer = userCert.getIssuerDN();
        int start = 0;
        while (true) {
            boolean match = false;
            int i = start;
            while (i < pkcsCerts.length) {
                if (issuer.equals(pkcsCerts[i].getSubjectDN())) {
                    // next cert in chain found
                    certList.add(pkcsCerts[i]);
                    // if selected cert is self-signed, we're done
                    // constructing the chain
                    if (pkcsCerts[i].getSubjectDN().equals(
                                            pkcsCerts[i].getIssuerDN())) {
                        start = pkcsCerts.length;
                    } else {
                        issuer = pkcsCerts[i].getIssuerDN();
                        X509Certificate tmpCert = pkcsCerts[start];
                        pkcsCerts[start] = pkcsCerts[i];
                        pkcsCerts[i] = tmpCert;
                        start++;
                    }
                    match = true;
                    break;
                } else {
                    i++;
                }
            }
            if (!match)
                break;
        }

        return certList;
    }

    /* Returns null if verify fails, this signerInfo if
       verify succeeds. */
    SignerInfo verify(PKCS7 block, byte[] data)
            throws NoSuchAlgorithmException, SignatureException {

        PKCS7.PKCS7Verifier p7v = PKCS7.PKCS7Verifier.from(block, this);
        if (p7v == null) return null;
        if (data == null) {
            try {
                data = block.getContentInfo().getContentBytes();
            } catch (IOException e) {
                throw new SignatureException("IO error verifying signature:\n" +
                                             e.getMessage());
            }
        }
        p7v.update(data, 0, data.length);
        return p7v.verify();
    }

    /* Verify the content of the pkcs7 block. */
    SignerInfo verify(PKCS7 block)
    throws NoSuchAlgorithmException, SignatureException {
        return verify(block, null);
    }


    public BigInteger getVersion() {
            return version;
    }

    public X500Name getIssuerName() {
        return issuerName;
    }

    public BigInteger getCertificateSerialNumber() {
        return certificateSerialNumber;
    }

    public AlgorithmId getDigestAlgorithmId() {
        return digestAlgorithmId;
    }

    public PKCS9Attributes getAuthenticatedAttributes() {
        return authenticatedAttributes;
    }

    public AlgorithmId getDigestEncryptionAlgorithmId() {
        return digestEncryptionAlgorithmId;
    }

    public byte[] getEncryptedDigest() {
        return encryptedDigest;
    }

    public PKCS9Attributes getUnauthenticatedAttributes() {
        return unauthenticatedAttributes;
    }

    public String toString() {
        HexDumpEncoder hexDump = new HexDumpEncoder();

        String out = "";

        out += "Signer Info for (issuer): " + issuerName + "\n";
        out += "\tversion: " + Debug.toHexString(version) + "\n";
        out += "\tcertificateSerialNumber: " +
               Debug.toHexString(certificateSerialNumber) + "\n";
        out += "\tdigestAlgorithmId: " + digestAlgorithmId + "\n";
        if (authenticatedAttributes != null) {
            out += "\tauthenticatedAttributes: " + authenticatedAttributes +
                   "\n";
        }
        out += "\tdigestEncryptionAlgorithmId: " + digestEncryptionAlgorithmId +
            "\n";

        out += "\tencryptedDigest: " + "\n" +
            hexDump.encodeBuffer(encryptedDigest) + "\n";
        if (unauthenticatedAttributes != null) {
            out += "\tunauthenticatedAttributes: " +
                   unauthenticatedAttributes + "\n";
        }
        return out;
    }

}
