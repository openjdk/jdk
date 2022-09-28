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
 * @build NativeTestHelper CallGeneratorHelper TestDowncallBase
 *
 * @run testng/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=17
 *   TestDowncallStack
 */

import org.testng.annotations.Test;

import java.lang.foreign.Addressable;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;

public class TestDowncallStack extends TestDowncallBase {

    static {
        System.loadLibrary("TestDowncallStack");
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testDowncallStack(int count, String fName, CallGeneratorHelper.Ret ret,
                                  List<CallGeneratorHelper.ParamType> paramTypes,
                                  List<CallGeneratorHelper.StructFieldType> fields) throws Throwable {
        List<Consumer<Object>> checks = new ArrayList<>();
        Addressable addr = findNativeOrThrow("s" + fName);
        FunctionDescriptor descriptor = functionStack(ret, paramTypes, fields);
        Object[] args = makeArgsStack(paramTypes, fields, checks);
        try (MemorySession session = MemorySession.openShared()) {
            boolean needsScope = descriptor.returnLayout().map(GroupLayout.class::isInstance).orElse(false);
            SegmentAllocator allocator = needsScope ?
                    SegmentAllocator.newNativeArena(session) :
                    THROWING_ALLOCATOR;
            Object res = doCall(addr, allocator, descriptor, args);
            if (ret == CallGeneratorHelper.Ret.NON_VOID) {
                checks.forEach(c -> c.accept(res));
                if (needsScope) {
                    // check that return struct has indeed been allocated in the native scope
                    assertEquals(((MemorySegment)res).session(), session);
                }
            }
        }
    }

    static FunctionDescriptor functionStack(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        return function(ret, params, fields, STACK_PREFIX_LAYOUTS);
    }

    static Object[] makeArgsStack(List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks) throws ReflectiveOperationException {
        return makeArgs(params, fields, checks, STACK_PREFIX_LAYOUTS);
    }

}
