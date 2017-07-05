/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.linker;

import static jdk.dynalink.StandardNamespace.METHOD;
import static jdk.dynalink.StandardOperation.GET;
import static jdk.nashorn.internal.runtime.linker.JavaAdapterBytecodeGenerator.SUPER_PREFIX;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.Operation;
import jdk.dynalink.beans.BeansLinker;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.support.Lookup;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * A linker for instances of {@code JavaSuperAdapter}. Only links {@code getMethod} calls, by forwarding them to the
 * bean linker for the adapter class and prepending {@code super$} to method names.
 *
 */
final class JavaSuperAdapterLinker implements TypeBasedGuardingDynamicLinker {
    private static final MethodHandle ADD_PREFIX_TO_METHOD_NAME;
    private static final MethodHandle BIND_DYNAMIC_METHOD;
    private static final MethodHandle GET_ADAPTER;
    private static final MethodHandle IS_ADAPTER_OF_CLASS;

    static {
        final Lookup lookup = new Lookup(MethodHandles.lookup());
        ADD_PREFIX_TO_METHOD_NAME = lookup.findOwnStatic("addPrefixToMethodName", Object.class, Object.class);
        BIND_DYNAMIC_METHOD = lookup.findOwnStatic("bindDynamicMethod", Object.class, Object.class, Object.class);
        GET_ADAPTER = lookup.findVirtual(JavaSuperAdapter.class, "getAdapter", MethodType.methodType(Object.class));
        IS_ADAPTER_OF_CLASS = lookup.findOwnStatic("isAdapterOfClass", boolean.class, Class.class, Object.class);
    }

    private static final Operation GET_METHOD = GET.withNamespace(METHOD);

    private final BeansLinker beansLinker;

    JavaSuperAdapterLinker(final BeansLinker beansLinker) {
        this.beansLinker = beansLinker;
    }

    @Override
    public boolean canLinkType(final Class<?> type) {
        return type == JavaSuperAdapter.class;
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest linkRequest, final LinkerServices linkerServices)
            throws Exception {
        final Object objSuperAdapter = linkRequest.getReceiver();
        if(!(objSuperAdapter instanceof JavaSuperAdapter)) {
            return null;
        }

        final CallSiteDescriptor descriptor = linkRequest.getCallSiteDescriptor();

        if(!NashornCallSiteDescriptor.contains(descriptor, GET, METHOD)) {
            // We only handle GET:METHOD
            return null;
        }

        final Object adapter = ((JavaSuperAdapter)objSuperAdapter).getAdapter();

        // Replace argument (javaSuperAdapter, ...) => (adapter, ...) when delegating to BeansLinker
        final Object[] args = linkRequest.getArguments();
        args[0] = adapter;

        // Use R(T0, ...) => R(adapter.class, ...) call site type when delegating to BeansLinker.
        final MethodType type = descriptor.getMethodType();
        final Class<?> adapterClass = adapter.getClass();
        final String name = NashornCallSiteDescriptor.getOperand(descriptor);
        final Operation newOp = name == null ? GET_METHOD : GET_METHOD.named(SUPER_PREFIX + name);

        final CallSiteDescriptor newDescriptor = new CallSiteDescriptor(
                NashornCallSiteDescriptor.getLookupInternal(descriptor), newOp,
                type.changeParameterType(0, adapterClass));

        // Delegate to BeansLinker
        final GuardedInvocation guardedInv = NashornBeansLinker.getGuardedInvocation(
                beansLinker, linkRequest.replaceArguments(newDescriptor, args),
                linkerServices);
        // Even for non-existent methods, Bootstrap's BeansLinker will link a
        // noSuchMember handler.
        assert guardedInv != null;

        final MethodHandle guard = IS_ADAPTER_OF_CLASS.bindTo(adapterClass);

        final MethodHandle invocation = guardedInv.getInvocation();
        final MethodType invType = invocation.type();
        // For invocation typed R(T0, ...) create a dynamic method binder of type Object(R, T0)
        final MethodHandle typedBinder = BIND_DYNAMIC_METHOD.asType(MethodType.methodType(Object.class,
                invType.returnType(), invType.parameterType(0)));
        // For invocation typed R(T0, T1, ...) create a dynamic method binder of type Object(R, T0, T1, ...)
        final MethodHandle droppingBinder = MethodHandles.dropArguments(typedBinder, 2,
                invType.parameterList().subList(1, invType.parameterCount()));
        // Finally, fold the invocation into the binder to produce a method handle that will bind every returned
        // DynamicMethod object from StandardOperation.GET_METHOD calls to the actual receiver
        // Object(R(T0, T1, ...), T0, T1, ...)
        final MethodHandle bindingInvocation = MethodHandles.foldArguments(droppingBinder, invocation);

        final MethodHandle typedGetAdapter = asFilterType(GET_ADAPTER, 0, invType, type);
        final MethodHandle adaptedInvocation;
        if(name != null) {
            adaptedInvocation = MethodHandles.filterArguments(bindingInvocation, 0, typedGetAdapter);
        } else {
            // Add a filter that'll prepend "super$" to each name passed to the variable-name StandardOperation.GET_METHOD.
            final MethodHandle typedAddPrefix = asFilterType(ADD_PREFIX_TO_METHOD_NAME, 1, invType, type);
            adaptedInvocation = MethodHandles.filterArguments(bindingInvocation, 0, typedGetAdapter, typedAddPrefix);
        }

        return guardedInv.replaceMethods(adaptedInvocation, guard).asType(descriptor);
    }

    /**
     * Adapts the type of a method handle used as a filter in a position from a source method type to a target method type.
     * @param filter the filter method handle
     * @param pos the position in the argument list that it's filtering
     * @param targetType the target method type for filtering
     * @param sourceType the source method type for filtering
     * @return a type adapted filter
     */
    private static MethodHandle asFilterType(final MethodHandle filter, final int pos, final MethodType targetType, final MethodType sourceType) {
        return filter.asType(MethodType.methodType(targetType.parameterType(pos), sourceType.parameterType(pos)));
    }

    @SuppressWarnings("unused")
    private static Object addPrefixToMethodName(final Object name) {
        return SUPER_PREFIX.concat(String.valueOf(name));
    }

    /**
     * Used to transform the return value of getMethod; transform a {@code DynamicMethod} into a
     * {@code BoundDynamicMethod} while also accounting for the possibility of a non-existent method.
     * @param dynamicMethod the dynamic method to bind
     * @param boundThis the adapter underlying a super adapter, to which the dynamic method is bound.
     * @return a dynamic method bound to the adapter instance.
     */
    @SuppressWarnings("unused")
    private static Object bindDynamicMethod(final Object dynamicMethod, final Object boundThis) {
        return dynamicMethod == ScriptRuntime.UNDEFINED ? ScriptRuntime.UNDEFINED : Bootstrap.bindCallable(dynamicMethod, boundThis, null);
    }

    /**
     * Used as the guard of linkages, as the receiver is not guaranteed to be a JavaSuperAdapter.
     * @param clazz the class the receiver's adapter is tested against.
     * @param obj receiver
     * @return true if the receiver is a super adapter, and its underlying adapter is of the specified class
     */
    @SuppressWarnings("unused")
    private static boolean isAdapterOfClass(final Class<?> clazz, final Object obj) {
        return obj instanceof JavaSuperAdapter && clazz == (((JavaSuperAdapter)obj).getAdapter()).getClass();
    }
}
