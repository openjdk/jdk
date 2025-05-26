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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.WinMsiPackage;
import jdk.jpackage.internal.util.XmlConsumer;


/**
 * WiX Condition to block/allow installation based on OS version.
 */
record OSVersionCondition(WindowsVersion version) {

    static OSVersionCondition createFromAppImage(BuildEnv env, Application app) {
        Objects.requireNonNull(env);
        Objects.requireNonNull(app);

        final List<Path> executables = new ArrayList<>();

        final var appImageLayout = app.imageLayout().resolveAt(env.appImageDir());

        app.mainLauncher().map(mainLauncher -> {
            return ((ApplicationLayout)appImageLayout).launchersDirectory().resolve(mainLauncher.executableNameWithSuffix());
        }).ifPresent(executables::add);

        executables.add(appImageLayout.runtimeDirectory().resolve("bin\\java.dll"));

        final var lowestOsVersion = executables.stream()
                .filter(Files::isRegularFile)
                .map(WindowsVersion::getExecutableOSVersion)
                // Order by version, with the higher version first
                .sorted(WindowsVersion.descendingOrder())
                .findFirst().orElseGet(() -> {
                    // No java.dll, no launchers, it is either a highly customized or messed up app image.
                    // Let it install on Windows NT/95 or newer.
                    return new WindowsVersion(4, 0);
                });

        return new OSVersionCondition(lowestOsVersion);
    }

    record WindowsVersion(int majorOSVersion, int minorOSVersion) {

        WindowsVersion {
            if (majorOSVersion <= 0) {
                throw new IllegalArgumentException("Invalid major version");
            }

            if (minorOSVersion < 0) {
                throw new IllegalArgumentException("Invalid minor version");
            }
        }

        static WindowsVersion getExecutableOSVersion(Path executable) {
            try (final var fin = Files.newInputStream(executable);
                    final var in = new BufferedInputStream(fin)) {
                // Skip all but "e_lfanew" fields of DOS stub (https://wiki.osdev.org/PE#DOS_Stub)
                in.skipNBytes(64 - 4);

                final int peHeaderOffset = read32BitLE(in);
                if (peHeaderOffset <= 0) {
                    throw new IOException("Invalid PE header offset");
                }

                // Move to PE header
                in.skip(peHeaderOffset - 64);

                // Read "mMagic" field (aka PE signature), (https://wiki.osdev.org/PE#PE_header)
                final byte[] peSignature = in.readNBytes(4);
                if (peSignature.length != 4) {
                    throw notEnoughBytes();
                }

                if (peSignature[0] != 'P' || peSignature[1] != 'E' || peSignature[2] != 0 || peSignature[3] != 0) {
                    throw new IOException(String.format("Invalid PE signature: %s", HexFormat.of().formatHex(peSignature)));
                }

                // Read size of optional PE header from "mSizeOfOptionalHeader" field (https://wiki.osdev.org/PE#PE_header)
                in.skip(16);
                final int sizeOfOptionalHeader = read16BitLE(in);
                if (sizeOfOptionalHeader < (40 + 4)) {
                    throw new IOException("Invalid PE optional header size");
                }

                // Skip PE header
                in.skip(2);

                // Skip all fields of Optional PE header until "mMajorOperatingSystemVersion" field (https://wiki.osdev.org/PE#Optional_header)
                in.skip(40);

                final int mMajorOperatingSystemVersion = read16BitLE(in);
                final int mMinorOperatingSystemVersion = read16BitLE(in);

                return new WindowsVersion(mMajorOperatingSystemVersion, mMinorOperatingSystemVersion);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        static Comparator<WindowsVersion> descendingOrder() {
            return Comparator.comparing(WindowsVersion::majorOSVersion).thenComparing(WindowsVersion::minorOSVersion).reversed();
        }

        private static int read16BitLE(InputStream in) throws IOException {
            byte buffer[] = new byte[2];
            if (buffer.length != in.read(buffer)) {
                throw notEnoughBytes();
            }

            return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8));
        }

        private static int read32BitLE(InputStream in) throws IOException {
            byte buffer[] = new byte[4];
            if (buffer.length != in.read(buffer)) {
                throw notEnoughBytes();
            }

            return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8) |
                    ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
        }

        private static IOException notEnoughBytes() {
            return new IOException("Invalid PE file");
        }
    }

    int msiVersionNumber() {
        return version.majorOSVersion() * 100 + version.minorOSVersion();
    }

    String msiVersionString() {
        return String.valueOf(msiVersionNumber());
    }

    static WixFragmentBuilder createWixFragmentBuilder() {
        final var builder = new WixFragmentBuilder() {
            @Override
            protected Collection<XmlConsumer> getFragmentWriters() {
                return Collections.emptyList();
            }

            @Override
            void initFromParams(BuildEnv env, WinMsiPackage pkg) {
                super.initFromParams(env, pkg);

                final var cond = OSVersionCondition.createFromAppImage(env, pkg.app());

                setWixVariable("JpExecutableMajorOSVersion", String.valueOf(cond.version().majorOSVersion));
                setWixVariable("JpExecutableMinorOSVersion", String.valueOf(cond.version().minorOSVersion));
                setWixVariable("JpExecutableOSVersion", String.valueOf(cond.msiVersionString()));
            }
        };

        builder.setDefaultResourceName("os-condition.wxf");

        return builder;
    }
}
