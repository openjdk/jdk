/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestDowncall
 *
 * @run testng/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=17
 *   TestDowncall
 */

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SymbolLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SegmentAllocator;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestDowncall extends CallGeneratorHelper {

    static CLinker abi = CLinker.systemCLinker();
    static {
        System.loadLibrary("TestDowncall");
    }

    static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testDowncall(int count, String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
        List<Consumer<Object>> checks = new ArrayList<>();
        NativeSymbol addr = LOOKUP.lookup(fName).get();
        MethodType mt = methodType(ret, paramTypes, fields);
        FunctionDescriptor descriptor = function(ret, paramTypes, fields);
        Object[] args = makeArgs(paramTypes, fields, checks);
        try (ResourceScope scope = ResourceScope.newSharedScope()) {
            boolean needsScope = mt.returnType().equals(MemorySegment.class);
            SegmentAllocator allocator = needsScope ?
                    SegmentAllocator.newNativeArena(scope) :
                    THROWING_ALLOCATOR;
            Object res = doCall(addr, allocator, descriptor, args);
            if (ret == Ret.NON_VOID) {
                checks.forEach(c -> c.accept(res));
                if (needsScope) {
                    // check that return struct has indeed been allocated in the native scope
                    assertEquals(((MemorySegment) res).scope(), scope);
                }
            }
        }
    }

    Object doCall(NativeSymbol symbol, SegmentAllocator allocator, FunctionDescriptor descriptor, Object[] args) throws Throwable {
        MethodHandle mh = downcallHandle(abi, symbol, allocator, descriptor);
        Object res = mh.invokeWithArguments(args);
        return res;
    }

    static MethodType methodType(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        MethodType mt = ret == Ret.VOID ?
                MethodType.methodType(void.class) : MethodType.methodType(carrier(params.get(0).layout(fields), false));
        for (ParamType p : params) {
            mt = mt.appendParameterTypes(carrier(p.layout(fields), true));
        }
        return mt;
    }

    static FunctionDescriptor function(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        MemoryLayout[] paramLayouts = params.stream().map(p -> p.layout(fields)).toArray(MemoryLayout[]::new);
        return ret == Ret.VOID ?
                FunctionDescriptor.ofVoid(paramLayouts) :
                FunctionDescriptor.of(paramLayouts[0], paramLayouts);
    }

    static Object[] makeArgs(List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks) throws ReflectiveOperationException {
        Object[] args = new Object[params.size()];
        for (int i = 0 ; i < params.size() ; i++) {
            args[i] = makeArg(params.get(i).layout(fields), checks, i == 0);
        }
        return args;
    }
}
