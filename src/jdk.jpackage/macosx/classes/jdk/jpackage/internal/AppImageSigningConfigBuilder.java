/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.AppImageSigningConfig;
import jdk.jpackage.internal.model.LauncherStartupInfo;

final class AppImageSigningConfigBuilder {

    AppImageSigningConfigBuilder(SigningIdentityBuilder signingIdentityBuilder) {
        this.signingIdentityBuilder = Objects.requireNonNull(signingIdentityBuilder);
    }

    AppImageSigningConfigBuilder entitlements(Path v) {
        entitlements = v;
        return this;
    }

    AppImageSigningConfigBuilder entitlementsResourceName(String v) {
        entitlementsResourceName = v;
        return this;
    }

    AppImageSigningConfigBuilder signingIdentifierPrefix(LauncherStartupInfo mainLauncherStartupInfo) {
        final var pkgName = mainLauncherStartupInfo.packageName();
        if (!pkgName.isEmpty()) {
            signingIdentifierPrefix(pkgName + ".");
        } else {
            signingIdentifierPrefix(mainLauncherStartupInfo.simpleClassName() + ".");
        }
        return this;
    }

    AppImageSigningConfigBuilder signingIdentifierPrefix(String v) {
        signingIdentifierPrefix = v;
        return this;
    }

    AppImageSigningConfig create() {

        var cfg = signingIdentityBuilder.create();

        var validatedEntitlements = validatedEntitlements();

        return new AppImageSigningConfig.Stub(
                Objects.requireNonNull(cfg.identity()),
                Objects.requireNonNull(signingIdentifierPrefix),
                validatedEntitlements,
                cfg.keychain().map(Keychain::name),
                Optional.ofNullable(entitlementsResourceName).orElse("entitlements.plist")
        );
    }

    private Optional<Path> validatedEntitlements() {
        return Optional.ofNullable(entitlements);
    }

    private final SigningIdentityBuilder signingIdentityBuilder;
    private Path entitlements;
    private String entitlementsResourceName;
    private String signingIdentifierPrefix;
}
