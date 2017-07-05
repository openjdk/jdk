/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.tools.jarsigner;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import com.sun.jarsigner.*;
import sun.security.pkcs.PKCS7;
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
     * Object identifier for the subject information access X.509 certificate
     * extension.
     */
    private static final String SUBJECT_INFO_ACCESS_OID = "1.3.6.1.5.5.7.1.11";

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
     * @param params The non-null input parameters.
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
    public byte[] generateSignedData(ContentSignerParameters params,
        boolean omitContent, boolean applyTimestamp)
            throws NoSuchAlgorithmException, CertificateException, IOException {

        if (params == null) {
            throw new NullPointerException();
        }

        // Parse the signature algorithm to extract the digest
        // algorithm. The expected format is:
        //     "<digest>with<encryption>"
        // or  "<digest>with<encryption>and<mgf>"
        String signatureAlgorithm = params.getSignatureAlgorithm();

        X509Certificate[] signerChain = params.getSignerCertificateChain();
        byte[] signature = params.getSignature();

        // Include or exclude content
        byte[] content = (omitContent == true) ? null : params.getContent();

        URI tsaURI = null;
        if (applyTimestamp) {
            tsaURI = params.getTimestampingAuthority();
            if (tsaURI == null) {
                // Examine TSA cert
                tsaURI = getTimestampingURI(
                    params.getTimestampingAuthorityCertificate());
                if (tsaURI == null) {
                    throw new CertificateException(
                        "Subject Information Access extension not found");
                }
            }
        }
        return PKCS7.generateSignedData(signature, signerChain, content,
                                        params.getSignatureAlgorithm(), tsaURI,
                                        params.getTSAPolicyID(),
                                        params.getTSADigestAlg());
    }

    /**
     * Examine the certificate for a Subject Information Access extension
     * (<a href="http://tools.ietf.org/html/rfc5280">RFC 5280</a>).
     * The extension's <tt>accessMethod</tt> field should contain the object
     * identifier defined for timestamping: 1.3.6.1.5.5.7.48.3 and its
     * <tt>accessLocation</tt> field should contain an HTTP or HTTPS URL.
     *
     * @param tsaCertificate An X.509 certificate for the TSA.
     * @return An HTTP or HTTPS URI or null if none was found.
     */
    public static URI getTimestampingURI(X509Certificate tsaCertificate) {

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
                if (description.getAccessMethod()
                        .equals((Object)AD_TIMESTAMPING_Id)) {
                    location = description.getAccessLocation();
                    if (location.getType() == GeneralNameInterface.NAME_URI) {
                        uri = (URIName) location.getName();
                        if (uri.getScheme().equalsIgnoreCase("http") ||
                                uri.getScheme().equalsIgnoreCase("https")) {
                            return uri.getURI();
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            // ignore
        }
        return null;
    }
}
