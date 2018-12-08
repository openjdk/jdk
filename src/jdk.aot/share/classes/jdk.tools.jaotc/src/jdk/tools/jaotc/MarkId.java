/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * Constants used to mark special positions in code being installed into the code cache by Graal C++
 * code.
 */
enum MarkId {
    VERIFIED_ENTRY("CodeInstaller::VERIFIED_ENTRY"),
    UNVERIFIED_ENTRY("CodeInstaller::UNVERIFIED_ENTRY"),
    OSR_ENTRY("CodeInstaller::OSR_ENTRY"),
    EXCEPTION_HANDLER_ENTRY("CodeInstaller::EXCEPTION_HANDLER_ENTRY"),
    DEOPT_HANDLER_ENTRY("CodeInstaller::DEOPT_HANDLER_ENTRY"),
    INVOKEINTERFACE("CodeInstaller::INVOKEINTERFACE"),
    INVOKEVIRTUAL("CodeInstaller::INVOKEVIRTUAL"),
    INVOKESTATIC("CodeInstaller::INVOKESTATIC"),
    INVOKESPECIAL("CodeInstaller::INVOKESPECIAL"),
    INLINE_INVOKE("CodeInstaller::INLINE_INVOKE"),
    POLL_NEAR("CodeInstaller::POLL_NEAR"),
    POLL_RETURN_NEAR("CodeInstaller::POLL_RETURN_NEAR"),
    POLL_FAR("CodeInstaller::POLL_FAR"),
    POLL_RETURN_FAR("CodeInstaller::POLL_RETURN_FAR"),
    CARD_TABLE_ADDRESS("CodeInstaller::CARD_TABLE_ADDRESS"),
    HEAP_TOP_ADDRESS("CodeInstaller::HEAP_TOP_ADDRESS"),
    HEAP_END_ADDRESS("CodeInstaller::HEAP_END_ADDRESS"),
    NARROW_KLASS_BASE_ADDRESS("CodeInstaller::NARROW_KLASS_BASE_ADDRESS"),
    NARROW_OOP_BASE_ADDRESS("CodeInstaller::NARROW_OOP_BASE_ADDRESS"),
    CRC_TABLE_ADDRESS("CodeInstaller::CRC_TABLE_ADDRESS"),
    LOG_OF_HEAP_REGION_GRAIN_BYTES("CodeInstaller::LOG_OF_HEAP_REGION_GRAIN_BYTES"),
    INLINE_CONTIGUOUS_ALLOCATION_SUPPORTED("CodeInstaller::INLINE_CONTIGUOUS_ALLOCATION_SUPPORTED");

    private final int value;

    MarkId(String name) {
        this.value = (int) (long) HotSpotJVMCIRuntime.runtime().getConfigStore().getConstants().get(name);
    }

    static MarkId getEnum(int value) {
        for (MarkId e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        throw new InternalError("Unknown enum value: " + value);
    }
}
