/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
 * Copyright (c) 2021, 2022, Microsoft. All rights reserved.
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

import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.aarch64.CallArranger;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * ABI implementation for Windows/AArch64. Based on AAPCS with
 * changes to va_list.
 */
public final class WindowsAArch64Linker extends AbstractLinker {

    static final Map<String, MemoryLayout> CANONICAL_LAYOUTS =
            SharedUtils.canonicalLayouts(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_CHAR);

    public static WindowsAArch64Linker getInstance() {
        class Holder {
            private static final WindowsAArch64Linker INSTANCE =  new WindowsAArch64Linker();
        }
        return Holder.INSTANCE;
    }

    @Override
    protected MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options) {
        return CallArranger.WINDOWS.arrangeDowncall(inferredMethodType, function, options);
    }

    @Override
    protected UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options) {
        return CallArranger.WINDOWS.arrangeUpcall(targetType, function, options);
    }

    @Override
    protected ByteOrder linkerByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    public Map<String, MemoryLayout> canonicalLayouts() {
        return CANONICAL_LAYOUTS;
    }
}
