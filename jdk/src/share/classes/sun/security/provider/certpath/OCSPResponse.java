/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CRLReason;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import sun.misc.HexDumpEncoder;
import sun.security.x509.*;
import sun.security.util.*;

/**
 * This class is used to process an OCSP response.
 * The OCSP Response is defined
 * in RFC 2560 and the ASN.1 encoding is as follows:
 * <pre>
 *
 *  OCSPResponse ::= SEQUENCE {
 *      responseStatus         OCSPResponseStatus,
 *      responseBytes          [0] EXPLICIT ResponseBytes OPTIONAL }
 *
 *   OCSPResponseStatus ::= ENUMERATED {
 *       successful            (0),  --Response has valid confirmations
 *       malformedRequest      (1),  --Illegal confirmation request
 *       internalError         (2),  --Internal error in issuer
 *       tryLater              (3),  --Try again later
 *                                   --(4) is not used
 *       sigRequired           (5),  --Must sign the request
 *       unauthorized          (6)   --Request unauthorized
 *   }
 *
 *   ResponseBytes ::=       SEQUENCE {
 *       responseType   OBJECT IDENTIFIER,
 *       response       OCTET STRING }
 *
 *   BasicOCSPResponse       ::= SEQUENCE {
 *      tbsResponseData      ResponseData,
 *      signatureAlgorithm   AlgorithmIdentifier,
 *      signature            BIT STRING,
 *      certs                [0] EXPLICIT SEQUENCE OF Certificate OPTIONAL }
 *
 *   The value for signature SHALL be computed on the hash of the DER
 *   encoding ResponseData.
 *
 *   ResponseData ::= SEQUENCE {
 *      version              [0] EXPLICIT Version DEFAULT v1,
 *      responderID              ResponderID,
 *      producedAt               GeneralizedTime,
 *      responses                SEQUENCE OF SingleResponse,
 *      responseExtensions   [1] EXPLICIT Extensions OPTIONAL }
 *
 *   ResponderID ::= CHOICE {
 *      byName               [1] Name,
 *      byKey                [2] KeyHash }
 *
 *   KeyHash ::= OCTET STRING -- SHA-1 hash of responder's public key
 *   (excluding the tag and length fields)
 *
 *   SingleResponse ::= SEQUENCE {
 *      certID                       CertID,
 *      certStatus                   CertStatus,
 *      thisUpdate                   GeneralizedTime,
 *      nextUpdate         [0]       EXPLICIT GeneralizedTime OPTIONAL,
 *      singleExtensions   [1]       EXPLICIT Extensions OPTIONAL }
 *
 *   CertStatus ::= CHOICE {
 *       good        [0]     IMPLICIT NULL,
 *       revoked     [1]     IMPLICIT RevokedInfo,
 *       unknown     [2]     IMPLICIT UnknownInfo }
 *
 *   RevokedInfo ::= SEQUENCE {
 *       revocationTime              GeneralizedTime,
 *       revocationReason    [0]     EXPLICIT CRLReason OPTIONAL }
 *
 *   UnknownInfo ::= NULL -- this can be replaced with an enumeration
 *
 * </pre>
 *
 * @author      Ram Marti
 */

public final class OCSPResponse {

    public enum ResponseStatus {
        SUCCESSFUL,            // Response has valid confirmations
        MALFORMED_REQUEST,     // Illegal confirmation request
        INTERNAL_ERROR,        // Internal error in issuer
        TRY_LATER,             // Try again later
        UNUSED,                // is not used
        SIG_REQUIRED,          // Must sign the request
        UNAUTHORIZED           // Request unauthorized
    };
    private static ResponseStatus[] rsvalues = ResponseStatus.values();

    private static final Debug DEBUG = Debug.getInstance("certpath");
    private static final boolean dump = false;
    private static final ObjectIdentifier OCSP_BASIC_RESPONSE_OID =
        ObjectIdentifier.newInternal(new int[] { 1, 3, 6, 1, 5, 5, 7, 48, 1, 1});
    private static final ObjectIdentifier OCSP_NONCE_EXTENSION_OID =
        ObjectIdentifier.newInternal(new int[] { 1, 3, 6, 1, 5, 5, 7, 48, 1, 2});

    private static final int CERT_STATUS_GOOD = 0;
    private static final int CERT_STATUS_REVOKED = 1;
    private static final int CERT_STATUS_UNKNOWN = 2;

    // ResponderID CHOICE tags
    private static final int NAME_TAG = 1;
    private static final int KEY_TAG = 2;

    // Object identifier for the OCSPSigning key purpose
    private static final String KP_OCSP_SIGNING_OID = "1.3.6.1.5.5.7.3.9";

    private final ResponseStatus responseStatus;
    private final Map<CertId, SingleResponse> singleResponseMap;

    // Maximum clock skew in milliseconds (15 minutes) allowed when checking
    // validity of OCSP responses
    private static final long MAX_CLOCK_SKEW = 900000;

    // an array of all of the CRLReasons (used in SingleResponse)
    private static CRLReason[] values = CRLReason.values();

    /*
     * Create an OCSP response from its ASN.1 DER encoding.
     */
    OCSPResponse(byte[] bytes, Date dateCheckedAgainst,
        X509Certificate responderCert)
        throws IOException, CertPathValidatorException {

        // OCSPResponse
        if (dump) {
            HexDumpEncoder hexEnc = new HexDumpEncoder();
            System.out.println("OCSPResponse bytes are...");
            System.out.println(hexEnc.encode(bytes));
        }
        DerValue der = new DerValue(bytes);
        if (der.tag != DerValue.tag_Sequence) {
            throw new IOException("Bad encoding in OCSP response: " +
                "expected ASN.1 SEQUENCE tag.");
        }
        DerInputStream derIn = der.getData();

        // responseStatus
        int status = derIn.getEnumerated();
        if (status >= 0 && status < rsvalues.length) {
            responseStatus = rsvalues[status];
        } else {
            // unspecified responseStatus
            throw new IOException("Unknown OCSPResponse status: " + status);
        }
        if (DEBUG != null) {
            DEBUG.println("OCSP response status: " + responseStatus);
        }
        if (responseStatus != ResponseStatus.SUCCESSFUL) {
            // no need to continue, responseBytes are not set.
            singleResponseMap = Collections.emptyMap();
            return;
        }

        // responseBytes
        der = derIn.getDerValue();
        if (!der.isContextSpecific((byte)0)) {
            throw new IOException("Bad encoding in responseBytes element " +
                "of OCSP response: expected ASN.1 context specific tag 0.");
        }
        DerValue tmp = der.data.getDerValue();
        if (tmp.tag != DerValue.tag_Sequence) {
            throw new IOException("Bad encoding in responseBytes element " +
                "of OCSP response: expected ASN.1 SEQUENCE tag.");
        }

        // responseType
        derIn = tmp.data;
        ObjectIdentifier responseType = derIn.getOID();
        if (responseType.equals(OCSP_BASIC_RESPONSE_OID)) {
            if (DEBUG != null) {
                DEBUG.println("OCSP response type: basic");
            }
        } else {
            if (DEBUG != null) {
                DEBUG.println("OCSP response type: " + responseType);
            }
            throw new IOException("Unsupported OCSP response type: " +
                responseType);
        }

        // BasicOCSPResponse
        DerInputStream basicOCSPResponse =
            new DerInputStream(derIn.getOctetString());

        DerValue[] seqTmp = basicOCSPResponse.getSequence(2);
        if (seqTmp.length < 3) {
            throw new IOException("Unexpected BasicOCSPResponse value");
        }

        DerValue responseData = seqTmp[0];

        // Need the DER encoded ResponseData to verify the signature later
        byte[] responseDataDer = seqTmp[0].toByteArray();

        // tbsResponseData
        if (responseData.tag != DerValue.tag_Sequence) {
            throw new IOException("Bad encoding in tbsResponseData " +
                "element of OCSP response: expected ASN.1 SEQUENCE tag.");
        }
        DerInputStream seqDerIn = responseData.data;
        DerValue seq = seqDerIn.getDerValue();

        // version
        if (seq.isContextSpecific((byte)0)) {
            // seq[0] is version
            if (seq.isConstructed() && seq.isContextSpecific()) {
                //System.out.println ("version is available");
                seq = seq.data.getDerValue();
                int version = seq.getInteger();
                if (seq.data.available() != 0) {
                    throw new IOException("Bad encoding in version " +
                        " element of OCSP response: bad format");
                }
                seq = seqDerIn.getDerValue();
            }
        }

        // responderID
        short tag = (byte)(seq.tag & 0x1f);
        if (tag == NAME_TAG) {
            if (DEBUG != null) {
                X500Name responderName = new X500Name(seq.getData());
                DEBUG.println("OCSP Responder name: " + responderName);
            }
        } else if (tag == KEY_TAG) {
            // Ignore, for now
        } else {
            throw new IOException("Bad encoding in responderID element of " +
                "OCSP response: expected ASN.1 context specific tag 0 or 1");
        }

        // producedAt
        seq = seqDerIn.getDerValue();
        if (DEBUG != null) {
            Date producedAtDate = seq.getGeneralizedTime();
            DEBUG.println("OCSP response produced at: " + producedAtDate);
        }

        // responses
        DerValue[] singleResponseDer = seqDerIn.getSequence(1);
        singleResponseMap
            = new HashMap<CertId, SingleResponse>(singleResponseDer.length);
        if (DEBUG != null) {
            DEBUG.println("OCSP number of SingleResponses: "
                + singleResponseDer.length);
        }
        for (int i = 0; i < singleResponseDer.length; i++) {
            SingleResponse singleResponse
                = new SingleResponse(singleResponseDer[i]);
            singleResponseMap.put(singleResponse.getCertId(), singleResponse);
        }

        // responseExtensions
        if (seqDerIn.available() > 0) {
            seq = seqDerIn.getDerValue();
            if (seq.isContextSpecific((byte)1)) {
                DerValue[] responseExtDer = seq.data.getSequence(3);
                for (int i = 0; i < responseExtDer.length; i++) {
                    Extension responseExtension
                        = new Extension(responseExtDer[i]);
                    if (DEBUG != null) {
                        DEBUG.println("OCSP extension: " + responseExtension);
                    }
                    if (responseExtension.getExtensionId().equals(
                        OCSP_NONCE_EXTENSION_OID)) {
                        /*
                        ocspNonce =
                            responseExtension[i].getExtensionValue();
                         */
                    } else if (responseExtension.isCritical())  {
                        throw new IOException(
                            "Unsupported OCSP critical extension: " +
                            responseExtension.getExtensionId());
                    }
                }
            }
        }

        // signatureAlgorithmId
        AlgorithmId sigAlgId = AlgorithmId.parse(seqTmp[1]);

        // signature
        byte[] signature = seqTmp[2].getBitString();
        X509CertImpl[] x509Certs = null;

        // if seq[3] is available , then it is a sequence of certificates
        if (seqTmp.length > 3) {
            // certs are available
            DerValue seqCert = seqTmp[3];
            if (!seqCert.isContextSpecific((byte)0)) {
                throw new IOException("Bad encoding in certs element of " +
                    "OCSP response: expected ASN.1 context specific tag 0.");
            }
            DerValue[] certs = seqCert.getData().getSequence(3);
            x509Certs = new X509CertImpl[certs.length];
            try {
                for (int i = 0; i < certs.length; i++) {
                    x509Certs[i] = new X509CertImpl(certs[i].toByteArray());
                }
            } catch (CertificateException ce) {
                throw new IOException("Bad encoding in X509 Certificate", ce);
            }
        }

        // Check whether the cert returned by the responder is trusted
        if (x509Certs != null && x509Certs[0] != null) {
            X509CertImpl cert = x509Certs[0];

            // First check if the cert matches the responder cert which
            // was set locally.
            if (cert.equals(responderCert)) {
                // cert is trusted, now verify the signed response

            // Next check if the cert was issued by the responder cert
            // which was set locally.
            } else if (cert.getIssuerX500Principal().equals(
                responderCert.getSubjectX500Principal())) {

                // Check for the OCSPSigning key purpose
                try {
                    List<String> keyPurposes = cert.getExtendedKeyUsage();
                    if (keyPurposes == null ||
                        !keyPurposes.contains(KP_OCSP_SIGNING_OID)) {
                        throw new CertPathValidatorException(
                            "Responder's certificate not valid for signing " +
                            "OCSP responses");
                    }
                } catch (CertificateParsingException cpe) {
                    // assume cert is not valid for signing
                    throw new CertPathValidatorException(
                        "Responder's certificate not valid for signing " +
                        "OCSP responses", cpe);
                }

                // check the validity
                try {
                    if (dateCheckedAgainst == null) {
                        cert.checkValidity();
                    } else {
                        cert.checkValidity(dateCheckedAgainst);
                    }
                } catch (GeneralSecurityException e) {
                    throw new CertPathValidatorException(
                        "Responder's certificate not within the " +
                        "validity period", e);
                }

                // check for revocation
                //
                // A CA may specify that an OCSP client can trust a
                // responder for the lifetime of the responder's
                // certificate. The CA does so by including the
                // extension id-pkix-ocsp-nocheck.
                //
                Extension noCheck =
                    cert.getExtension(PKIXExtensions.OCSPNoCheck_Id);
                if (noCheck != null) {
                    if (DEBUG != null) {
                        DEBUG.println("Responder's certificate includes " +
                            "the extension id-pkix-ocsp-nocheck.");
                    }
                } else {
                    // we should do the revocation checking of the
                    // authorized responder in a future update.
                }

                // verify the signature
                try {
                    cert.verify(responderCert.getPublicKey());
                    responderCert = cert;
                    // cert is trusted, now verify the signed response

                } catch (GeneralSecurityException e) {
                    responderCert = null;
                }
            } else {
                throw new CertPathValidatorException(
                    "Responder's certificate is not authorized to sign " +
                    "OCSP responses");
            }
        }

        // Confirm that the signed response was generated using the public
        // key from the trusted responder cert
        if (responderCert != null) {
            if (!verifyResponse(responseDataDer, responderCert,
                sigAlgId, signature)) {
                throw new CertPathValidatorException(
                    "Error verifying OCSP Responder's signature");
            }
        } else {
            // Need responder's cert in order to verify the signature
            throw new CertPathValidatorException(
                "Unable to verify OCSP Responder's signature");
        }
    }

    /**
     * Returns the OCSP ResponseStatus.
     */
    ResponseStatus getResponseStatus() {
        return responseStatus;
    }

    /*
     * Verify the signature of the OCSP response.
     * The responder's cert is implicitly trusted.
     */
    private boolean verifyResponse(byte[] responseData, X509Certificate cert,
        AlgorithmId sigAlgId, byte[] signBytes)
        throws CertPathValidatorException {

        try {
            Signature respSignature = Signature.getInstance(sigAlgId.getName());
            respSignature.initVerify(cert);
            respSignature.update(responseData);

            if (respSignature.verify(signBytes)) {
                if (DEBUG != null) {
                    DEBUG.println("Verified signature of OCSP Responder");
                }
                return true;

            } else {
                if (DEBUG != null) {
                    DEBUG.println(
                        "Error verifying signature of OCSP Responder");
                }
                return false;
            }
        } catch (InvalidKeyException ike) {
            throw new CertPathValidatorException(ike);
        } catch (NoSuchAlgorithmException nsae) {
            throw new CertPathValidatorException(nsae);
        } catch (SignatureException se) {
            throw new CertPathValidatorException(se);
        }
    }

    /**
     * Returns the SingleResponse of the specified CertId, or null if
     * there is no response for that CertId.
     */
    SingleResponse getSingleResponse(CertId certId) {
        return singleResponseMap.get(certId);
    }

    /*
     * A class representing a single OCSP response.
     */
    final static class SingleResponse implements OCSP.RevocationStatus {
        private final CertId certId;
        private final CertStatus certStatus;
        private final Date thisUpdate;
        private final Date nextUpdate;
        private final Date revocationTime;
        private final CRLReason revocationReason;
        private final Map<String, java.security.cert.Extension> singleExtensions;

        private SingleResponse(DerValue der) throws IOException {
            if (der.tag != DerValue.tag_Sequence) {
                throw new IOException("Bad ASN.1 encoding in SingleResponse");
            }
            DerInputStream tmp = der.data;

            certId = new CertId(tmp.getDerValue().data);
            DerValue derVal = tmp.getDerValue();
            short tag = (byte)(derVal.tag & 0x1f);
            if (tag ==  CERT_STATUS_REVOKED) {
                certStatus = CertStatus.REVOKED;
                revocationTime = derVal.data.getGeneralizedTime();
                if (derVal.data.available() != 0) {
                    DerValue dv = derVal.data.getDerValue();
                    tag = (byte)(dv.tag & 0x1f);
                    if (tag == 0) {
                        int reason = dv.data.getEnumerated();
                        // if reason out-of-range just leave as UNSPECIFIED
                        if (reason >= 0 && reason < values.length) {
                            revocationReason = values[reason];
                        } else {
                            revocationReason = CRLReason.UNSPECIFIED;
                        }
                    } else {
                        revocationReason = CRLReason.UNSPECIFIED;
                    }
                } else {
                    revocationReason = CRLReason.UNSPECIFIED;
                }
                // RevokedInfo
                if (DEBUG != null) {
                    DEBUG.println("Revocation time: " + revocationTime);
                    DEBUG.println("Revocation reason: " + revocationReason);
                }
            } else {
                revocationTime = null;
                revocationReason = CRLReason.UNSPECIFIED;
                if (tag == CERT_STATUS_GOOD) {
                    certStatus = CertStatus.GOOD;
                } else if (tag == CERT_STATUS_UNKNOWN) {
                    certStatus = CertStatus.UNKNOWN;
                } else {
                    throw new IOException("Invalid certificate status");
                }
            }

            thisUpdate = tmp.getGeneralizedTime();

            if (tmp.available() == 0)  {
                // we are done
                nextUpdate = null;
            } else {
                derVal = tmp.getDerValue();
                tag = (byte)(derVal.tag & 0x1f);
                if (tag == 0) {
                    // next update
                    nextUpdate = derVal.data.getGeneralizedTime();

                    if (tmp.available() == 0)  {
                        // we are done
                    } else {
                        derVal = tmp.getDerValue();
                        tag = (byte)(derVal.tag & 0x1f);
                    }
                } else {
                    nextUpdate = null;
                }
            }
            // singleExtensions
            if (tmp.available() > 0) {
                derVal = tmp.getDerValue();
                if (derVal.isContextSpecific((byte)1)) {
                    DerValue[] singleExtDer = derVal.data.getSequence(3);
                    singleExtensions =
                        new HashMap<String, java.security.cert.Extension>
                            (singleExtDer.length);
                    for (int i = 0; i < singleExtDer.length; i++) {
                        Extension ext = new Extension(singleExtDer[i]);
                        if (DEBUG != null) {
                            DEBUG.println("OCSP single extension: " + ext);
                        }
                        // We don't support any extensions yet. Therefore, if it
                        // is critical we must throw an exception because we
                        // don't know how to process it.
                        if (ext.isCritical()) {
                            throw new IOException(
                                "Unsupported OCSP critical extension: " +
                                ext.getExtensionId());
                        }
                        singleExtensions.put(ext.getId(), ext);
                    }
                } else {
                    singleExtensions = Collections.emptyMap();
                }
            } else {
                singleExtensions = Collections.emptyMap();
            }

            long now = System.currentTimeMillis();
            Date nowPlusSkew = new Date(now + MAX_CLOCK_SKEW);
            Date nowMinusSkew = new Date(now - MAX_CLOCK_SKEW);
            if (DEBUG != null) {
                String until = "";
                if (nextUpdate != null) {
                    until = " until " + nextUpdate;
                }
                DEBUG.println("Response's validity interval is from " +
                    thisUpdate + until);
            }
            // Check that the test date is within the validity interval
            if ((thisUpdate != null && nowPlusSkew.before(thisUpdate)) ||
                (nextUpdate != null && nowMinusSkew.after(nextUpdate))) {

                if (DEBUG != null) {
                    DEBUG.println("Response is unreliable: its validity " +
                        "interval is out-of-date");
                }
                throw new IOException("Response is unreliable: its validity " +
                    "interval is out-of-date");
            }
        }

        /*
         * Return the certificate's revocation status code
         */
        @Override public CertStatus getCertStatus() {
            return certStatus;
        }

        private CertId getCertId() {
            return certId;
        }

        @Override public Date getRevocationTime() {
            return (Date) revocationTime.clone();
        }

        @Override public CRLReason getRevocationReason() {
            return revocationReason;
        }

        @Override
        public Map<String, java.security.cert.Extension> getSingleExtensions() {
            return Collections.unmodifiableMap(singleExtensions);
        }

        /**
         * Construct a string representation of a single OCSP response.
         */
        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("SingleResponse:  \n");
            sb.append(certId);
            sb.append("\nCertStatus: "+ certStatus + "\n");
            if (certStatus == CertStatus.REVOKED) {
                sb.append("revocationTime is " + revocationTime + "\n");
                sb.append("revocationReason is " + revocationReason + "\n");
            }
            sb.append("thisUpdate is " + thisUpdate + "\n");
            if (nextUpdate != null) {
                sb.append("nextUpdate is " + nextUpdate + "\n");
            }
            return sb.toString();
        }
    }
}
