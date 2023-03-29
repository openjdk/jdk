/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Properties;

/**
 * Supported platforms
 */
public record Platform(OperatingSystem os, Architecture arch, ByteOrder endianness) {
    private static final Properties PLATFORM_PROPERTIES;
    private static final String ENDIANNESS_KEY_SUFFIX = ".endianness";

    static {
        Properties p = null;
        try (InputStream is = Platform.class.getResourceAsStream("target.properties")) {
            p = new Properties();
            p.load(is);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        PLATFORM_PROPERTIES = p;
    }

    public enum OperatingSystem {
        WINDOWS,
        LINUX,
        MACOS,
        AIX,
        UNKNOWN;
    }

    public enum Architecture {
        X86,
        x64,
        ARM,
        AARCH64,
        PPC64,
        PPC64LE,
        s390x,
        UNKNOWN;
    }

    public static final Platform UNKNOWN = new Platform(OperatingSystem.UNKNOWN, Architecture.UNKNOWN, null);

    /*
     * Returns the {@code Platform} based on the platformString of the form <operating system>-<arch>.
     */
    public static Platform parsePlatform(String platformString) {
        String osName;
        String archName;
        int index = platformString.indexOf("-");
        if (index < 0) {
            osName = platformString;
            archName = "UNKNOWN";
        } else {
            osName = platformString.substring(0, index);
            archName = platformString.substring(index + 1);
        }
        OperatingSystem os;
        try {
            os = OperatingSystem.valueOf(osName.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            os = OperatingSystem.UNKNOWN;
        }
        Architecture arch = toArch(archName);

        if (os == OperatingSystem.UNKNOWN || arch == Architecture.UNKNOWN) {
            return UNKNOWN;
        }
        // map the endianness from target.properties
        // until ModuleTarget attribute is extended to include the endianness
        String v = PLATFORM_PROPERTIES.getProperty(platformString + ENDIANNESS_KEY_SUFFIX);
        ByteOrder endian = switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "little" -> ByteOrder.LITTLE_ENDIAN;
            case "big" -> ByteOrder.BIG_ENDIAN;
            default -> throw new InternalError("Unrecognized endian value '" + platformString + "'");
        };

        return new Platform(os, arch, endian);
    }

    /**
     * @return true if it's a 64-bit platform
     */
    public boolean is64Bit() {
        return switch (arch) {
            case x64, AARCH64, PPC64, PPC64LE, s390x -> true;
            default -> false;
        };
    }

    /**
     * Returns the runtime {@code Platform}.
     */
    public static Platform runtime() {
        return new Platform(runtimeOS(), runtimeArch(), ByteOrder.nativeOrder());
    }

    /**
     * Returns a {@code String} representation of a {@code Platform} in the format of <os>-<arch>
     */
    @Override
    public String toString() {
        return os.toString().toLowerCase() + "-" + arch.toString().toLowerCase();
    }

    /**
     * Returns the runtime {@code Platform.OperatingSystem}.
     */
    private static OperatingSystem runtimeOS() {
        String osName = System.getProperty("os.name").substring(0, 3).toLowerCase();
        OperatingSystem os = switch (osName) {
            case "win" -> OperatingSystem.WINDOWS;
            case "lin" -> OperatingSystem.LINUX;
            case "mac" -> OperatingSystem.MACOS;
            case "aix" -> OperatingSystem.AIX;
            default    -> OperatingSystem.UNKNOWN;
        };
        return os;
    }

    /**
     * Returns the runtime {@code Platform.Architechrure}.
     */
    private static Architecture runtimeArch() {
        String archName = System.getProperty("os.arch");
        return toArch(archName);
    }

    /**
     * Returns the {@code Platform.Architecture} based on the archName.
     */
    private static Architecture toArch(String archName) {
        Architecture arch = switch (archName) {
            case "x86"             -> Architecture.X86;
            case "amd64", "x86_64" -> Architecture.x64;
            case "arm"             -> Architecture.ARM;
            case "aarch64"         -> Architecture.AARCH64;
            case "ppc64"           -> Architecture.PPC64;
            case "ppc64le"         -> Architecture.PPC64LE;
            case "s390x"           -> Architecture.s390x;
            default                -> Architecture.UNKNOWN;
        };
        return arch;
    }
}
