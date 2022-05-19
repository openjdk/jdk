/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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


import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.VaList;

import jdk.internal.foreign.SystemLookup;
import jdk.internal.foreign.abi.SharedUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * ABI implementation based on System V ABI AMD64 supplement v.0.99.6
 */
public final class SysVx64Linker implements Linker {
    public static final int MAX_INTEGER_ARGUMENT_REGISTERS = 6;
    public static final int MAX_INTEGER_RETURN_REGISTERS = 2;
    public static final int MAX_VECTOR_ARGUMENT_REGISTERS = 8;
    public static final int MAX_VECTOR_RETURN_REGISTERS = 2;
    public static final int MAX_X87_RETURN_REGISTERS = 2;

    private static SysVx64Linker instance;

    static final long ADDRESS_SIZE = 64; // bits

    public static SysVx64Linker getInstance() {
        if (instance == null) {
            instance = new SysVx64Linker();
        }
        return instance;
    }

    public static VaList newVaList(Consumer<VaList.Builder> actions, MemorySession session) {
        SysVVaList.Builder builder = SysVVaList.builder(session);
        actions.accept(builder);
        return builder.build();
    }

    @Override
    public final MethodHandle downcallHandle(FunctionDescriptor function) {
        Objects.requireNonNull(function);
        MethodType type = SharedUtils.inferMethodType(function, false);
        MethodHandle handle = CallArranger.arrangeDowncall(type, function);
        handle = SharedUtils.maybeInsertAllocator(handle);
        return SharedUtils.wrapDowncall(handle, function);
    }

    @Override
    public final MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, MemorySession session) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(target);
        Objects.requireNonNull(function);
        SharedUtils.checkExceptions(target);
        MethodType type = SharedUtils.inferMethodType(function, true);
        if (!type.equals(target.type())) {
            throw new IllegalArgumentException("Wrong method handle type: " + target.type());
        }
        return CallArranger.arrangeUpcall(target, target.type(), function, session);
    }

    public static VaList newVaListOfAddress(MemoryAddress ma, MemorySession session) {
        return SysVVaList.ofAddress(ma, session);
    }

    public static VaList emptyVaList() {
        return SysVVaList.empty();
    }

    @Override
    public SystemLookup defaultLookup() {
        return SystemLookup.getInstance();
    }
}
