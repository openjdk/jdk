/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.MacCertificateUtils.findCertificates;

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import jdk.jpackage.internal.MacCertificateUtils.CertificateHash;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.model.SigningConfig;
import jdk.jpackage.internal.model.SigningIdentity;

final class SigningConfigBuilder {

    SigningConfigBuilder signingIdentity(String v) {
        signingIdentity = v;
        return this;
    }

    SigningConfigBuilder signingIdentityPrefix(LauncherStartupInfo mainLauncherStartupInfo) {
        final var pkgName = mainLauncherStartupInfo.packageName();
        if (!pkgName.isEmpty()) {
            signingIdentityPrefix(pkgName + ".");
        }
        return this;
    }

    SigningConfigBuilder signingIdentityPrefix(String v) {
        signingIdentityPrefix = v;
        return this;
    }

    SigningConfigBuilder addCertificateSelectors(CertificateSelector ...v) {
        return addCertificateSelectors(List.of(v));
    }

    SigningConfigBuilder addCertificateSelectors(Collection<CertificateSelector> v) {
        certificateSelectors.addAll(v);
        return this;
    }

    SigningConfigBuilder keychain(String v) {
        keychain = v;
        return this;
    }

    SigningConfigBuilder entitlements(Path v) {
        entitlements = v;
        return this;
    }

    SigningConfig create() throws ConfigException {
        return new SigningConfig.Stub(validatedSigningIdentity(), validatedEntitlements(),
                validatedKeychain().map(Keychain::name), "sandbox.plist");
    }

    private Optional<Path> validatedEntitlements() throws ConfigException {
        return Optional.ofNullable(entitlements);
    }

    private Optional<Keychain> validatedKeychain() throws ConfigException {
        return Optional.ofNullable(keychain).map(Keychain::new);
    }

    private Optional<SigningIdentity> validatedSigningIdentity() throws ConfigException {
        CertificateHash signingIdentityHash = null;
        if (signingIdentity != null) {
            try {
                signingIdentityHash = CertificateHash.fromHexString(signingIdentity);
            } catch (Throwable t) {
                // Not a valid certificate hash
            }
        }

        final var validatedKeychain = validatedKeychain();

        final var allCertificates = findCertificates(validatedKeychain, Optional.empty());

        if (signingIdentityHash != null) {
            if (allCertificates.stream().map(CertificateHash::of).anyMatch(Predicate.isEqual(signingIdentityHash))) {
                return Optional.of(new SigningIdentityImpl(signingIdentityHash.toString(),
                        Optional.ofNullable(signingIdentityPrefix)));
            } else {
                throw I18N.buildConfigException("error.cert.not.found", validatedKeychain.map(Keychain::name).orElse("")).create();
            }
        }

        final var mappedCertficates = allCertificates.stream().<Map.Entry<String, X509Certificate>>mapMulti((cert, acc) -> {
            findSubjectCNs(cert).stream().map(cn -> {
                return Map.entry(cn, cert);
            }).forEach(acc::accept);
        }).toList();

        final var resolvedCertificateSelectors = certificateSelectors.stream().map(CertificateSelector::fullName).toList();

        var matchingCertificates = mappedCertficates.stream().filter(e -> {
            return resolvedCertificateSelectors.contains(e.getKey());
        }).map(Map.Entry::getValue).toList();

        if (!matchingCertificates.isEmpty()) {
            signingIdentityHash = selectSigningIdentity(matchingCertificates, certificateSelectors, validatedKeychain);
        } else {
            matchingCertificates = mappedCertficates.stream().filter(e -> {
                return resolvedCertificateSelectors.stream().anyMatch(filter -> {
                    return filter.startsWith(e.getKey());
                });
            }).map(Map.Entry::getValue).toList();
            signingIdentityHash = selectSigningIdentity(matchingCertificates, certificateSelectors, validatedKeychain);
        }

        return Optional.of(new SigningIdentityImpl(signingIdentityHash.toString(),
                Optional.ofNullable(signingIdentityPrefix)));
    }

    private static CertificateHash selectSigningIdentity(List<X509Certificate> certs,
            List<CertificateSelector> certificateSelectors, Optional<Keychain> keychain) throws ConfigException {
        switch (certs.size()) {
            case 0 -> {
                throw I18N.buildConfigException("error.explicit-sign-no-cert")
                        .advice("error.explicit-sign-no-cert.advice").create();
            }
            case 1 -> {
                return CertificateHash.of(certs.getFirst());
            }
            default -> {
                Log.error(I18N.format("error.multiple.certs.found",
                        certificateSelectors.getFirst().team().orElse(""), keychain.map(Keychain::name).orElse("")));
                return CertificateHash.of(certs.getFirst());
            }
        }
    }

    private static List<String> findSubjectCNs(X509Certificate cert) {
        final LdapName ldapName;
        try {
            ldapName = new LdapName(cert.getSubjectX500Principal().getName(X500Principal.RFC2253));
        } catch (InvalidNameException e) {
            return List.of();
        }

        return ldapName.getRdns().stream().filter(rdn -> {
            return rdn.getType().equalsIgnoreCase("CN");
        }).map(Rdn::getValue).map(Object::toString).toList();
    }

    record CertificateSelector(StandardCertificatePrefix prefix, Optional<String> team) {
        CertificateSelector {
            Objects.requireNonNull(prefix);
            Objects.requireNonNull(team);
            team.ifPresent(v -> {
                if (v.isEmpty()) {
                    throw new IllegalArgumentException();
                }
            });
        }

        CertificateSelector(StandardCertificatePrefix prefix) {
            this(prefix, Optional.empty());
        }

        static Optional<CertificateSelector> createFromFullName(String fullName) {
            Objects.requireNonNull(fullName);
            return Stream.of(StandardCertificatePrefix.values()).map(CertificateSelector::new).filter(selector -> {
                return fullName.startsWith(selector.fullName());
            }).reduce((x, y) -> {
                throw new UnsupportedOperationException();
            }).map(selector -> {
                final var team = fullName.substring(selector.fullName().length());
                return new CertificateSelector(selector.prefix, team.isEmpty() ? Optional.empty() : Optional.of(team));
            });
        }

        String fullName() {
            final var sb = new StringBuilder();
            sb.append(prefix.value()).append(": ");
            team.ifPresent(sb::append);
            return sb.toString();
        }
    }

    enum StandardCertificatePrefix {
        APP_SIGN_APP_STORE("3rd Party Mac Developer Application"),
        INSTALLER_SIGN_APP_STORE("3rd Party Mac Developer Installer"),
        APP_SIGN_PERSONAL("Developer ID Application"),
        INSTALLER_SIGN_PERSONAL("Developer ID Installer");

        StandardCertificatePrefix(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        private final String value;
    }

    enum StandardCertificateSelector {
        APP_IMAGE(StandardCertificatePrefix.APP_SIGN_PERSONAL),
        PKG_INSTALLER(StandardCertificatePrefix.INSTALLER_SIGN_PERSONAL),
        APP_STORE_APP_IMAGE(StandardCertificatePrefix.APP_SIGN_PERSONAL, StandardCertificatePrefix.APP_SIGN_APP_STORE),
        APP_STORE_PKG_INSTALLER(StandardCertificatePrefix.INSTALLER_SIGN_PERSONAL, StandardCertificatePrefix.INSTALLER_SIGN_APP_STORE);

        StandardCertificateSelector(StandardCertificatePrefix ... prefixes) {
            this.prefixes = List.of(prefixes);
        }

        static List<CertificateSelector> create(Optional<String> certificateName, StandardCertificateSelector defaultSelector) {
            return certificateName.flatMap(CertificateSelector::createFromFullName).map(List::of).orElseGet(() -> {
                return defaultSelector.prefixes.stream().map(prefix -> {
                    return new CertificateSelector(prefix, certificateName);
                }).toList();
            });
        }

        private final List<StandardCertificatePrefix> prefixes;
    }

    private String signingIdentity;
    private String signingIdentityPrefix;
    private List<CertificateSelector> certificateSelectors = new ArrayList<>();
    private String keychain;
    private Path entitlements;
}
