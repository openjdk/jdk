/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.MacHelper.SignKeyOption.Type.SIGN_KEY_IDENTITY;
import static jdk.jpackage.test.MacHelper.SignKeyOption.Type.SIGN_KEY_IDENTITY_APP_IMAGE;
import static jdk.jpackage.test.MacHelper.SignKeyOption.Type.SIGN_KEY_USER_FULL_NAME;
import static jdk.jpackage.test.MacHelper.SignKeyOption.Type.SIGN_KEY_USER_SHORT_NAME;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.FailedCommandErrorValidator;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacHelper.NamedCertificateRequestSupplier;
import jdk.jpackage.test.MacHelper.ResolvableCertificateRequest;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacHelper.SignKeyOptionWithKeychain;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSign.CertificateType;
import jdk.jpackage.test.MacSignVerify;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage with --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @compile -Xlint:all -Werror MacSignTest.java
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

        final var group = TKit.TextStreamVerifier.group();

        group.add(TKit.assertTextStream(JPackageStringBundle.MAIN.cannedFormattedString(
                "message.codesign.failed.reason.app.content").getValue()).predicate(String::equals));

        final var xcodeWarning = TKit.assertTextStream(JPackageStringBundle.MAIN.cannedFormattedString(
                "message.codesign.failed.reason.xcode.tools").getValue()).predicate(String::equals);

        if (!MacHelper.isXcodeDevToolsInstalled()) {
            group.add(xcodeWarning);
        }

        var keychain = SigningBase.StandardKeychain.MAIN.keychain();

        var signingKeyOption = new SignKeyOptionWithKeychain(
                SIGN_KEY_IDENTITY,
                SigningBase.StandardCertificateRequest.CODESIGN,
                keychain);

        new FailedCommandErrorValidator(Pattern.compile(String.format(
                "/usr/bin/codesign -s %s -vvvv --timestamp --options runtime --prefix \\S+ --keychain %s --entitlements \\S+ \\S+",
                Pattern.quote(String.format("'%s'", signingKeyOption.certRequest().name())),
                Pattern.quote(keychain.name())
        ))).exitCode(1).createGroup().mutate(group::add);

        MacSign.withKeychain(_ -> {

            // --app-content and --type app-image
            // Expect `message.codesign.failed.reason.app.content` message in the log.
            // This is not a fatal error, just a warning.
            // To make jpackage fail, specify bad additional content.
            JPackageCommand.helloAppImage()
                    .ignoreDefaultVerbose(true)
                    .validateOutput(group.create())
                    .addArguments("--app-content", appContent)
                    .mutate(signingKeyOption::addTo)
                    .mutate(cmd -> {
                        if (MacHelper.isXcodeDevToolsInstalled()) {
                            // Check there is no warning about missing xcode command line developer tools.
                            cmd.validateOutput(xcodeWarning.copy().negate());
                        }
                    }).execute(1);

        }, MacSign.Keychain.UsageBuilder::addToSearchList, keychain);
    }

    @Test
    public static void testCodesignUnspecificFailure() throws IOException {

        // This test expects jpackage to respond in a specific way on a codesign failure.
        // There are a few ways to make jpackage fail signing. One is using an erroneous
        // combination of a signing key and a keychain.

        var signingKeyOption = new SignKeyOption(
                SIGN_KEY_IDENTITY,
                SigningBase.StandardCertificateRequest.CODESIGN_ACME_TECH_LTD.certRequest(
                        SigningBase.StandardKeychain.MAIN.keychain()));

        MacSign.withKeychain(keychain -> {

            // Build a matcher for jpackage's failed command output.
            var errorValidator = new FailedCommandErrorValidator(Pattern.compile(String.format(
                    "/usr/bin/codesign -s %s -vvvv --timestamp --options runtime --prefix \\S+ --keychain %s",
                    Pattern.quote(String.format("'%s'", signingKeyOption.certRequest().name())),
                    Pattern.quote(keychain.name())
            ))).exitCode(1).output(String.format("%s: no identity found", signingKeyOption.certRequest().name())).createGroup();

            JPackageCommand.helloAppImage()
                    .setFakeRuntime()
                    .ignoreDefaultVerbose(true)
                    .validateOutput(errorValidator.create())
                    .mutate(signingKeyOption::addTo)
                    .mutate(MacHelper.useKeychain(keychain))
                    .execute(1);

        }, MacSign.Keychain.UsageBuilder::addToSearchList, SigningBase.StandardKeychain.DUPLICATE.keychain());
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

        MacSign.withKeychain(keychain -> {
            final var cmd = MacHelper.useKeychain(JPackageCommand.helloAppImage(), keychain)
                    .ignoreDefaultVerbose(true)
                    .addArguments(Stream.of(options).map(SignOption::args).flatMap(List::stream).toList())
                    .setPackageType(type);

            SignOption.configureOutputValidation(cmd, Stream.of(options).filter(SignOption::expired).toList(), opt -> {
                return JPackageStringBundle.MAIN.cannedFormattedString("error.certificate.expired", opt.identityName());
            }).execute(1);
        }, MacSign.Keychain.UsageBuilder::addToSearchList, SigningBase.StandardKeychain.EXPIRED.keychain());
    }

    @Test
    @Parameter({"IMAGE", "GOOD_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_DMG", "GOOD_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_PKG", "GOOD_SIGNING_KEY_USER_NAME_PKG", "GOOD_SIGNING_KEY_USER_NAME"})
    @Parameter({"IMAGE", "GOOD_CODESIGN_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "GOOD_CODESIGN_SIGN_IDENTITY", "GOOD_PKG_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "GOOD_PKG_SIGN_IDENTITY"})
    public static void testMultipleCertificates(PackageType type, SignOption... options) {

        MacSign.withKeychain(keychain -> {
            final var cmd = MacHelper.useKeychain(JPackageCommand.helloAppImage(), keychain)
                    .ignoreDefaultVerbose(true)
                    .addArguments(Stream.of(options).map(SignOption::args).flatMap(List::stream).toList())
                    .setPackageType(type);

            Predicate<SignOption> filter = opt -> {
                if (type == PackageType.MAC_PKG && options.length > 1) {
                    // Only the first error will be reported and it should always be
                    // for the app image signing, not for the PKG signing.
                    return opt.identityType() == CertificateType.CODE_SIGN;
                } else {
                    return true;
                }
            };

            SignOption.configureOutputValidation(cmd, Stream.of(options).filter(filter).toList(), opt -> {
                return JPackageStringBundle.MAIN.cannedFormattedString("error.multiple.certs.found", opt.identityName(), keychain.name());
            }).execute(1);
        }, MacSign.Keychain.UsageBuilder::addToSearchList, SigningBase.StandardKeychain.DUPLICATE.keychain());
    }

    @Test
    @ParameterSupplier
    @ParameterSupplier("testSelectSigningIdentity_JDK_8371094")
    public static void testSelectSigningIdentity(SignKeyOptionWithKeychain signKeyOption) {

        MacSign.withKeychain(keychain -> {
            final var cmd = JPackageCommand.helloAppImage().setFakeRuntime().mutate(signKeyOption::addTo);

            cmd.executeAndAssertImageCreated();

            MacSignVerify.verifyAppImageSigned(cmd, signKeyOption.certRequest());
        }, MacSign.Keychain.UsageBuilder::addToSearchList, SigningBase.StandardKeychain.MAIN.keychain());
    }

    public static Collection<Object[]> testSelectSigningIdentity() {
        var keychain = SigningBase.StandardKeychain.MAIN.keychain();
        return Stream.of(
                SigningBase.StandardCertificateRequest.CODESIGN,
                SigningBase.StandardCertificateRequest.CODESIGN_UNICODE
        ).map(certRequest -> {
            return Stream.of(
                    SIGN_KEY_USER_FULL_NAME,
                    SIGN_KEY_USER_SHORT_NAME
            ).map(type -> {
                return new SignKeyOptionWithKeychain(type, certRequest, keychain);
            });
        }).flatMap(x -> x).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    public static Collection<Object[]> testSelectSigningIdentity_JDK_8371094() {
        return List.<Object[]>of(new Object[] {
                new SignKeyOptionWithKeychain(
                        "ACME Technologies Limited",
                        SigningBase.StandardCertificateRequest.CODESIGN_ACME_TECH_LTD,
                        SigningBase.StandardKeychain.MAIN.keychain())
        });
    }

    enum SignOption {
        EXPIRED_SIGNING_KEY_USER_NAME(SIGN_KEY_USER_SHORT_NAME, SigningBase.StandardCertificateRequest.CODESIGN_EXPIRED),
        EXPIRED_SIGNING_KEY_USER_NAME_PKG(SIGN_KEY_USER_SHORT_NAME, SigningBase.StandardCertificateRequest.PKG_EXPIRED),
        EXPIRED_SIGN_IDENTITY(SIGN_KEY_USER_FULL_NAME, SigningBase.StandardCertificateRequest.CODESIGN_EXPIRED),
        EXPIRED_CODESIGN_SIGN_IDENTITY(SIGN_KEY_IDENTITY, SigningBase.StandardCertificateRequest.CODESIGN_EXPIRED),
        EXPIRED_PKG_SIGN_IDENTITY(SIGN_KEY_IDENTITY, SigningBase.StandardCertificateRequest.PKG_EXPIRED),
        GOOD_SIGNING_KEY_USER_NAME(SIGN_KEY_USER_SHORT_NAME, SigningBase.StandardCertificateRequest.CODESIGN),
        GOOD_SIGNING_KEY_USER_NAME_PKG(SIGN_KEY_USER_SHORT_NAME, SigningBase.StandardCertificateRequest.PKG),
        GOOD_CODESIGN_SIGN_IDENTITY(SIGN_KEY_IDENTITY, SigningBase.StandardCertificateRequest.CODESIGN),
        GOOD_PKG_SIGN_IDENTITY(SIGN_KEY_IDENTITY_APP_IMAGE, SigningBase.StandardCertificateRequest.PKG);

        SignOption(SignKeyOption.Type optionType, NamedCertificateRequestSupplier certRequestSupplier) {
            this.option = new SignKeyOption(optionType, new ResolvableCertificateRequest(certRequestSupplier.certRequest(), _ -> {
                throw new UnsupportedOperationException();
            }, certRequestSupplier.name()));
        }

        boolean passThrough() {
            return option.type().mapOptionName(option.certRequest().type()).orElseThrow().passThrough();
        }

        boolean expired() {
            return option.certRequest().expired();
        }

        String identityName() {
            return option.certRequest().name();
        }

        CertificateType identityType() {
            return option.certRequest().type();
        }

        List<String> args() {
            return option.asCmdlineArgs();
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

        private final SignKeyOption option;
    }
}
