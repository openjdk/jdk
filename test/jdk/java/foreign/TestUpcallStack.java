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
 * @requires (!(os.name == "Mac OS X" & os.arch == "aarch64") | jdk.foreign.linker != "FALLBACK")
 * @modules java.base/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcallBase
 *
 * @run testng/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=17
 *   TestUpcallStack
 */

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class TestUpcallStack extends TestUpcallBase {

    static {
        System.loadLibrary("TestUpcallStack");
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testUpcallsStack(int count, String fName, Ret ret, List<ParamType> paramTypes,
                                 List<StructFieldType> fields) throws Throwable {
        List<Consumer<Object>> returnChecks = new ArrayList<>();
        List<Consumer<Object>> argChecks = new ArrayList<>();
        MemorySegment addr = findNativeOrThrow("s" + fName);
        try (Arena arena = Arena.ofConfined()) {
            FunctionDescriptor descriptor = functionStack(ret, paramTypes, fields);
            MethodHandle mh = downcallHandle(LINKER, addr, arena, descriptor);
            AtomicReference<Object[]> capturedArgs = new AtomicReference<>();
            Object[] args = makeArgsStack(capturedArgs, arena, descriptor, returnChecks, argChecks);

            Object res = mh.invokeWithArguments(args);

            if (ret == Ret.NON_VOID) {
                returnChecks.forEach(c -> c.accept(res));
            }

            Object[] capturedArgsArr = capturedArgs.get();
            for (int capturedIdx = STACK_PREFIX_LAYOUTS.size(), checkIdx = 0;
                 capturedIdx < capturedArgsArr.length;
                 capturedIdx++, checkIdx++) {
                argChecks.get(checkIdx).accept(capturedArgsArr[capturedIdx]);
            }
        }
    }

    static FunctionDescriptor functionStack(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        return function(ret, params, fields, STACK_PREFIX_LAYOUTS);
    }

    static Object[] makeArgsStack(AtomicReference<Object[]> capturedArgs, Arena session, FunctionDescriptor descriptor,
                                  List<Consumer<Object>> checks, List<Consumer<Object>> argChecks) {
        return makeArgs(capturedArgs, session, descriptor, checks, argChecks, STACK_PREFIX_LAYOUTS.size());
    }

}
