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

package jdk.internal.foreign.abi;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class describes a 'native invoker', which is used as an appendix argument to linkToNative calls.
 */
public class NativeEntryPoint {
    static {
        registerNatives();
    }

    private final MethodType methodType;
    private final long invoker; // read by VM

    private static final Map<CacheKey, Long> INVOKER_CACHE = new ConcurrentHashMap<>();
    private record CacheKey(MethodType methodType, ABIDescriptor abi,
                            List<VMStorage> argMoves, List<VMStorage> retMoves,
                            boolean needsReturnBuffer) {}

    private NativeEntryPoint(MethodType methodType, long invoker) {
        this.methodType = methodType;
        this.invoker = invoker;
    }

    public static NativeEntryPoint make(ABIDescriptor abi,
                                        VMStorage[] argMoves, VMStorage[] returnMoves,
                                        MethodType methodType, boolean needsReturnBuffer) {
        if (returnMoves.length > 1 != needsReturnBuffer) {
            throw new IllegalArgumentException("Multiple register return, but needsReturnBuffer was false");
        }

        assert (methodType.parameterType(0) == long.class) : "Address expected";
        assert (!needsReturnBuffer || methodType.parameterType(1) == long.class) : "return buffer address expected";

        CacheKey key = new CacheKey(methodType, abi, Arrays.asList(argMoves), Arrays.asList(returnMoves), needsReturnBuffer);
        long invoker = INVOKER_CACHE.computeIfAbsent(key, k ->
            makeInvoker(methodType, abi, argMoves, returnMoves, needsReturnBuffer));

        return new NativeEntryPoint(methodType, invoker);
    }

    private static native long makeInvoker(MethodType methodType, ABIDescriptor abi,
                                           VMStorage[] encArgMoves, VMStorage[] encRetMoves,
                                           boolean needsReturnBuffer);

    public MethodType type() {
        return methodType;
    }

    private static native void registerNatives();
}
