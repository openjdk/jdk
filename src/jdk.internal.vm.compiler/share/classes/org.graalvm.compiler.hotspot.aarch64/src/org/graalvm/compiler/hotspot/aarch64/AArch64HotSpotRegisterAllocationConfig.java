/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r17;
import static jdk.vm.ci.aarch64.AArch64.r18;
import static jdk.vm.ci.aarch64.AArch64.r19;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r21;
import static jdk.vm.ci.aarch64.AArch64.r22;
import static jdk.vm.ci.aarch64.AArch64.r23;
import static jdk.vm.ci.aarch64.AArch64.r24;
import static jdk.vm.ci.aarch64.AArch64.r25;
import static jdk.vm.ci.aarch64.AArch64.r26;
import static jdk.vm.ci.aarch64.AArch64.r27;
import static jdk.vm.ci.aarch64.AArch64.r28;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.r8;
import static jdk.vm.ci.aarch64.AArch64.r9;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v10;
import static jdk.vm.ci.aarch64.AArch64.v11;
import static jdk.vm.ci.aarch64.AArch64.v12;
import static jdk.vm.ci.aarch64.AArch64.v13;
import static jdk.vm.ci.aarch64.AArch64.v14;
import static jdk.vm.ci.aarch64.AArch64.v15;
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v23;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v25;
import static jdk.vm.ci.aarch64.AArch64.v26;
import static jdk.vm.ci.aarch64.AArch64.v27;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.v8;
import static jdk.vm.ci.aarch64.AArch64.v9;

import java.util.ArrayList;
import java.util.BitSet;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterConfig;

public class AArch64HotSpotRegisterAllocationConfig extends RegisterAllocationConfig {

    // @formatter:off
    static final Register[] registerAllocationOrder = {
        r0,  r1,  r2,  r3,  r4,  r5,  r6,  r7,
        r8,  r9,  r10, r11, r12, r13, r14, r15,
        r16, r17, r18, r19, r20, r21, r22, r23,
        r24, r25, r26, r27, r28, /* r29, r30, r31 */

        v0,  v1,  v2,  v3,  v4,  v5,  v6,  v7,
        v8,  v9,  v10, v11, v12, v13, v14, v15,
        v16, v17, v18, v19, v20, v21, v22, v23,
        v24, v25, v26, v27, v28, v29, v30, v31
    };
    // @formatter:on

    public AArch64HotSpotRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        super(registerConfig, allocationRestrictedTo);
    }

    @Override
    protected RegisterArray initAllocatable(RegisterArray registers) {
        BitSet regMap = new BitSet(registerConfig.getAllocatableRegisters().size());
        for (Register reg : registers) {
            regMap.set(reg.number);
        }

        ArrayList<Register> allocatableRegisters = new ArrayList<>(registers.size());
        for (Register reg : registerAllocationOrder) {
            if (regMap.get(reg.number)) {
                allocatableRegisters.add(reg);
            }
        }

        return super.initAllocatable(new RegisterArray(allocatableRegisters));
    }
}
