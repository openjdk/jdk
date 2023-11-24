/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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
package jdk.internal.foreign.abi.aarch64.windows;

import jdk.internal.foreign.abi.aarch64.CallArranger;
import jdk.internal.foreign.abi.aarch64.TypeClass;
import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.VMStorage;

import java.lang.foreign.MemoryLayout;

import static jdk.internal.foreign.abi.aarch64.AArch64Architecture.*;
import static jdk.internal.foreign.abi.aarch64.AArch64Architecture.Regs.*;

/**
 * AArch64 CallArranger specialized for Windows ABI.
 */
public class WindowsAArch64CallArranger extends CallArranger {

    private static final VMStorage INDIRECT_RESULT = r8;

    // This is derived from the AAPCS64 spec, restricted to what's
    // possible when calling to/from C code.
    //
    // The indirect result register, r8, is used to return a large
    // struct by value. It's treated as an input here as the caller is
    // responsible for allocating storage and passing this into the
    // function.
    //
    // Although the AAPCS64 says r0-7 and v0-7 are all valid return
    // registers, it's not possible to generate a C function that uses
    // r2-7 and v4-7 so, they are omitted here.
    private static final ABIDescriptor WindowsAArch64AbiDescriptor = abiFor(
        new VMStorage[] { r0, r1, r2, r3, r4, r5, r6, r7, INDIRECT_RESULT},
        new VMStorage[] { v0, v1, v2, v3, v4, v5, v6, v7 },
        new VMStorage[] { r0, r1 },
        new VMStorage[] { v0, v1, v2, v3 },
        new VMStorage[] { r9, r10, r11, r12, r13, r14, r15, r16, r17 },
        new VMStorage[] { v16, v17, v18, v19, v20, v21, v22, v23, v24, v25,
                          v26, v27, v28, v29, v30, v31 },
        16,  // Stack is always 16 byte aligned on AArch64
        0,   // No shadow space
        r9,  // target addr reg
        r10  // return buffer addr reg
    );

    @Override
    protected ABIDescriptor abiDescriptor() {
        return WindowsAArch64AbiDescriptor;
    }

    @Override
    protected boolean varArgsOnStack() {
        return false;
    }

    @Override
    protected boolean requiresSubSlotStackPacking() {
        return false;
    }

    @Override
    protected boolean useIntRegsForVariadicFloatingPointArgs() {
        // The Windows ABI requires floating point arguments to be passed in
        // general purpose registers when calling variadic functions.
        return true;
    }

    @Override
    protected boolean spillsVariadicStructsPartially() {
        return true;
    }

    @Override
    protected TypeClass getArgumentClassForBindings(MemoryLayout layout, boolean forVariadicFunction) {
        TypeClass argumentClass = TypeClass.classifyLayout(layout);

        // HFA struct arguments are classified as STRUCT_REGISTER when
        // general purpose registers are being used to pass floating point
        // arguments. If the HFA is too big to pass entirely in general
        // purpose registers, it is classified as an ordinary struct
        // (i.e. as a STRUCT_REFERENCE).
        if (argumentClass == TypeClass.STRUCT_HFA && forVariadicFunction) {
            // The Windows ABI requires the members of the variadic HFA to be
            // passed in general purpose registers but only a STRUCT_HFA that
            // is at most 16 bytes can be passed in general purpose registers.
            argumentClass = layout.byteSize() <= 16 ? TypeClass.STRUCT_REGISTER : TypeClass.STRUCT_REFERENCE;
        }

        return argumentClass;
    }
}