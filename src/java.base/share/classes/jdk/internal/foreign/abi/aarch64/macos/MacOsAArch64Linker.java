/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi.aarch64.macos;

import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.aarch64.CallArranger;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.VaList;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

/**
 * ABI implementation for macOS on Apple silicon. Based on AAPCS with
 * changes to va_list and passing arguments on the stack.
 */
public final class MacOsAArch64Linker extends AbstractLinker {

    public static MacOsAArch64Linker getInstance() {
        final class Holder {
            private static final MacOsAArch64Linker INSTANCE = new MacOsAArch64Linker();
        }

        return Holder.INSTANCE;
    }

    private MacOsAArch64Linker() {
        // Ensure there is only one instance
    }

    @Override
    protected MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options) {
        return CallArranger.MACOS.arrangeDowncall(inferredMethodType, function, options);
    }

    @Override
    protected MemorySegment arrangeUpcall(MethodHandle target, MethodType targetType, FunctionDescriptor function, SegmentScope scope) {
        return CallArranger.MACOS.arrangeUpcall(target, targetType, function, scope);
    }

    public static VaList newVaList(Consumer<VaList.Builder> actions, SegmentScope scope) {
        MacOsAArch64VaList.Builder builder = MacOsAArch64VaList.builder(scope);
        actions.accept(builder);
        return builder.build();
    }

    public static VaList newVaListOfAddress(long address, SegmentScope scope) {
        return MacOsAArch64VaList.ofAddress(address, scope);
    }

    public static VaList emptyVaList() {
        return MacOsAArch64VaList.empty();
    }
}
