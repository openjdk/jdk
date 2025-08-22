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
package jdk.jpackage.test;

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static jdk.jpackage.test.MacSign.DigestAlgorithm.SHA256;

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.test.MacSign.CertificateHash;
import jdk.jpackage.test.MacSign.CertificateRequest;

/**
 * Utilities to verify sign signatures.
 */
public final class MacSignVerify {

    public static void assertSigned(Path path, CertificateRequest certRequest) {
        assertSigned(path);
        TKit.assertEquals(certRequest.name(), findCodesignSignOrigin(path).orElse(null),
                String.format("Check [%s] signed with certificate", path));
    }

    public static void assertAdhocSigned(Path path) {
        assertSigned(path);
        TKit.assertEquals(ADHOC_SIGN_ORIGIN, findCodesignSignOrigin(path).orElse(null),
                String.format("Check [%s] signed with adhoc signature", path));
    }

    public static void assertUnsigned(Path path) {
        TKit.assertTrue(findSpctlSignOrigin(SpctlType.EXEC, path).isEmpty(),
                String.format("Check [%s] unsigned", path));
    }

    public static void assertPkgSigned(Path path, CertificateRequest certRequest, X509Certificate cert) {
        final var expectedCertChain = List.of(new SignIdentity(certRequest.name(), CertificateHash.of(cert, SHA256)));
        final var actualCertChain = getPkgCertificateChain(path);
        TKit.assertStringListEquals(
                expectedCertChain.stream().map(SignIdentity::toString).toList(),
                actualCertChain.stream().map(SignIdentity::toString).toList(),
                String.format("Check certificate chain of [%s] is as expected", path));
        TKit.assertEquals(certRequest.name(), findSpctlSignOrigin(SpctlType.INSTALL, path).orElse(null),
                String.format("Check [%s] signed for installation", path));
    }

    public enum SpctlType {
        EXEC("exec"), INSTALL("install");

        SpctlType(String value) {
            this.value = Objects.requireNonNull(value);
        }

        public String value() {
            return value;
        }

        private final String value;
    }

    public static final String ADHOC_SIGN_ORIGIN = "-";

    public static Optional<String> findSpctlSignOrigin(SpctlType type, Path path) {
        final var exec = Executor.of("/usr/sbin/spctl", "-vv", "--raw", "--assess", "--type", type.value(), path.toString()).saveOutput().discardStderr();
        final var result = exec.executeWithoutExitCodeCheck();
        TKit.assertTrue(Set.of(0, 3).contains(result.exitCode()),
                String.format("Check exit code of command %s is either 0 or 3", exec.getPrintableCommandLine()));
        return toSupplier(() -> {
            try {
                return Optional.of(new PListReader(String.join("", result.getOutput()).getBytes()).queryValue("assessment:originator"));
            } catch (NoSuchElementException ex) {
                return Optional.<String>empty();
            }
        }).get();
    }

    public static Optional<String> findCodesignSignOrigin(Path path) {
        final var exec = Executor.of("/usr/bin/codesign", "--display", "--verbose=4", path.toString()).saveOutput();
        final var result = exec.executeWithoutExitCodeCheck();
        if (result.getExitCode() == 0) {
            return Optional.of(result.getOutput().stream().map(line -> {
                if (line.equals("Signature=adhoc")) {
                    return ADHOC_SIGN_ORIGIN;
                } else if (line.startsWith("Authority=")) {
                    return line.substring("Authority=".length());
                } else {
                    return null;
                }
            }).filter(Objects::nonNull).reduce((x, y) -> {
                throw new UnsupportedOperationException(String.format(
                        "Both adhoc [%s] and certificate [%s] signatures found in codesign output", x, y));
            }).orElseThrow(() -> {
                final var msg = "Neither adhoc nor certificate signatures found in codesign output";
                TKit.trace(msg + ":");
                result.getOutput().forEach(TKit::trace);
                TKit.trace("Done");
                return new UnsupportedOperationException(msg);
            }));
        } else if (result.getExitCode() == 1 && result.getFirstLineOfOutput().endsWith("code object is not signed at all")) {
            return Optional.empty();
        } else {
            reportUnexpectedCommandOutcome(exec.getPrintableCommandLine(), result);
            return null; // Unreachable
        }
    }

    public static void assertSigned(Path path) {
        final var verifier = TKit.TextStreamVerifier.group()
                .add(TKit.assertTextStream(": valid on disk").predicate(String::endsWith))
                .add(TKit.assertTextStream(": satisfies its Designated Requirement").predicate(String::endsWith))
                .create();
        verifier.accept(Executor.of("/usr/bin/codesign", "--verify", "--deep",
                "--strict", "--verbose=2", path.toString()).executeAndGetOutput().iterator());
    }

    public static List<SignIdentity> getPkgCertificateChain(Path path) {
        //
        // Typical output of `/usr/sbin/pkgutil --check-signature`:
        // Package "foo.pkg":
        //    Status: signed by a developer certificate issued by Apple for distribution
        //    Notarization: trusted by the Apple notary service
        //    Signed with a trusted timestamp on: 2022-05-10 19:54:56 +0000
        //    Certificate Chain:
        //      1. Developer ID Installer: Foo
        //         SHA256 Fingerprint:
        //             4A A9 4A 85 20 2A DE 02 B2 9B 36 DA 45 00 B4 40 CF 31 43 4E 96 02
        //             60 6A 6D BC 02 F4 5D 3A 86 4A
        //         ------------------------------------------------------------------------
        //      2. Developer ID Certification Authority
        //         Expires: 2027-02-01 22:12:15 +0000
        //         SHA256 Fingerprint:
        //             7A FC 9D 01 A6 2F 03 A2 DE 96 37 93 6D 4A FE 68 09 0D 2D E1 8D 03
        //             F2 9C 88 CF B0 B1 BA 63 58 7F
        //         ------------------------------------------------------------------------
        //      3. Apple Root CA
        //         Expires: 2035-02-09 21:40:36 +0000
        //         SHA256 Fingerprint:
        //             B0 B1 73 0E CB C7 FF 45 05 14 2C 49 F1 29 5E 6E DA 6B CA ED 7E 2C
        //             68 C5 BE 91 B5 A1 10 01 F0 24
        final var exec = Executor.of("/usr/sbin/pkgutil", "--check-signature", path.toString()).saveOutput();
        final var result = exec.executeWithoutExitCodeCheck();
        if (result.getExitCode() == 0) {
            try {
                final List<SignIdentity> signIdentities = new ArrayList<>();
                final var lineIt = result.getOutput().iterator();
                while (!lineIt.next().endsWith("Certificate Chain:")) {

                }
                do {
                    final var m = SIGN_IDENTITY_NAME_REGEXP.matcher(lineIt.next());
                    m.find();
                    final var name = m.group(1);
                    while (!lineIt.next().endsWith("SHA256 Fingerprint:")) {

                    }
                    final var digest = new StringBuilder();
                    do {
                        final var line = lineIt.next().strip();
                        if (line.endsWith("----") || line.isEmpty()) {
                            break;
                        }
                        digest.append(" " + line.strip());
                    } while (lineIt.hasNext());
                    final var fingerprint = new CertificateHash(
                            FINGERPRINT_FORMAT.parseHex(digest.substring(1)), SHA256);
                    signIdentities.add(new SignIdentity(name, fingerprint));
                } while (lineIt.hasNext());
                return signIdentities;
            } catch (Throwable t) {
                t.printStackTrace();
                reportUnexpectedCommandOutcome(exec.getPrintableCommandLine(), result);
                return null; // Unreachable
            }
        } else if (result.getExitCode() == 1 && result.getOutput().getLast().endsWith("Status: no signature")) {
            return List.of();
        } else {
            reportUnexpectedCommandOutcome(exec.getPrintableCommandLine(), result);
            return null; // Unreachable
        }
    }

    public record SignIdentity(String name, CertificateHash fingerprint) {
        public SignIdentity {
            Objects.requireNonNull(name);
            Objects.requireNonNull(fingerprint);
        }
    }

    private static void reportUnexpectedCommandOutcome(String printableCommandLine, Executor.Result result) {
        Objects.requireNonNull(printableCommandLine);
        Objects.requireNonNull(result);
        TKit.trace(String.format("Command %s exited with exit code %d and the following output:",
                printableCommandLine, result.getExitCode()));
        result.getOutput().forEach(TKit::trace);
        TKit.trace("Done");
        TKit.assertUnexpected(String.format("Outcome of command %s", printableCommandLine));
    }

    private static final Pattern SIGN_IDENTITY_NAME_REGEXP = Pattern.compile("^\\s+\\d+\\.\\s+(.*)$");
    private static final HexFormat FINGERPRINT_FORMAT = HexFormat.ofDelimiter(" ").withUpperCase();
}
