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

import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.MacPkgPackage;
import jdk.jpackage.internal.model.MacPkgPackageMixin;
import jdk.jpackage.internal.model.PkgSigningConfig;

final class MacPkgPackageBuilder {

    MacPkgPackageBuilder(MacPackageBuilder pkgBuilder) {
        this.pkgBuilder = Objects.requireNonNull(pkgBuilder);
    }

    MacPkgPackageBuilder signingBuilder(SigningIdentityBuilder v) {
        signingBuilder = v;
        return this;
    }

    MacPkgPackage create() {
        var pkg = MacPkgPackage.create(pkgBuilder.create(), new MacPkgPackageMixin.Stub(createSigningConfig()));
        validatePredefinedAppImage(pkg);
        return pkg;
    }

    private Optional<PkgSigningConfig> createSigningConfig() {
        return Optional.ofNullable(signingBuilder).map(SigningIdentityBuilder::create).map(cfg -> {
            return new PkgSigningConfig.Stub(cfg.identity(), cfg.keychain().map(Keychain::name));
        });
    }

    private static void validatePredefinedAppImage(MacPkgPackage pkg) {
        if (!pkg.predefinedAppImageSigned().orElse(false) && pkg.sign()) {
            pkg.predefinedAppImage().ifPresent(predefinedAppImage -> {
                Log.info(I18N.format("warning.unsigned.app.image", "pkg"));
            });
        }
    }

    private final MacPackageBuilder pkgBuilder;
    private SigningIdentityBuilder signingBuilder;
}
