/*
 *  Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.SegmentAllocator;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class BindingInterpreter {

    static void unbox(Object arg, List<Binding> bindings, StoreFunc storeFunc, SegmentAllocator allocator) {
        Deque<Object> stack = new LinkedList<>(); // Use LinkedList as a null-friendly Deque for null segment bases

        stack.push(arg);
        for (Binding b : bindings) {
            b.interpret(stack, storeFunc, null, allocator);
        }
    }

    static Object box(List<Binding> bindings, LoadFunc loadFunc, SegmentAllocator allocator) {
        Deque<Object> stack = new ArrayDeque<>();
        for (Binding b : bindings) {
            b.interpret(stack, null, loadFunc, allocator);
        }
       return stack.pop();
    }

    @FunctionalInterface
    public interface StoreFunc {
        void store(VMStorage storage, Object o);
    }

    @FunctionalInterface
    public interface LoadFunc {
        Object load(VMStorage storage, Class<?> type);
    }
}
