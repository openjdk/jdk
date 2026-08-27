/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.jpackage.test;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.BundlingEnvironment;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.util.function.ThrowingSupplier;

/**
 * jpackage type traits.
 */
public enum PackageType {

    WIN_MSI(".msi", OperatingSystem.WINDOWS, "bundle-type.win-msi"),
    WIN_EXE(".exe", OperatingSystem.WINDOWS, "bundle-type.win-exe"),
    LINUX_DEB(".deb", OperatingSystem.LINUX, "bundle-type.linux-deb"),
    LINUX_RPM(".rpm", OperatingSystem.LINUX, "bundle-type.linux-rpm"),
    MAC_DMG(".dmg", OperatingSystem.MACOS, "bundle-type.mac-dmg"),
    MAC_PKG(".pkg", OperatingSystem.MACOS, "bundle-type.mac-pkg"),
    IMAGE(OperatingSystem.current()),
    WIN_IMAGE(OperatingSystem.WINDOWS),
    LINUX_IMAGE(OperatingSystem.LINUX),
    MAC_IMAGE(OperatingSystem.MACOS),
    ;

    PackageType(OperatingSystem os) {
        this.os = Objects.requireNonNull(os);
        type = "app-image";
        suffix = null;
        supported = (os == OperatingSystem.current());
        enabled = supported;
        this.bundleTypeLabelKey = switch (os) {
            case WINDOWS -> "bundle-type.win-app";
            case LINUX -> "bundle-type.linux-app";
            case MACOS -> "bundle-type.mac-app";
            default -> throw new AssertionError();
        };
    }

    PackageType(String packageName, String bundleSuffix, OperatingSystem os, String bundleTypeLabelKey) {
        this.os = Objects.requireNonNull(os);
        type  = Objects.requireNonNull(packageName);
        suffix = Objects.requireNonNull(bundleSuffix);
        supported = isSupported(new BundlingOperationDescriptor(os, type));
        enabled = supported && !Inner.DISABLED_PACKAGERS.contains(getType());
        this.bundleTypeLabelKey = Objects.requireNonNull(bundleTypeLabelKey);

        if (suffix != null && enabled) {
            TKit.trace(String.format("Bundler %s enabled", getType()));
        }
    }

    PackageType(String bundleSuffix, OperatingSystem os, String bundleTypeLabelKey) {
        this(bundleSuffix.substring(1), bundleSuffix, os, bundleTypeLabelKey);
    }

    void applyTo(JPackageCommand cmd) {
        cmd.setArgumentValue("--type", getType());
    }

    String getSuffix() {
        return Optional.ofNullable(suffix).orElseThrow(UnsupportedOperationException::new);
    }

    public boolean isSupported() {
        return supported;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAppImage() {
        return type.equals(IMAGE.type);
    }

    public boolean isNative() {
        return !isAppImage();
    }

    public String getType() {
        return type;
    }

    public OperatingSystem os() {
        return os;
    }

    public CannedFormattedString bundleTypeLabel() {
        return JPackageStringBundle.MAIN.cannedFormattedString(bundleTypeLabelKey);
    }

    public static PackageType appImageForOS(OperatingSystem os) {
        Objects.requireNonNull(os);
        return Stream.of(LINUX_IMAGE, MAC_IMAGE, WIN_IMAGE).filter(type -> {
            return type.os() == os;
        }).findFirst().orElseThrow();
    }

    public static RuntimeException throwSkippedExceptionIfNativePackagingUnavailable() {
        if (NATIVE.stream().noneMatch(PackageType::isSupported)) {
            TKit.throwSkippedException("None of the native packagers supported in this environment");
        } else if (NATIVE.stream().noneMatch(PackageType::isEnabled)) {
            TKit.throwSkippedException("All native packagers supported in this environment are disabled");
        }
        return null;
    }

    private static boolean isSupported(BundlingOperationDescriptor op) {
        return Inner.BUNDLING_ENV.filter(bundlingEnv -> {
            try {
                return bundlingEnv.configurationErrors(op).isEmpty();
            } catch (NoSuchElementException ex) {
                return false;
            }
        }).isPresent();
    }

    private static Set<PackageType> orderedSet(PackageType... types) {
        return new LinkedHashSet<>(List.of(types));
    }

    private final OperatingSystem os;
    private final String type;
    private final String suffix;
    private final boolean enabled;
    private final boolean supported;
    private final String bundleTypeLabelKey;

    public static final Set<PackageType> LINUX = orderedSet(LINUX_DEB, LINUX_RPM);
    public static final Set<PackageType> WINDOWS = orderedSet(WIN_MSI, WIN_EXE);
    public static final Set<PackageType> MAC = orderedSet(MAC_DMG, MAC_PKG);
    public static final Set<PackageType> NATIVE = Stream.of(LINUX, WINDOWS, MAC)
            .flatMap(Collection::stream).collect(Collectors.toUnmodifiableSet());

    public static final Set<PackageType> ALL_LINUX = orderedSet(LINUX_IMAGE, LINUX_DEB, LINUX_RPM);
    public static final Set<PackageType> ALL_WINDOWS = orderedSet(WIN_IMAGE, WIN_MSI, WIN_EXE);
    public static final Set<PackageType> ALL_MAC = orderedSet(MAC_IMAGE, MAC_DMG, MAC_PKG);

    private static final class Inner {
        private static final Set<String> DISABLED_PACKAGERS = Optional.ofNullable(
                TKit.tokenizeConfigProperty("disabledPackagers")).orElse(
                TKit.isLinuxAPT() ? Set.of("rpm") : Collections.emptySet());

        private static final Optional<BundlingEnvironment> BUNDLING_ENV = ServiceLoader.load(
                ThrowingSupplier.toSupplier(() -> {
                    @SuppressWarnings("unchecked")
                    var reply = (Class<BundlingEnvironment>)Class.forName("jdk.jpackage.internal.cli.CliBundlingEnvironment");
                    return reply;
                }).get(),
                BundlingEnvironment.class.getClassLoader()
        ).findFirst();
    }
}
