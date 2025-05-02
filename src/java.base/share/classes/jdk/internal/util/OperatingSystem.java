/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.util;

import java.util.Locale;
import jdk.internal.util.PlatformProps;
import jdk.internal.vm.annotation.ForceInline;

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
 * if (OperatingSystem.current() == OperatingSystem.WINDOWS) {
 *     // Windows only code.
 * }
 *}
 * Dispatch based on the current operating system or choose a value.
 * For example,
 * {@snippet lang = "java":
 * int port() {
 *      return switch(OperatingSystem.current()) {
 *          case LINUX->32768;
 *          case AIX->32768;
 *          case MACOS->49152;
 *          case WINDOWS->49152;
 *      };
 * }
 *}
 */
public enum OperatingSystem {

    /**
     * Operating systems based on the Linux kernel.
     */
    LINUX,
    /**
     * The Mac OS X Operating system.
     */
    MACOS,
    /**
     * The Windows Operating system.
     */
    WINDOWS,
    /**
     * The AIX Operating system.
     */
    AIX,
    ;

    // The current OperatingSystem
    private static final OperatingSystem CURRENT_OS = initOS();

    /**
     * {@return {@code true} if built for the Linux operating system}
     */
    @ForceInline
    public static boolean isLinux() {
        return PlatformProps.TARGET_OS_IS_LINUX;
    }

    /**
     * {@return {@code true} if built for the Mac OS X operating system}
     */
    @ForceInline
    public static boolean isMacOS() {
        return PlatformProps.TARGET_OS_IS_MACOS;
    }

    /**
     * {@return {@code true} if built for the Windows operating system}
     */
    @ForceInline
    public static boolean isWindows() {
        return PlatformProps.TARGET_OS_IS_WINDOWS;
    }

    /**
     * {@return {@code true} if built for the AIX operating system}
     */
    @ForceInline
    public static boolean isAix() {
        return PlatformProps.TARGET_OS_IS_AIX;
    }

    /**
     * {@return the current operating system}
     */
    public static OperatingSystem current() {
        return CURRENT_OS;
    }

    /**
     * Returns the OperatingSystem of the build.
     * Build time names are mapped to respective uppercase enum values.
     * Names not recognized throw ExceptionInInitializerError with IllegalArgumentException.
     */
    private static OperatingSystem initOS() {
        return OperatingSystem.valueOf(PlatformProps.CURRENT_OS_STRING.toUpperCase(Locale.ROOT));
    }
}
