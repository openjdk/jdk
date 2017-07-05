/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import sun.invoke.empty.Empty;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * Construction and caching of often-used invokers.
 * @author jrose
 */
class Invokers {
    // exact type (sans leading taget MH) for the outgoing call
    private final MethodType targetType;

    // exact invoker for the outgoing call
    private /*lazy*/ MethodHandle exactInvoker;

    // erased (partially untyped but with primitives) invoker for the outgoing call
    private /*lazy*/ MethodHandle erasedInvoker;
    /*lazy*/ MethodHandle erasedInvokerWithDrops;  // for InvokeGeneric

    // general invoker for the outgoing call
    private /*lazy*/ MethodHandle generalInvoker;

    // general invoker for the outgoing call; accepts a single Object[]
    private final /*lazy*/ MethodHandle[] spreadInvokers;

    // invoker for an unbound callsite
    private /*lazy*/ MethodHandle uninitializedCallSite;

    /** Compute and cache information common to all collecting adapters
     *  that implement members of the erasure-family of the given erased type.
     */
    /*non-public*/ Invokers(MethodType targetType) {
        this.targetType = targetType;
        this.spreadInvokers = new MethodHandle[targetType.parameterCount()+1];
    }

    /*non-public*/ static MethodType invokerType(MethodType targetType) {
        return targetType.insertParameterTypes(0, MethodHandle.class);
    }

    /*non-public*/ MethodHandle exactInvoker() {
        MethodHandle invoker = exactInvoker;
        if (invoker != null)  return invoker;
        try {
            invoker = IMPL_LOOKUP.findVirtual(MethodHandle.class, "invokeExact", targetType);
        } catch (ReflectiveOperationException ex) {
            throw new InternalError("JVM cannot find invoker for "+targetType);
        }
        assert(invokerType(targetType) == invoker.type());
        exactInvoker = invoker;
        return invoker;
    }

    /*non-public*/ MethodHandle generalInvoker() {
        MethodHandle invoker1 = exactInvoker();
        MethodHandle invoker = generalInvoker;
        if (invoker != null)  return invoker;
        MethodType generalType = targetType.generic();
        invoker = invoker1.asType(invokerType(generalType));
        generalInvoker = invoker;
        return invoker;
    }

    /*non-public*/ MethodHandle erasedInvoker() {
        MethodHandle invoker1 = exactInvoker();
        MethodHandle invoker = erasedInvoker;
        if (invoker != null)  return invoker;
        MethodType erasedType = targetType.erase();
        if (erasedType == targetType.generic())
            invoker = generalInvoker();
        else
            invoker = invoker1.asType(invokerType(erasedType));
        erasedInvoker = invoker;
        return invoker;
    }

    /*non-public*/ MethodHandle spreadInvoker(int objectArgCount) {
        MethodHandle vaInvoker = spreadInvokers[objectArgCount];
        if (vaInvoker != null)  return vaInvoker;
        MethodHandle gInvoker = generalInvoker();
        vaInvoker = gInvoker.asSpreader(Object[].class, targetType.parameterCount() - objectArgCount);
        spreadInvokers[objectArgCount] = vaInvoker;
        return vaInvoker;
    }

    private static MethodHandle THROW_UCS = null;

    /*non-public*/ MethodHandle uninitializedCallSite() {
        MethodHandle invoker = uninitializedCallSite;
        if (invoker != null)  return invoker;
        if (targetType.parameterCount() > 0) {
            MethodType type0 = targetType.dropParameterTypes(0, targetType.parameterCount());
            Invokers invokers0 = type0.invokers();
            invoker = MethodHandles.dropArguments(invokers0.uninitializedCallSite(),
                                                  0, targetType.parameterList());
            assert(invoker.type().equals(targetType));
            uninitializedCallSite = invoker;
            return invoker;
        }
        if (THROW_UCS == null) {
            try {
                THROW_UCS = IMPL_LOOKUP
                    .findStatic(CallSite.class, "uninitializedCallSite",
                                MethodType.methodType(Empty.class));
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        }
        invoker = AdapterMethodHandle.makeRetypeRaw(targetType, THROW_UCS);
        assert(invoker.type().equals(targetType));
        uninitializedCallSite = invoker;
        return invoker;
    }

    public String toString() {
        return "Invokers"+targetType;
    }
}
