/*
 * Copyright (c) 2007, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class implements a content signing service.
 * It generates a timestamped signature for a given content according to
 * <a href="http://www.ietf.org/rfc/rfc3161.txt">RFC 3161</a>.
 * The signature along with a trusted timestamp and the signer's certificate
 * are all packaged into a standard PKCS #7 Signed Data message.
 *
 * @author Vincent Ryan
 */
@Deprecated(since="16", forRemoval=true)
@SuppressWarnings("removal")
public final class TimestampedSigner extends ContentSigner {

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

        X509Certificate[] signerChain = params.getSignerCertificateChain();
        byte[] signature = params.getSignature();

        // Include or exclude content
        byte[] content = (omitContent == true) ? null : params.getContent();

        URI tsaURI = null;
        if (applyTimestamp) {
            tsaURI = params.getTimestampingAuthority();
            if (tsaURI == null) {
                // Examine TSA cert
                tsaURI = PKCS7.getTimestampingURI(
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
}
