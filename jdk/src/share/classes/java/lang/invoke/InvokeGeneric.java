/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import sun.invoke.util.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * Adapters which manage MethodHandle.invokeGeneric calls.
 * The JVM calls one of these when the exact type match fails.
 * @author jrose
 */
class InvokeGeneric {
    // erased type for the call, which originates from an invokeGeneric site
    private final MethodType erasedCallerType;
    // an invoker of type (MT, MH; A...) -> R
    private final MethodHandle initialInvoker;

    /** Compute and cache information for this adapter, so that it can
     *  call out to targets of the erasure-family of the given erased type.
     */
    /*non-public*/ InvokeGeneric(MethodType erasedCallerType) throws ReflectiveOperationException {
        assert(erasedCallerType.equals(erasedCallerType.erase()));
        this.erasedCallerType = erasedCallerType;
        this.initialInvoker = makeInitialInvoker();
        assert initialInvoker.type().equals(erasedCallerType
                                            .insertParameterTypes(0, MethodType.class, MethodHandle.class))
            : initialInvoker.type();
    }

    private static MethodHandles.Lookup lookup() {
        return IMPL_LOOKUP;
    }

    /** Return the adapter information for this type's erasure. */
    /*non-public*/ static MethodHandle genericInvokerOf(MethodType erasedCallerType) throws ReflectiveOperationException {
        InvokeGeneric gen = new InvokeGeneric(erasedCallerType);
        return gen.initialInvoker;
    }

    private MethodHandle makeInitialInvoker() throws ReflectiveOperationException {
        // postDispatch = #(MH'; MT, MH; A...){MH'(MT, MH; A)}
        MethodHandle postDispatch = makePostDispatchInvoker();
        MethodHandle invoker;
        if (returnConversionPossible()) {
            invoker = MethodHandles.foldArguments(postDispatch,
                                                  dispatcher("dispatchWithConversion"));
        } else {
            invoker = MethodHandles.foldArguments(postDispatch, dispatcher("dispatch"));
        }
        return invoker;
    }

    private static final Class<?>[] EXTRA_ARGS = { MethodType.class, MethodHandle.class };
    private MethodHandle makePostDispatchInvoker() {
        // Take (MH'; MT, MH; A...) and run MH'(MT, MH; A...).
        MethodType invokerType = erasedCallerType.insertParameterTypes(0, EXTRA_ARGS);
        return invokerType.invokers().exactInvoker();
    }
    private MethodHandle dropDispatchArguments(MethodHandle targetInvoker) {
        assert(targetInvoker.type().parameterType(0) == MethodHandle.class);
        return MethodHandles.dropArguments(targetInvoker, 1, EXTRA_ARGS);
    }

    private MethodHandle dispatcher(String dispatchName) throws ReflectiveOperationException {
        return lookup().bind(this, dispatchName,
                             MethodType.methodType(MethodHandle.class,
                                                   MethodType.class, MethodHandle.class));
    }

    static final boolean USE_AS_TYPE_PATH = true;

    /** Return a method handle to invoke on the callerType, target, and remaining arguments.
     *  The method handle must finish the call.
     *  This is the first look at the caller type and target.
     */
    private MethodHandle dispatch(MethodType callerType, MethodHandle target) {
        MethodType targetType = target.type();
        if (USE_AS_TYPE_PATH || target.isVarargsCollector()) {
            MethodHandle newTarget = target.asType(callerType);
            targetType = callerType;
            Invokers invokers = targetType.invokers();
            MethodHandle invoker = invokers.erasedInvokerWithDrops;
            if (invoker == null) {
                invokers.erasedInvokerWithDrops = invoker =
                    dropDispatchArguments(invokers.erasedInvoker());
            }
            return invoker.bindTo(newTarget);
        }
        throw new RuntimeException("NYI");
    }

    private MethodHandle dispatchWithConversion(MethodType callerType, MethodHandle target) {
        MethodHandle finisher = dispatch(callerType, target);
        if (returnConversionNeeded(callerType, target))
            finisher = addReturnConversion(finisher, callerType.returnType());  //FIXME: slow
        return finisher;
    }

    private boolean returnConversionPossible() {
        Class<?> needType = erasedCallerType.returnType();
        return !needType.isPrimitive();
    }
    private boolean returnConversionNeeded(MethodType callerType, MethodHandle target) {
        Class<?> needType = callerType.returnType();
        if (needType == erasedCallerType.returnType())
            return false;  // no conversions possible, since must be primitive or Object
        Class<?> haveType = target.type().returnType();
        if (VerifyType.isNullConversion(haveType, needType))
            return false;
        return true;
    }
    private MethodHandle addReturnConversion(MethodHandle target, Class<?> type) {
        if (true) throw new RuntimeException("NYI");
        // FIXME: This is slow because it creates a closure node on every call that requires a return cast.
        MethodType targetType = target.type();
        MethodHandle caster = ValueConversions.identity(type);
        caster = caster.asType(MethodType.methodType(type, targetType.returnType()));
        // Drop irrelevant arguments, because we only care about the return value:
        caster = MethodHandles.dropArguments(caster, 1, targetType.parameterList());
        MethodHandle result = MethodHandles.foldArguments(caster, target);
        return result.asType(target.type());
    }

    public String toString() {
        return "InvokeGeneric"+erasedCallerType;
    }
}
