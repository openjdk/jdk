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

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSign.CertificateRequest;
import jdk.jpackage.test.MacSignVerify;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage with --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @library base
 * @build SigningBase
 * @build jdk.jpackage.test.*
 * @build MacSignTest
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=MacSignTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class MacSignTest {

    @Test
    public static void testAppContentWarning() throws IOException {

        // Create app content directory with the name known to fail signing.
        // This will trigger jpackage exit with status code "1".
        final var appContent = TKit.createTempDirectory("app-content").resolve("foo.1");
        Files.createDirectory(appContent);
        Files.createFile(appContent.resolve("file"));

        final List<CannedFormattedString> expectedStrings = new ArrayList<>();
        expectedStrings.add(JPackageStringBundle.MAIN.cannedFormattedString("message.codesign.failed.reason.app.content"));

        final var xcodeWarning = JPackageStringBundle.MAIN.cannedFormattedString("message.codesign.failed.reason.xcode.tools");
        if (!MacHelper.isXcodeDevToolsInstalled()) {
            expectedStrings.add(xcodeWarning);
        }

        final var keychain = SigningBase.StandardKeychain.EXPIRED.spec().keychain();

        MacSign.Keychain.withAddedKeychains(List.of(keychain), () -> {
            // --app-content and --type app-image
            // Expect `message.codesign.failed.reason.app.content` message in the log.
            // This is not a fatal error, just a warning.
            // To make jpackage fail, specify bad additional content.
            final var cmd = JPackageCommand.helloAppImage()
                    .ignoreDefaultVerbose(true)
                    .validateOutput(expectedStrings.toArray(CannedFormattedString[]::new))
                    .addArguments("--app-content", appContent)
                    .addArguments("--mac-sign")
                    .addArguments("--mac-signing-keychain", keychain.name())
                    .addArguments("--mac-app-image-sign-identity", SigningBase.StandardCertificateRequest.CODESIGN.spec().name());

            if (MacHelper.isXcodeDevToolsInstalled()) {
                // Check there is no warning about missing xcode command line developer tools.
                cmd.validateOutput(TKit.assertTextStream(xcodeWarning.getValue()).negate());
            }

            cmd.execute(1);
        });
    }

    @Test
    @Parameter({"IMAGE", "EXPIRED_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_DMG", "EXPIRED_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_PKG", "EXPIRED_SIGNING_KEY_USER_NAME", "EXPIRED_SIGNING_KEY_USER_NAME_PKG"})

    @Parameter({"IMAGE", "EXPIRED_SIGN_IDENTITY"})
    @Parameter({"MAC_DMG", "EXPIRED_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "EXPIRED_SIGN_IDENTITY"})

    @Parameter({"IMAGE", "EXPIRED_CODESIGN_SIGN_IDENTITY"})
    @Parameter({"MAC_DMG", "EXPIRED_CODESIGN_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "EXPIRED_CODESIGN_SIGN_IDENTITY"})

    @Parameter({"MAC_PKG", "GOOD_CODESIGN_SIGN_IDENTITY", "EXPIRED_PKG_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "EXPIRED_CODESIGN_SIGN_IDENTITY", "GOOD_PKG_SIGN_IDENTITY"})
    public static void testExpiredCertificate(PackageType type, SignOption... options) {

        final var keychain = SigningBase.StandardKeychain.EXPIRED.spec().keychain();

        MacSign.Keychain.withAddedKeychains(List.of(keychain), () -> {
            final var cmd = JPackageCommand.helloAppImage()
                    .ignoreDefaultVerbose(true)
                    .addArguments("--mac-sign")
                    .addArguments("--mac-signing-keychain", keychain.name())
                    .addArguments(Stream.of(options).map(SignOption::args).flatMap(List::stream).toList())
                    .setPackageType(type);

            SignOption.configureOutputValidation(cmd, Stream.of(options).filter(SignOption::expired).toList(), opt -> {
                return JPackageStringBundle.MAIN.cannedFormattedString("error.certificate.expired", opt.identityName());
            }).execute(1);
        });
    }

    @Test
    // Case "--mac-signing-key-user-name": jpackage selects first certificate
    // found with warning message. Certificate hash is pass to "codesign" in this
    // case.
    @Parameter({"IMAGE", "0", "GOOD_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_DMG", "0", "GOOD_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_PKG", "0", "GOOD_SIGNING_KEY_USER_NAME_PKG", "GOOD_SIGNING_KEY_USER_NAME"})

    // Case "--mac-app-image-sign-identity": sign identity will be pass to
    // "codesign" and "codesign" should fail due to multiple certificates with
    // same common name found.
    @Parameter({"IMAGE", "1", "GOOD_CODESIGN_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "1", "GOOD_CODESIGN_SIGN_IDENTITY", "GOOD_PKG_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "1", "GOOD_PKG_SIGN_IDENTITY"})
    public static void testMultipleCertificates(PackageType type, int jpackageExitCode, SignOption... options) {

        final var keychain = SigningBase.StandardKeychain.DUPLICATE.spec().keychain();

        MacSign.Keychain.withAddedKeychains(List.of(keychain), () -> {
            final var cmd = JPackageCommand.helloAppImage()
                    .ignoreDefaultVerbose(true)
                    .addArguments("--mac-sign")
                    .addArguments("--mac-signing-keychain", keychain.name())
                    .addArguments(Stream.of(options).map(SignOption::args).flatMap(List::stream).toList())
                    .setPackageType(type);

            SignOption.configureOutputValidation(cmd, List.of(options), opt -> {
                return JPackageStringBundle.MAIN.cannedFormattedString("error.multiple.certs.found", opt.identityName(), keychain.name());
            }).execute(jpackageExitCode);
        });
    }

    @Test
    @ParameterSupplier
    public static void testSelectSigningIdentity(String signingKeyUserName, CertificateRequest certRequest) {

        final var keychain = SigningBase.StandardKeychain.MAIN.spec().keychain();

        MacSign.Keychain.withAddedKeychains(List.of(keychain), () -> {
            final var cmd = JPackageCommand.helloAppImage()
                    .setFakeRuntime()
                    .addArguments("--mac-sign")
                    .addArguments("--mac-signing-keychain", keychain.name())
                    .addArguments("--mac-signing-key-user-name", signingKeyUserName);

            cmd.executeAndAssertHelloAppImageCreated();

            MacSignVerify.assertSigned(cmd.outputBundle(), certRequest);
        });
    }

    public static Collection<Object[]> testSelectSigningIdentity() {
        return Stream.of(
                SigningBase.StandardCertificateRequest.CODESIGN,
                SigningBase.StandardCertificateRequest.CODESIGN_UNICODE
        ).map(SigningBase.StandardCertificateRequest::spec).<Object[]>mapMulti((certRequest, acc) -> {
            acc.accept(new Object[] {certRequest.shortName(), certRequest});
            acc.accept(new Object[] {certRequest.name(), certRequest});
        }).toList();
    }

    enum SignOption {
        EXPIRED_SIGNING_KEY_USER_NAME("--mac-signing-key-user-name", SigningBase.StandardCertificateRequest.CODESIGN_EXPIRED.spec(), true, false),
        EXPIRED_SIGNING_KEY_USER_NAME_PKG("--mac-signing-key-user-name", SigningBase.StandardCertificateRequest.PKG_EXPIRED.spec(), true, false),
        EXPIRED_SIGN_IDENTITY("--mac-signing-key-user-name", SigningBase.StandardCertificateRequest.CODESIGN_EXPIRED.spec(), false, false),
        EXPIRED_CODESIGN_SIGN_IDENTITY("--mac-app-image-sign-identity", SigningBase.StandardCertificateRequest.CODESIGN_EXPIRED.spec(), false, true),
        EXPIRED_PKG_SIGN_IDENTITY("--mac-installer-sign-identity", SigningBase.StandardCertificateRequest.PKG_EXPIRED.spec(), false, true),
        GOOD_SIGNING_KEY_USER_NAME("--mac-signing-key-user-name", SigningBase.StandardCertificateRequest.CODESIGN.spec(), true, false),
        GOOD_SIGNING_KEY_USER_NAME_PKG("--mac-signing-key-user-name", SigningBase.StandardCertificateRequest.PKG.spec(), true, false),
        GOOD_CODESIGN_SIGN_IDENTITY("--mac-app-image-sign-identity", SigningBase.StandardCertificateRequest.CODESIGN.spec(), false, true),
        GOOD_PKG_SIGN_IDENTITY("--mac-app-image-sign-identity", SigningBase.StandardCertificateRequest.PKG.spec(), false, true);

        SignOption(String option, MacSign.CertificateRequest cert, boolean shortName, boolean passThrough) {
            this.option = Objects.requireNonNull(option);
            this.cert = Objects.requireNonNull(cert);
            this.shortName = shortName;
            this.passThrough = passThrough;
        }

        boolean passThrough() {
            return passThrough;
        }

        boolean expired() {
            return cert.expired();
        }

        String identityName() {
            return cert.name();
        }

        List<String> args() {
            return List.of(option, shortName ? cert.shortName() : cert.name());
        }

        static JPackageCommand configureOutputValidation(JPackageCommand cmd, List<SignOption> options,
                Function<SignOption, CannedFormattedString> conv) {
            options.stream().filter(SignOption::passThrough)
                    .map(conv)
                    .map(CannedFormattedString::getValue)
                    .map(TKit::assertTextStream)
                    .map(TKit.TextStreamVerifier::negate)
                    .forEach(cmd::validateOutput);

            options.stream().filter(Predicate.not(SignOption::passThrough))
                    .map(conv)
                    .map(CannedFormattedString::getValue)
                    .map(TKit::assertTextStream)
                    .forEach(cmd::validateOutput);

            return cmd;
        }

        private final String option;
        private final MacSign.CertificateRequest cert;
        private final boolean shortName;
        private final boolean passThrough;
    }
}
