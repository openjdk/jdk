/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.timestamp;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Extension;
import sun.security.util.DerValue;
import sun.security.util.DerOutputStream;
import sun.security.util.ObjectIdentifier;

/**
 * This class provides a timestamp request, as defined in
 * <a href="http://www.ietf.org/rfc/rfc3161.txt">RFC 3161</a>.
 *
 * The TimeStampReq ASN.1 type has the following definition:
 * <pre>
 *
 *     TimeStampReq ::= SEQUENCE {
 *         version           INTEGER { v1(1) },
 *         messageImprint    MessageImprint
 *           -- a hash algorithm OID and the hash value of the data to be
 *           -- time-stamped.
 *         reqPolicy         TSAPolicyId    OPTIONAL,
 *         nonce             INTEGER        OPTIONAL,
 *         certReq           BOOLEAN        DEFAULT FALSE,
 *         extensions        [0] IMPLICIT Extensions OPTIONAL }
 *
 *     MessageImprint ::= SEQUENCE {
 *         hashAlgorithm     AlgorithmIdentifier,
 *         hashedMessage     OCTET STRING }
 *
 *     TSAPolicyId ::= OBJECT IDENTIFIER
 *
 * </pre>
 *
 * @since 1.5
 * @author Vincent Ryan
 * @see Timestamper
 */

public class TSRequest {

    private static final ObjectIdentifier SHA1_OID;
    private static final ObjectIdentifier MD5_OID;
    static {
        ObjectIdentifier sha1 = null;
        ObjectIdentifier md5 = null;
        try {
            sha1 = new ObjectIdentifier("1.3.14.3.2.26");
            md5 = new ObjectIdentifier("1.2.840.113549.2.5");
        } catch (IOException ioe) {
            // should not happen
        }
        SHA1_OID = sha1;
        MD5_OID = md5;
    }

    private int version = 1;

    private ObjectIdentifier hashAlgorithmId = null;

    private byte[] hashValue;

    private String policyId = null;

    private BigInteger nonce = null;

    private boolean returnCertificate = false;

    private X509Extension[] extensions = null;

    /**
     * Constructs a timestamp request for the supplied hash value..
     *
     * @param hashValue     The hash value. This is the data to be timestamped.
     * @param hashAlgorithm The name of the hash algorithm.
     */
    public TSRequest(byte[] hashValue, String hashAlgorithm) {

        // Check the common hash algorithms
        if ("MD5".equalsIgnoreCase(hashAlgorithm)) {
            hashAlgorithmId = MD5_OID;
            // Check that the hash value matches the hash algorithm
            assert hashValue.length == 16;

        } else if ("SHA-1".equalsIgnoreCase(hashAlgorithm) ||
            "SHA".equalsIgnoreCase(hashAlgorithm) ||
            "SHA1".equalsIgnoreCase(hashAlgorithm)) {
            hashAlgorithmId = SHA1_OID;
            // Check that the hash value matches the hash algorithm
            assert hashValue.length == 20;

        }
        // Clone the hash value
        this.hashValue = new byte[hashValue.length];
        System.arraycopy(hashValue, 0, this.hashValue, 0, hashValue.length);
    }

    /**
     * Sets the Time-Stamp Protocol version.
     *
     * @param version The TSP version.
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Sets an object identifier for the Time-Stamp Protocol policy.
     *
     * @param version The policy object identifier.
     */
    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    /**
     * Sets a nonce.
     * A nonce is a single-use random number.
     *
     * @param nonce The nonce value.
     */
    public void setNonce(BigInteger nonce) {
        this.nonce = nonce;
    }

    /**
     * Request that the TSA include its signing certificate in the response.
     *
     * @param returnCertificate True if the TSA should return its signing
     *                          certificate. By default it is not returned.
     */
    public void requestCertificate(boolean returnCertificate) {
        this.returnCertificate = returnCertificate;
    }

    /**
     * Sets the Time-Stamp Protocol extensions.
     *
     * @param extensions The protocol extensions.
     */
    public void setExtensions(X509Extension[] extensions) {
        this.extensions = extensions;
    }

    public byte[] encode() throws IOException {

        DerOutputStream request = new DerOutputStream();

        // encode version
        request.putInteger(version);

        // encode messageImprint
        DerOutputStream messageImprint = new DerOutputStream();
        DerOutputStream hashAlgorithm = new DerOutputStream();
        hashAlgorithm.putOID(hashAlgorithmId);
        messageImprint.write(DerValue.tag_Sequence, hashAlgorithm);
        messageImprint.putOctetString(hashValue);
        request.write(DerValue.tag_Sequence, messageImprint);

        // encode optional elements

        if (policyId != null) {
            request.putOID(new ObjectIdentifier(policyId));
        }
        if (nonce != null) {
            request.putInteger(nonce);
        }
        if (returnCertificate) {
            request.putBoolean(true);
        }

        DerOutputStream out = new DerOutputStream();
        out.write(DerValue.tag_Sequence, request);
        return out.toByteArray();
    }
}
