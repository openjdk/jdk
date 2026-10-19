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
package jdk.jpackage.internal.cli;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.AppImageBundleType;
import jdk.jpackage.internal.model.BundleType;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.SetBuilder;


/**
 * Standard jpackage operations.
 */
public enum StandardBundlingOperation implements BundlingOperationOptionScope {
    CREATE_WIN_APP_IMAGE(AppImageBundleType.WIN_APP_IMAGE, "^(?!(linux-|mac-|win-exe-|win-msi-))", OperatingSystem.WINDOWS),
    CREATE_LINUX_APP_IMAGE(AppImageBundleType.LINUX_APP_IMAGE, "^(?!(win-|mac-|linux-rpm-|linux-deb-))", OperatingSystem.LINUX),
    CREATE_MAC_APP_IMAGE(AppImageBundleType.MAC_APP_IMAGE, "^(?!(linux-|win-|mac-dmg-|mac-pkg-))", OperatingSystem.MACOS),
    CREATE_WIN_EXE(StandardPackageType.WIN_EXE, "^(?!(linux-|mac-|win-msi-))", OperatingSystem.WINDOWS),
    CREATE_WIN_MSI(StandardPackageType.WIN_MSI, "^(?!(linux-|mac-|win-exe-))", OperatingSystem.WINDOWS),
    CREATE_LINUX_RPM(StandardPackageType.LINUX_RPM, "^(?!(win-|mac-|linux-deb-))", OperatingSystem.LINUX),
    CREATE_LINUX_DEB(StandardPackageType.LINUX_DEB, "^(?!(win-|mac-|linux-rpm-))", OperatingSystem.LINUX),
    CREATE_MAC_PKG(StandardPackageType.MAC_PKG, "^(?!(linux-|win-|mac-dmg-))", OperatingSystem.MACOS),
    CREATE_MAC_DMG(StandardPackageType.MAC_DMG, "^(?!(linux-|win-|mac-pkg-))", OperatingSystem.MACOS),
    SIGN_MAC_APP_IMAGE(AppImageBundleType.MAC_APP_IMAGE, OperatingSystem.MACOS, Verb.SIGN);

    /**
     * Supported values of the {@link BundlingOperationDescriptor#verb()} property.
     */
    private enum Verb {
        CREATE(BundlingOperationDescriptor.VERB_CREATE_BUNDLE),
        SIGN("sign"),
        ;

        Verb(String value) {
            this.value = Objects.requireNonNull(value);
        }

        String value() {
            return value;
        }

        boolean createBundle() {
            return this == CREATE;
        }

        private final String value;
    }

    StandardBundlingOperation(BundleType bundleType, String optionNameRegexp, OperatingSystem os, Verb descriptorVerb) {
        this.bundleType = Objects.requireNonNull(bundleType);
        optionNamePredicate = Pattern.compile(optionNameRegexp).asPredicate();
        this.os = Objects.requireNonNull(os);
        this.descriptorVerb = Objects.requireNonNull(descriptorVerb);
    }

    StandardBundlingOperation(BundleType bundleType, String optionNameRegexp, OperatingSystem os) {
        this(bundleType, optionNameRegexp, os, Verb.CREATE);
    }

    StandardBundlingOperation(BundleType bundleType, OperatingSystem os, Verb descriptorVerb) {
        this.bundleType = Objects.requireNonNull(bundleType);
        optionNamePredicate = v -> false;
        this.os = Objects.requireNonNull(os);
        this.descriptorVerb = Objects.requireNonNull(descriptorVerb);
    }

    public OperatingSystem os() {
        return os;
    }

    public String bundleTypeValue() {
        if (bundleType instanceof AppImageBundleType) {
            return "app-image";
        } else {
            return ((StandardPackageType)bundleType).suffix().substring(1);
        }
    }

    public BundleType bundleType() {
        return bundleType;
    }

    public PackageType packageType() {
        return (PackageType)bundleType();
    }

    /**
     * Returns {@code true} if this bundling operation will create a new bundle and
     * {@code false} otherwise.
     */
    public boolean isCreateBundle() {
        return descriptorVerb.createBundle();
    }

    @Override
    public BundlingOperationDescriptor descriptor() {
        return new BundlingOperationDescriptor(os(), bundleTypeValue(), descriptorVerb.value());
    }

    public static Optional<StandardBundlingOperation> valueOf(BundlingOperationDescriptor descriptor) {
        Objects.requireNonNull(descriptor);
        return Stream.of(values()).filter(op -> {
            return op.descriptor().equals(descriptor);
        }).findFirst();
    }

    public static Stream<StandardBundlingOperation> ofPlatform(OperatingSystem os) {
        return Stream.of(values()).filter(platform(os));
    }

    public static Predicate<StandardBundlingOperation> platform(OperatingSystem os) {
        Objects.requireNonNull(os);
        return op -> {
            return  op.os() == os;
        };
    }

    static Set<BundlingOperationOptionScope> fromOptionName(String optionName) {
        Objects.requireNonNull(optionName);
        return Stream.of(StandardBundlingOperation.values()).filter(v -> {
            return v.optionNamePredicate.test(optionName);
        }).collect(Collectors.toUnmodifiableSet());
    }

    static Stream<StandardBundlingOperation> narrow(Stream<OptionScope> scope) {
        return scope.filter(StandardBundlingOperation.class::isInstance).map(StandardBundlingOperation.class::cast);
    }

    static final Set<BundlingOperationOptionScope> WINDOWS_CREATE_BUNDLE = Set.of(
            CREATE_WIN_APP_IMAGE, CREATE_WIN_MSI, CREATE_WIN_EXE);

    static final Set<BundlingOperationOptionScope> LINUX_CREATE_BUNDLE = Set.of(
            CREATE_LINUX_APP_IMAGE, CREATE_LINUX_RPM, CREATE_LINUX_DEB);

    static final Set<BundlingOperationOptionScope> MACOS_CREATE_BUNDLE = Set.of(
            CREATE_MAC_APP_IMAGE, CREATE_MAC_DMG, CREATE_MAC_PKG);

    static final Set<BundlingOperationOptionScope> WINDOWS_CREATE_NATIVE = Set.of(
            CREATE_WIN_MSI, CREATE_WIN_EXE);

    static final Set<BundlingOperationOptionScope> LINUX_CREATE_NATIVE = Set.of(
            CREATE_LINUX_RPM, CREATE_LINUX_DEB);

    static final Set<BundlingOperationOptionScope> MACOS_CREATE_NATIVE = Set.of(
            CREATE_MAC_DMG, CREATE_MAC_PKG);

    static final Set<BundlingOperationOptionScope> WINDOWS = WINDOWS_CREATE_BUNDLE;

    static final Set<BundlingOperationOptionScope> LINUX = LINUX_CREATE_BUNDLE;

    static final Set<BundlingOperationOptionScope> MACOS = SetBuilder.<BundlingOperationOptionScope>build(
            ).add(MACOS_CREATE_BUNDLE).add(SIGN_MAC_APP_IMAGE).create();

    static final Set<BundlingOperationOptionScope> MACOS_APP_IMAGE = Set.of(
            SIGN_MAC_APP_IMAGE, CREATE_MAC_APP_IMAGE);

    static final Set<BundlingOperationOptionScope> MAC_SIGNING = MACOS;

    static final Set<BundlingOperationOptionScope> CREATE_APP_IMAGE = Set.of(
            CREATE_WIN_APP_IMAGE, CREATE_LINUX_APP_IMAGE, CREATE_MAC_APP_IMAGE);

    static final Set<BundlingOperationOptionScope> CREATE_NATIVE = Set.of(
            CREATE_WIN_MSI, CREATE_WIN_EXE,
            CREATE_LINUX_RPM, CREATE_LINUX_DEB,
            CREATE_MAC_DMG, CREATE_MAC_PKG);

    static final Set<BundlingOperationOptionScope> CREATE_BUNDLE = Stream.of(
            CREATE_APP_IMAGE,
            CREATE_NATIVE
    ).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());

    private final Predicate<String> optionNamePredicate;
    private final OperatingSystem os;
    private final BundleType bundleType;
    private final Verb descriptorVerb;
}
