/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import jtreg.SkippedException;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.*;
import java.util.HexFormat;

public class ValidatePathWithURL {
    private static MessageDigest md;
    private static final HexFormat HEX = HexFormat.ofDelimiter(":").withUpperCase();

    public ValidatePathWithURL() throws NoSuchAlgorithmException {
        System.setProperty("com.sun.net.ssl.checkRevocation", "true");
        Security.setProperty("ssl.TrustManagerFactory.algorithm", "SunPKIX");

        // some test sites don't have correct hostname specified in test certificate
        HttpsURLConnection.setDefaultHostnameVerifier(new CustomHostnameVerifier());

        md = MessageDigest.getInstance("SHA-256");
    }

    public void enableOCSPOnly() {
        System.setProperty("com.sun.security.enableCRLDP", "false");
        Security.setProperty("ocsp.enable", "true");
    }

    public void enableCRLOnly() {
        System.setProperty("com.sun.security.enableCRLDP", "true");
    }

    public void enableOCSPAndCRL() {
        System.setProperty("com.sun.security.enableCRLDP", "true");
        Security.setProperty("ocsp.enable", "true");
    }

    public void validateDomain(final String testURL,
                               final boolean revokedCert,
                               final String fingerPrint) throws CertificateEncodingException {
        System.out.println();
        System.out.println("===== Validate " + testURL + "=====");
        if (!validateDomainCertChain(testURL, revokedCert, fingerPrint)) {
            throw new RuntimeException("Failed to validate " + testURL);
        }
        System.out.println("======> SUCCESS");
    }

    private boolean validateDomainCertChain(final String testURL,
                                            final boolean revokedCert,
                                            final String fingerPrint)
            throws CertificateEncodingException {
        HttpsURLConnection httpsURLConnection = null;
        try {
            URL url = new URL(testURL);
            httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setInstanceFollowRedirects(false);
            httpsURLConnection.connect();

            // certain that test certificate anchors to trusted CA for VALID certificate
            // if the connection is successful
            /*Certificate[] chain =
                    httpsURLConnection.getSSLSession().get().getPeerCertificates();
            httpsURLConnection.disconnect();
            X509Certificate rootCert = null;

            // fail if there is no intermediate CA
            if (chain.length < 3) {
                throw new RuntimeException("Cert chain too short " + chain.length);
            } else {
                System.out.println("Finding root certificate..." + chain.length);
                for (Certificate cert: chain){
                    if(cert instanceof X509Certificate) {
                        X509Certificate certificate = (X509Certificate) cert;
                        System.out.println("Checking: " + certificate.getSubjectX500Principal());
                        System.out.println("Checking2: " + certificate.getIssuerX500Principal());
                        if (certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
                            rootCert = certificate;
                            break;
                        }
                    } else {
                        throw new RuntimeException("Certificate not X509");
                    }
                }
            }

            if (rootCert == null){
                throw new RuntimeException("Root CA not found in the chain");
            }

            // validate root CA serial number
            System.out.println("Found root CA: " + rootCert.getSubjectX500Principal());
            byte[] digest = md.digest(rootCert.getEncoded());
            if(!fingerPrint.equals(HEX.formatHex(digest))) {
                System.out.println("Expected fingerprint: " + fingerPrint);
                System.out.println("Actual fingerprint: " + HEX.formatHex(digest));
                throw new RuntimeException("Root CA serial doesn't match");
            }*/
        } catch (SSLHandshakeException e) {
            System.out.println("SSLHandshakeException: " + e.getMessage());
            Throwable cause = e.getCause();

            while (cause != null) {
                if (cause instanceof CertPathValidatorException cpve) {
                    if (cpve.getReason() == CertPathValidatorException.BasicReason.REVOKED
                            || cpve.getCause() instanceof CertificateRevokedException) {
                        System.out.println("Certificate is revoked");
                        if (revokedCert) {
                            return true;
                        }
                    } else if (cpve.getReason() == CertPathValidatorException.BasicReason.EXPIRED
                            || cpve.getCause() instanceof CertificateExpiredException) {
                        System.out.println("Certificate is expired");
                        throw new SkippedException("Certificate is expired, skip the test");
                    }
                    break;
                }
                cause = cause.getCause();
            }

            throw new RuntimeException("Unknown exception", e);
        } catch (SSLException e) {
            // thrown if root CA is not included in cacerts
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new SkippedException("Network setup issue, skip this test", e);
        } finally {
            if(httpsURLConnection != null) {
                httpsURLConnection.disconnect();
            }
        }

        return !revokedCert;
    }

    private class CustomHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            // Allow any hostname
            return true;
        }
    }
}
