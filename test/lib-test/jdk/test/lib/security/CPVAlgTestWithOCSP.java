/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8349759
 * @summary Test the CertificateBuilder and SimpleOCSPServer test utility
 *          classes using a range of signature algorithms and parameters.
 *          The goal is to test with both no-parameter and parameterized
 *          signature algorithms and use the CertPathValidator to validate
 *          the correctness of the certificate and OCSP server-side structures.
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main/othervm CPVAlgTestWithOCSP RSA
 * @run main/othervm CPVAlgTestWithOCSP RSA:3072
 * @run main/othervm CPVAlgTestWithOCSP DSA
 * @run main/othervm CPVAlgTestWithOCSP DSA:3072
 * @run main/othervm CPVAlgTestWithOCSP RSASSA-PSS
 * @run main/othervm CPVAlgTestWithOCSP RSASSA-PSS:3072
 * @run main/othervm CPVAlgTestWithOCSP RSASSA-PSS:4096:SHA-512:SHA3-384:128:1
 * @run main/othervm CPVAlgTestWithOCSP EC
 * @run main/othervm CPVAlgTestWithOCSP EC:secp521r1
 * @run main/othervm CPVAlgTestWithOCSP Ed25519
 * @run main/othervm CPVAlgTestWithOCSP ML-DSA-65
 */

import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.security.SimpleOCSPServer;
import jdk.test.lib.security.CertificateBuilder;

import static java.security.cert.PKIXRevocationChecker.Option.NO_FALLBACK;

public class CPVAlgTestWithOCSP {

    static final String passwd = "passphrase";
    static final String ROOT_ALIAS = "root";
    static final boolean[] CA_KU_FLAGS = {true, false, false, false, false,
            true, true, false, false};
    static final boolean[] EE_KU_FLAGS = {true, false, false, false, false,
            false, false, false, false};
    static final List<String> EE_EKU_OIDS = List.of("1.3.6.1.5.5.7.3.1",
            "1.3.6.1.5.5.7.3.2");

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 1) {
            throw new RuntimeException(
                    "Usage: CPVAlgTestWithOCSP <IssKeyAlg>");
        }
        String keyGenAlg = args[0];

        // Generate Root and EE keys
        KeyPairGenerator keyGen = getKpGen(keyGenAlg);
        KeyPair rootCaKP = keyGen.genKeyPair();
        KeyPair eeKp = keyGen.genKeyPair();

        // Set up the Root CA Cert
        // Make a 3 year validity starting from 60 days ago
        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
        long end = start + TimeUnit.DAYS.toMillis(1085);
        CertificateBuilder cbld = new CertificateBuilder();
        cbld.setSubjectName("CN=Root CA Cert, O=SomeCompany").
                setPublicKey(rootCaKP.getPublic()).
                setSerialNumber(new BigInteger("1")).
                setValidity(new Date(start), new Date(end)).
                addSubjectKeyIdExt(rootCaKP.getPublic()).
                addAuthorityKeyIdExt(rootCaKP.getPublic()).
                addBasicConstraintsExt(true, true, -1).
                addKeyUsageExt(CA_KU_FLAGS);

        // Make our Root CA Cert!
        X509Certificate rootCert = cbld.build(null, rootCaKP.getPrivate());
        log("Root CA Created:\n%s", rootCert);

        // Now build a keystore and add the keys and cert
        KeyStore.Builder keyStoreBuilder =
                KeyStore.Builder.newInstance("PKCS12", null,
                new KeyStore.PasswordProtection("adminadmin0".toCharArray()));
        KeyStore rootKeystore = keyStoreBuilder.getKeyStore();
        Certificate[] rootChain = {rootCert};
        rootKeystore.setKeyEntry(ROOT_ALIAS, rootCaKP.getPrivate(),
                passwd.toCharArray(), rootChain);

        // Now fire up the OCSP responder
        SimpleOCSPServer rootOcsp = new SimpleOCSPServer(rootKeystore,
                passwd, ROOT_ALIAS, null);
        rootOcsp.enableLog(true);
        rootOcsp.setNextUpdateInterval(3600);
        rootOcsp.start();

        // Wait 60 seconds for server ready
        boolean readyStatus = rootOcsp.awaitServerReady(60, TimeUnit.SECONDS);
        if (!readyStatus) {
            throw new RuntimeException("Server not ready");
        }
        int rootOcspPort = rootOcsp.getPort();
        String rootRespURI = "http://localhost:" + rootOcspPort;
        log("Root OCSP Responder URI is %s", rootRespURI);

        // Let's make an EE cert
        // Make a 1 year validity starting from 60 days ago
        start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
        end = start + TimeUnit.DAYS.toMillis(365);
        cbld.reset().setSubjectName("CN=Brave Sir Robin, O=SomeCompany").
                setPublicKey(eeKp.getPublic()).
                setValidity(new Date(start), new Date(end)).
                addSubjectKeyIdExt(eeKp.getPublic()).
                addAuthorityKeyIdExt(rootCaKP.getPublic()).
                addKeyUsageExt(EE_KU_FLAGS).
                addExtendedKeyUsageExt(EE_EKU_OIDS).
                addSubjectAltNameDNSExt(Collections.singletonList("localhost")).
                addAIAExt(Collections.singletonList(rootRespURI));
        X509Certificate eeCert = cbld.build(rootCert, rootCaKP.getPrivate());
        log("EE CA Created:\n%s", eeCert);

        // Provide end entity cert revocation info to the Root CA
        // OCSP responder.
        Map<BigInteger, SimpleOCSPServer.CertStatusInfo> revInfo =
                new HashMap<>();
        revInfo.put(eeCert.getSerialNumber(),
                new SimpleOCSPServer.CertStatusInfo(
                        SimpleOCSPServer.CertStatus.CERT_STATUS_GOOD));
        rootOcsp.updateStatusDb(revInfo);

        // validate chain
        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
        PKIXRevocationChecker prc =
                (PKIXRevocationChecker) cpv.getRevocationChecker();
        prc.setOptions(EnumSet.of(NO_FALLBACK));
        PKIXParameters params =
                new PKIXParameters(Set.of(new TrustAnchor(rootCert, null)));
        params.addCertPathChecker(prc);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        CertPath cp = cf.generateCertPath(List.of(eeCert));
        cpv.validate(cp, params);
    }

    private static KeyPairGenerator getKpGen(String keyGenAlg)
            throws GeneralSecurityException {
        String[] algComps = keyGenAlg.split(":");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(algComps[0]);
        int bitLen;

        // Handle any parameters in additional tokenized fields
        switch (algComps[0].toUpperCase()) {
            case "EC":
                // The curve name will be the second token, or secp256r1
                // if not provided.
                String curveName = (algComps.length >= 2) ? algComps[1] :
                        "secp256r1";
                kpg.initialize(new ECGenParameterSpec(curveName));
                break;
            case "RSA":
            case "DSA":
                // Form is RSA|DSA[:<KeyBitLen>]
                bitLen = (algComps.length >= 2) ?
                        Integer.parseInt(algComps[1]) : 2048;
                kpg.initialize(bitLen);
                break;
            case "RSASSA-PSS":
                // Form is RSASSA-PSS[:<KeyBitLen>[:HASH:MGFHASH:SALTLEN:TR]]
                switch (algComps.length) {
                    case 1:     // Default key length and parameters
                        kpg.initialize(2048);
                        break;
                    case 2:     // Specified key length, default params
                        kpg.initialize(Integer.parseInt(algComps[1]));
                        break;
                    default:    // len > 2, key length and specified parameters
                        bitLen = Integer.parseInt(algComps[1]);
                        String hashAlg = algComps[2];
                        MGF1ParameterSpec mSpec = (algComps.length >= 4) ?
                                new MGF1ParameterSpec(algComps[3]) :
                                MGF1ParameterSpec.SHA256;
                        int saltLen = (algComps.length >= 5) ?
                                Integer.parseInt(algComps[4]) : 32;
                        int trail = (algComps.length >= 6) ?
                                Integer.parseInt(algComps[5]) :
                                PSSParameterSpec.TRAILER_FIELD_BC;
                        PSSParameterSpec pSpec = new PSSParameterSpec(hashAlg,
                                "MGF1", mSpec, saltLen, trail);
                        kpg.initialize(new RSAKeyGenParameterSpec(bitLen,
                                RSAKeyGenParameterSpec.F4, pSpec));
                        break;
                }

            // Default: just use the KPG as-is, no additional init needed.
    }

        return kpg;
    }

    /**
     * Log a message on stdout
     *
     * @param format the format string for the log entry
     * @param args zero or more arguments corresponding to the format string
     */
    private static void log(String format, Object ... args) {
        System.out.format(format + "\n", args);
    }
}
