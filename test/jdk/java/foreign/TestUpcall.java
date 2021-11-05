/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=async
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED -Dgenerator.sample.factor=17
 *   -DUPCALL_TEST_TYPE=ASYNC
 *   TestUpcall
 */

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.SymbolLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;

import jdk.incubator.foreign.ResourceScope;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.invoke.MethodHandles.insertArguments;
import static org.testng.Assert.assertEquals;


public class TestUpcall extends CallGeneratorHelper {

    private enum TestType {
        SCOPE,
        NO_SCOPE,
        ASYNC
    }

    private static final TestType UPCALL_TEST_TYPE = TestType.valueOf(System.getProperty("UPCALL_TEST_TYPE"));

    static {
        System.loadLibrary("TestUpcall");
        System.loadLibrary("AsyncInvokers");
    }
    static CLinker abi = CLinker.systemCLinker();

    static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    static MethodHandle DUMMY;
    static MethodHandle PASS_AND_SAVE;

    static {
        try {
            DUMMY = MethodHandles.lookup().findStatic(TestUpcall.class, "dummy", MethodType.methodType(void.class));
            PASS_AND_SAVE = MethodHandles.lookup().findStatic(TestUpcall.class, "passAndSave",
                    MethodType.methodType(Object.class, Object[].class, AtomicReference.class));
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    static NativeSymbol dummyStub;

    @BeforeClass
    void setup() {
        dummyStub = abi.upcallStub(DUMMY, FunctionDescriptor.ofVoid(), ResourceScope.newImplicitScope());
    }

    private static void checkSelected(TestType type) {
        if (UPCALL_TEST_TYPE != type)
            return;//throw new SkipException("Skipping tests that were not selected");
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testUpcalls(int count, String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
        checkSelected(TestType.SCOPE);

        List<Consumer<Object>> returnChecks = new ArrayList<>();
        List<Consumer<Object[]>> argChecks = new ArrayList<>();
        NativeSymbol addr = LOOKUP.lookup(fName).get();
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            SegmentAllocator allocator = SegmentAllocator.newNativeArena(scope);
            MethodHandle mh = downcallHandle(abi, addr, allocator, function(ret, paramTypes, fields));
            Object[] args = makeArgs(scope, ret, paramTypes, fields, returnChecks, argChecks);
            Object[] callArgs = args;
            Object res = mh.invokeWithArguments(callArgs);
            argChecks.forEach(c -> c.accept(args));
            if (ret == Ret.NON_VOID) {
                returnChecks.forEach(c -> c.accept(res));
            }
        }
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testUpcallsAsync(int count, String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
        checkSelected(TestType.ASYNC);
        List<Consumer<Object>> returnChecks = new ArrayList<>();
        List<Consumer<Object[]>> argChecks = new ArrayList<>();
        NativeSymbol addr = LOOKUP.lookup(fName).get();
        try (ResourceScope scope = ResourceScope.newSharedScope()) {
            SegmentAllocator allocator = SegmentAllocator.newNativeArena(scope);
            FunctionDescriptor descriptor = function(ret, paramTypes, fields);
            MethodHandle mh = downcallHandle(abi, addr, allocator, descriptor);
            Object[] args = makeArgs(ResourceScope.newImplicitScope(), ret, paramTypes, fields, returnChecks, argChecks);

            mh = mh.asSpreader(Object[].class, args.length);
            mh = MethodHandles.insertArguments(mh, 0, (Object) args);
            FunctionDescriptor callbackDesc = descriptor.returnLayout()
                    .map(FunctionDescriptor::of)
                    .orElse(FunctionDescriptor.ofVoid());
            NativeSymbol callback = abi.upcallStub(mh.asType(CLinker.upcallType(callbackDesc)), callbackDesc, scope);

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
                abi.downcallHandle(
                    LOOKUP.lookup(symbol).orElseThrow(),
                        FunctionDescriptor.ofVoid(C_POINTER)));
        }

        String name = "call_async_" + returnType.name().charAt(0)
                + (returnType == ParamType.STRUCT ? "_" + sigCode(fields) : "");

        return INVOKERS.computeIfAbsent(name, symbol -> {
            NativeSymbol invokerSymbol = LOOKUP.lookup(symbol).orElseThrow();
            MemoryLayout returnLayout = returnType.layout(fields);
            FunctionDescriptor desc = FunctionDescriptor.of(returnLayout, C_POINTER);

            return abi.downcallHandle(invokerSymbol, desc);
        });
    }

    static FunctionDescriptor function(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        List<MemoryLayout> paramLayouts = params.stream().map(p -> p.layout(fields)).collect(Collectors.toList());
        paramLayouts.add(C_POINTER); // the callback
        MemoryLayout[] layouts = paramLayouts.toArray(new MemoryLayout[0]);
        return ret == Ret.VOID ?
                FunctionDescriptor.ofVoid(layouts) :
                FunctionDescriptor.of(layouts[0], layouts);
    }

    static Object[] makeArgs(ResourceScope scope, Ret ret, List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<Consumer<Object[]>> argChecks) throws ReflectiveOperationException {
        Object[] args = new Object[params.size() + 1];
        for (int i = 0 ; i < params.size() ; i++) {
            args[i] = makeArg(params.get(i).layout(fields), checks, i == 0);
        }
        args[params.size()] = makeCallback(scope, ret, params, fields, checks, argChecks);
        return args;
    }

    @SuppressWarnings("unchecked")
    static NativeSymbol makeCallback(ResourceScope scope, Ret ret, List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<Consumer<Object[]>> argChecks) {
        if (params.isEmpty()) {
            return dummyStub;
        }

        AtomicReference<Object[]> box = new AtomicReference<>();
        MethodHandle mh = insertArguments(PASS_AND_SAVE, 1, box);
        mh = mh.asCollector(Object[].class, params.size());

        for (int i = 0; i < params.size(); i++) {
            ParamType pt = params.get(i);
            MemoryLayout layout = pt.layout(fields);
            Class<?> carrier = carrier(layout, false);
            mh = mh.asType(mh.type().changeParameterType(i, carrier));

            final int finalI = i;
            if (carrier == MemorySegment.class) {
                argChecks.add(o -> assertStructEquals((MemorySegment) box.get()[finalI], (MemorySegment) o[finalI], layout));
            } else {
                argChecks.add(o -> assertEquals(box.get()[finalI], o[finalI]));
            }
        }

        ParamType firstParam = params.get(0);
        MemoryLayout firstlayout = firstParam.layout(fields);
        Class<?> firstCarrier = carrier(firstlayout, true);

        if (firstCarrier == MemorySegment.class) {
            checks.add(o -> assertStructEquals((MemorySegment) box.get()[0], (MemorySegment) o, firstlayout));
        } else {
            checks.add(o -> assertEquals(o, box.get()[0]));
        }

        mh = mh.asType(mh.type().changeReturnType(ret == Ret.VOID ? void.class : firstCarrier));

        MemoryLayout[] paramLayouts = params.stream().map(p -> p.layout(fields)).toArray(MemoryLayout[]::new);
        FunctionDescriptor func = ret != Ret.VOID
                ? FunctionDescriptor.of(firstlayout, paramLayouts)
                : FunctionDescriptor.ofVoid(paramLayouts);
        return abi.upcallStub(mh, func, scope);
    }

    static Object passAndSave(Object[] o, AtomicReference<Object[]> ref) {
        for (int i = 0; i < o.length; i++) {
            if (o[i] instanceof MemorySegment) {
                MemorySegment ms = (MemorySegment) o[i];
                MemorySegment copy = MemorySegment.allocateNative(ms.byteSize(), ResourceScope.newImplicitScope());
                copy.copyFrom(ms);
                o[i] = copy;
            }
        }
        ref.set(o);
        return o[0];
    }

    static void dummy() {
        //do nothing
    }
}
