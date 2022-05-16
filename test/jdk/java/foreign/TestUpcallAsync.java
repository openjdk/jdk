/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=17
 *   TestUpcallAsync
 */

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TestUpcallAsync extends TestUpcallBase {

    static {
        System.loadLibrary("TestUpcall");
        System.loadLibrary("AsyncInvokers");
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testUpcallsAsync(int count, String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
        List<Consumer<Object>> returnChecks = new ArrayList<>();
        List<Consumer<Object[]>> argChecks = new ArrayList<>();
        Addressable addr = findNativeOrThrow(fName);
        try (MemorySession session = MemorySession.openShared()) {
            SegmentAllocator allocator = SegmentAllocator.newNativeArena(session);
            FunctionDescriptor descriptor = function(ret, paramTypes, fields);
            MethodHandle mh = downcallHandle(ABI, addr, allocator, descriptor);
            Object[] args = makeArgs(MemorySession.openImplicit(), ret, paramTypes, fields, returnChecks, argChecks);

            mh = mh.asSpreader(Object[].class, args.length);
            mh = MethodHandles.insertArguments(mh, 0, (Object) args);
            FunctionDescriptor callbackDesc = descriptor.returnLayout()
                    .map(FunctionDescriptor::of)
                    .orElse(FunctionDescriptor.ofVoid());
            Addressable callback = ABI.upcallStub(mh.asType(Linker.upcallType(callbackDesc)), callbackDesc, session);

            MethodHandle invoker = asyncInvoker(ret, ret == Ret.VOID ? null : paramTypes.get(0), fields);

            Object res = invoker.type().returnType() == MemorySegment.class
                    ? invoker.invoke(allocator, callback)
                    : invoker.invoke(callback);
            argChecks.forEach(c -> c.accept(args));
            if (ret == Ret.NON_VOID) {
                returnChecks.forEach(c -> c.accept(res));
            }
        }
    }

    private static final Map<String, MethodHandle> INVOKERS = new HashMap<>();

    private MethodHandle asyncInvoker(Ret ret, ParamType returnType, List<StructFieldType> fields) {
        if (ret == Ret.VOID) {
            String name = "call_async_V";
            return INVOKERS.computeIfAbsent(name, symbol ->
                    ABI.downcallHandle(
                            findNativeOrThrow(symbol),
                            FunctionDescriptor.ofVoid(C_POINTER)));
        }

        String name = "call_async_" + returnType.name().charAt(0)
                + (returnType == ParamType.STRUCT ? "_" + sigCode(fields) : "");

        return INVOKERS.computeIfAbsent(name, symbol -> {
            Addressable invokerSymbol = findNativeOrThrow(symbol);
            MemoryLayout returnLayout = returnType.layout(fields);
            FunctionDescriptor desc = FunctionDescriptor.of(returnLayout, C_POINTER);

            return ABI.downcallHandle(invokerSymbol, desc);
        });
    }

}
