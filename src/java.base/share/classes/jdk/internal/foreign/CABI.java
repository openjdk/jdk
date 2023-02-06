/*
 *  Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.OperatingSystem;
import jdk.internal.util.StaticProperty;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static sun.security.action.GetPropertyAction.privilegedGetProperty;

public enum CABI {
    SYS_V,
    WIN_64,
    LINUX_AARCH_64,
    MAC_OS_AARCH_64,
    LINUX_RISCV_64;

    private static final CABI ABI;
    private static final String ARCH;
    private static final long ADDRESS_SIZE;

    static {
        ARCH = StaticProperty.osArch();
        ADDRESS_SIZE = ADDRESS.bitSize();
        // might be running in a 32-bit VM on a 64-bit platform.
        // addressSize will be correctly 32
        if ((ARCH.equals("amd64") || ARCH.equals("x86_64")) && ADDRESS_SIZE == 64) {
            if (OperatingSystem.isWindows()) {
                ABI = WIN_64;
            } else {
                ABI = SYS_V;
            }
        } else if (ARCH.equals("aarch64")) {
            if (OperatingSystem.isMacOS()) {
                ABI = MAC_OS_AARCH_64;
            } else {
                // The Linux ABI follows the standard AAPCS ABI
                ABI = LINUX_AARCH_64;
            }
        } else if (ARCH.equals("riscv64")) {
            if (OperatingSystem.isLinux()) {
                ABI = LINUX_RISCV_64;
            } else {
                // unsupported
                ABI = null;
            }
        } else {
            // unsupported
            ABI = null;
        }
    }

    public static CABI current() {
        if (ABI == null) {
            throw new UnsupportedOperationException(
                    "Unsupported os, arch, or address size: " + OperatingSystem.current() +
                            ", " + ARCH + ", " + ADDRESS_SIZE);
        }
        return ABI;
    }
}
