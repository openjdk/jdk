/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * System architectures.
 */
public enum Architecture {
    /**
     * X86 architecture.
     */
    X86("x86", 32),
    /**
     * X64 architecture.
     */
    X64("x86_64", 64),
    /**
     * Arm architecture.
     */
    ARM("arm", 32),
    /**
     * Aarch64 architecture.
     */
    AARCH64("aarch64", 64),
    ;

    static final Architecture currentArch = initArch();

    /**
     * {@return the current architecture}
     * The current architecture is the first (only) of the enum's with isCurrent == true.
     */
    private static Architecture initArch() {
        for (Architecture p : Architecture.values()) {
            if (p.isCurrent()) {
                return p;
            }
        }
        throw new InternalError("No current architecture for: " + StaticProperty.osArch());
    }

    /**
     * The name of the architecture.
     */
    private final String name;

    private final int addressBits;

    /**
     * True if this Operating system Enum is the current running enum.
     * Set on construction if this is the current build.
     */
    private final boolean isCurrent;

    Architecture(String name, int addressBits) {
        this.name = name;
        this.addressBits = addressBits;
        isCurrent = InitPlatform.targetCpu().equals(name);
    }

    /**
     * {@return return the current architecture}
     */
    public static Architecture current() {
        return currentArch;
    }

    /**
     * {@return true if this architecture is the architecture of the current host, otherwise false}
     */
    public boolean isCurrent() {
        return isCurrent;
    }

    /**
     * {@return true if the architecture uses 64-bit addressing}
     */
    public boolean is64Bit() {
        return addressBits == 64;
    }

    /**
     * {@return the name of the architecture}
     */
    public String toString() {
        return name;
    }
}
