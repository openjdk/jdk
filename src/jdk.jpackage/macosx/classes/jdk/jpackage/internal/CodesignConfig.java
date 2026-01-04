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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.AppImageSigningConfig;
import jdk.jpackage.internal.model.SigningIdentity;
import jdk.jpackage.internal.util.PathUtils;


record CodesignConfig(Optional<SigningIdentity> identity, Optional<String> identifierPrefix,
        Optional<Path> entitlements, Optional<Keychain> keychain) {

    CodesignConfig {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(identifierPrefix);
        Objects.requireNonNull(entitlements);
        Objects.requireNonNull(keychain);

        if (identity.isPresent() != identifierPrefix.isPresent()) {
            throw new IllegalArgumentException(
                "Signing identity (" + identity + ") and identifier prefix (" +
                identifierPrefix + ") mismatch");
        }

        identifierPrefix.ifPresent(v -> {
            if (!v.endsWith(".")) {
                throw new IllegalArgumentException("Invalid identifier prefix");
            }
        });
    }

    static final class Builder {

        private Builder() {
        }

        CodesignConfig create() {
            return new CodesignConfig(Optional.ofNullable(identity), Optional.ofNullable(identifierPrefix),
                    Optional.ofNullable(entitlements), Optional.ofNullable(keychain));
        }

        Builder entitlements(Path v) {
            entitlements = v;
            return this;
        }

        Builder identity(SigningIdentity v) {
            identity = v;
            return this;
        }

        Builder identifierPrefix(String v) {
            identifierPrefix = v;
            return this;
        }

        Builder keychain(String v) {
            return keychain(Optional.ofNullable(v).map(Keychain::new).orElse(null));
        }

        Builder keychain(Keychain v) {
            keychain = v;
            return this;
        }

        Builder from(AppImageSigningConfig v) {
            return identity(v.identity())
                    .identifierPrefix(v.identifierPrefix())
                    .entitlements(v.entitlements().orElse(null))
                    .keychain(v.keychain().orElse(null));
        }

        Builder from(CodesignConfig v) {
            return identity(v.identity().orElse(null))
                    .identifierPrefix(v.identifierPrefix().orElse(null))
                    .entitlements(v.entitlements().orElse(null))
                    .keychain(v.keychain().orElse(null));
        }

        private SigningIdentity identity;
        private String identifierPrefix;
        private Path entitlements;
        private Keychain keychain;
    }

    static Builder build() {
        return new Builder();
    }

    List<String> toCodesignArgs() {
        List<String> args = new ArrayList<>(List.of("-s", identity.map(SigningIdentity::id).orElse(ADHOC_SIGNING_IDENTITY), "-vvvv"));

        if (identity.isPresent()) {
            args.addAll(List.of("--timestamp", "--options", "runtime"));
            identifierPrefix.ifPresent(v -> {
                args.addAll(List.of("--prefix", v));
            });
            keychain.map(Keychain::asCliArg).ifPresent(k -> args.addAll(List.of("--keychain", k)));
            entitlements.map(PathUtils::normalizedAbsolutePathString).ifPresent(e -> args.addAll(List.of("--entitlements", e)));
        }

        return args;
    }

    static final String ADHOC_SIGNING_IDENTITY = "-";
}
