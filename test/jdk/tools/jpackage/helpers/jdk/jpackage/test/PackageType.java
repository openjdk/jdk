/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.Log;

/**
 * jpackage type traits.
 */
public enum PackageType {
    WIN_MSI(".msi",
            TKit.isWindows() ? "jdk.jpackage.internal.WinMsiBundler" : null),
    WIN_EXE(".exe",
            TKit.isWindows() ? "jdk.jpackage.internal.WinMsiBundler" : null),
    LINUX_DEB(".deb",
            TKit.isLinux() ? "jdk.jpackage.internal.LinuxDebBundler" : null),
    LINUX_RPM(".rpm",
            TKit.isLinux() ? "jdk.jpackage.internal.LinuxRpmBundler" : null),
    MAC_DMG(".dmg", TKit.isOSX() ? "jdk.jpackage.internal.MacDmgBundler" : null),
    MAC_PKG(".pkg", TKit.isOSX() ? "jdk.jpackage.internal.MacPkgBundler" : null),
    IMAGE;

    PackageType() {
        type  = "app-image";
        suffix = null;
        supported = true;
        enabled = true;
    }

    PackageType(String packageName, String bundleSuffix, String bundlerClass) {
        type  = Objects.requireNonNull(packageName);
        suffix = Objects.requireNonNull(bundleSuffix);
        supported = Optional.ofNullable(bundlerClass).map(PackageType::isBundlerSupported).orElse(false);
        enabled = supported && !Inner.DISABLED_PACKAGERS.contains(getType());

        if (suffix != null && enabled) {
            TKit.trace(String.format("Bundler %s enabled", getType()));
        }
    }

    PackageType(String bundleSuffix, String bundlerClass) {
        this(bundleSuffix.substring(1), bundleSuffix, bundlerClass);
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

    public String getType() {
        return type;
    }

    public static RuntimeException throwSkippedExceptionIfNativePackagingUnavailable() {
        if (NATIVE.stream().noneMatch(PackageType::isSupported)) {
            TKit.throwSkippedException("None of the native packagers supported in this environment");
        } else if (NATIVE.stream().noneMatch(PackageType::isEnabled)) {
            TKit.throwSkippedException("All native packagers supported in this environment are disabled");
        }
        return null;
    }

    private static boolean isBundlerSupportedImpl(String bundlerClass) {
        try {
            Class<?> clazz = Class.forName(bundlerClass);
            Method supported = clazz.getMethod("supported", boolean.class);
            return ((Boolean) supported.invoke(
                    clazz.getConstructor().newInstance(), true));
        } catch (ClassNotFoundException | IllegalAccessException ex) {
        } catch (InstantiationException | NoSuchMethodException
                | InvocationTargetException ex) {
            rethrowUnchecked(ex);
        }
        return false;
    }

    private static boolean isBundlerSupported(String bundlerClass) {
        AtomicBoolean reply = new AtomicBoolean();
        try {
            // Capture jpackage's activity on configuring bundlers.
            // Log configuration is thread-local.
            // Call Log.setPrintWriter and Log.setVerbose in a separate
            // thread to keep the main log configuration intact.
            var thread = new Thread(() -> {
                Log.setPrintWriter(new PrintWriter(System.out), new PrintWriter(System.err));
                Log.setVerbose();
                try {
                    reply.set(isBundlerSupportedImpl(bundlerClass));
                } finally {
                    Log.flush();
                }
            });
            thread.run();
            thread.join();
        } catch (InterruptedException ex) {
            rethrowUnchecked(ex);
        }
        return reply.get();
    }

    private static Set<PackageType> orderedSet(PackageType... types) {
        return new LinkedHashSet<>(List.of(types));
    }

    private final String type;
    private final String suffix;
    private final boolean enabled;
    private final boolean supported;

    public static final Set<PackageType> LINUX = orderedSet(LINUX_DEB, LINUX_RPM);
    public static final Set<PackageType> WINDOWS = orderedSet(WIN_MSI, WIN_EXE);
    public static final Set<PackageType> MAC = orderedSet(MAC_DMG, MAC_PKG);
    public static final Set<PackageType> NATIVE = Stream.concat(
            Stream.concat(LINUX.stream(), WINDOWS.stream()),
            MAC.stream()).collect(Collectors.toUnmodifiableSet());

    private static final class Inner {
        private static final Set<String> DISABLED_PACKAGERS = Optional.ofNullable(
                TKit.tokenizeConfigProperty("disabledPackagers")).orElse(
                TKit.isLinuxAPT() ? Set.of("rpm") : Collections.emptySet());
    }
}
