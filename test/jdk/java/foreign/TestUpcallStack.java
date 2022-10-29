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
 *   TestUpcallStack
 */

import java.lang.foreign.Addressable;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TestUpcallStack extends TestUpcallBase {

    static {
        System.loadLibrary("TestUpcallStack");
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testUpcallsStack(int count, String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
        List<Consumer<Object>> returnChecks = new ArrayList<>();
        List<Consumer<Object[]>> argChecks = new ArrayList<>();
        Addressable addr = findNativeOrThrow("s" + fName);
        try (MemorySession session = MemorySession.openConfined()) {
            SegmentAllocator allocator = SegmentAllocator.newNativeArena(session);
            MethodHandle mh = downcallHandle(ABI, addr, allocator, functionStack(ret, paramTypes, fields));
            Object[] args = makeArgsStack(session, ret, paramTypes, fields, returnChecks, argChecks);
            Object[] callArgs = args;
            Object res = mh.invokeWithArguments(callArgs);
            argChecks.forEach(c -> c.accept(args));
            if (ret == Ret.NON_VOID) {
                returnChecks.forEach(c -> c.accept(res));
            }
        }
    }

    static FunctionDescriptor functionStack(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        return function(ret, params, fields, STACK_PREFIX_LAYOUTS);
    }

    static Object[] makeArgsStack(MemorySession session, Ret ret, List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<Consumer<Object[]>> argChecks) throws ReflectiveOperationException {
        return makeArgs(session, ret, params, fields, checks, argChecks, STACK_PREFIX_LAYOUTS);
    }

}
