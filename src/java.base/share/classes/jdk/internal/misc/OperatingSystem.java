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
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

/**
 * Enumeration of operating system types and testing for the current OS.
 * The enumeration can be used to dispatch to OS specific code or values.
 * Checking if a specific operating system is current uses a simple
 * static method for each operating system.
 * <p>
 * For example,
 * {@snippet lang = "java":
 * if (OperatingSystem.isWindows()) {
 *     // Windows only code.
 * } else if (OperatingSystem.isLinux()) {
 *     // Linux only code
 * }
 *}
 *
 * Alternatively, compare with the {@linkplain #current() current} operating system.
 * For example,
 * {@snippet lang = "java":
 * if (OperatingSystem.current() == Windows) {
 *     // Windows only code.
 * }
 *}
 * Dispatch based on the current operating system or choose a value.
 * For example,
 * {@snippet lang = "java":
 * int port() {
 *      return switch(OperatingSystem.current()) {
 *          case Linux->32768;
 *          case AIX->32768;
 *          case Mac->49152;
 *          case Windows->49152;
 *      };
 * }
 * }
 */
public enum OperatingSystem {

    /**
     * The Linux Operating system.
     */
    Linux("Linux"),
    /**
     * The Mac OS X Operating system.
     */
    Mac("Mac OS X"),
    /**
     * The Windows Operating system.
     */
    Windows("Windows"),
    /**
     * The AIX Operating system.
     */
    AIX("AIX"),
    ;

    // Cache a copy of the array for lightweight indexing
    private static final OperatingSystem[] osValues = OperatingSystem.values();

    /**
     * The operating system name.
     */
    private final String name;

    /**
     * {@return {@code true} if built for the Linux operating system}
     */
    @ForceInline
    public static boolean isLinux() {
        return OperatingSystemProps.TARGET_OS_IS_LINUX;
    }

    /**
     * {@return {@code true} if built for the Mac OS X operating system}
     */
    @ForceInline
    public static boolean isMac() {
        return OperatingSystemProps.TARGET_OS_IS_MACOSX;
    }

    /**
     * {@return {@code true} if built for the Windows operating system}
     */
    @ForceInline
    public static boolean isWindows() {
        return OperatingSystemProps.TARGET_OS_IS_WINDOWS;
    }

    /**
     * {@return {@code true} if built for the AIX operating system}
     */
    @ForceInline
    public static boolean isAix() {
        return OperatingSystemProps.TARGET_OS_IS_AIX;
    }

    /**
     * Construct an operating system enum with the given name.
     *
     * @param name       the name of the operating system
     */
    OperatingSystem(String name) {
        this.name = name;
    }

    /**
     * {@return the current operating system}
     */
    public static OperatingSystem current() {
        return osValues[OperatingSystemProps.TARGET_OS_ORDINAL];
    }

    /**
     * {@return the name of the operating system}
     */
    public String getName() {
        return name;
    }

}
