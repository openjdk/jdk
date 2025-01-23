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

import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.internal.MacCertificateUtils.filterX509Certificates;
import static jdk.jpackage.internal.MacCertificateUtils.findCertificates;

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.model.SigningConfig;
import jdk.jpackage.internal.model.SigningIdentifier;

final class SigningConfigBuilder {

    SigningConfigBuilder signingIdentifier(String v) {
        signingIdentifier = v;
        return this;
    }

    SigningConfigBuilder signingIdentifierPrefix(LauncherStartupInfo mainLauncherStartupInfo) {
        final var pkgName = mainLauncherStartupInfo.packageName();
        if (!pkgName.isEmpty()) {
            signingIdentifierPrefix(pkgName + ".");
        }
        return this;
    }

    SigningConfigBuilder signingIdentifierPrefix(String v) {
        signingIdentifierPrefix = v;
        return this;
    }

    SigningConfigBuilder addCertificateNameFilters(String ...certificateNameFilters) {
        return addCertificateNameFilters(List.of(certificateNameFilters));
    }

    SigningConfigBuilder addCertificateNameFilters(Collection<String> certificateNameFilters) {
        this.certificateNameFilters.addAll(certificateNameFilters);
        return this;
    }

    SigningConfigBuilder keyChain(Path v) {
        keyChain = v;
        return this;
    }

    SigningConfigBuilder entitlements(Path v) {
        entitlements = v;
        return this;
    }

    SigningConfig create() throws ConfigException {
        return new SigningConfig.Stub(validatedSigningIdentifier(), validatedEntitlements(),
                validatedKeyChain(), "sandbox.plist");
    }

    private Optional<Path> validatedEntitlements() throws ConfigException {
        return Optional.ofNullable(entitlements);
    }

    private Optional<Path> validatedKeyChain() throws ConfigException {
        return Optional.ofNullable(keyChain);
    }

    private Optional<SigningIdentifier> validatedSigningIdentifier() throws ConfigException {
        if (signingIdentifier != null) {
            return Optional.of(new SigningIdentifierImpl(signingIdentifier, Optional.ofNullable(signingIdentifierPrefix)));
        }

        final var validatedKeyChain = validatedKeyChain();

        final var signingIdentifierMap = certificateNameFilters.stream().collect(toMap(x -> x, certificateNameFilter -> {
            return filterX509Certificates(findCertificates(validatedKeyChain, Optional.of(certificateNameFilter))).stream()
                    .map(SigningConfigBuilder::findSubjectCN)
                    .filter(Optional::isPresent)
                    .map(Optional::get).toList();
        }));

        for (var certificateNameFilter : certificateNameFilters) {
            final var signingIdentifiers = signingIdentifierMap.get(certificateNameFilter);
            switch (signingIdentifiers.size()) {
                case 0 -> {
                    break;
                }
                case 1 -> {
                    return Optional.of(new SigningIdentifierImpl(signingIdentifiers.get(0), Optional.ofNullable(signingIdentifierPrefix)));
                }
                default -> {
                    // Multiple certificates matching the same criteria found
                    // FIXME: warn the user?
                    break;
                }
            }
        }

        throw I18N.buildConfigException("error.cert.not.found", validatedKeyChain).create();
    }

    private static Optional<String> findSubjectCN(X509Certificate cert) {
        final LdapName ldapName;
        try {
            ldapName = new LdapName(cert.getSubjectX500Principal().getName(X500Principal.RFC2253));
        } catch (InvalidNameException e) {
            return Optional.empty();
        }

        return ldapName.getRdns().stream().filter(rdn -> {
            return rdn.getType().equalsIgnoreCase("CN");
        }).map(Rdn::getValue).map(Object::toString).findFirst();
    }

    enum CertificateNameFilter {
        APP_IMAGE("3rd Party Mac Developer Application: "),
        PKG_INSTALLER("3rd Party Mac Developer Installer: "),
        APP_STORE_APP_IMAGE("3rd Party Mac Developer Application: ", "Developer ID Application: "),
        APP_STORE_PKG_INSTALLER("3rd Party Mac Developer Installer: ", "Developer ID Installer: ");

        CertificateNameFilter(String ... prefixes) {
            filters = List.of(prefixes);
        }

        List<String> getFilters(Optional<String> certificateNameFilter) {
            if (certificateNameFilter.map(CertificateNameFilter::useAsIs).orElse(false)) {
                return List.of(certificateNameFilter.orElseThrow());
            } else  {
                return certificateNameFilter.map(filter -> {
                    return filters.stream().map(stdFilter -> {
                        return stdFilter + filter;
                    }).toList();
                }).orElse(filters);
            }
        }

        private static boolean useAsIs(String certificateNameFilter) {
            return Stream.of("3rd Party Mac", "Developer ID").noneMatch(certificateNameFilter::startsWith);
        }

        private final List<String> filters;
    }

    private String signingIdentifier;
    private String signingIdentifierPrefix;
    private List<String> certificateNameFilters = new ArrayList<>();
    private Path keyChain;
    private Path entitlements;
}
