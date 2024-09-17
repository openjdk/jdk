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
import java.time.*;
import java.util.*;
import javax.net.ssl.*;
import sun.security.validator.Validator;
import sun.security.validator.ValidatorException;

import jdk.test.lib.security.SecurityUtils;

/**
 * @test
 * @bug 8337664
 * @summary Check that TLS Server certificates chaining back to distrusted
 *          Entrust roots are invalid
 * @library /test/lib
 * @modules java.base/sun.security.validator
 * @run main/othervm Distrust after policyOn invalid
 * @run main/othervm Distrust after policyOff valid
 * @run main/othervm Distrust before policyOn valid
 * @run main/othervm Distrust before policyOff valid
 */

public class Distrust {

    private static final String TEST_SRC = System.getProperty("test.src", ".");
    private static CertificateFactory cf;

    // Each of the roots have a test certificate chain stored in a file
    // named "<root>-chain.pem".
    private static String[] rootsToTest = new String[] {
        "entrustevca", "entrustrootcaec1", "entrustrootcag2", "entrustrootcag4",
        "entrust2048ca", "affirmtrustcommercialca", "affirmtrustnetworkingca",
        "affirmtrustpremiumca", "affirmtrustpremiumeccca" };

    // A date that is after the restrictions take effect
    private static final Date NOVEMBER_1_2024 =
        Date.from(LocalDate.of(2024, 11, 1)
                           .atStartOfDay(ZoneOffset.UTC)
                           .toInstant());

    // A date that is a second before the restrictions take effect
    private static final Date BEFORE_NOVEMBER_1_2024 =
        Date.from(LocalDate.of(2024, 11, 1)
                           .atStartOfDay(ZoneOffset.UTC)
                           .minusSeconds(1)
                           .toInstant());

    public static void main(String[] args) throws Exception {

        cf = CertificateFactory.getInstance("X.509");

        boolean before = args[0].equals("before");
        boolean policyOn = args[1].equals("policyOn");
        boolean isValid = args[2].equals("valid");

        if (!policyOn) {
            // disable policy (default is on)
            Security.setProperty("jdk.security.caDistrustPolicies", "");
        }

        Date notBefore = before ? BEFORE_NOVEMBER_1_2024 : NOVEMBER_1_2024;

        X509TrustManager pkixTM = getTMF("PKIX", null);
        X509TrustManager sunX509TM = getTMF("SunX509", null);
        for (String test : rootsToTest) {
            System.err.println("Testing " + test);
            X509Certificate[] chain = loadCertificateChain(test);

            testTM(sunX509TM, chain, notBefore, isValid);
            testTM(pkixTM, chain, notBefore, isValid);
        }
    }

    private static X509TrustManager getTMF(String type,
            PKIXBuilderParameters params) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(type);
        if (params == null) {
            tmf.init((KeyStore)null);
        } else {
            tmf.init(new CertPathTrustManagerParameters(params));
        }
        TrustManager[] tms = tmf.getTrustManagers();
        for (TrustManager tm : tms) {
            X509TrustManager xtm = (X509TrustManager)tm;
            return xtm;
        }
        throw new Exception("No TrustManager for " + type);
    }

    private static PKIXBuilderParameters getParams() throws Exception {
        PKIXBuilderParameters pbp =
            new PKIXBuilderParameters(SecurityUtils.getCacertsKeyStore(),
                                      new X509CertSelector());
        pbp.setRevocationEnabled(false);
        return pbp;
    }

    private static void testTM(X509TrustManager xtm, X509Certificate[] chain,
                               Date notBefore, boolean valid) throws Exception {
        // Check if TLS Server certificate (the first element of the chain)
        // is issued after the specified notBefore date (should be rejected
        // unless distrust property is false). To do this, we need to
        // fake the notBefore date since none of the test certs are issued
        // after then.
        chain[0] = new DistrustedTLSServerCert(chain[0], notBefore);

        try {
            xtm.checkServerTrusted(chain, "ECDHE_RSA");
            if (!valid) {
                throw new Exception("chain should be invalid");
            }
        } catch (CertificateException ce) {
            // expired TLS certificates should not be treated as failure
            if (expired(ce)) {
                System.err.println("Test is N/A, chain is expired");
                return;
            }
            if (valid) {
                throw new Exception("Unexpected exception, chain " +
                                    "should be valid", ce);
            }
            if (ce instanceof ValidatorException) {
                ValidatorException ve = (ValidatorException)ce;
                if (ve.getErrorType() != ValidatorException.T_UNTRUSTED_CERT) {
                    ce.printStackTrace(System.err);
                    throw new Exception("Unexpected exception: " + ce);
                }
            } else {
                throw new Exception("Unexpected exception: " + ce);
            }
        }
    }

    // check if a cause of exception is an expired cert
    private static boolean expired(CertificateException ce) {
        if (ce instanceof CertificateExpiredException) {
            return true;
        }
        Throwable t = ce.getCause();
        while (t != null) {
            if (t instanceof CertificateExpiredException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static X509Certificate[] loadCertificateChain(String name)
            throws Exception {
        try (InputStream in = new FileInputStream(TEST_SRC + File.separator +
                                                  name + "-chain.pem")) {
            Collection<X509Certificate> certs =
                (Collection<X509Certificate>)cf.generateCertificates(in);
            return certs.toArray(new X509Certificate[0]);
        }
    }

    private static class DistrustedTLSServerCert extends X509Certificate {
        private final X509Certificate cert;
        private final Date notBefore;
        DistrustedTLSServerCert(X509Certificate cert, Date notBefore) {
            this.cert = cert;
            this.notBefore = notBefore;
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
        public void checkValidity() throws CertificateExpiredException,
            CertificateNotYetValidException {
            // always pass
        }
        public void checkValidity(Date date) throws CertificateExpiredException,
            CertificateNotYetValidException {
            // always pass
        }
        public int getVersion() { return cert.getVersion(); }
        public BigInteger getSerialNumber() { return cert.getSerialNumber(); }
        public Principal getIssuerDN() { return cert.getIssuerDN(); }
        public Principal getSubjectDN() { return cert.getSubjectDN(); }
        public Date getNotBefore() { return notBefore; }
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
}
