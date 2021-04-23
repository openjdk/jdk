/*
 *  Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

public abstract class AbstractCLinker implements CLinker {
    @CallerSensitive
    public final MethodHandle downcallHandle(Addressable symbol, MethodType type, FunctionDescriptor function) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Objects.requireNonNull(symbol);
        return MethodHandles.insertArguments(downcallHandle(type, function), 0, symbol);
    }

    @CallerSensitive
    public final MethodHandle downcallHandle(Addressable symbol, SegmentAllocator allocator, MethodType type, FunctionDescriptor function) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass());
        Objects.requireNonNull(symbol);
        Objects.requireNonNull(allocator);
        MethodHandle downcall = MethodHandles.insertArguments(downcallHandle(type, function), 0, symbol);
        if (type.returnType().equals(MemorySegment.class)) {
            downcall = MethodHandles.insertArguments(downcall, 0, allocator);
        }
        return downcall;
    }
}
