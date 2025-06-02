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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.MacDmgPackage;
import jdk.jpackage.internal.model.MacDmgPackageMixin;

final class MacDmgPackageBuilder {

    MacDmgPackageBuilder(MacPackageBuilder pkgBuilder) {
        this.pkgBuilder = Objects.requireNonNull(pkgBuilder);
    }

    MacDmgPackageBuilder dmgContent(List<Path> v) {
        dmgContent = v;
        return this;
    }

    MacDmgPackageBuilder icon(Path v) {
        icon = v;
        return this;
    }

    List<Path> validatedDmgContent() {
        return Optional.ofNullable(dmgContent).orElseGet(List::of);
    }

    MacDmgPackage create() throws ConfigException {
        final var superPkgBuilder = pkgBuilder.pkgBuilder();
        final var pkg = pkgBuilder.create();

        return MacDmgPackage.create(pkg, new MacDmgPackageMixin.Stub(
                Optional.ofNullable(icon).or((pkg.app())::icon),
                validatedDmgContent()));
    }

    private Path icon;
    private List<Path> dmgContent;
    private final MacPackageBuilder pkgBuilder;
}
