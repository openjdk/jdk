/*
 * Copyright (c) 2007-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import com.sun.jarsigner.*;
import java.util.Arrays;
import sun.security.pkcs.*;
import sun.security.timestamp.*;
import sun.security.util.*;
import sun.security.x509.*;

/**
 * This class implements a content signing service.
 * It generates a timestamped signature for a given content according to
 * <a href="http://www.ietf.org/rfc/rfc3161.txt">RFC 3161</a>.
 * The signature along with a trusted timestamp and the signer's certificate
 * are all packaged into a standard PKCS #7 Signed Data message.
 *
 * @author Vincent Ryan
 */

public final class TimestampedSigner extends ContentSigner {

    /*
     * Random number generator for creating nonce values
     */
    private static final SecureRandom RANDOM;
    static {
        SecureRandom tmp = null;
        try {
            tmp = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            // should not happen
        }
        RANDOM = tmp;
    }

    /*
     * Object identifier for the subject information access X.509 certificate
     * extension.
     */
    private static final String SUBJECT_INFO_ACCESS_OID = "1.3.6.1.5.5.7.1.11";

    /*
     * Object identifier for the timestamping key purpose.
     */
    private static final String KP_TIMESTAMPING_OID = "1.3.6.1.5.5.7.3.8";

    /*
     * Object identifier for extendedKeyUsage extension
     */
    private static final String EXTENDED_KEY_USAGE_OID = "2.5.29.37";

    /*
     * Object identifier for the timestamping access descriptors.
     */
    private static final ObjectIdentifier AD_TIMESTAMPING_Id;
    static {
        ObjectIdentifier tmp = null;
        try {
            tmp = new ObjectIdentifier("1.3.6.1.5.5.7.48.3");
        } catch (IOException e) {
            // ignore
        }
        AD_TIMESTAMPING_Id = tmp;
    }

    /*
     * Location of the TSA.
     */
    private String tsaUrl = null;

    /*
     * TSA's X.509 certificate.
     */
    private X509Certificate tsaCertificate = null;

    /*
     * Generates an SHA-1 hash value for the data to be timestamped.
     */
    private MessageDigest messageDigest = null;

    /*
     * Parameters for the timestamping protocol.
     */
    private boolean tsRequestCertificate = true;

    /**
     * Instantiates a content signer that supports timestamped signatures.
     */
    public TimestampedSigner() {
    }

    /**
     * Generates a PKCS #7 signed data message that includes a signature
     * timestamp.
     * This method is used when a signature has already been generated.
     * The signature, a signature timestamp, the signer's certificate chain,
     * and optionally the content that was signed, are packaged into a PKCS #7
     * signed data message.
     *
     * @param parameters The non-null input parameters.
     * @param omitContent true if the content should be omitted from the
     *        signed data message. Otherwise the content is included.
     * @param applyTimestamp true if the signature should be timestamped.
     *        Otherwise timestamping is not performed.
     * @return A PKCS #7 signed data message including a signature timestamp.
     * @throws NoSuchAlgorithmException The exception is thrown if the signature
     *         algorithm is unrecognised.
     * @throws CertificateException The exception is thrown if an error occurs
     *         while processing the signer's certificate or the TSA's
     *         certificate.
     * @throws IOException The exception is thrown if an error occurs while
     *         generating the signature timestamp or while generating the signed
     *         data message.
     * @throws NullPointerException The exception is thrown if parameters is
     *         null.
     */
    public byte[] generateSignedData(ContentSignerParameters parameters,
        boolean omitContent, boolean applyTimestamp)
            throws NoSuchAlgorithmException, CertificateException, IOException {

        if (parameters == null) {
            throw new NullPointerException();
        }

        // Parse the signature algorithm to extract the digest and key
        // algorithms. The expected format is:
        //     "<digest>with<encryption>"
        // or  "<digest>with<encryption>and<mgf>"
        String signatureAlgorithm = parameters.getSignatureAlgorithm();
        String keyAlgorithm =
                AlgorithmId.getEncAlgFromSigAlg(signatureAlgorithm);
        String digestAlgorithm =
                AlgorithmId.getDigAlgFromSigAlg(signatureAlgorithm);
        AlgorithmId digestAlgorithmId = AlgorithmId.get(digestAlgorithm);

        // Examine signer's certificate
        X509Certificate[] signerCertificateChain =
            parameters.getSignerCertificateChain();
        Principal issuerName = signerCertificateChain[0].getIssuerDN();
        if (!(issuerName instanceof X500Name)) {
            // must extract the original encoded form of DN for subsequent
            // name comparison checks (converting to a String and back to
            // an encoded DN could cause the types of String attribute
            // values to be changed)
            X509CertInfo tbsCert = new
                X509CertInfo(signerCertificateChain[0].getTBSCertificate());
            issuerName = (Principal)
                tbsCert.get(CertificateIssuerName.NAME + "." +
                CertificateIssuerName.DN_NAME);
        }
        BigInteger serialNumber = signerCertificateChain[0].getSerialNumber();

        // Include or exclude content
        byte[] content = parameters.getContent();
        ContentInfo contentInfo;
        if (omitContent) {
            contentInfo = new ContentInfo(ContentInfo.DATA_OID, null);
        } else {
            contentInfo = new ContentInfo(content);
        }

        // Generate the timestamp token
        byte[] signature = parameters.getSignature();
        SignerInfo signerInfo = null;
        if (applyTimestamp) {

            tsaCertificate = parameters.getTimestampingAuthorityCertificate();
            URI tsaUri = parameters.getTimestampingAuthority();
            if (tsaUri != null) {
                tsaUrl = tsaUri.toString();
            } else {
                // Examine TSA cert
                String certUrl = getTimestampingUrl(tsaCertificate);
                if (certUrl == null) {
                    throw new CertificateException(
                        "Subject Information Access extension not found");
                }
                tsaUrl = certUrl;
            }

            // Timestamp the signature
            byte[] tsToken = generateTimestampToken(signature);

            // Insert the timestamp token into the PKCS #7 signer info element
            // (as an unsigned attribute)
            PKCS9Attributes unsignedAttrs =
                new PKCS9Attributes(new PKCS9Attribute[]{
                    new PKCS9Attribute(
                        PKCS9Attribute.SIGNATURE_TIMESTAMP_TOKEN_STR,
                        tsToken)});
            signerInfo = new SignerInfo((X500Name)issuerName, serialNumber,
                digestAlgorithmId, null, AlgorithmId.get(keyAlgorithm),
                    signature, unsignedAttrs);
        } else {
            signerInfo = new SignerInfo((X500Name)issuerName, serialNumber,
                digestAlgorithmId, AlgorithmId.get(keyAlgorithm), signature);
        }

        SignerInfo[] signerInfos = {signerInfo};
        AlgorithmId[] algorithms = {digestAlgorithmId};

        // Create the PKCS #7 signed data message
        PKCS7 p7 =
            new PKCS7(algorithms, contentInfo, signerCertificateChain,
                signerInfos);
        ByteArrayOutputStream p7out = new ByteArrayOutputStream();
        p7.encodeSignedData(p7out);

        return p7out.toByteArray();
    }

    /**
     * Examine the certificate for a Subject Information Access extension
     * (<a href="http://www.ietf.org/rfc/rfc3280.txt">RFC 3280</a>).
     * The extension's <tt>accessMethod</tt> field should contain the object
     * identifier defined for timestamping: 1.3.6.1.5.5.7.48.3 and its
     * <tt>accessLocation</tt> field should contain an HTTP URL.
     *
     * @param tsaCertificate An X.509 certificate for the TSA.
     * @return An HTTP URL or null if none was found.
     */
    public static String getTimestampingUrl(X509Certificate tsaCertificate) {

        if (tsaCertificate == null) {
            return null;
        }
        // Parse the extensions
        try {
            byte[] extensionValue =
                tsaCertificate.getExtensionValue(SUBJECT_INFO_ACCESS_OID);
            if (extensionValue == null) {
                return null;
            }
            DerInputStream der = new DerInputStream(extensionValue);
            der = new DerInputStream(der.getOctetString());
            DerValue[] derValue = der.getSequence(5);
            AccessDescription description;
            GeneralName location;
            URIName uri;
            for (int i = 0; i < derValue.length; i++) {
                description = new AccessDescription(derValue[i]);
                if (description.getAccessMethod().equals(AD_TIMESTAMPING_Id)) {
                    location = description.getAccessLocation();
                    if (location.getType() == GeneralNameInterface.NAME_URI) {
                        uri = (URIName) location.getName();
                        if (uri.getScheme().equalsIgnoreCase("http")) {
                            return uri.getName();
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            // ignore
        }
        return null;
    }

    /*
     * Returns a timestamp token from a TSA for the given content.
     * Performs a basic check on the token to confirm that it has been signed
     * by a certificate that is permitted to sign timestamps.
     *
     * @param  toBeTimestamped The data to be timestamped.
     * @throws IOException The exception is throw if an error occurs while
     *                     communicating with the TSA.
     * @throws CertificateException The exception is throw if the TSA's
     *                     certificate is not permitted for timestamping.
     */
    private byte[] generateTimestampToken(byte[] toBeTimestamped)
            throws CertificateException, IOException {

        // Generate hash value for the data to be timestamped
        // SHA-1 is always used.
        if (messageDigest == null) {
            try {
                messageDigest = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                // ignore
            }
        }
        byte[] digest = messageDigest.digest(toBeTimestamped);

        // Generate a timestamp
        TSRequest tsQuery = new TSRequest(digest, "SHA-1");
        // Generate a nonce
        BigInteger nonce = null;
        if (RANDOM != null) {
            nonce = new BigInteger(64, RANDOM);
            tsQuery.setNonce(nonce);
        }
        tsQuery.requestCertificate(tsRequestCertificate);

        Timestamper tsa = new HttpTimestamper(tsaUrl); // use supplied TSA
        TSResponse tsReply = tsa.generateTimestamp(tsQuery);
        int status = tsReply.getStatusCode();
        // Handle TSP error
        if (status != 0 && status != 1) {
            int failureCode = tsReply.getFailureCode();
            if (failureCode == -1) {
                throw new IOException("Error generating timestamp: " +
                    tsReply.getStatusCodeAsText());
            } else {
                throw new IOException("Error generating timestamp: " +
                    tsReply.getStatusCodeAsText() + " " +
                    tsReply.getFailureCodeAsText());
            }
        }
        PKCS7 tsToken = tsReply.getToken();

        TimestampToken tst = new TimestampToken(tsToken.getContentInfo().getData());
        if (!tst.getHashAlgorithm().equals(
                new AlgorithmId(new ObjectIdentifier("1.3.14.3.2.26")))) {
            throw new IOException("Digest algorithm not SHA-1 in timestamp token");
        }
        if (!Arrays.equals(tst.getHashedMessage(), digest)) {
            throw new IOException("Digest octets changed in timestamp token");
        };

        BigInteger replyNonce = tst.getNonce();
        if (replyNonce == null && nonce != null) {
            throw new IOException("Nonce missing in timestamp token");
        }
        if (replyNonce != null && !replyNonce.equals(nonce)) {
            throw new IOException("Nonce changed in timestamp token");
        }

        // Examine the TSA's certificate (if present)
        for (SignerInfo si: tsToken.getSignerInfos()) {
            X509Certificate cert = si.getCertificate(tsToken);
            if (cert == null) {
                // Error, we've already set tsRequestCertificate = true
                throw new CertificateException(
                "Certificate not included in timestamp token");
            } else {
                if (!cert.getCriticalExtensionOIDs().contains(
                        EXTENDED_KEY_USAGE_OID)) {
                    throw new CertificateException(
                    "Certificate is not valid for timestamping");
                }
                List keyPurposes = cert.getExtendedKeyUsage();
                if (keyPurposes == null ||
                        ! keyPurposes.contains(KP_TIMESTAMPING_OID)) {
                    throw new CertificateException(
                    "Certificate is not valid for timestamping");
                }
            }
        }
        return tsReply.getEncodedToken();
    }
}
