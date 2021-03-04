/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi.x64.sysv;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.CLinker;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.UpcallStubs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static jdk.internal.foreign.PlatformLayouts.*;

/**
 * ABI implementation based on System V ABI AMD64 supplement v.0.99.6
 */
public class SysVx64Linker implements CLinker {
    public static final int MAX_INTEGER_ARGUMENT_REGISTERS = 6;
    public static final int MAX_INTEGER_RETURN_REGISTERS = 2;
    public static final int MAX_VECTOR_ARGUMENT_REGISTERS = 8;
    public static final int MAX_VECTOR_RETURN_REGISTERS = 2;
    public static final int MAX_X87_RETURN_REGISTERS = 2;

    private static SysVx64Linker instance;

    static final long ADDRESS_SIZE = 64; // bits

    private static final MethodHandle MH_unboxVaList;
    private static final MethodHandle MH_boxVaList;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_unboxVaList = lookup.findVirtual(VaList.class, "address",
                MethodType.methodType(MemoryAddress.class));
            MH_boxVaList = lookup.findStatic(SysVx64Linker.class, "newVaListOfAddress",
                MethodType.methodType(VaList.class, MemoryAddress.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static SysVx64Linker getInstance() {
        if (instance == null) {
            instance = new SysVx64Linker();
        }
        return instance;
    }

    public static VaList newVaList(Consumer<VaList.Builder> actions, SharedUtils.Allocator allocator) {
        SysVVaList.Builder builder = SysVVaList.builder(allocator);
        actions.accept(builder);
        return builder.build();
    }

    @Override
    public MethodHandle downcallHandle(Addressable symbol, MethodType type, FunctionDescriptor function) {
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(type);
        Objects.requireNonNull(function);
        MethodType llMt = SharedUtils.convertVaListCarriers(type, SysVVaList.CARRIER);
        MethodHandle handle = CallArranger.arrangeDowncall(symbol, llMt, function);
        handle = SharedUtils.unboxVaLists(type, handle, MH_unboxVaList);
        return handle;
    }

    @Override
    public MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(function);
        target = SharedUtils.boxVaLists(target, MH_boxVaList);
        return UpcallStubs.upcallAddress(CallArranger.arrangeUpcall(target, target.type(), function));
    }

    public static VaList newVaListOfAddress(MemoryAddress ma) {
        return SysVVaList.ofAddress(ma);
    }

    public static VaList emptyVaList() {
        return SysVVaList.empty();
    }
}
