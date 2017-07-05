/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.ObjectClassGenerator.AllocatorDescriptor;

/**
 * Encapsulates the allocation strategy for a function when used as a constructor. Basically the same as
 * {@link AllocatorDescriptor}, but with an additionally cached resolved method handle. There is also a
 * canonical default allocation strategy for functions that don't assign any "this" properties (vast majority
 * of all functions), therefore saving some storage space in {@link RecompilableScriptFunctionData} that would
 * otherwise be lost to identical tuples of (map, className, handle) fields.
 */
final class AllocationStrategy implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final AllocationStrategy DEFAULT_STRATEGY = new AllocationStrategy(new AllocatorDescriptor(0));

    /** Allocator map from allocator descriptor */
    private final PropertyMap allocatorMap;

    /** Name of class where allocator function resides */
    private final String allocatorClassName;

    /** lazily generated allocator */
    private transient MethodHandle allocator;

    private AllocationStrategy(final AllocatorDescriptor desc) {
        this.allocatorMap = desc.getAllocatorMap();
        // These classes get loaded, so an interned variant of their name is most likely around anyway.
        this.allocatorClassName = desc.getAllocatorClassName().intern();
    }

    private boolean matches(final AllocatorDescriptor desc) {
        return desc.getAllocatorMap().size() == allocatorMap.size() &&
                desc.getAllocatorClassName().equals(allocatorClassName);
    }

    static AllocationStrategy get(final AllocatorDescriptor desc) {
        return DEFAULT_STRATEGY.matches(desc) ? DEFAULT_STRATEGY : new AllocationStrategy(desc);
    }

    PropertyMap getAllocatorMap() {
        return allocatorMap;
    }

    ScriptObject allocate(final PropertyMap map) {
        try {
            if (allocator == null) {
                allocator = MH.findStatic(LOOKUP, Context.forStructureClass(allocatorClassName),
                        CompilerConstants.ALLOCATE.symbolName(), MH.type(ScriptObject.class, PropertyMap.class));
            }
            return (ScriptObject)allocator.invokeExact(map);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private Object readResolve() {
        if(allocatorMap.size() == DEFAULT_STRATEGY.allocatorMap.size() &&
                allocatorClassName.equals(DEFAULT_STRATEGY.allocatorClassName)) {
            return DEFAULT_STRATEGY;
        }
        return this;
    }

    @Override
    public String toString() {
        return "AllocationStrategy[allocatorClassName=" + allocatorClassName + ", allocatorMap.size=" +
                allocatorMap.size() + "]";
    }
}
