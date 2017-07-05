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
package org.graalvm.compiler.hotspot.sparc;

import static jdk.vm.ci.sparc.SPARC.d32;
import static jdk.vm.ci.sparc.SPARC.d34;
import static jdk.vm.ci.sparc.SPARC.d36;
import static jdk.vm.ci.sparc.SPARC.d38;
import static jdk.vm.ci.sparc.SPARC.d40;
import static jdk.vm.ci.sparc.SPARC.d42;
import static jdk.vm.ci.sparc.SPARC.d44;
import static jdk.vm.ci.sparc.SPARC.d46;
import static jdk.vm.ci.sparc.SPARC.d48;
import static jdk.vm.ci.sparc.SPARC.d50;
import static jdk.vm.ci.sparc.SPARC.d52;
import static jdk.vm.ci.sparc.SPARC.d54;
import static jdk.vm.ci.sparc.SPARC.d56;
import static jdk.vm.ci.sparc.SPARC.d58;
import static jdk.vm.ci.sparc.SPARC.d60;
import static jdk.vm.ci.sparc.SPARC.d62;
import static jdk.vm.ci.sparc.SPARC.f10;
import static jdk.vm.ci.sparc.SPARC.f11;
import static jdk.vm.ci.sparc.SPARC.f12;
import static jdk.vm.ci.sparc.SPARC.f13;
import static jdk.vm.ci.sparc.SPARC.f14;
import static jdk.vm.ci.sparc.SPARC.f15;
import static jdk.vm.ci.sparc.SPARC.f16;
import static jdk.vm.ci.sparc.SPARC.f17;
import static jdk.vm.ci.sparc.SPARC.f18;
import static jdk.vm.ci.sparc.SPARC.f19;
import static jdk.vm.ci.sparc.SPARC.f20;
import static jdk.vm.ci.sparc.SPARC.f21;
import static jdk.vm.ci.sparc.SPARC.f22;
import static jdk.vm.ci.sparc.SPARC.f23;
import static jdk.vm.ci.sparc.SPARC.f24;
import static jdk.vm.ci.sparc.SPARC.f25;
import static jdk.vm.ci.sparc.SPARC.f26;
import static jdk.vm.ci.sparc.SPARC.f27;
import static jdk.vm.ci.sparc.SPARC.f28;
import static jdk.vm.ci.sparc.SPARC.f29;
import static jdk.vm.ci.sparc.SPARC.f30;
import static jdk.vm.ci.sparc.SPARC.f31;
import static jdk.vm.ci.sparc.SPARC.f8;
import static jdk.vm.ci.sparc.SPARC.f9;
import static jdk.vm.ci.sparc.SPARC.g1;
import static jdk.vm.ci.sparc.SPARC.g4;
import static jdk.vm.ci.sparc.SPARC.g5;
import static jdk.vm.ci.sparc.SPARC.i0;
import static jdk.vm.ci.sparc.SPARC.i1;
import static jdk.vm.ci.sparc.SPARC.i2;
import static jdk.vm.ci.sparc.SPARC.i3;
import static jdk.vm.ci.sparc.SPARC.i4;
import static jdk.vm.ci.sparc.SPARC.i5;
import static jdk.vm.ci.sparc.SPARC.l0;
import static jdk.vm.ci.sparc.SPARC.l1;
import static jdk.vm.ci.sparc.SPARC.l2;
import static jdk.vm.ci.sparc.SPARC.l3;
import static jdk.vm.ci.sparc.SPARC.l4;
import static jdk.vm.ci.sparc.SPARC.l5;
import static jdk.vm.ci.sparc.SPARC.l6;
import static jdk.vm.ci.sparc.SPARC.l7;
import static jdk.vm.ci.sparc.SPARC.o0;
import static jdk.vm.ci.sparc.SPARC.o1;
import static jdk.vm.ci.sparc.SPARC.o2;
import static jdk.vm.ci.sparc.SPARC.o3;
import static jdk.vm.ci.sparc.SPARC.o4;
import static jdk.vm.ci.sparc.SPARC.o5;

import java.util.ArrayList;
import java.util.BitSet;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterConfig;

public class SPARCHotSpotRegisterAllocationConfig extends RegisterAllocationConfig {

    // @formatter:off
    static final Register[] registerAllocationOrder = {
      l0, l1, l2, l3, l4, l5, l6, l7,
      i0, i1, i2, i3, i4, i5, /*i6,*/ /*i7,*/
      o0, o1, o2, o3, o4, o5, /*o6, o7,*/
      g1, g4, g5,
      // f0, f1, f2, f3, f4, f5, f6, f7
      f8,  f9,  f10, f11, f12, f13, f14, f15,
      f16, f17, f18, f19, f20, f21, f22, f23,
      f24, f25, f26, f27, f28, f29, f30, f31,
      d32, d34, d36, d38, d40, d42, d44, d46,
      d48, d50, d52, d54, d56, d58, d60, d62
    };
    // @formatter:on

    public SPARCHotSpotRegisterAllocationConfig(RegisterConfig registerConfig) {
        super(registerConfig);
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
