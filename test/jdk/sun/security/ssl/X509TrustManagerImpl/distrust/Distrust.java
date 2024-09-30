/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.time.ZonedDateTime;
import java.util.*;
import javax.net.ssl.*;
import sun.security.validator.Validator;
import sun.security.validator.ValidatorException;

import jdk.test.lib.security.SecurityUtils;

/**
 * Helper class that provides methods to facilitate testing of distrusted roots.
 */
public final class Distrust {

    private static final String TEST_SRC = System.getProperty("test.src", ".");
    private static CertificateFactory cf;

    private final boolean before;
    private final boolean policyOn;
    private final boolean isValid;

    public Distrust(String[] args) {
        before = args[0].equals("before");
        policyOn = args[1].equals("policyOn");
        isValid = args[2].equals("valid");

        if (!policyOn) {
            // disable policy (default is on)
            Security.setProperty("jdk.security.caDistrustPolicies", "");
        }
    }

    public Date getNotBefore(ZonedDateTime distrustDate) {
        ZonedDateTime notBefore = before ? distrustDate.minusSeconds(1) : distrustDate;
        return Date.from(notBefore.toInstant());
    }

    public void testCodeSigningChain(String certPath, String name, Date validationDate)
            throws Exception {
        System.err.println("Testing " + name + " code-signing chain");
        Validator v = Validator.getInstance(Validator.TYPE_PKIX,
                Validator.VAR_CODE_SIGNING,
                getParams());
        // set validation date so this will still pass when cert expires
        v.setValidationDate(validationDate);
        v.validate(loadCertificateChain(certPath, name));
    }

    public void testCertificateChain(String certPath, Date notBefore, X509TrustManager[] tms,
                                     String... tests) throws Exception {
        for (String test : tests) {
            System.err.println("Testing " + test);
            X509Certificate[] chain = loadCertificateChain(certPath, test);

            for (X509TrustManager tm : tms) {
                testTM(tm, chain, notBefore, isValid);
            }
        }
    }

    public X509TrustManager getTMF(String type, PKIXBuilderParameters params) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(type);
        if (params == null) {
            tmf.init((KeyStore) null);
        } else {
            tmf.init(new CertPathTrustManagerParameters(params));
        }
        TrustManager[] tms = tmf.getTrustManagers();
        for (TrustManager tm : tms) {
            return (X509TrustManager) tm;
        }
        throw new RuntimeException("No TrustManager for " + type);
    }

    public PKIXBuilderParameters getParams() throws Exception {
        PKIXBuilderParameters pbp =
                new PKIXBuilderParameters(SecurityUtils.getCacertsKeyStore(),
                        new X509CertSelector());
        pbp.setRevocationEnabled(false);
        return pbp;
    }

    public void testTM(X509TrustManager xtm, X509Certificate[] chain,
                              Date notBefore, boolean valid) {
        // Check if TLS Server certificate (the first element of the chain)
        // is issued after the specified notBefore date (should be rejected
        // unless distrust property is false). To do this, we need to
        // fake the notBefore date since none of the test certs are issued
        // after then.
        chain[0] = new DistrustedTLSServerCert(chain[0], notBefore);

        // Wrap the intermediate and root CA certs in NonExpiringTLSServerCert
        // so it will never throw a CertificateExpiredException
        for (int i = 1; i < chain.length; i++) {
            chain[i] = new NonExpiringTLSServerCert(chain[i]);
        }

        try {
            xtm.checkServerTrusted(chain, "ECDHE_RSA");
            if (!valid) {
                throw new RuntimeException("chain should be invalid");
            }
        } catch (CertificateException ce) {
            if (valid) {
                throw new RuntimeException("Unexpected exception, chain " +
                        "should be valid", ce);
            }
            if (ce instanceof ValidatorException) {
                ValidatorException ve = (ValidatorException) ce;
                if (ve.getErrorType() != ValidatorException.T_UNTRUSTED_CERT) {
                    ce.printStackTrace(System.err);
                    throw new RuntimeException("Unexpected exception: " + ce);
                }
            } else {
                throw new RuntimeException(ce);
            }
        }
    }

    private X509Certificate[] loadCertificateChain(String certPath, String name)
            throws Exception {
        if (cf == null) {
            cf = CertificateFactory.getInstance("X.509");
        }
        try (InputStream in = new FileInputStream(TEST_SRC + File.separator + certPath +
                File.separator + name + "-chain.pem")) {
            Collection<X509Certificate> certs =
                    (Collection<X509Certificate>) cf.generateCertificates(in);
            return certs.toArray(new X509Certificate[0]);
        }
    }

    private static class NonExpiringTLSServerCert extends X509Certificate {
        private final X509Certificate cert;
        NonExpiringTLSServerCert(X509Certificate cert) {
            this.cert = cert;
        }
        public Set<String> getCriticalExtensionOIDs() {
            return cert.getCriticalExtensionOIDs();
        }
        public byte[] getExtensionValue(String oid) {
            return cert.getExtensionValue(oid);
        }
        public Set<String> getNonCriticalExtensionOIDs() {
            return cert.getNonCriticalExtensionOIDs();
        }
        public boolean hasUnsupportedCriticalExtension() {
            return cert.hasUnsupportedCriticalExtension();
        }
        public void checkValidity() {
            // always pass
        }
        public void checkValidity(Date date) {
            // always pass
        }
        public int getVersion() { return cert.getVersion(); }
        public BigInteger getSerialNumber() { return cert.getSerialNumber(); }
        public Principal getIssuerDN() { return cert.getIssuerDN(); }
        public Principal getSubjectDN() { return cert.getSubjectDN(); }
        public Date getNotBefore() { return cert.getNotBefore(); }
        public Date getNotAfter() { return cert.getNotAfter(); }
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return cert.getTBSCertificate();
        }
        public byte[] getSignature() { return cert.getSignature(); }
        public String getSigAlgName() { return cert.getSigAlgName(); }
        public String getSigAlgOID() { return cert.getSigAlgOID(); }
        public byte[] getSigAlgParams() { return cert.getSigAlgParams(); }
        public boolean[] getIssuerUniqueID() {
            return cert.getIssuerUniqueID();
        }
        public boolean[] getSubjectUniqueID() {
            return cert.getSubjectUniqueID();
        }
        public boolean[] getKeyUsage() { return cert.getKeyUsage(); }
        public int getBasicConstraints() { return cert.getBasicConstraints(); }
        public byte[] getEncoded() throws CertificateEncodingException {
            return cert.getEncoded();
        }
        public void verify(PublicKey key) throws CertificateException,
                InvalidKeyException, NoSuchAlgorithmException,
                NoSuchProviderException, SignatureException {
            cert.verify(key);
        }
        public void verify(PublicKey key, String sigProvider) throws
                CertificateException, InvalidKeyException, NoSuchAlgorithmException,
                NoSuchProviderException, SignatureException {
            cert.verify(key, sigProvider);
        }
        public PublicKey getPublicKey() { return cert.getPublicKey(); }
        public String toString() { return cert.toString(); }
    }

    private static class DistrustedTLSServerCert extends NonExpiringTLSServerCert {
        private final Date notBefore;
        DistrustedTLSServerCert(X509Certificate cert, Date notBefore) {
            super(cert);
            this.notBefore = notBefore;
        }
        public Date getNotBefore() { return notBefore; }
    }
}
