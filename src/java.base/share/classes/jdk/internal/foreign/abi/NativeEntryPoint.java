/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.ref.CleanerFactory;

import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;

/**
 * This class describes a 'native entry point', which is used as an appendix argument to linkToNative calls.
 */
public class NativeEntryPoint {
    static {
        registerNatives();
    }

    private final MethodType methodType;
    private final long downcallStubAddress; // read by VM

    private static final Cleaner CLEANER = CleanerFactory.cleaner();
    private static final SoftReferenceCache<CacheKey, NativeEntryPoint> NEP_CACHE = new SoftReferenceCache<>();
    private record CacheKey(MethodType methodType, ABIDescriptor abi,
                            List<VMStorage> argMoves, List<VMStorage> retMoves,
                            boolean needsReturnBuffer, int capturedStateMask,
                            boolean needsTransition) {

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CacheKey other)) return false;

            return methodType == other.methodType && abi == other.abi && capturedStateMask == other.capturedStateMask
                    && needsTransition == other.needsTransition && needsReturnBuffer == other.needsReturnBuffer
                    && argMoves.equals(other.argMoves) && retMoves.equals(other.retMoves);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(methodType);
            result = 31 * result + abi.hashCode();
            result = 31 * result + argMoves.hashCode();
            result = 31 * result + retMoves.hashCode();
            result = 31 * result + Boolean.hashCode(needsReturnBuffer);
            result = 31 * result + capturedStateMask;
            result = 31 * result + Boolean.hashCode(needsTransition);
            return result;
        }
    }

    private NativeEntryPoint(MethodType methodType, long downcallStubAddress) {
        this.methodType = methodType;
        this.downcallStubAddress = downcallStubAddress;
    }

    public static NativeEntryPoint make(ABIDescriptor abi,
                                        VMStorage[] argMoves, VMStorage[] returnMoves,
                                        MethodType methodType,
                                        boolean needsReturnBuffer,
                                        int capturedStateMask,
                                        boolean needsTransition,
                                        boolean usingAddressPairs) {
        if (returnMoves.length > 1 != needsReturnBuffer) {
            throw new AssertionError("Multiple register return, but needsReturnBuffer was false");
        }
        checkMethodType(methodType, needsReturnBuffer, capturedStateMask, usingAddressPairs);

        CacheKey key = new CacheKey(methodType, abi, Arrays.asList(argMoves), Arrays.asList(returnMoves),
                                    needsReturnBuffer, capturedStateMask, needsTransition);
        return NEP_CACHE.get(key, k -> {
            long downcallStub = makeDowncallStub(methodType, abi, argMoves, returnMoves, needsReturnBuffer,
                                                 capturedStateMask, needsTransition);
            if (downcallStub == 0) {
                throw new OutOfMemoryError("Failed to allocate downcall stub");
            }
            NativeEntryPoint nep = new NativeEntryPoint(methodType, downcallStub);
            CLEANER.register(nep, () -> freeDowncallStub(downcallStub));
            return nep;
        });
    }

    private static void checkMethodType(MethodType methodType, boolean needsReturnBuffer, int savedValueMask,
                                        boolean usingAddressPairs) {
        int checkIdx = 0;
        checkParamType(methodType, checkIdx++, long.class, "Function address");
        if (needsReturnBuffer) {
            checkParamType(methodType, checkIdx++, long.class, "Return buffer address");
        }
        if (savedValueMask != 0) { // capturing call state
            if (usingAddressPairs) {
                checkParamType(methodType, checkIdx++, Object.class, "Capture state heap base");
                checkParamType(methodType, checkIdx, long.class, "Capture state offset");
            } else {
                checkParamType(methodType, checkIdx, long.class, "Capture state address");
            }
        }
    }

    private static void checkParamType(MethodType methodType, int checkIdx, Class<?> expectedType, String name) {
        if (methodType.parameterType(checkIdx) != expectedType) {
            throw new AssertionError(name + " expected at index " + checkIdx + ": " + methodType);
        }
    }

    private static native long makeDowncallStub(MethodType methodType, ABIDescriptor abi,
                                                VMStorage[] encArgMoves, VMStorage[] encRetMoves,
                                                boolean needsReturnBuffer,
                                                int capturedStateMask,
                                                boolean needsTransition);

    private static native boolean freeDowncallStub0(long downcallStub);
    private static void freeDowncallStub(long downcallStub) {
        if (!freeDowncallStub0(downcallStub)) {
            throw new InternalError("Could not free downcall stub");
        }
    }

    public MethodType type() {
        return methodType;
    }

    private static native void registerNatives();
}
