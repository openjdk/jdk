/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.dyn;

import java.dyn.*;

/**
 * Construction and caching of often-used invokers.
 * @author jrose
 */
public class Invokers {
    // exact type (sans leading taget MH) for the outgoing call
    private final MethodType targetType;

    // exact invoker for the outgoing call
    private /*lazy*/ MethodHandle exactInvoker;

    // erased (partially untyped but with primitives) invoker for the outgoing call
    private /*lazy*/ MethodHandle erasedInvoker;
    /*lazy*/ MethodHandle erasedInvokerWithDrops;  // for InvokeGeneric

    // generic (untyped) invoker for the outgoing call
    private /*lazy*/ MethodHandle genericInvoker;

    // generic (untyped) invoker for the outgoing call; accepts a single Object[]
    private final /*lazy*/ MethodHandle[] varargsInvokers;

    /** Compute and cache information common to all collecting adapters
     *  that implement members of the erasure-family of the given erased type.
     */
    public Invokers(Access token, MethodType targetType) {
        Access.check(token);
        this.targetType = targetType;
        this.varargsInvokers = new MethodHandle[targetType.parameterCount()+1];
    }

    public static MethodType invokerType(MethodType targetType) {
        return targetType.insertParameterTypes(0, MethodHandle.class);
    }

    public MethodHandle exactInvoker() {
        MethodHandle invoker = exactInvoker;
        if (invoker != null)  return invoker;
        try {
            invoker = MethodHandleImpl.IMPL_LOOKUP.findVirtual(MethodHandle.class, "invoke", targetType);
        } catch (NoAccessException ex) {
            throw new InternalError("JVM cannot find invoker for "+targetType);
        }
        assert(invokerType(targetType) == invoker.type());
        exactInvoker = invoker;
        return invoker;
    }

    public MethodHandle genericInvoker() {
        MethodHandle invoker1 = exactInvoker();
        MethodHandle invoker = genericInvoker;
        if (invoker != null)  return invoker;
        MethodType genericType = targetType.generic();
        invoker = MethodHandles.convertArguments(invoker1, invokerType(genericType));
        genericInvoker = invoker;
        return invoker;
    }

    public MethodHandle erasedInvoker() {
        MethodHandle invoker1 = exactInvoker();
        MethodHandle invoker = erasedInvoker;
        if (invoker != null)  return invoker;
        MethodType erasedType = targetType.erase();
        if (erasedType == targetType.generic())
            invoker = genericInvoker();
        else
            invoker = MethodHandles.convertArguments(invoker1, invokerType(erasedType));
        erasedInvoker = invoker;
        return invoker;
    }

    public MethodHandle varargsInvoker(int objectArgCount) {
        MethodHandle vaInvoker = varargsInvokers[objectArgCount];
        if (vaInvoker != null)  return vaInvoker;
        MethodHandle gInvoker = genericInvoker();
        MethodType vaType = MethodType.genericMethodType(objectArgCount, true);
        vaInvoker = MethodHandles.spreadArguments(gInvoker, invokerType(vaType));
        varargsInvokers[objectArgCount] = vaInvoker;
        return vaInvoker;
    }

    public String toString() {
        return "Invokers"+targetType;
    }
}
