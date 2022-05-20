/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @build NativeTestHelper CallGeneratorHelper TestDowncall
 *
 * @run testng/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=17
 *   TestDowncall
 *
 * @run testng/othervm -Xint -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=100000
 *   TestDowncall
 */

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;

import java.lang.foreign.MemorySession;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestDowncall extends CallGeneratorHelper {

    static Linker abi = Linker.nativeLinker();
    static {
        System.loadLibrary("TestDowncall");
        System.loadLibrary("TestDowncallStack");
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testDowncall(int count, String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
        List<Consumer<Object>> checks = new ArrayList<>();
        Addressable addr = findNativeOrThrow(fName);
        FunctionDescriptor descriptor = function(ret, paramTypes, fields);
        Object[] args = makeArgs(paramTypes, fields, checks);
        try (MemorySession session = MemorySession.openShared()) {
            boolean needsScope = descriptor.returnLayout().map(GroupLayout.class::isInstance).orElse(false);
            SegmentAllocator allocator = needsScope ?
                    SegmentAllocator.newNativeArena(session) :
                    THROWING_ALLOCATOR;
            Object res = doCall(addr, allocator, descriptor, args);
            if (ret == Ret.NON_VOID) {
                checks.forEach(c -> c.accept(res));
                if (needsScope) {
                    // check that return struct has indeed been allocated in the native scope
                    assertEquals(((MemorySegment)res).session(), session);
                }
            }
        }
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testDowncallStack(int count, String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
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
            if (ret == Ret.NON_VOID) {
                checks.forEach(c -> c.accept(res));
                if (needsScope) {
                    // check that return struct has indeed been allocated in the native scope
                    assertEquals(((MemorySegment)res).session(), session);
                }
            }
        }
    }

    Object doCall(Addressable symbol, SegmentAllocator allocator, FunctionDescriptor descriptor, Object[] args) throws Throwable {
        MethodHandle mh = downcallHandle(abi, symbol, allocator, descriptor);
        Object res = mh.invokeWithArguments(args);
        return res;
    }

    static FunctionDescriptor functionStack(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        return function(ret, params, fields, STACK_PREFIX_LAYOUTS);
    }

    static FunctionDescriptor function(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        return function(ret, params, fields, List.of());
    }

    static FunctionDescriptor function(Ret ret, List<ParamType> params, List<StructFieldType> fields, List<MemoryLayout> prefix) {
        List<MemoryLayout> pLayouts = params.stream().map(p -> p.layout(fields)).toList();
        MemoryLayout[] paramLayouts = Stream.concat(prefix.stream(), pLayouts.stream()).toArray(MemoryLayout[]::new);
        return ret == Ret.VOID ?
                FunctionDescriptor.ofVoid(paramLayouts) :
                FunctionDescriptor.of(paramLayouts[prefix.size()], paramLayouts);
    }

    static Object[] makeArgsStack(List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks) throws ReflectiveOperationException {
        return makeArgs(params, fields, checks, STACK_PREFIX_LAYOUTS);
    }

    static Object[] makeArgs(List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks) throws ReflectiveOperationException {
        return makeArgs(params, fields, checks, List.of());
    }

    static Object[] makeArgs(List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<MemoryLayout> prefix) throws ReflectiveOperationException {
        Object[] args = new Object[prefix.size() + params.size()];
        int argNum = 0;
        for (MemoryLayout layout : prefix) {
            args[argNum++] = makeArg(layout, null, false);
        }
        for (int i = 0 ; i < params.size() ; i++) {
            args[argNum++] = makeArg(params.get(i).layout(fields), checks, i == 0);
        }
        return args;
    }
}
