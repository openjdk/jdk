/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.misc;

import jdk.internal.util.StaticProperty;

import java.util.Objects;

/**
 * Enumeration of operating system type.
 * The enum can test if the current operating system is a particular type
 * or switch on the operating system type.
 * <p>
 * For example,
 * {@snippet lang = "java":
 * if (OperatingSystem.isWindows()) {
 *     // Windows only code.
 * } else if (OperatingSystem.isLinux()) {
 *     // Linux only code
 * }
 *
 * // Switch on the current operating system:
 * switch (OperatingSystem.current()) {
 *     case Windows:
 *          break;
 *     case Linux:
 *          break;
 *     case MacOSX:
 *          break;
 *     case AIX:
 *          break;
 * }
 *
 * // Perform an action if the operating system is current:
 * OperatingSystem.Linux.ifCurrent(() -> System.out.println("Linux"));
 * }
 *
 * @since xx
 */
public enum OperatingSystem {

    /**
     * The Linux Operating system.
     */
    Linux("Linux", PlatformProperties.TARGET_OS_IS_LINUX),
    /**
     * The Mac OS X Operating system.
     */
    MacOSX("Mac OS X", PlatformProperties.TARGET_OS_IS_MACOS),
    /**
     * The Windows Operating system.
     */
    Windows("Windows", PlatformProperties.TARGET_OS_IS_WINDOWS),
    /**
     * The AIX Operating system.
     */
    AIX("AIX", PlatformProperties.TARGET_OS_IS_AIX),
    ;

    /**
     * Cache the current operating system and architecture.
     */
    private static final OperatingSystem currentOS = initOS();

    /**
     * Name of the Operating system.
     * Also used as the prefix of the os.name that identifies the Operating system.
     */
    private final String name;

    /**
     * True if this Operating system Enum is the current running enum.
     * Set on construction if this is the current build.
     */
    private final boolean isCurrent;

    /**
     * {@return {@code true} if the operating system is Linux}
     */
    public static boolean isLinux() {
        return PlatformProperties.TARGET_OS_IS_LINUX;
    }

    /**
     * {@return {@code true} if the operating system is Linux}
     */
    public static boolean isMacOS() {
        return PlatformProperties.TARGET_OS_IS_MACOS;
    }

    /**
     * {@return {@code true} if the operating system is Linux}
     */
    public static boolean isWindows() {
        return PlatformProperties.TARGET_OS_IS_WINDOWS;
    }

    /**
     * {@return {@code true} if the operating system is Linux}
     */
    public static boolean isAix() {
        return PlatformProperties.TARGET_OS_IS_AIX;
    }

    /**
     * Construct an operating system enum for the named operating system.
     *
     * @param name       the name that identifies the operating system
     * @param isCurrent  the PlatformProperties.TARGET_OS_IS_XXX boolean for the OS
     */
    OperatingSystem(String name, boolean isCurrent) {
        this.name = name;
        this.isCurrent = isCurrent;
    }

    /**
     * {@return {@code true} if this operating system is the current operating system}
     */
    public boolean isCurrent() {
        return isCurrent;
    }

    /**
     * {@return the current operating system}
     */
    public static OperatingSystem current() {
        return currentOS;
    }

    /**
     * Perform the given action only if this operating system is the current operating system.
     *
     * @param runnable the action to be performed
     * @throws NullPointerException if the runnable is {@code null}
     */
    public void ifCurrent(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (isCurrent()) {
            runnable.run();
        }
    }

    /**
     * {@return the major version number of the current operating system, otherwise 0}
     */
    public int versionMajor() {
        return isCurrent() ? parsedMajor() : 0;
    }

    /**
     * {@return the minor version number of the current operating system, otherwise 0}
     */
    public int versionMinor() {
        return isCurrent() ? parsedMinor() : 0;
    }

    /**
     * {@return the name and if it is current, the version of the operating system}
     */
    public String toString() {
        if (isCurrent()) {
            return name + ", version: " + StaticProperty.osVersion();
        } else {
            return name;
        }
    }

    /**
     * Private racy cache of parsed os.version into int[major, minor,...]
     */
    private static int[] currentVersion;

    /**
     * {@return the major version number of the current operating system}
     */
    private static int parsedMajor() {
        var parsed = parsedOsVersion();
        return parsed.length > 0 ? parsed[0] : 0;        }

    /**
     * {@return the minor version number of the current operating system}
     */
    private static int parsedMinor() {
        var parsed = parsedOsVersion();
        return parsed.length > 1 ? parsed[1] : 0;
    }


    // Parse os.version and return its components
    private static int[] parsedOsVersion() {
        int[] curr = currentVersion;
        if (curr == null) {
            String[] parts = StaticProperty.osVersion().split("\\.");
            curr = new int[parts.length];
            try {
                for (int i = 0; i < parts.length; i++) {
                    curr[i] = Integer.parseInt(parts[0]);
                }
            } catch (NumberFormatException nfe) {
                // ignore and use 0 for missing/bad values
            }
            // Racy assignment is ok
            currentVersion = curr;
        }
        return curr;
    }

    /**
     * {@return the current operating system}
     * The current operating system is the first one with isCurrent true.
     *
     * @throws InternalError if the current operating system is not found among
     *                       the declared OperatingSystem enum.
     */
    private static OperatingSystem initOS() {
        for (OperatingSystem p : OperatingSystem.values()) {
            if (p.isCurrent()) {
                return p;
            }
        }
        throw new InternalError("No current operating system for: " + StaticProperty.osName());
    }
}
