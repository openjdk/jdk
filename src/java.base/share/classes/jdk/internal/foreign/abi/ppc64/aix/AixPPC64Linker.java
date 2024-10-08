/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 SAP SE. All rights reserved.
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
package jdk.internal.foreign.abi.ppc64.aix;

import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.ppc64.CallArranger;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.util.Map;

public final class AixPPC64Linker extends AbstractLinker {

    static final Map<String, MemoryLayout> CANONICAL_LAYOUTS =
            SharedUtils.canonicalLayouts(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);

    public static AixPPC64Linker getInstance() {
        final class Holder {
            private static final AixPPC64Linker INSTANCE = new AixPPC64Linker();
        }

        return Holder.INSTANCE;
    }

    private AixPPC64Linker() {
        // Ensure there is only one instance
    }

    @Override
    protected void checkStructMember(MemoryLayout member, long offset) {
        // special case double members that are not the first member
        // see: https://www.ibm.com/docs/en/xl-c-and-cpp-aix/16.1?topic=data-using-alignment-modes
        // Note: It is possible to enforce 8-byte alignment by #pragma align (natural)
        // Therefore, we use normal checks if we are already 8-byte aligned.
        if ((offset % 8 != 0) && (member instanceof ValueLayout vl && vl.carrier() == double.class)) {
            if (vl.byteAlignment() != 4) {
                throw new IllegalArgumentException("double struct member " + vl + " at offset " + offset + " should be 4-byte aligned");
            }
            if (vl.order() != ByteOrder.BIG_ENDIAN) {
                throw new IllegalArgumentException("double struct member " + vl + " at offset " + offset + " has an unexpected byte order");
            }
        } else {
            super.checkStructMember(member, offset);
        }
    }

    @Override
    protected MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options) {
        return CallArranger.AIX.arrangeDowncall(inferredMethodType, function, options);
    }

    @Override
    protected UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options) {
        return CallArranger.AIX.arrangeUpcall(targetType, function, options);
    }

    @Override
    public Map<String, MemoryLayout> canonicalLayouts() {
        return CANONICAL_LAYOUTS;
    }
}
