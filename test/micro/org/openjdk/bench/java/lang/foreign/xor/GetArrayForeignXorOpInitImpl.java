/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.foreign.xor;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.Linker.Option.critical;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.openjdk.bench.java.lang.foreign.CLayouts.C_INT;
import static org.openjdk.bench.java.lang.foreign.CLayouts.C_POINTER;

public class GetArrayForeignXorOpInitImpl implements XorOp {

    static {
        System.loadLibrary("jnitest");

        Linker linker;
        linker = Linker.nativeLinker();
        FunctionDescriptor xor_op_func = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_INT);
        xor_op = linker.downcallHandle(SymbolLookup.loaderLookup().findOrThrow("xor_op"), xor_op_func, critical(false));
    }

    static final MethodHandle xor_op;
    GetArrayForeignXorOpInitImpl() {
    }

    public void xor(byte[] src, int sOff, byte[] dst, int dOff, int len) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment srcBuf = arena.allocate(len);
            MemorySegment.copy(src, sOff, srcBuf, JAVA_BYTE, 0, len);
            MemorySegment dstBuf = arena.allocate(len);
            MemorySegment.copy(dst, dOff, dstBuf, JAVA_BYTE, 0, len);
            xor_op.invokeExact(srcBuf, dstBuf, len);
            MemorySegment.copy(dstBuf, JAVA_BYTE, 0, dst, dOff, len);
        }
    }
}
