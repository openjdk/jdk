/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySession;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

import org.testng.annotations.BeforeClass;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.insertArguments;
import static org.testng.Assert.assertEquals;

public abstract class TestUpcallBase extends CallGeneratorHelper {

    static Linker ABI = Linker.nativeLinker();

    private static MethodHandle DUMMY;
    private static MethodHandle PASS_AND_SAVE;

    static {
        try {
            DUMMY = MethodHandles.lookup().findStatic(TestUpcallBase.class, "dummy", MethodType.methodType(void.class));
            PASS_AND_SAVE = MethodHandles.lookup().findStatic(TestUpcallBase.class, "passAndSave",
                    MethodType.methodType(Object.class, Object[].class, AtomicReference.class, int.class));
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Addressable DUMMY_STUB;

    @BeforeClass
    void setup() {
        DUMMY_STUB = ABI.upcallStub(DUMMY, FunctionDescriptor.ofVoid(), MemorySession.openImplicit());
    }

    static FunctionDescriptor function(Ret ret, List<ParamType> params, List<StructFieldType> fields) {
        return function(ret, params, fields, List.of());
    }

    static FunctionDescriptor function(Ret ret, List<ParamType> params, List<StructFieldType> fields, List<MemoryLayout> prefix) {
        List<MemoryLayout> paramLayouts = params.stream().map(p -> p.layout(fields)).collect(Collectors.toList());
        paramLayouts.add(C_POINTER); // the callback
        MemoryLayout[] layouts = Stream.concat(prefix.stream(), paramLayouts.stream()).toArray(MemoryLayout[]::new);
        return ret == Ret.VOID ?
                FunctionDescriptor.ofVoid(layouts) :
                FunctionDescriptor.of(layouts[prefix.size()], layouts);
    }

    static Object[] makeArgs(MemorySession session, Ret ret, List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<Consumer<Object[]>> argChecks) throws ReflectiveOperationException {
        return makeArgs(session, ret, params, fields, checks, argChecks, List.of());
    }

    static Object[] makeArgs(MemorySession session, Ret ret, List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<Consumer<Object[]>> argChecks, List<MemoryLayout> prefix) throws ReflectiveOperationException {
        Object[] args = new Object[prefix.size() + params.size() + 1];
        int argNum = 0;
        for (MemoryLayout layout : prefix) {
            args[argNum++] = makeArg(layout, null, false);
        }
        for (int i = 0 ; i < params.size() ; i++) {
            args[argNum++] = makeArg(params.get(i).layout(fields), checks, i == 0);
        }
        args[argNum] = makeCallback(session, ret, params, fields, checks, argChecks, prefix);
        return args;
    }

    static Addressable makeCallback(MemorySession session, Ret ret, List<ParamType> params, List<StructFieldType> fields, List<Consumer<Object>> checks, List<Consumer<Object[]>> argChecks, List<MemoryLayout> prefix) {
        if (params.isEmpty()) {
            return DUMMY_STUB;
        }

        AtomicReference<Object[]> box = new AtomicReference<>();
        MethodHandle mh = insertArguments(PASS_AND_SAVE, 1, box, prefix.size());
        mh = mh.asCollector(Object[].class, prefix.size() + params.size());

        for(int i = 0; i < prefix.size(); i++) {
            mh = mh.asType(mh.type().changeParameterType(i, carrier(prefix.get(i), false)));
        }

        for (int i = 0; i < params.size(); i++) {
            ParamType pt = params.get(i);
            MemoryLayout layout = pt.layout(fields);
            Class<?> carrier = carrier(layout, false);
            mh = mh.asType(mh.type().changeParameterType(prefix.size() + i, carrier));

            final int finalI = prefix.size() + i;
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
            checks.add(o -> assertStructEquals((MemorySegment) box.get()[prefix.size()], (MemorySegment) o, firstlayout));
        } else {
            checks.add(o -> assertEquals(o, box.get()[prefix.size()]));
        }

        mh = mh.asType(mh.type().changeReturnType(ret == Ret.VOID ? void.class : firstCarrier));

        MemoryLayout[] paramLayouts = Stream.concat(prefix.stream(), params.stream().map(p -> p.layout(fields))).toArray(MemoryLayout[]::new);
        FunctionDescriptor func = ret != Ret.VOID
                ? FunctionDescriptor.of(firstlayout, paramLayouts)
                : FunctionDescriptor.ofVoid(paramLayouts);
        return ABI.upcallStub(mh, func, session);
    }

    static Object passAndSave(Object[] o, AtomicReference<Object[]> ref, int retArg) {
        for (int i = 0; i < o.length; i++) {
            if (o[i] instanceof MemorySegment) {
                MemorySegment ms = (MemorySegment) o[i];
                MemorySegment copy = MemorySegment.allocateNative(ms.byteSize(), MemorySession.openImplicit());
                copy.copyFrom(ms);
                o[i] = copy;
            }
        }
        ref.set(o);
        return o[retArg];
    }

    static void dummy() {
        //do nothing
    }
}
