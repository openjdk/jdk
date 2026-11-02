/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import java.util.Set;
import java.util.List;
import java.util.Locale;
import jdk.internal.util.OperatingSystem;

/**
 * Checks for disallowed filenames that may be provided by a server
 * which we do not want to write to. On Windows we disallow any filename
 * that looks like a DOS legacy device. No restrictions on other platforms.
 */
final class CheckFilename {

    private CheckFilename() {
        throw new InternalError();
    }

    private static final boolean isWindows = OperatingSystem.isWindows();

    /**
     * Reserved names documented at:
     *     https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file
     * and other Microsoft api documentation
     */
    private static final Set<String> WINDOWS_RESERVED_NAMES =
        Set.of("AUX", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6",
            "COM7", "COM8", "COM9", "COM¹", "COM²", "COM³", "CON", "CONIN$",
            "CONOUT$", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8",
            "LPT9", "LPT¹", "LPT²", "LPT³", "NUL", "PRN");

    /**
     * name must be the final component of the pathname only.
     * Possibly including an extension.
     */
    public static boolean isAllowed(String name) {
        if (!isWindows)
            return true;

        // Microsoft documents reserved device names followed by extensions as equivalent to the device name.
        // `CON.txt`, `NUL.tar.gz`, `COM1.txt`, etc. should not be allowed.
        int dot = name.indexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        name = name.toUpperCase(Locale.ROOT)
                   .stripTrailing();

        return !WINDOWS_RESERVED_NAMES.contains(name);
    }
}
