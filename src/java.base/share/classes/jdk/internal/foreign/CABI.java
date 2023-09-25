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

import jdk.internal.foreign.abi.fallback.FallbackLinker;
import jdk.internal.vm.ForeignLinkerSupport;
import jdk.internal.util.OperatingSystem;
import jdk.internal.util.StaticProperty;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static sun.security.action.GetPropertyAction.privilegedGetProperty;

public enum CABI {
    SYS_V,
    WIN_64,
    LINUX_AARCH_64,
    MAC_OS_AARCH_64,
    WIN_AARCH_64,
    LINUX_PPC_64_LE,
    LINUX_RISCV_64,
    LINUX_S390,
    FALLBACK,
    UNSUPPORTED;

    private static final CABI CURRENT = computeCurrent();

    private static CABI computeCurrent() {
        String abi = privilegedGetProperty("jdk.internal.foreign.CABI");
        if (abi != null) {
            return CABI.valueOf(abi);
        }

        if (ForeignLinkerSupport.isSupported()) {
            // figure out the ABI based on the platform
            String arch = StaticProperty.osArch();
            long addressSize = ADDRESS.byteSize();
            // might be running in a 32-bit VM on a 64-bit platform.
            // addressSize will be correctly 32
            if ((arch.equals("amd64") || arch.equals("x86_64")) && addressSize == 8) {
                if (OperatingSystem.isWindows()) {
                    return WIN_64;
                } else {
                    return SYS_V;
                }
            } else if (arch.equals("aarch64")) {
                if (OperatingSystem.isMacOS()) {
                    return MAC_OS_AARCH_64;
                } else if (OperatingSystem.isWindows()) {
                    return WIN_AARCH_64;
                } else {
                    // The Linux ABI follows the standard AAPCS ABI
                    return LINUX_AARCH_64;
                }
            } else if (arch.equals("ppc64le")) {
                if (OperatingSystem.isLinux()) {
                    return LINUX_PPC_64_LE;
                }
            } else if (arch.equals("riscv64")) {
                if (OperatingSystem.isLinux()) {
                    return LINUX_RISCV_64;
                }
            } else if (arch.equals("s390x")) {
                if (OperatingSystem.isLinux()) {
                    return LINUX_S390;
                }
        }
        } else if (FallbackLinker.isSupported()) {
            return FALLBACK; // fallback linker
        }

        return UNSUPPORTED;
    }

    public static CABI current() {
        return CURRENT;
    }
}
