/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.jpackage.test.MacHelper.NamedCertificateRequestSupplier;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSign.CertificateRequest;
import jdk.jpackage.test.MacSign.CertificateType;
import jdk.jpackage.test.MacSign.KeychainWithCertsSpec;
import jdk.jpackage.test.MacSign.ResolvedKeychain;
import jdk.jpackage.test.TKit;


/*
 * @test
 * @summary Setup the environment for jpackage macos signing tests.
 *          Creates required keychains and signing identities.
 *          Does NOT run any jpackag tests.
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @requires (jpackage.test.MacSignTests == "setup")
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningBase.setUp
 */

/*
 * @test
 * @summary Tear down the environment for jpackage macos signing tests.
 *          Deletes required keychains and signing identities.
 *          Does NOT run any jpackag tests.
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @requires (jpackage.test.MacSignTests == "teardown")
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningBase.tearDown
 */

public class SigningBase {

    public enum StandardCertificateRequest implements NamedCertificateRequestSupplier {
        CODESIGN(cert().userName(NAME_ASCII)),
        CODESIGN_COPY(cert().days(100).userName(NAME_ASCII)),
        CODESIGN_ACME_TECH_LTD(cert().days(100).userName("ACME Technologies Limited (ABC12345)")),
        PKG(cert().type(CertificateType.INSTALLER).userName(NAME_ASCII)),
        PKG_COPY(cert().type(CertificateType.INSTALLER).days(100).userName(NAME_ASCII)),
        CODESIGN_UNICODE(cert().userName(NAME_UNICODE)),
        PKG_UNICODE(cert().type(CertificateType.INSTALLER).userName(NAME_UNICODE)),
        CODESIGN_EXPIRED(cert().expired().userName("expired jpackage test")),
        PKG_EXPIRED(cert().expired().type(CertificateType.INSTALLER).userName("expired jpackage test"));

        StandardCertificateRequest(CertificateRequest.Builder specBuilder) {
            this.spec = specBuilder.create();
        }

        @Override
        public CertificateRequest certRequest() {
            return spec;
        }

        private static CertificateRequest.Builder cert() {
            return new CertificateRequest.Builder();
        }

        private final CertificateRequest spec;
    }

    /**
     * Standard keychains used in signing tests.
     */
    public enum StandardKeychain {
        /**
         * The primary keychain with good certificates.
         */
        MAIN("jpackagerTest.keychain",
                StandardCertificateRequest.CODESIGN,
                StandardCertificateRequest.PKG,
                StandardCertificateRequest.CODESIGN_UNICODE,
                StandardCertificateRequest.PKG_UNICODE,
                StandardCertificateRequest.CODESIGN_ACME_TECH_LTD),
        /**
         * A keychain with some good and some expired certificates.
         */
        EXPIRED("jpackagerTest-expired.keychain",
                StandardCertificateRequest.CODESIGN,
                StandardCertificateRequest.PKG,
                StandardCertificateRequest.CODESIGN_EXPIRED,
                StandardCertificateRequest.PKG_EXPIRED),
        /**
         * A keychain with duplicated certificates.
         */
        DUPLICATE("jpackagerTest-duplicate.keychain",
                StandardCertificateRequest.CODESIGN,
                StandardCertificateRequest.PKG,
                StandardCertificateRequest.CODESIGN_COPY,
                StandardCertificateRequest.PKG_COPY),
        ;

        StandardKeychain(String keychainName, StandardCertificateRequest... certs) {
            this(keychainName,
                    certs[0].certRequest(),
                    Stream.of(certs).skip(1).map(StandardCertificateRequest::certRequest).toArray(CertificateRequest[]::new));
        }

        StandardKeychain(String keychainName, CertificateRequest cert, CertificateRequest... otherCerts) {
            final var builder = keychain(keychainName).addCert(cert);
            List.of(otherCerts).forEach(builder::addCert);
            this.keychain = new ResolvedKeychain(builder.create());
        }

        public ResolvedKeychain keychain() {
            return keychain;
        }

        public X509Certificate mapCertificateRequest(CertificateRequest certRequest) {
            return Objects.requireNonNull(keychain.mapCertificateRequests().get(certRequest));
        }

        public boolean contains(StandardCertificateRequest certRequest) {
            return keychain.spec().certificateRequests().contains(certRequest.spec);
        }

        private static KeychainWithCertsSpec.Builder keychain(String name) {
            return new KeychainWithCertsSpec.Builder().name(name);
        }

        private static List<KeychainWithCertsSpec> signingEnv() {
            return Stream.of(values()).map(StandardKeychain::keychain).map(ResolvedKeychain::spec).toList();
        }

        private final ResolvedKeychain keychain;
    }

    public static void setUp() {
        MacSign.setUp(StandardKeychain.signingEnv());
    }

    public static void tearDown() {
        MacSign.tearDown(StandardKeychain.signingEnv());
    }

    public static void verifySignTestEnvReady() {
        if (!Inner.SIGN_ENV_READY) {
            TKit.throwSkippedException(new IllegalStateException("Misconfigured signing test environment"));
        }
    }

    private final class Inner {
        private static final boolean SIGN_ENV_READY = MacSign.isDeployed(StandardKeychain.signingEnv());
    }

    private static final String NAME_ASCII = "jpackage.openjdk.java.net";
    private static final String NAME_UNICODE = "jpackage.openjdk.java.net (ö)";
}
