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

import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.runtime.ScriptObject;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.support.CallSiteDescriptorFactory;
import org.dynalang.dynalink.support.Guards;

/**
 * Implements lookup of methods to link for dynamic operations on JavaScript primitive values (booleans, strings, and
 * numbers). This class is only public so it can be accessed by classes in the {@code jdk.nashorn.internal.objects}
 * package.
 */
public class PrimitiveLookup {

    private PrimitiveLookup() {
    }

    /**
     * Returns a guarded invocation representing the linkage for a dynamic operation on a primitive Java value.
     * @param desc the descriptor of the call site identifying the dynamic operation.
     * @param receiverClass the class of the receiver value (e.g., {@link java.lang.Boolean}, {@link java.lang.String} etc.)
     * @param wrappedReceiver a transient JavaScript native wrapper object created as the object proxy for the primitive
     * value; see ECMAScript 5.1, section 8.7.1 for discussion of using {@code [[Get]]} on a property reference with a
     * primitive base value. This instance will be used to delegate actual lookup.
     * @param wrapFilter A method handle that takes a primitive value of type specified in the {@code receiverClass} and
     * creates a transient native wrapper of the same type as {@code wrappedReceiver} for subsequent invocations of the
     * method - it will be combined into the returned invocation as an argument filter on the receiver.
     * @return a guarded invocation representing the operation at the call site when performed on a JavaScript primitive
     * type {@code receiverClass}.
     */
    public static GuardedInvocation lookupPrimitive(final CallSiteDescriptor desc, final Class<?> receiverClass,
                                                    final ScriptObject wrappedReceiver, final MethodHandle wrapFilter) {
        return lookupPrimitive(desc, Guards.getInstanceOfGuard(receiverClass), wrappedReceiver, wrapFilter);
    }

    /**
     * Returns a guarded invocation representing the linkage for a dynamic operation on a primitive Java value.
     * @param desc the descriptor of the call site identifying the dynamic operation.
     * @param guard an explicit guard that will be used for the returned guarded invocation.
     * @param wrappedReceiver a transient JavaScript native wrapper object created as the object proxy for the primitive
     * value; see ECMAScript 5.1, section 8.7.1 for discussion of using {@code [[Get]]} on a property reference with a
     * primitive base value. This instance will be used to delegate actual lookup.
     * @param wrapFilter A method handle that takes a primitive value of type guarded by the {@code guard} and
     * creates a transient native wrapper of the same type as {@code wrappedReceiver} for subsequent invocations of the
     * method - it will be combined into the returned invocation as an argument filter on the receiver.
     * @return a guarded invocation representing the operation at the call site when performed on a JavaScript primitive
     * type (that is implied by both {@code guard} and {@code wrappedReceiver}).
     */
    public static GuardedInvocation lookupPrimitive(final CallSiteDescriptor desc, final MethodHandle guard,
                                                    final ScriptObject wrappedReceiver, final MethodHandle wrapFilter) {
        final String operator = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);
        if ("setProp".equals(operator) && desc.getNameTokenCount() > 2) {
            return new GuardedInvocation(MH.asType(Lookup.EMPTY_SETTER, MH.type(void.class, Object.class,
                    desc.getMethodType().parameterType(1))), guard);
        }

        if(desc.getNameTokenCount() > 2) {
            final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
            if(wrappedReceiver.findProperty(name, true) == null) {
                // Give up early, give chance to BeanLinker and NashornBottomLinker to deal with it.
                return null;
            }
        }
        final GuardedInvocation link = wrappedReceiver.lookup(desc);
        if (link != null) {
            MethodHandle method = link.getInvocation();
            final Class<?> receiverType = method.type().parameterType(0);
            if (receiverType != Object.class || NashornGuardedInvocation.isNonStrict(link)) {
                final MethodType wrapType = wrapFilter.type();
                assert receiverType.isAssignableFrom(wrapType.returnType());
                method = MH.filterArguments(method, 0, MH.asType(wrapFilter, wrapType.changeReturnType(receiverType)));
            }
            return new GuardedInvocation(method, guard, link.getSwitchPoint());
        }
        assert desc.getNameTokenCount() <= 2; // Named operations would hit the return null after findProperty
        return null;
    }
}
