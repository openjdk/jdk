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
 * @test
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @build NativeTestHelper CallGeneratorHelper TestUpcall
 *
 * @run testng/othervm
 *   -Dforeign.restricted=permit
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   TestUpcall
 * @run testng/othervm
 *   -Dforeign.restricted=permit
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=false
 *   TestUpcall
 * @run testng/othervm
 *   -Dforeign.restricted=permit
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=false
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   TestUpcall
 * @run testng/othervm
 *   -Dforeign.restricted=permit
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_SPEC=true
 *   -Djdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS=true
 *   TestUpcall
 */

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ValueLayout;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.invoke.MethodHandles.insertArguments;
import static jdk.incubator.foreign.CLinker.C_POINTER;
import static org.testng.Assert.assertEquals;


public class TestUpcall extends CallGeneratorHelper {

    static LibraryLookup lib = LibraryLookup.ofLibrary("TestUpcall");
    static CLinker abi = CLinker.getInstance();

    static MethodHandle DUMMY;
    static MethodHandle PASS_AND_SAVE;

    static {
        try {
            DUMMY = MethodHandles.lookup().findStatic(TestUpcall.class, "dummy", MethodType.methodType(void.class));
            PASS_AND_SAVE = MethodHandles.lookup().findStatic(TestUpcall.class, "passAndSave", MethodType.methodType(Object.class, Object[].class, AtomicReference.class));
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    static MemorySegment dummyStub;

    @BeforeClass
    void setup() {
        dummyStub = abi.upcallStub(DUMMY, FunctionDescriptor.ofVoid());
    }

    @AfterClass
    void teardown() {
        dummyStub.close();
    }

    @Test(dataProvider="functions", dataProviderClass=CallGeneratorHelper.class)
    public void testUpcalls(String fName, Ret ret, List<ParamType> paramTypes, List<StructFieldType> fields) throws Throwable {
        List<MemorySegment> segments = new ArrayList<>();
        List<Consumer<Object>> returnChecks = new ArrayList<>();
        List<Consumer<Object[]>> argChecks = new ArrayList<>();
        LibraryLookup.Symbol addr = lib.lookup(fName).get();
        MethodHandle mh = abi.downcallHandle(addr, methodType(ret, paramTypes, fields), function(ret, paramTypes, fields));
        Object[] args = makeArgs(ret, paramTypes, fields, returnChecks, argChecks, segments);
        mh = mh.asSpreader(Object[].class, paramTypes.size() + 1);
        Object res = mh.invoke(args);
        argChecks.forEach(c -> c.accept(args));
        if (ret == Ret.NON_VOID) {
            returnChecks.forEach(c -> c.accept(res));
        }
        segments.forEach(MemorySegment::close);
    }

    static MethodType methodType(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        MethodType mt = ret == Ret.VOID ?
                MethodType.methodType(void.class) : MethodType.methodType(paramCarrier(params.get(0).layout(fields)));
        for (ParamType p : params) {
            mt = mt.appendParameterTypes(paramCarrier(p.layout(fields)));
        }
        mt = mt.appendParameterTypes(MemoryAddress.class); //the callback
        return mt;
    }

    static FunctionDescriptor function(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        List<MemoryLayout> paramLayouts = params.stream().map(p -> p.layout(fields)).collect(Collectors.toList());
        paramLayouts.add(C_POINTER); // the callback
        MemoryLayout[] layouts = paramLayouts.toArray(new MemoryLayout[0]);
        return ret == Ret.VOID ?
                FunctionDescriptor.ofVoid(layouts) :
                FunctionDescriptor.of(layouts[0], layouts);
    }

    static Object[] makeArgs(Ret ret, List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<Consumer<Object[]>> argChecks, List<MemorySegment> segments) throws ReflectiveOperationException {
        Object[] args = new Object[params.size() + 1];
        for (int i = 0 ; i < params.size() ; i++) {
            args[i] = makeArg(params.get(i).layout(fields), checks, i == 0, segments);
        }
        args[params.size()] = makeCallback(ret, params, fields, checks, argChecks, segments);
        return args;
    }

    @SuppressWarnings("unchecked")
    static MemoryAddress makeCallback(Ret ret, List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<Consumer<Object[]>> argChecks, List<MemorySegment> segments) {
        if (params.isEmpty()) {
            return dummyStub.address();
        }

        AtomicReference<Object[]> box = new AtomicReference<>();
        MethodHandle mh = insertArguments(PASS_AND_SAVE, 1, box);
        mh = mh.asCollector(Object[].class, params.size());

        for (int i = 0; i < params.size(); i++) {
            ParamType pt = params.get(i);
            MemoryLayout layout = pt.layout(fields);
            Class<?> carrier = paramCarrier(layout);
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
        Class<?> firstCarrier = paramCarrier(firstlayout);

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
        MemorySegment stub = abi.upcallStub(mh, func);
        segments.add(stub);
        return stub.address();
    }

    static Object passAndSave(Object[] o, AtomicReference<Object[]> ref) {
        ref.set(o);
        return o[0];
    }

    static void dummy() {
        //do nothing
    }
}
