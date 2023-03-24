/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.regex.Pattern;
import jdk.internal.util.OperatingSystem;

/**
 * Platform - Utility methods not replaced by jdk.internal.util.OperatingSystem.
 */
class Platform {
    private static final int majorVersion;
    private static final int minorVersion;

    static {

        String version = System.getProperty("os.version");
        String[] parts = version.split(Pattern.quote("."));

        if (parts.length > 0) {
            majorVersion = Integer.parseInt(parts[0]);

            if (parts.length > 1) {
                minorVersion = Integer.parseInt(parts[1]);
            }
            else {
                minorVersion = -1;
            }
        }
        else {
            majorVersion = -1;
            minorVersion = -1;
        }
    }

    private Platform() {}

    static int getMajorVersion() {
        return majorVersion;
    }

    static int getMinorVersion() {
        return minorVersion;
    }

    static boolean isWindows() { // Used by tests
        return OperatingSystem.isWindows();
    }

    static boolean isMac() { // Used by tests
        return OperatingSystem.isMacOS();
    }

    static boolean isLinux() { // Used by tests
        return OperatingSystem.isLinux();
    }

    static RuntimeException throwUnknownPlatformError() {
        throw new IllegalArgumentException("Unknown platform");
    }
}
