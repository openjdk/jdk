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

import java.lang.foreign.*;
import java.lang.foreign.Arena;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class TestUpcallBase extends CallGeneratorHelper {

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

    static Object[] makeArgs(AtomicReference<Object[]> capturedArgs, Arena arena, FunctionDescriptor downcallDescriptor,
                             List<Consumer<Object>> checks, List<Consumer<Object>> argChecks, int numPrefixArgs) {
        MemoryLayout[] upcallArgLayouts = downcallDescriptor.argumentLayouts()
                .subList(0, downcallDescriptor.argumentLayouts().size() - 1)  // drop CB layout
                .toArray(MemoryLayout[]::new);

        TestValue[] args = new TestValue[upcallArgLayouts.length];
        for (int i = 0; i < args.length; i++) {
            MemoryLayout layout = upcallArgLayouts[i];
            TestValue testValue = genTestValue(layout, arena);
            args[i] = testValue;
            if (i >= numPrefixArgs) {
                argChecks.add(testValue.check());
            }
        }

        int returnedArgIdx;
        FunctionDescriptor upcallDescriptor;
        if (downcallDescriptor.returnLayout().isPresent()) {
            returnedArgIdx = numPrefixArgs;
            upcallDescriptor = FunctionDescriptor.of(downcallDescriptor.returnLayout().get(), upcallArgLayouts);
            checks.add(args[returnedArgIdx].check());
        } else {
            returnedArgIdx = -1;
            upcallDescriptor = FunctionDescriptor.ofVoid(upcallArgLayouts);
        }

        MemorySegment callback = makeArgSaverCB(upcallDescriptor, arena, capturedArgs, returnedArgIdx);
        return Stream.concat(Stream.of(args).map(TestValue::value), Stream.of(callback)).toArray();
    }
}
