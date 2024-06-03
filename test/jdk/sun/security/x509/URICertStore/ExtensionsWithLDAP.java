/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8134708
 * @summary Check if LDAP resources from CRLDP and AIA extensions can be loaded
 * @library /test/jdk/java/security/testlibrary
 * @modules jdk.security.auth
 *          java.base/sun.security.provider.certpath
 *          java.base/sun.security.util
 *          java.base/sun.security.validator
 *          java.base/sun.security.x509
 * @build CertificateBuilder
 * @run main/othervm -Djdk.net.hosts.file=${test.src}/CRLDP
 *      -Dcom.sun.security.enableCRLDP=true
 *      ExtensionsWithLDAP CRLDP ldap.host.for.crldp
 * @run main/othervm -Djdk.net.hosts.file=${test.src}/AIA
 *      -Dcom.sun.security.enableAIAcaIssuers=true
 *      ExtensionsWithLDAP AIA ldap.host.for.aia
 */

import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import sun.security.testlibrary.CertificateBuilder;
import sun.security.x509.*;

public class ExtensionsWithLDAP {

    static X509Certificate caCertificate;
    static X509Certificate eeCertificate;

    static boolean debug = Boolean.getBoolean("test.debug");

    public static void main(String[] args) throws Exception {
        String extension = args[0];
        String targetHost = args[1];

        loadCertificates();

        Set<TrustAnchor> trustedCertsSet = new HashSet<>();
        trustedCertsSet.add(new TrustAnchor(caCertificate, null));

        CertPath cp = CertificateFactory.getInstance("X509")
                .generateCertPath(Arrays.asList(eeCertificate));

        // CertPath validator should try to parse CRLDP and AIA extensions,
        // and load CRLs/certs which they point to.
        // If proxy server catches requests for resolving host names
        // which extensions contain, then it means that CertPath validator
        // tried to load CRLs/certs which they point to.
        List<String> hosts = new ArrayList<>();
        Consumer<Socket> socketConsumer = (Socket socket) -> {
            InetSocketAddress remoteAddress
                    = (InetSocketAddress) socket.getRemoteSocketAddress();
            hosts.add(remoteAddress.getHostName());
        };
        try (SocksProxy proxy = SocksProxy.startProxy(socketConsumer)) {
            CertPathValidator.getInstance("PKIX").validate(cp,
                    new PKIXParameters(trustedCertsSet));
            throw new RuntimeException("CertPathValidatorException not thrown");
        } catch (CertPathValidatorException cpve) {
            System.out.println("Expected exception: " + cpve);
        }

        if (!hosts.contains(targetHost)) {
            throw new RuntimeException(
                    String.format("The %s from %s extension is not requested",
                            targetHost, extension));
        }

        System.out.println("Test passed");
    }

    static void loadCertificates() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        KeyPair caKeys = kpg.generateKeyPair();
        caCertificate = CertificateBuilder.newSelfSignedCA(
            "CN = Root", caKeys)
                .build(null, caKeys.getPrivate(), "SHA512withRSA");

        KeyPair eeKeys = kpg.generateKeyPair();
        GeneralNames gns = new GeneralNames();
        gns.add(new GeneralName(new URIName("ldap://ldap.host.for.crldp/main.crl")));
        eeCertificate = CertificateBuilder.newServerCertificateBuilder(
            "CN = EE", eeKeys.getPublic(), caKeys.getPublic())
            .addAIAExt(List.of("CAISSUER|ldap://ldap.host.for.aia/dc=Root?cACertificate"))
            .addExtension(new CRLDistributionPointsExtension(List.of(
                    new DistributionPoint(gns, null, null))))
            .addKeyUsageExt(new boolean[]{false, false, false, false, false, false, false, false, false})
            .build(caCertificate, caKeys.getPrivate(), "SHA512withRSA");

        if (debug) {
            System.err.println("CA Certificate");
            CertificateBuilder.printCertificate(caCertificate, System.err);
            System.err.println("EE Certificate");
            CertificateBuilder.printCertificate(eeCertificate, System.err);
        }
    }
}
