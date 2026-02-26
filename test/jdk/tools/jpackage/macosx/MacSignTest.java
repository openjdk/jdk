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
import static jdk.jpackage.test.MacHelper.SignKeyOption.Type.SIGN_KEY_USER_FULL_NAME;
import static jdk.jpackage.test.MacHelper.SignKeyOption.Type.SIGN_KEY_USER_SHORT_NAME;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.FailedCommandErrorValidator;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageOutputValidator;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacHelper.NamedCertificateRequestSupplier;
import jdk.jpackage.test.MacHelper.ResolvableCertificateRequest;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacHelper.SignKeyOptionWithKeychain;
import jdk.jpackage.test.MacSign;
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

        final var validator = new JPackageOutputValidator().stderr();

        validator.expectMatchingStrings(JPackageStringBundle.MAIN.cannedFormattedString(
                "message.codesign.failed.reason.app.content"));

        final var xcodeWarning = TKit.assertTextStream(JPackageStringBundle.MAIN.cannedFormattedString(
                "message.codesign.failed.reason.xcode.tools").getValue()).predicate(String::equals);

        if (!MacHelper.isXcodeDevToolsInstalled()) {
            validator.add(xcodeWarning);
        }

        var keychain = SigningBase.StandardKeychain.MAIN.keychain();

        var signingKeyOption = new SignKeyOptionWithKeychain(
                SIGN_KEY_IDENTITY,
                SigningBase.StandardCertificateRequest.CODESIGN,
                keychain);

        validator.add(buildSignCommandErrorValidator(signingKeyOption).create());

        MacSign.withKeychain(_ -> {

            // --app-content and --type app-image
            // Expect `message.codesign.failed.reason.app.content` message in the log.
            // This is not a fatal error, just a warning.
            // To make jpackage fail, specify bad additional content.
            JPackageCommand.helloAppImage()
                    .mutate(MacSignTest::init)
                    .mutate(validator::applyTo)
                    .addArguments("--app-content", appContent)
                    .mutate(signingKeyOption::addTo)
                    .mutate(cmd -> {
                        if (MacHelper.isXcodeDevToolsInstalled()) {
                            // Check there is no warning about missing xcode command line developer tools.
                            cmd.validateErr(xcodeWarning.copy().negate());
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
            var errorValidator = buildSignCommandErrorValidator(signingKeyOption, keychain)
                    .output(String.format("%s: no identity found", signingKeyOption.certRequest().name()))
                    .create();

            JPackageCommand.helloAppImage()
                    .mutate(MacSignTest::init)
                    .mutate(errorValidator::applyTo)
                    .mutate(signingKeyOption::addTo)
                    .mutate(MacHelper.useKeychain(keychain))
                    .execute(1);

        }, MacSign.Keychain.UsageBuilder::addToSearchList, SigningBase.StandardKeychain.DUPLICATE.keychain());
    }

    @Test
    @Parameter({"IMAGE", "EXPIRED_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_DMG", "EXPIRED_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_PKG", "EXPIRED_SIGNING_KEY_USER_NAME", "EXPIRED_SIGNING_KEY_USER_NAME_PKG"})
    @Parameter({"MAC_PKG", "EXPIRED_SIGNING_KEY_USER_NAME_PKG", "EXPIRED_SIGNING_KEY_USER_NAME"})

    @Parameter({"IMAGE", "EXPIRED_SIGN_IDENTITY"})
    @Parameter({"MAC_DMG", "EXPIRED_SIGN_IDENTITY"})

    // Test that jpackage doesn't print duplicated error messages.
    @Parameter({"MAC_PKG", "EXPIRED_SIGN_IDENTITY"})

    @Parameter({"IMAGE", "EXPIRED_CODESIGN_SIGN_IDENTITY"})
    @Parameter({"MAC_DMG", "EXPIRED_CODESIGN_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "EXPIRED_CODESIGN_SIGN_IDENTITY"})

    @Parameter({"MAC_PKG", "GOOD_CODESIGN_SIGN_IDENTITY", "EXPIRED_PKG_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "EXPIRED_CODESIGN_SIGN_IDENTITY", "GOOD_PKG_SIGN_IDENTITY"})
    public static void testExpiredCertificate(PackageType type, SignOption... optionIds) {

        var options = Stream.of(optionIds).map(SignOption::option).toList();

        var badOptions = options.stream().filter(option -> {
            return option.certRequest().expired();
        }).toList();

        MacSign.withKeychain(keychain -> {

            final var cmd = MacHelper.useKeychain(JPackageCommand.helloAppImage(), keychain)
                    .mutate(MacSignTest::init)
                    .addArguments(options.stream().map(SignKeyOption::asCmdlineArgs).flatMap(List::stream).toList())
                    .setPackageType(type);

            configureOutputValidation(cmd, expectFailures(badOptions), opt -> {
                if (!opt.passThrough().orElse(false)) {
                    return expectConfigurationError("error.certificate.outside-validity-period", opt.certRequest().name());
                } else {
                    var builder = buildSignCommandErrorValidator(opt, keychain);
                    switch (opt.optionName().orElseThrow()) {
                        case KEY_IDENTITY_APP_IMAGE, KEY_USER_NAME -> {
                            builder.output(String.format("%s: no identity found", opt.certRequest().name()));
                        }
                        case KEY_IDENTITY_INSTALLER -> {
                            var regexp = Pattern.compile(String.format(
                                    "^productbuild: error: Cannot write product to \\S+ \\(Could not find appropriate signing identity for “%s” in keychain at “%s”\\.\\)",
                                    Pattern.quote(opt.certRequest().name()),
                                    Pattern.quote(keychain.name())
                            ));
                            builder.validators(TKit.assertTextStream(regexp));
                        }
                    }
                    return builder.create();
                }
            }).execute(1);
        }, MacSign.Keychain.UsageBuilder::addToSearchList, SigningBase.StandardKeychain.EXPIRED.keychain());
    }

    @Test
    @Parameter({"IMAGE", "GOOD_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_DMG", "GOOD_SIGNING_KEY_USER_NAME"})
    @Parameter({"MAC_PKG", "GOOD_SIGNING_KEY_USER_NAME", "GOOD_SIGNING_KEY_USER_NAME_PKG"})
    @Parameter({"MAC_PKG", "GOOD_SIGNING_KEY_USER_NAME_PKG", "GOOD_SIGNING_KEY_USER_NAME"})
    @Parameter({"IMAGE", "GOOD_CODESIGN_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "GOOD_CODESIGN_SIGN_IDENTITY", "GOOD_PKG_SIGN_IDENTITY"})
    @Parameter({"MAC_PKG", "GOOD_PKG_SIGN_IDENTITY"})
    public static void testMultipleCertificates(PackageType type, SignOption... optionIds) {

        var options = Stream.of(optionIds).map(SignOption::option).toList();

        MacSign.withKeychain(keychain -> {

            final var cmd = MacHelper.useKeychain(JPackageCommand.helloAppImage(), keychain)
                    .mutate(MacSignTest::init)
                    .addArguments(options.stream().map(SignKeyOption::asCmdlineArgs).flatMap(List::stream).toList())
                    .setPackageType(type);

            if (List.of(optionIds).equals(List.of(SignOption.GOOD_PKG_SIGN_IDENTITY))) {
                /**
                 * Normally, if multiple signing identities share the same name, signing should
                 * fail. However, in pass-through signing, when jpackage passes signing
                 * identities without validation to signing commands, signing a .pkg installer
                 * with a name matching two signing identities succeeds.
                 */
                new JPackageOutputValidator().stdout()
                        .expectMatchingStrings(JPackageStringBundle.MAIN.cannedFormattedString("warning.unsigned.app.image", "pkg"))
                        .validateEndOfStream()
                        .applyTo(cmd);

                cmd.execute();
                return;
            }

            configureOutputValidation(cmd, expectFailures(options), opt -> {
                if (!opt.passThrough().orElse(false)) {
                    return expectConfigurationError("error.multiple.certs.found", opt.certRequest().name(), keychain.name());
                } else {
                    var builder = buildSignCommandErrorValidator(opt, keychain);
                    switch (opt.optionName().orElseThrow()) {
                        case KEY_IDENTITY_APP_IMAGE, KEY_USER_NAME -> {
                            builder.output(String.format("%s: ambiguous", opt.certRequest().name()));
                        }
                        case KEY_IDENTITY_INSTALLER -> {
                            var regexp = Pattern.compile(String.format(
                                    "^productbuild: error: Cannot write product to \\S+ \\(Could not find appropriate signing identity for “%s” in keychain at “%s”\\.\\)",
                                    Pattern.quote(opt.certRequest().name()),
                                    Pattern.quote(keychain.name())
                            ));
                            builder.validators(TKit.assertTextStream(regexp));
                        }
                    }
                    return builder.create();
                }
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
        GOOD_PKG_SIGN_IDENTITY(SIGN_KEY_IDENTITY, SigningBase.StandardCertificateRequest.PKG);

        SignOption(SignKeyOption.Type optionType, NamedCertificateRequestSupplier certRequestSupplier) {
            this.option = new SignKeyOption(optionType, new ResolvableCertificateRequest(certRequestSupplier.certRequest(), _ -> {
                throw new UnsupportedOperationException();
            }, certRequestSupplier.name()));
        }

        SignKeyOption option() {
            return option;
        }

        private final SignKeyOption option;
    }

    private static JPackageCommand configureOutputValidation(JPackageCommand cmd, Stream<SignKeyOption> options,
            Function<SignKeyOption, JPackageOutputValidator> conv) {

        // Validate jpackage's output matches expected output.

        var outputValidator = new JPackageOutputValidator().stderr();

        options.sorted(Comparator.comparing(option -> {
            return option.certRequest().type();
        })).map(conv).forEach(outputValidator::add);

        outputValidator.validateEndOfStream().applyTo(cmd);

        return cmd;
    }

    private static Stream<SignKeyOption> expectFailures(Collection<SignKeyOption> options) {

        if (!options.stream().map(option -> {
            return option.passThrough().orElse(false);
        }).distinct().reduce((x, y) -> {
            throw new IllegalArgumentException();
        }).get()) {
            // No pass-through signing options.
            // This means jpackage will validate them at the configuration phase and report all detected errors.
            return options.stream();
        }

        // Pass-through signing options.
        // jpackage will not validate them and will report only one error at the packaging phase.

        Function<MacSign.CertificateType, Predicate<SignKeyOption>> filterType = type -> {
            return option -> {
                return option.certRequest().type() == type;
            };
        };

        var appImageSignOption = options.stream()
                .filter(filterType.apply(MacSign.CertificateType.CODE_SIGN)).findFirst();
        var pkgSignOption = options.stream()
                .filter(filterType.apply(MacSign.CertificateType.INSTALLER)).findFirst();

        if (appImageSignOption.isPresent() && pkgSignOption.isPresent()) {
            return options.stream().filter(option -> {
                return appImageSignOption.get() == option;
            });
        } else {
            return options.stream();
        }
    }

    private static JPackageOutputValidator expectConfigurationError(String key, Object ... args) {
        return new JPackageOutputValidator().expectMatchingStrings(JPackageCommand.makeError(key, args)).stderr();
    }

    private static FailedCommandErrorValidator buildSignCommandErrorValidator(SignKeyOptionWithKeychain option) {
        return buildSignCommandErrorValidator(option.signKeyOption(), option.keychain());
    }

    private static FailedCommandErrorValidator buildSignCommandErrorValidator(SignKeyOption option, MacSign.ResolvedKeychain keychain) {

        Objects.requireNonNull(option);
        Objects.requireNonNull(keychain);

        String cmdlinePatternFormat;
        String signIdentity;

        switch (option.optionName().orElseThrow()) {
            case KEY_IDENTITY_APP_IMAGE, KEY_USER_NAME -> {
                cmdlinePatternFormat = "^/usr/bin/codesign -s %s -vvvv --timestamp --options runtime --prefix \\S+ --keychain %s";
                if (option.passThrough().orElse(false)) {
                    signIdentity = String.format("'%s'", option.asCmdlineArgs().getLast());
                } else {
                    signIdentity = MacSign.CertificateHash.of(option.certRequest().cert()).toString();
                }
            }
            case KEY_IDENTITY_INSTALLER -> {
                cmdlinePatternFormat = "^/usr/bin/productbuild --resources \\S+ --sign %s --keychain %s";
                signIdentity = String.format("'%s'", option.asCmdlineArgs().getLast());
            }
            default -> {
                throw ExceptionBox.reachedUnreachable();
            }
        }

        return new FailedCommandErrorValidator(Pattern.compile(String.format(
                cmdlinePatternFormat,
                Pattern.quote(signIdentity),
                Pattern.quote(keychain.name())
        ))).exitCode(1);
    }

    private static void init(JPackageCommand cmd) {
        cmd.setFakeRuntime().ignoreDefaultVerbose(true);
    }
}
