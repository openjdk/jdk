/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */
package jdk.internal.foreign.abi;

import jdk.internal.foreign.Utils;
import sun.security.action.GetPropertyAction;

import java.lang.foreign.Addressable;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static jdk.internal.foreign.abi.Binding.Tag.*;

public class CallingSequenceBuilder {
    private static final boolean VERIFY_BINDINGS = Boolean.parseBoolean(
            GetPropertyAction.privilegedGetProperty("java.lang.foreign.VERIFY_BINDINGS", "true"));

    private final ABIDescriptor abi;

    private final boolean forUpcall;
    private final List<List<Binding>> inputBindings = new ArrayList<>();
    private List<Binding> outputBindings = List.of();

    private MethodType mt = MethodType.methodType(void.class);
    private FunctionDescriptor desc = FunctionDescriptor.ofVoid();

    public CallingSequenceBuilder(ABIDescriptor abi, boolean forUpcall) {
        this.abi = abi;
        this.forUpcall = forUpcall;
    }

    public final CallingSequenceBuilder addArgumentBindings(Class<?> carrier, MemoryLayout layout,
                                                            List<Binding> bindings) {
        addArgumentBinding(inputBindings.size(), carrier, layout, bindings);
        return this;
    }

    private void addArgumentBinding(int index, Class<?> carrier, MemoryLayout layout, List<Binding> bindings) {
        verifyBindings(true, carrier, bindings);
        inputBindings.add(index, bindings);
        mt = mt.insertParameterTypes(index, carrier);
        desc = desc.insertArgumentLayouts(index, layout);
    }

    public CallingSequenceBuilder setReturnBindings(Class<?> carrier, MemoryLayout layout,
                                                    List<Binding> bindings) {
        verifyBindings(false, carrier, bindings);
        this.outputBindings = bindings;
        mt = mt.changeReturnType(carrier);
        desc = desc.changeReturnLayout(layout);
        return this;
    }

    private boolean needsReturnBuffer() {
        return outputBindings.stream()
            .filter(Binding.Move.class::isInstance)
            .count() > 1;
    }

    public CallingSequence build() {
        boolean needsReturnBuffer = needsReturnBuffer();
        long returnBufferSize = needsReturnBuffer ? computeReturnBuferSize() : 0;
        long allocationSize = computeAllocationSize() + returnBufferSize;
        if (!forUpcall) {
            addArgumentBinding(0, Addressable.class, ValueLayout.ADDRESS, List.of(
                Binding.unboxAddress(Addressable.class),
                Binding.vmStore(abi.targetAddrStorage(), long.class)));
            if (needsReturnBuffer) {
                addArgumentBinding(0, MemorySegment.class, ValueLayout.ADDRESS, List.of(
                    Binding.unboxAddress(MemorySegment.class),
                    Binding.vmStore(abi.retBufAddrStorage(), long.class)));
            }
        } else if (needsReturnBuffer) { // forUpcall == true
            addArgumentBinding(0, MemorySegment.class, ValueLayout.ADDRESS, List.of(
                Binding.vmLoad(abi.retBufAddrStorage(), long.class),
                Binding.boxAddress(),
                Binding.toSegment(returnBufferSize)));
        }
        return new CallingSequence(mt, desc, needsReturnBuffer, returnBufferSize, allocationSize, inputBindings, outputBindings);
    }

    private long computeAllocationSize() {
        // FIXME: > 16 bytes alignment might need extra space since the
        // starting address of the allocator might be un-aligned.
        long size = 0;
        for (List<Binding> bindings : inputBindings) {
            for (Binding b : bindings) {
                if (b instanceof Binding.Copy copy) {
                    size = Utils.alignUp(size, copy.alignment());
                    size += copy.size();
                } else if (b instanceof Binding.Allocate allocate) {
                    size = Utils.alignUp(size, allocate.alignment());
                    size += allocate.size();
                }
            }
        }
        return size;
    }

    private long computeReturnBuferSize() {
        return outputBindings.stream()
                .filter(Binding.Move.class::isInstance)
                .map(Binding.Move.class::cast)
                .map(Binding.Move::storage)
                .map(VMStorage::type)
                .mapToLong(abi.arch::typeSize)
                .sum();
    }

    private void verifyBindings(boolean forArguments, Class<?> carrier, List<Binding> bindings) {
        if (VERIFY_BINDINGS) {
            if (forUpcall == forArguments) {
                verifyBoxBindings(carrier, bindings);
            } else {
                verifyUnboxBindings(carrier, bindings);
            }
        }
    }

    private static final Set<Binding.Tag> UNBOX_TAGS = EnumSet.of(
        VM_STORE,
        //VM_LOAD,
        //BUFFER_STORE,
        BUFFER_LOAD,
        COPY_BUFFER,
        //ALLOC_BUFFER,
        //BOX_ADDRESS,
        UNBOX_ADDRESS,
        //TO_SEGMENT,
        DUP
    );

    private static void verifyUnboxBindings(Class<?> inType, List<Binding> bindings) {
        Deque<Class<?>> stack = new ArrayDeque<>();
        stack.push(inType);

        for (Binding b : bindings) {
            if (!UNBOX_TAGS.contains(b.tag()))
                throw new IllegalArgumentException("Unexpected operator: " + b);
            b.verify(stack);
        }

        if (!stack.isEmpty()) {
            throw new IllegalArgumentException("Stack must be empty after recipe");
        }
    }

    private static final Set<Binding.Tag> BOX_TAGS = EnumSet.of(
        //VM_STORE,
        VM_LOAD,
        BUFFER_STORE,
        //BUFFER_LOAD,
        COPY_BUFFER,
        ALLOC_BUFFER,
        BOX_ADDRESS,
        //UNBOX_ADDRESS,
        TO_SEGMENT,
        DUP
    );

    private static void verifyBoxBindings(Class<?> expectedOutType, List<Binding> bindings) {
        Deque<Class<?>> stack = new ArrayDeque<>();

        for (Binding b : bindings) {
            if (!BOX_TAGS.contains(b.tag()))
                throw new IllegalArgumentException("Unexpected operator: " + b);
            b.verify(stack);
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Stack must contain exactly 1 value");
        }

        Class<?> actualOutType = stack.pop();
        SharedUtils.checkType(actualOutType, expectedOutType);
    }
}
