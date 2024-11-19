/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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


record SigningConfig(String signingIdentity, String identifierPrefix, Path entitlements, String keyChain) {

    static final class Builder {

        private Builder(String signingIdentity) {
            this.signingIdentity = Objects.requireNonNull(signingIdentity);
        }

        SigningConfig create() {
            return new SigningConfig(signingIdentity, identifierPrefix, entitlements, keyChain);
        }

        Builder entitlements(Path v) {
            entitlements = v;
            return this;
        }

        Builder identifierPrefix(String v) {
            identifierPrefix = v;
            return this;
        }

        Builder keyChain(String v) {
            keyChain = v;
            return this;
        }

        private final String signingIdentity;
        private String identifierPrefix;
        private Path entitlements;
        private String keyChain;
    }

    static Builder build(String signingIdentity) {
        return new Builder(signingIdentity);
    }

    static Builder build(SigningConfig signingCfg) {
        return new Builder(signingCfg.signingIdentity)
                .entitlements(signingCfg.entitlements)
                .identifierPrefix(signingCfg.identifierPrefix)
                .keyChain(signingCfg.keyChain);
    }

    SigningConfig {
        Objects.requireNonNull(signingIdentity);
    }

    List<String> toCodesignArgs() {
        List<String> args = new ArrayList<>(List.of("-s", signingIdentity, "-vvvv"));

        if (!signingIdentity.equals("-")) {
            args.addAll(List.of("--timestamp", "--options", "runtime",
                    "--prefix", identifierPrefix));
            if (keyChain != null && !keyChain.isEmpty()) {
                args.addAll(List.of("--keychain", keyChain));
            }

            if (entitlements != null) {
                args.addAll(List.of("--entitlements",
                        entitlements.toString()));
            }
        }

        return args;
    }
}
