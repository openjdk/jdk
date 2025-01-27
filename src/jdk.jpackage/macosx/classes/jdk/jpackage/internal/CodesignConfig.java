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
import java.util.Optional;
import jdk.jpackage.internal.model.SigningIdentifier;
import jdk.jpackage.internal.model.SigningConfig;
import jdk.jpackage.internal.util.PathUtils;


record CodesignConfig(Optional<SigningIdentifier> identifier,
        Optional<Path> entitlements, Optional<Path> keyChain) {

    static final class Builder {

        private Builder() {
        }

        CodesignConfig create() {
            return new CodesignConfig(Optional.ofNullable(identifier),
                    Optional.ofNullable(entitlements), Optional.ofNullable(keyChain));
        }

        Builder entitlements(Path v) {
            entitlements = v;
            return this;
        }

        Builder identifier(SigningIdentifier v) {
            identifier = v;
            return this;
        }

        Builder keyChain(Path v) {
            keyChain = v;
            return this;
        }

        Builder from(SigningConfig v) {
            return identifier(v.identifier().orElse(null))
                    .entitlements(v.entitlements().orElse(null))
                    .keyChain(v.keyChain().orElse(null));
        }

        Builder from(CodesignConfig v) {
            return identifier(v.identifier().orElse(null))
                    .entitlements(v.entitlements().orElse(null))
                    .keyChain(v.keyChain().orElse(null));
        }

        private SigningIdentifier identifier;
        private Path entitlements;
        private Path keyChain;
    }

    static Builder build() {
        return new Builder();
    }

    List<String> toCodesignArgs() {
        List<String> args = new ArrayList<>(List.of("-s", identifier.map(SigningIdentifier::name).orElse(ADHOC_SIGNING_IDENTIFIER), "-vvvv"));

        if (identifier.isPresent()) {
            args.addAll(List.of("--timestamp", "--options", "runtime"));
            identifier.flatMap(SigningIdentifier::prefix).ifPresent(identifierPrefix -> {
                args.addAll(List.of("--prefix", identifierPrefix));
            });
            keyChain.map(PathUtils::normalizedAbsolutePathString).ifPresent(theKeyChain -> args.addAll(List.of("--keychain", theKeyChain)));
            entitlements.map(PathUtils::normalizedAbsolutePathString).ifPresent(theEntitlements -> args.addAll(List.of("--entitlements", theEntitlements)));
        }

        return args;
    }

    static final String ADHOC_SIGNING_IDENTIFIER = "-";
}
