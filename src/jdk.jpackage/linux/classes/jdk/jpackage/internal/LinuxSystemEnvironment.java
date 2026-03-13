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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.CompositeProxy;
import jdk.jpackage.internal.util.Result;

interface LinuxSystemEnvironment extends SystemEnvironment {
    boolean soLookupAvailable();
    PackageType nativePackageType();
    LinuxPackageArch packageArch();

    static Result<LinuxSystemEnvironment> create() {
        return detectNativePackageType().map(LinuxSystemEnvironment::create).orElseGet(() -> {
            return Result.ofError(new RuntimeException("Unknown native package type"));
        });
    }

    static Optional<StandardPackageType> detectNativePackageType() {
        if (Internal.isDebian()) {
            return Optional.of(StandardPackageType.LINUX_DEB);
        } else if (Internal.isRpm()) {
            return Optional.of(StandardPackageType.LINUX_RPM);
        } else {
            return Optional.empty();
        }
    }

    static Result<LinuxSystemEnvironment> create(StandardPackageType nativePackageType) {
        return LinuxPackageArch.create(nativePackageType).map(arch -> {
            return new Stub(LibProvidersLookup.supported(), nativePackageType, arch);
        });
    }

    static <T, U extends LinuxSystemEnvironment> U createWithMixin(Class<U> type, LinuxSystemEnvironment base, T mixin) {
        return CompositeProxy.build().invokeTunnel(CompositeProxyTunnel.INSTANCE).create(type, base, mixin);
    }

    static <T, U extends LinuxSystemEnvironment> Result<U> mixin(Class<U> type,
            Result<LinuxSystemEnvironment> base, Supplier<Result<T>> mixinResultSupplier) {
        final var mixin = mixinResultSupplier.get();

        final List<Exception> errors = new ArrayList<>();
        errors.addAll(base.errors());
        errors.addAll(mixin.errors());

        if (errors.isEmpty()) {
            return Result.ofValue(createWithMixin(type, base.orElseThrow(), mixin.orElseThrow()));
        } else {
            return Result.ofErrors(errors);
        }
    }

    record Stub(boolean soLookupAvailable, PackageType nativePackageType, LinuxPackageArch packageArch) implements LinuxSystemEnvironment {
    }

    static final class Internal {

        private static boolean isDebian() {
            // we are just going to run "dpkg -s coreutils" and assume Debian
            // or derivative if no error is returned.
            try {
                Executor.of("dpkg", "-s", "coreutils").executeExpectSuccess();
                return true;
            } catch (IOException e) {
                // just fall thru
                return false;
            }
        }

        private static boolean isRpm() {
            // we are just going to run "rpm -q rpm" and assume RPM
            // or derivative if no error is returned.
            try {
                Executor.of("rpm", "-q", "rpm").executeExpectSuccess();
                return true;
            } catch (IOException e) {
                // just fall thru
                return false;
            }
        }
    }
}
