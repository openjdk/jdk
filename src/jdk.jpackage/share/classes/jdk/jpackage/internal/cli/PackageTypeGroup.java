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
package jdk.jpackage.internal.cli;

import java.util.Set;
import java.util.function.Function;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.AppImagePackageType;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_RPM;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_DMG;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_PKG;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_EXE;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_MSI;

enum PackageTypeGroup {
    APP_IMAGE(name -> Set.of(AppImagePackageType.APP_IMAGE)),
    NATIVE_PACKAGE(name -> {
        if (name == null) {
            return Set.of(StandardPackageType.values());
        } else if (name.startsWith("win")) {
            return Set.of(WIN_EXE, WIN_MSI);
        } else if (name.startsWith("linux")) {
            return Set.of(LINUX_DEB, LINUX_RPM);
        } else if (name.startsWith("mac")) {
            return Set.of(MAC_DMG, MAC_PKG);
        } else {
            throw new IllegalArgumentException("Unsupported name");
        }
    }),
    ALL_PACKAGE_TYPES(name -> {
        return Stream.of(
                APP_IMAGE.forOptionName(name),
                NATIVE_PACKAGE.forOptionName(name)
        ).flatMap(Set::stream).collect(toSet());
    });

    PackageTypeGroup(Function<String, Set<PackageType>> conv) {
        this.conv = conv;
    }

    Set<PackageType> forOptionName(String name) {
        return conv.apply(name);
    }

    private final Function<String, Set<PackageType>> conv;
}
