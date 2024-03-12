/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @requires !vm.musl
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=17
 *   TestUpcallAsync
 */

import java.lang.foreign.*;

import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class TestUpcallAsync extends TestUpcallBase {

    static {
        System.loadLibrary("TestUpcall");
        System.loadLibrary("AsyncInvokers");
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testUpcallsAsync(int count, String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
        List<Consumer<Object>> returnChecks = new ArrayList<>();
        List<Consumer<Object>> argChecks = new ArrayList<>();
        MemorySegment addr = findNativeOrThrow(fName);
        try (Arena arena = Arena.ofShared()) {
            FunctionDescriptor descriptor = function(ret, paramTypes, fields);
            MethodHandle mh = downcallHandle(LINKER, addr, arena, descriptor);
            AtomicReference<Object[]> capturedArgs = new AtomicReference<>();
            Object[] args = makeArgs(capturedArgs, arena, descriptor, returnChecks, argChecks, 0);

            mh = mh.asSpreader(Object[].class, args.length);
            mh = MethodHandles.insertArguments(mh, 0, (Object) args);
            FunctionDescriptor callbackDesc = descriptor.returnLayout()
                    .map(FunctionDescriptor::of)
                    .orElse(FunctionDescriptor.ofVoid());
            MemorySegment callback = LINKER.upcallStub(mh, callbackDesc, arena);

            MethodHandle invoker = asyncInvoker(ret, ret == Ret.VOID ? null : paramTypes.get(0), fields);

            Object res = (descriptor.returnLayout().isPresent() &&
                         descriptor.returnLayout().get() instanceof GroupLayout)
                    ? invoker.invoke(arena, callback)
                    : invoker.invoke(callback);

            if (ret == Ret.NON_VOID) {
                returnChecks.forEach(c -> c.accept(res));
            }

            Object[] capturedArgsArr = capturedArgs.get();
            for (int i = 0; i < capturedArgsArr.length; i++) {
                argChecks.get(i).accept(capturedArgsArr[i]);
            }
        }
    }

    private static final Map<String, MethodHandle> INVOKERS = new HashMap<>();

    private MethodHandle asyncInvoker(Ret ret, ParamType returnType, List<StructFieldType> fields) {
        if (ret == Ret.VOID) {
            String name = "call_async_V";
            return INVOKERS.computeIfAbsent(name, symbol ->
                    LINKER.downcallHandle(
                            findNativeOrThrow(symbol),
                            FunctionDescriptor.ofVoid(C_POINTER)));
        }

        String name = "call_async_" + returnType.name().charAt(0)
                + (returnType == ParamType.STRUCT ? "_" + sigCode(fields) : "");

        return INVOKERS.computeIfAbsent(name, symbol -> {
            MemorySegment invokerSymbol = findNativeOrThrow(symbol);
            MemoryLayout returnLayout = returnType.layout(fields);
            FunctionDescriptor desc = FunctionDescriptor.of(returnLayout, C_POINTER);

            return LINKER.downcallHandle(invokerSymbol, desc);
        });
    }

}
