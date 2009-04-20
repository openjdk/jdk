/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.provider.certpath;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CRLReason;
import java.security.cert.X509Certificate;
import java.security.cert.PKIXParameters;
import javax.security.auth.x500.X500Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
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

class OCSPResponse {

    // Certificate status CHOICE
    public static final int CERT_STATUS_GOOD = 0;
    public static final int CERT_STATUS_REVOKED = 1;
    public static final int CERT_STATUS_UNKNOWN = 2;

    private static final Debug DEBUG = Debug.getInstance("certpath");
    private static final boolean dump = false;
    private static final ObjectIdentifier OCSP_BASIC_RESPONSE_OID;
    private static final ObjectIdentifier OCSP_NONCE_EXTENSION_OID;
    static {
        ObjectIdentifier tmp1 = null;
        ObjectIdentifier tmp2 = null;
        try {
            tmp1 = new ObjectIdentifier("1.3.6.1.5.5.7.48.1.1");
            tmp2 = new ObjectIdentifier("1.3.6.1.5.5.7.48.1.2");
        } catch (Exception e) {
            // should not happen; log and exit
        }
        OCSP_BASIC_RESPONSE_OID = tmp1;
        OCSP_NONCE_EXTENSION_OID = tmp2;
    }

    // OCSP response status code
    private static final int OCSP_RESPONSE_OK = 0;

    // ResponderID CHOICE tags
    private static final int NAME_TAG = 1;
    private static final int KEY_TAG = 2;

    // Object identifier for the OCSPSigning key purpose
    private static final String KP_OCSP_SIGNING_OID = "1.3.6.1.5.5.7.3.9";

    private SingleResponse singleResponse;

    // Maximum clock skew in milliseconds (10 minutes) allowed when checking
    // validity of OCSP responses
    private static final long MAX_CLOCK_SKEW = 600000;

    // an array of all of the CRLReasons (used in SingleResponse)
    private static CRLReason[] values = CRLReason.values();

    /*
     * Create an OCSP response from its ASN.1 DER encoding.
     */
    // used by OCSPChecker
    OCSPResponse(byte[] bytes, PKIXParameters params,
        X509Certificate responderCert)
        throws IOException, CertPathValidatorException {

        try {
            int responseStatus;
            ObjectIdentifier  responseType;
            int version;
            CertificateIssuerName responderName = null;
            Date producedAtDate;
            AlgorithmId sigAlgId;
            byte[] ocspNonce;

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
            responseStatus = derIn.getEnumerated();
            if (DEBUG != null) {
                DEBUG.println("OCSP response: " +
                    responseToText(responseStatus));
            }
            if (responseStatus != OCSP_RESPONSE_OK) {
                throw new CertPathValidatorException(
                    "OCSP Response Failure: " +
                        responseToText(responseStatus));
            }

            // responseBytes
            der = derIn.getDerValue();
            if (! der.isContextSpecific((byte)0)) {
                throw new IOException("Bad encoding in responseBytes element " +
                    "of OCSP response: expected ASN.1 context specific tag 0.");
            };
            DerValue tmp = der.data.getDerValue();
            if (tmp.tag != DerValue.tag_Sequence) {
                throw new IOException("Bad encoding in responseBytes element " +
                    "of OCSP response: expected ASN.1 SEQUENCE tag.");
            }

            // responseType
            derIn = tmp.data;
            responseType = derIn.getOID();
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

            DerValue[]  seqTmp = basicOCSPResponse.getSequence(2);
            DerValue responseData = seqTmp[0];

            // Need the DER encoded ResponseData to verify the signature later
            byte[] responseDataDer = seqTmp[0].toByteArray();

            // tbsResponseData
            if (responseData.tag != DerValue.tag_Sequence) {
                throw new IOException("Bad encoding in tbsResponseData " +
                    " element of OCSP response: expected ASN.1 SEQUENCE tag.");
            }
            DerInputStream seqDerIn = responseData.data;
            DerValue seq = seqDerIn.getDerValue();

            // version
            if (seq.isContextSpecific((byte)0)) {
                // seq[0] is version
                if (seq.isConstructed() && seq.isContextSpecific()) {
                    //System.out.println ("version is available");
                    seq = seq.data.getDerValue();
                    version = seq.getInteger();
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
                responderName = new CertificateIssuerName(seq.getData());
                if (DEBUG != null) {
                    DEBUG.println("OCSP Responder name: " + responderName);
                }
            } else if (tag == KEY_TAG) {
                // Ignore, for now
            } else {
                throw new IOException("Bad encoding in responderID element " +
                    "of OCSP response: expected ASN.1 context specific tag 0 " +
                    "or 1");
            }

            // producedAt
            seq = seqDerIn.getDerValue();
            producedAtDate = seq.getGeneralizedTime();

            // responses
            DerValue[] singleResponseDer = seqDerIn.getSequence(1);
            // Examine only the first response
            singleResponse = new SingleResponse(singleResponseDer[0]);

            // responseExtensions
            if (seqDerIn.available() > 0) {
                seq = seqDerIn.getDerValue();
                if (seq.isContextSpecific((byte)1)) {
                    DerValue[]  responseExtDer = seq.data.getSequence(3);
                    Extension[] responseExtension =
                        new Extension[responseExtDer.length];
                    for (int i = 0; i < responseExtDer.length; i++) {
                        responseExtension[i] = new Extension(responseExtDer[i]);
                        if (DEBUG != null) {
                            DEBUG.println("OCSP extension: " +
                                responseExtension[i]);
                        }
                        if ((responseExtension[i].getExtensionId()).equals(
                            OCSP_NONCE_EXTENSION_OID)) {
                            ocspNonce =
                                responseExtension[i].getExtensionValue();

                        } else if (responseExtension[i].isCritical())  {
                            throw new IOException(
                                "Unsupported OCSP critical extension: " +
                                responseExtension[i].getExtensionId());
                        }
                    }
                }
            }

            // signatureAlgorithmId
            sigAlgId = AlgorithmId.parse(seqTmp[1]);

            // signature
            byte[] signature = seqTmp[2].getBitString();
            X509CertImpl[] x509Certs = null;

            // if seq[3] is available , then it is a sequence of certificates
            if (seqTmp.length > 3) {
                // certs are available
                DerValue seqCert = seqTmp[3];
                if (! seqCert.isContextSpecific((byte)0)) {
                    throw new IOException("Bad encoding in certs element " +
                    "of OCSP response: expected ASN.1 context specific tag 0.");
                }
                DerValue[] certs = (seqCert.getData()).getSequence(3);
                x509Certs = new X509CertImpl[certs.length];
                for (int i = 0; i < certs.length; i++) {
                    x509Certs[i] = new X509CertImpl(certs[i].toByteArray());
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
                    List<String> keyPurposes = cert.getExtendedKeyUsage();
                    if (keyPurposes == null ||
                        !keyPurposes.contains(KP_OCSP_SIGNING_OID)) {
                        if (DEBUG != null) {
                            DEBUG.println("Responder's certificate is not " +
                                "valid for signing OCSP responses.");
                        }
                        throw new CertPathValidatorException(
                            "Responder's certificate not valid for signing " +
                            "OCSP responses");
                    }

                    // check the validity
                    try {
                        Date dateCheckedAgainst = params.getDate();
                        if (dateCheckedAgainst == null) {
                            cert.checkValidity();
                        } else {
                            cert.checkValidity(dateCheckedAgainst);
                        }
                    } catch (GeneralSecurityException e) {
                        if (DEBUG != null) {
                            DEBUG.println("Responder's certificate is not " +
                                "within the validity period.");
                        }
                        throw new CertPathValidatorException(
                            "Responder's certificate not within the " +
                            "validity period");
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
                        // we should do the revocating checking of the
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
                    if (DEBUG != null) {
                        DEBUG.println("Responder's certificate is not " +
                            "authorized to sign OCSP responses.");
                    }
                    throw new CertPathValidatorException(
                        "Responder's certificate not authorized to sign " +
                        "OCSP responses");
                }
            }

            // Confirm that the signed response was generated using the public
            // key from the trusted responder cert
            if (responderCert != null) {

                if (! verifyResponse(responseDataDer, responderCert,
                    sigAlgId, signature, params)) {
                    if (DEBUG != null) {
                        DEBUG.println("Error verifying OCSP Responder's " +
                            "signature");
                    }
                    throw new CertPathValidatorException(
                        "Error verifying OCSP Responder's signature");
                }
            } else {
                // Need responder's cert in order to verify the signature
                if (DEBUG != null) {
                    DEBUG.println("Unable to verify OCSP Responder's " +
                        "signature");
                }
                throw new CertPathValidatorException(
                    "Unable to verify OCSP Responder's signature");
            }
        } catch (CertPathValidatorException cpve) {
            throw cpve;
        } catch (Exception e) {
            throw new CertPathValidatorException(e);
        }
    }

    /*
     * Verify the signature of the OCSP response.
     * The responder's cert is implicitly trusted.
     */
    private boolean verifyResponse(byte[] responseData, X509Certificate cert,
        AlgorithmId sigAlgId, byte[] signBytes, PKIXParameters params)
        throws SignatureException {

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
            throw new SignatureException(ike);

        } catch (NoSuchAlgorithmException nsae) {
            throw new SignatureException(nsae);
        }
    }

    /*
     * Return the revocation status code for a given certificate.
     */
    // used by OCSPChecker
    int getCertStatus(SerialNumber sn) {
        // ignore serial number for now; if we support multiple
        // requests/responses then it will be used
        return singleResponse.getStatus();
    }

    // used by OCSPChecker
    CertId getCertId() {
        return singleResponse.getCertId();
    }

    Date getRevocationTime() {
        return singleResponse.getRevocationTime();
    }

    CRLReason getRevocationReason() {
        return singleResponse.getRevocationReason();
    }

    Map<String, java.security.cert.Extension> getSingleExtensions() {
        return singleResponse.getSingleExtensions();
    }

    /*
     * Map an OCSP response status code to a string.
     */
    static private String responseToText(int status) {
        switch (status)  {
        case 0:
            return "Successful";
        case 1:
            return "Malformed request";
        case 2:
            return "Internal error";
        case 3:
            return "Try again later";
        case 4:
            return "Unused status code";
        case 5:
            return "Request must be signed";
        case 6:
            return "Request is unauthorized";
        default:
            return ("Unknown status code: " + status);
        }
    }

    /*
     * Map a certificate's revocation status code to a string.
     */
    // used by OCSPChecker
    static String certStatusToText(int certStatus) {
        switch (certStatus)  {
        case 0:
            return "Good";
        case 1:
            return "Revoked";
        case 2:
            return "Unknown";
        default:
            return ("Unknown certificate status code: " + certStatus);
        }
    }

    /*
     * A class representing a single OCSP response.
     */
    private class SingleResponse {
        private CertId certId;
        private int certStatus;
        private Date thisUpdate;
        private Date nextUpdate;
        private Date revocationTime;
        private CRLReason revocationReason = CRLReason.UNSPECIFIED;
        private HashMap<String, java.security.cert.Extension> singleExtensions;

        private SingleResponse(DerValue der) throws IOException {
            if (der.tag != DerValue.tag_Sequence) {
                throw new IOException("Bad ASN.1 encoding in SingleResponse");
            }
            DerInputStream tmp = der.data;

            certId = new CertId(tmp.getDerValue().data);
            DerValue derVal = tmp.getDerValue();
            short tag = (byte)(derVal.tag & 0x1f);
            if (tag ==  CERT_STATUS_GOOD) {
                certStatus = CERT_STATUS_GOOD;
            } else if (tag == CERT_STATUS_REVOKED) {
                certStatus = CERT_STATUS_REVOKED;
                revocationTime = derVal.data.getGeneralizedTime();
                if (derVal.data.available() != 0) {
                    int reason = derVal.getEnumerated();
                    // if reason out-of-range just leave as UNSPECIFIED
                    if (reason >= 0 && reason < values.length) {
                        revocationReason = values[reason];
                    }
                }
                // RevokedInfo
                if (DEBUG != null) {
                    DEBUG.println("Revocation time: " + revocationTime);
                    DEBUG.println("Revocation reason: " + revocationReason);
                }

            } else if (tag == CERT_STATUS_UNKNOWN) {
                certStatus = CERT_STATUS_UNKNOWN;

            } else {
                throw new IOException("Invalid certificate status");
            }

            thisUpdate = tmp.getGeneralizedTime();

            if (tmp.available() == 0)  {
                // we are done
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
                        singleExtensions.put(ext.getId(), ext);
                        if (DEBUG != null) {
                            DEBUG.println("OCSP single extension: " + ext);
                        }
                    }
                }
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
        private int getStatus() {
            return certStatus;
        }

        private CertId getCertId() {
            return certId;
        }

        private Date getRevocationTime() {
            return revocationTime;
        }

        private CRLReason getRevocationReason() {
            return revocationReason;
        }

        private Map<String, java.security.cert.Extension> getSingleExtensions() {
            return singleExtensions;
        }

        /**
         * Construct a string representation of a single OCSP response.
         */
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("SingleResponse:  \n");
            sb.append(certId);
            sb.append("\nCertStatus: "+ certStatusToText(getCertStatus(null)) +
                "\n");
            if (certStatus == CERT_STATUS_REVOKED) {
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
