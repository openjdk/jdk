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

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import jdk.jpackage.internal.MacCertificateUtils.CertificateHash;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.SigningIdentity;

final class SigningIdentityBuilder {

    static class SigningConfigException extends ConfigException {
        SigningConfigException(ConfigException ex) {
            super(ex.getMessage(), ex.getAdvice(), ex.getCause());
        }

        private static final long serialVersionUID = 1L;
    }

    static class ExpiredCertificateException extends SigningConfigException {
        ExpiredCertificateException(ConfigException ex) {
            super(ex);
        }

        private static final long serialVersionUID = 1L;
    }

    record SigningConfig(SigningIdentity identity, Optional<Keychain> keychain) {
        SigningConfig {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(keychain);
        }
    }

    SigningIdentityBuilder signingIdentity(String v) {
        signingIdentity = v;
        return this;
    }

    SigningIdentityBuilder certificateSelector(CertificateSelector v) {
        certificateSelector = v;
        return this;
    }

    SigningIdentityBuilder keychain(String v) {
        keychain = v;
        return this;
    }

    Optional<SigningConfig> create() throws ConfigException {
        if (signingIdentity == null && certificateSelector == null) {
            return Optional.empty();
        } else {
            return Optional.of(new SigningConfig(validatedSigningIdentity(), validatedKeychain()));
        }
    }

    private Optional<Keychain> validatedKeychain() throws ConfigException {
        return Optional.ofNullable(keychain).map(Keychain::new);
    }

    private SigningIdentity validatedSigningIdentity() throws ConfigException {
        if (signingIdentity != null) {
            return new SigningIdentityImpl(signingIdentity);
        }

        Objects.requireNonNull(certificateSelector);

        final var validatedKeychain = validatedKeychain();

        final var allCertificates = findCertificates(validatedKeychain, Optional.empty());

        final var mappedCertficates = allCertificates.stream().<Map.Entry<String, X509Certificate>>mapMulti((cert, acc) -> {
            findSubjectCNs(cert).stream().map(cn -> {
                return Map.entry(cn, cert);
            }).forEach(acc::accept);
        }).toList();

        final var signingIdentityNames = certificateSelector.signingIdentities();

        var matchingCertificates = mappedCertficates.stream().filter(e -> {
            return signingIdentityNames.contains(e.getKey());
        }).map(Map.Entry::getValue).toList();

        if (matchingCertificates.isEmpty()) {
            matchingCertificates = mappedCertficates.stream().filter(e -> {
                return signingIdentityNames.stream().anyMatch(filter -> {
                    return filter.startsWith(e.getKey());
                });
            }).map(Map.Entry::getValue).toList();
        }

        final var cert = selectSigningIdentity(matchingCertificates,
                certificateSelector, validatedKeychain);

        try {
            cert.checkValidity();
        } catch (CertificateExpiredException|CertificateNotYetValidException ex) {
            throw new ExpiredCertificateException(I18N.buildConfigException("error.certificate.expired", findSubjectCNs(cert).getFirst()).create());
        }

        final var signingIdentityHash = CertificateHash.of(cert);

        return new SigningIdentityImpl(signingIdentityHash.toString());
    }

    private static X509Certificate selectSigningIdentity(List<X509Certificate> certs,
            CertificateSelector certificateSelector, Optional<Keychain> keychain) throws ConfigException {
        Objects.requireNonNull(certificateSelector);
        Objects.requireNonNull(keychain);
        switch (certs.size()) {
            case 0 -> {
                Log.error(I18N.format("error.cert.not.found", certificateSelector.signingIdentities().getFirst(),
                        keychain.map(Keychain::name).orElse("")));
                throw I18N.buildConfigException("error.explicit-sign-no-cert")
                        .advice("error.explicit-sign-no-cert.advice").create();
            }
            case 1 -> {
                return certs.getFirst();
            }
            default -> {
                Log.error(I18N.format("error.multiple.certs.found", certificateSelector.signingIdentities().getFirst(),
                        keychain.map(Keychain::name).orElse("")));
                return certs.getFirst();
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
        }).map(Rdn::getValue).map(Object::toString).distinct().toList();
    }

    record CertificateSelector(String name, List<StandardCertificatePrefix> prefixes) {
        CertificateSelector {
            Objects.requireNonNull(prefixes);
            prefixes.forEach(Objects::requireNonNull);
            Objects.requireNonNull(name);
        }

        List<String> signingIdentities() {
            if (prefixes().isEmpty()) {
                return List.of(name);
            } else {
                return prefixes.stream().map(StandardCertificatePrefix::value).map(prefix -> {
                    return prefix + name;
                }).toList();
            }
        }
    }

    enum StandardCertificatePrefix {
        APP_SIGN_APP_STORE("3rd Party Mac Developer Application"),
        INSTALLER_SIGN_APP_STORE("3rd Party Mac Developer Installer"),
        APP_SIGN_PERSONAL("Developer ID Application"),
        INSTALLER_SIGN_PERSONAL("Developer ID Installer");

        StandardCertificatePrefix(String value) {
            this.value = value + ": ";
        }

        String value() {
            return value;
        }

        static Optional<StandardCertificatePrefix> findStandardCertificatePrefix(String certificateLocator) {
            Objects.requireNonNull(certificateLocator);
            return Stream.of(StandardCertificatePrefix.values()).filter(prefix -> {
                return certificateLocator.startsWith(prefix.value);
            }).reduce((x, y) -> {
                throw new UnsupportedOperationException();
            });
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

        static CertificateSelector create(String certificateLocator, StandardCertificateSelector defaultSelector) {
            return StandardCertificatePrefix.findStandardCertificatePrefix(certificateLocator).map(prefix -> {
                return new CertificateSelector(certificateLocator, List.of());
            }).orElseGet(() -> {
                return new CertificateSelector(certificateLocator, defaultSelector.prefixes);
            });
        }

        private final List<StandardCertificatePrefix> prefixes;
    }

    private String signingIdentity;
    private CertificateSelector certificateSelector;
    private String keychain;
}
