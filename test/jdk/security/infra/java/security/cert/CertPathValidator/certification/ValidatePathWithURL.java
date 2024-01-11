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
import javax.security.auth.x500.X500Principal;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;

public class ValidatePathWithURL {

    private final X509Certificate rootCertificate;
    private final X500Principal rootPrincipal;

    /**
     * Enables the certificate revocation checking and loads the certificate from
     * <code>cacerts</code> file for give caAlias
     *
     * @param caAlias CA alias for CA certificate in <code>cacerts</code> file
     * @throws Exception when fails to get CA certificate from <code>cacerts</code> file
     */
    public ValidatePathWithURL(String caAlias) throws Exception {
        System.setProperty("com.sun.net.ssl.checkRevocation", "true");
        Security.setProperty("ssl.TrustManagerFactory.algorithm", "SunPKIX");

        // some test sites don't have correct hostname specified in test certificate
        HttpsURLConnection.setDefaultHostnameVerifier(new CustomHostnameVerifier());

        String FS = System.getProperty("file.separator");
        String CACERTS_STORE =
                System.getProperty("test.jdk") + FS + "lib" + FS + "security" + FS + "cacerts";

        KeyStore cacerts = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(CACERTS_STORE)) {
            cacerts.load(fis, null);
        }

        rootCertificate = (X509Certificate) cacerts.getCertificate(caAlias);
        rootPrincipal = rootCertificate.getSubjectX500Principal();
    }

    /**
     * Enable revocation checking using OCSP and disables CRL check
     */
    public static void enableOCSPOnly() {
        System.setProperty("com.sun.security.enableCRLDP", "false");
        Security.setProperty("ocsp.enable", "true");
    }

    /**
     * Enable revocation checking using CRL
     */
    public static void enableCRLOnly() {
        System.setProperty("com.sun.security.enableCRLDP", "true");
        Security.setProperty("ocsp.enable", "false");
    }

    /**
     * Enable revocation checking using OCSP or CRL
     */
    public static void enableOCSPAndCRL() {
        System.setProperty("com.sun.security.enableCRLDP", "true");
        Security.setProperty("ocsp.enable", "true");
    }

    /**
     * Logs revocation settings
     */
    public static void logRevocationSettings() {
        System.out.println("=====================================================");
        System.out.println("CONFIGURATION");
        System.out.println("=====================================================");
        System.out.println("http.proxyHost :" + System.getProperty("http.proxyHost"));
        System.out.println("http.proxyPort :" + System.getProperty("http.proxyPort"));
        System.out.println("https.proxyHost :" + System.getProperty("https.proxyHost"));
        System.out.println("https.proxyPort :" + System.getProperty("https.proxyPort"));
        System.out.println("https.socksProxyHost :"
                + System.getProperty("https.socksProxyHost"));
        System.out.println("https.socksProxyPort :"
                + System.getProperty("https.socksProxyPort"));
        System.out.println("jdk.certpath.disabledAlgorithms :"
                + Security.getProperty("jdk.certpath.disabledAlgorithms"));
        System.out.println("com.sun.security.enableCRLDP :"
                + System.getProperty("com.sun.security.enableCRLDP"));
        System.out.println("ocsp.enable :" + Security.getProperty("ocsp.enable"));
        System.out.println("=====================================================");
    }

    /**
     * Validates end entity certificate used in provided test URL using
     * <code>HttpsURLConnection</code>. Validation is skipped on network error or if
     * the certificate is expired.
     *
     * @param testURL     URL to validate
     * @param revokedCert if <code>true</code> then validate is REVOKED certificate
     * @throws Exception on failure to validate certificate
     */
    public void validateDomain(final String testURL,
                               final boolean revokedCert)
            throws Exception {
        System.out.println();
        System.out.println("===== Validate " + testURL + "=====");
        if (!validateDomainCertChain(testURL, revokedCert)) {
            throw new RuntimeException("Failed to validate " + testURL);
        }
        System.out.println("======> SUCCESS");
    }

    private boolean validateDomainCertChain(final String testURL,
                                            final boolean revokedCert)
            throws Exception {
        HttpsURLConnection httpsURLConnection = null;
        try {
            URL url = new URL(testURL);
            httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setInstanceFollowRedirects(false);
            httpsURLConnection.connect();

            // certain that test certificate anchors to trusted CA for VALID certificate
            // if the connection is successful
            Certificate[] chain = httpsURLConnection.getServerCertificates();
            httpsURLConnection.disconnect();
            validateAnchor(chain);
        } catch (SSLHandshakeException e) {
            System.out.println("SSLHandshakeException: " + e.getMessage());
            Throwable cause = e.getCause();

            while (cause != null) {
                if (cause instanceof CertPathValidatorException cpve) {
                    if (cpve.getReason() == CertPathValidatorException.BasicReason.REVOKED
                            || cpve.getCause() instanceof CertificateRevokedException) {
                        System.out.println("Certificate is revoked");

                        // We can validate anchor for revoked certificates as well
                        Certificate[] chain = cpve.getCertPath().getCertificates().toArray(new Certificate[0]);
                        validateAnchor(chain);

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

            throw new RuntimeException("Unhandled exception", e);
        } catch (SSLException e) {
            // thrown if root CA is not included in cacerts
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new SkippedException("Network setup issue, skip this test", e);
        } finally {
            if (httpsURLConnection != null) {
                httpsURLConnection.disconnect();
            }
        }

        return !revokedCert;
    }

    private void validateAnchor(Certificate[] chain) throws Exception {
        X509Certificate interCert = null;

        // fail if there is no intermediate CA or self-signed
        if (chain.length < 2) {
            throw new RuntimeException("Cert chain too short " + chain.length);
        } else {
            System.out.println("Finding intermediate certificate issued by CA");
            for (Certificate cert : chain) {
                if (cert instanceof X509Certificate certificate) {
                    System.out.println("Checking: " + certificate.getSubjectX500Principal());
                    System.out.println("Issuer: " + certificate.getIssuerX500Principal());
                    if (certificate.getIssuerX500Principal().equals(rootPrincipal)) {
                        interCert = certificate;
                        break;
                    }
                }
            }
        }

        if (interCert == null) {
            throw new RuntimeException("Intermediate Root CA not found in the chain");
        }

        // validate intermediate CA signed by root CA under test
        System.out.println("Found intermediate root CA: " + interCert.getSubjectX500Principal());
        System.out.println("intermediate CA Issuer: " + interCert.getIssuerX500Principal());
        interCert.verify(rootCertificate.getPublicKey());
        System.out.println("Verified: Intermediate CA signed by test root CA");
    }

    private static class CustomHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            // Allow any hostname
            return true;
        }
    }
}
