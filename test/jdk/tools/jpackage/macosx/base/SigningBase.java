/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSign.CertificateRequest;
import jdk.jpackage.test.MacSign.CertificateType;
import jdk.jpackage.test.MacSign.KeychainWithCertsSpec;
import jdk.jpackage.test.MacSign.ResolvedKeychain;
import jdk.jpackage.test.MacSignVerify;
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

    public enum StandardCertificateRequest {
        CODESIGN(cert().userName(DEV_NAMES[CertIndex.ASCII_INDEX.value()])),
        CODESIGN_COPY(cert().days(100).userName(DEV_NAMES[CertIndex.ASCII_INDEX.value()])),
        PKG(cert().type(CertificateType.INSTALLER).userName(DEV_NAMES[CertIndex.ASCII_INDEX.value()])),
        PKG_COPY(cert().type(CertificateType.INSTALLER).days(100).userName(DEV_NAMES[CertIndex.ASCII_INDEX.value()])),
        CODESIGN_UNICODE(cert().userName(DEV_NAMES[CertIndex.UNICODE_INDEX.value()])),
        PKG_UNICODE(cert().type(CertificateType.INSTALLER).userName(DEV_NAMES[CertIndex.UNICODE_INDEX.value()])),
        CODESIGN_EXPIRED(cert().expired().userName("expired jpackage test")),
        PKG_EXPIRED(cert().expired().type(CertificateType.INSTALLER).userName("expired jpackage test"));

        StandardCertificateRequest(CertificateRequest.Builder specBuilder) {
            this.spec = specBuilder.create();
        }

        public CertificateRequest spec() {
            return spec;
        }

        private static CertificateRequest.Builder cert() {
            return new CertificateRequest.Builder();
        }

        private final CertificateRequest spec;
    }

    public enum StandardKeychain {
        MAIN(DEFAULT_KEYCHAIN,
                StandardCertificateRequest.CODESIGN,
                StandardCertificateRequest.PKG,
                StandardCertificateRequest.CODESIGN_UNICODE,
                StandardCertificateRequest.PKG_UNICODE),
        EXPIRED("jpackagerTest-expired.keychain",
                StandardCertificateRequest.CODESIGN,
                StandardCertificateRequest.PKG,
                StandardCertificateRequest.CODESIGN_EXPIRED,
                StandardCertificateRequest.PKG_EXPIRED),
        DUPLICATE("jpackagerTest-duplicate.keychain",
                StandardCertificateRequest.CODESIGN,
                StandardCertificateRequest.PKG,
                StandardCertificateRequest.CODESIGN_COPY,
                StandardCertificateRequest.PKG_COPY);

        StandardKeychain(String keychainName, StandardCertificateRequest... certs) {
            this(keychainName, certs[0].spec(), Stream.of(certs).skip(1).map(StandardCertificateRequest::spec).toArray(CertificateRequest[]::new));
        }

        StandardKeychain(String keychainName, CertificateRequest cert, CertificateRequest... otherCerts) {
            final var builder = keychain(keychainName).addCert(cert);
            List.of(otherCerts).forEach(builder::addCert);
            this.spec = new ResolvedKeychain(builder.create());
        }

        public KeychainWithCertsSpec spec() {
            return spec.spec();
        }

        public X509Certificate mapCertificateRequest(CertificateRequest certRequest) {
            return Objects.requireNonNull(spec.mapCertificateRequests().get(certRequest));
        }

        private static KeychainWithCertsSpec.Builder keychain(String name) {
            return new KeychainWithCertsSpec.Builder().name(name);
        }

        private static CertificateRequest.Builder cert() {
            return new CertificateRequest.Builder();
        }

        private static List<KeychainWithCertsSpec> signingEnv() {
            return Stream.of(values()).map(StandardKeychain::spec).toList();
        }

        private final ResolvedKeychain spec;
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

    enum CertIndex {
        ASCII_INDEX(0),
        UNICODE_INDEX(1),
        INVALID_INDEX(-1);

        CertIndex(int value) {
            this.value = value;
        }

        int value() {
            return value;
        }

        private final int value;
    }

    public static int DEFAULT_INDEX = 0;
    private static String [] DEV_NAMES = {
        "jpackage.openjdk.java.net",
        "jpackage.openjdk.java.net (ö)",
    };
    private static String DEFAULT_KEYCHAIN = "jpackagerTest.keychain";

    public static String getDevName(int certIndex) {
        // Always use values from system properties if set
        String value = System.getProperty("jpackage.mac.signing.key.user.name");
        if (value != null) {
            return value;
        }

        return DEV_NAMES[certIndex];
    }

    public static int getDevNameIndex(String devName) {
        return Arrays.binarySearch(DEV_NAMES, devName);
    }

    // Returns 'true' if dev name from DEV_NAMES
    public static boolean isDevNameDefault() {
        String value = System.getProperty("jpackage.mac.signing.key.user.name");
        if (value != null) {
            return false;
        }

        return true;
    }

    public static String getAppCert(int certIndex) {
        return "Developer ID Application: " + getDevName(certIndex);
    }

    public static String getInstallerCert(int certIndex) {
        return "Developer ID Installer: " + getDevName(certIndex);
    }

    public static String getKeyChain() {
        // Always use values from system properties if set
        String value = System.getProperty("jpackage.mac.signing.keychain");
        if (value != null) {
            return value;
        }

        return DEFAULT_KEYCHAIN;
    }

    public static void verifyCodesign(Path target, boolean signed, int certIndex) {
        if (signed) {
            final var certRequest = getCertRequest(certIndex);
            MacSignVerify.assertSigned(target, certRequest);
        } else {
            MacSignVerify.assertAdhocSigned(target);
        }
    }

    // Since we no longer have unsigned app image, but we need to check
    // DMG which is not adhoc or certificate signed and we cannot use verifyCodesign
    // for this. verifyDMG() is introduced to check that DMG is unsigned.
    // Should not be used to validated anything else.
    public static void verifyDMG(Path target) {
        if (!target.toString().toLowerCase().endsWith(".dmg")) {
            throw new IllegalArgumentException("Unexpected target: " + target);
        }

        MacSignVerify.assertUnsigned(target);
    }

    public static void verifySpctl(Path target, String type, int certIndex) {
        final var standardCertIndex = Stream.of(CertIndex.values()).filter(v -> {
            return v.value() == certIndex;
        }).findFirst().orElseThrow();

        final var standardType = Stream.of(MacSignVerify.SpctlType.values()).filter(v -> {
            return v.value().equals(type);
        }).findFirst().orElseThrow();

        final String expectedSignOrigin;
        if (standardCertIndex == CertIndex.INVALID_INDEX) {
            expectedSignOrigin = null;
        } else if (standardType == MacSignVerify.SpctlType.EXEC) {
            expectedSignOrigin = getCertRequest(certIndex).name();
        } else if (standardType == MacSignVerify.SpctlType.INSTALL) {
            expectedSignOrigin = getPkgCertRequest(certIndex).name();
        } else {
            throw new IllegalArgumentException();
        }

        final var signOrigin = MacSignVerify.findSpctlSignOrigin(standardType, target).orElse(null);

        TKit.assertEquals(signOrigin, expectedSignOrigin,
                String.format("Check [%s] has sign origin as expected", target));
    }

    public static void verifyPkgutil(Path target, boolean signed, int certIndex) {
        if (signed) {
            final var certRequest = getPkgCertRequest(certIndex);
            MacSignVerify.assertPkgSigned(target, certRequest, StandardKeychain.MAIN.mapCertificateRequest(certRequest));
        } else {
            MacSignVerify.assertUnsigned(target);
        }
    }

    public static void verifyAppImageSignature(JPackageCommand appImageCmd,
            boolean isSigned, String... launchers) throws Exception {
        Path launcherPath = appImageCmd.appLauncherPath();
        SigningBase.verifyCodesign(launcherPath, isSigned, SigningBase.DEFAULT_INDEX);

        final List<String> launchersList = List.of(launchers);
        launchersList.forEach(launcher -> {
            Path testALPath = launcherPath.getParent().resolve(launcher);
            SigningBase.verifyCodesign(testALPath, isSigned, SigningBase.DEFAULT_INDEX);
        });

        Path appImage = appImageCmd.outputBundle();
        SigningBase.verifyCodesign(appImage, isSigned, SigningBase.DEFAULT_INDEX);
        if (isSigned) {
            SigningBase.verifySpctl(appImage, "exec", SigningBase.DEFAULT_INDEX);
        }
    }

    private static CertificateRequest getCertRequest(int certIndex) {
        switch (CertIndex.values()[certIndex]) {
            case ASCII_INDEX -> {
                return StandardCertificateRequest.CODESIGN.spec();
            }
            case UNICODE_INDEX -> {
                return StandardCertificateRequest.CODESIGN_UNICODE.spec();
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static CertificateRequest getPkgCertRequest(int certIndex) {
        switch (CertIndex.values()[certIndex]) {
            case ASCII_INDEX -> {
                return StandardCertificateRequest.PKG.spec();
            }
            case UNICODE_INDEX -> {
                return StandardCertificateRequest.PKG_UNICODE.spec();
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }
}
