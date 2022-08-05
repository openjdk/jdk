/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static sun.security.action.GetPropertyAction.privilegedGetProperty;

public enum CABI {
    SysV,
    Win64,
    LinuxAArch64,
    MacOsAArch64;

    private static final CABI ABI;
    private static final String ARCH;
    private static final String OS;
    private static final long ADDRESS_SIZE;

    static {
        ARCH = privilegedGetProperty("os.arch");
        OS = privilegedGetProperty("os.name");
        ADDRESS_SIZE = ADDRESS.bitSize();
        // might be running in a 32-bit VM on a 64-bit platform.
        // addressSize will be correctly 32
        if ((ARCH.equals("amd64") || ARCH.equals("x86_64")) && ADDRESS_SIZE == 64) {
            if (OS.startsWith("Windows")) {
                ABI = Win64;
            } else {
                ABI = SysV;
            }
        } else if (ARCH.equals("aarch64")) {
            if (OS.startsWith("Mac")) {
                ABI = MacOsAArch64;
            } else {
                // The Linux ABI follows the standard AAPCS ABI
                ABI = LinuxAArch64;
            }
        } else {
            // unsupported
            ABI = null;
        }
    }

    public static CABI current() {
        if (ABI == null) {
            throw new UnsupportedOperationException(
                    "Unsupported os, arch, or address size: " + OS + ", " + ARCH + ", " + ADDRESS_SIZE);
        }
        return ABI;
    }
}
