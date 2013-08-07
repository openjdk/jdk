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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.beans.BeansLinker;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardingDynamicLinker;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.support.Guards;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Nashorn bottom linker; used as a last-resort catch-all linker for all linking requests that fall through all other
 * linkers (see how {@link Bootstrap} class configures the dynamic linker in its static initializer). It will throw
 * appropriate ECMAScript errors for attempts to invoke operations on {@code null}, link no-op property getters and
 * setters for Java objects that couldn't be linked by any other linker, and throw appropriate ECMAScript errors for
 * attempts to invoke arbitrary Java objects as functions or constructors.
 */
final class NashornBottomLinker implements GuardingDynamicLinker {

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest linkRequest, final LinkerServices linkerServices)
            throws Exception {
        final Object self = linkRequest.getReceiver();

        if (self == null) {
            return linkNull(linkRequest);
        }

        // None of the objects that can be linked by NashornLinker should ever reach here. Basically, anything below
        // this point is a generic Java bean. Therefore, reaching here with a ScriptObject is a Nashorn bug.
        assert isExpectedObject(self) : "Couldn't link " + linkRequest.getCallSiteDescriptor() + " for " + self.getClass().getName();

        return linkBean(linkRequest, linkerServices);
    }

    private static final MethodHandle EMPTY_PROP_GETTER =
            MH.dropArguments(MH.constant(Object.class, UNDEFINED), 0, Object.class);
    private static final MethodHandle EMPTY_ELEM_GETTER =
            MH.dropArguments(EMPTY_PROP_GETTER, 0, Object.class);
    private static final MethodHandle EMPTY_PROP_SETTER =
            MH.asType(EMPTY_ELEM_GETTER, EMPTY_ELEM_GETTER.type().changeReturnType(void.class));
    private static final MethodHandle EMPTY_ELEM_SETTER =
            MH.dropArguments(EMPTY_PROP_SETTER, 0, Object.class);

    private static GuardedInvocation linkBean(final LinkRequest linkRequest, final LinkerServices linkerServices) throws Exception {
        final NashornCallSiteDescriptor desc = (NashornCallSiteDescriptor)linkRequest.getCallSiteDescriptor();
        final Object self = linkRequest.getReceiver();
        final String operator = desc.getFirstOperator();
        switch (operator) {
        case "new":
            if(BeansLinker.isDynamicMethod(self)) {
                throw typeError("method.not.constructor", ScriptRuntime.safeToString(self));
            }
            throw typeError("not.a.function", ScriptRuntime.safeToString(self));
        case "call":
            // Support dyn:call on any object that supports some @FunctionalInterface
            // annotated interface. This way Java method, constructor references or
            // implementations of java.util.function.* interfaces can be called as though
            // those are script functions.
            final Method m = getFunctionalInterfaceMethod(self.getClass());
            if (m != null) {
                final MethodType callType = desc.getMethodType();
                // 'callee' and 'thiz' passed from script + actual arguments
                if (callType.parameterCount() != m.getParameterCount() + 2) {
                    throw typeError("no.method.matches.args", ScriptRuntime.safeToString(self));
                }
                return new GuardedInvocation(
                        // drop 'thiz' passed from the script.
                        MH.dropArguments(desc.getLookup().unreflect(m), 1, callType.parameterType(1)),
                        Guards.getInstanceOfGuard(m.getDeclaringClass())).asType(callType);
            }
            if(BeansLinker.isDynamicMethod(self)) {
                throw typeError("no.method.matches.args", ScriptRuntime.safeToString(self));
            }
            throw typeError("not.a.function", ScriptRuntime.safeToString(self));
        case "callMethod":
        case "getMethod":
            throw typeError("no.such.function", getArgument(linkRequest), ScriptRuntime.safeToString(self));
        case "getProp":
        case "getElem":
            if (desc.getOperand() != null) {
                return getInvocation(EMPTY_PROP_GETTER, self, linkerServices, desc);
            }
            return getInvocation(EMPTY_ELEM_GETTER, self, linkerServices, desc);
        case "setProp":
        case "setElem":
            if (desc.getOperand() != null) {
                return getInvocation(EMPTY_PROP_SETTER, self, linkerServices, desc);
            }
            return getInvocation(EMPTY_ELEM_SETTER, self, linkerServices, desc);
        default:
            break;
        }
        throw new AssertionError("unknown call type " + desc);
    }

    private static GuardedInvocation getInvocation(final MethodHandle handle, final Object self, final LinkerServices linkerServices, final CallSiteDescriptor desc) {
        return Bootstrap.asType(new GuardedInvocation(handle, Guards.getClassGuard(self.getClass())), linkerServices, desc);
    }

    // Used solely in an assertion to figure out if the object we get here is something we in fact expect. Objects
    // linked by NashornLinker should never reach here.
    private static boolean isExpectedObject(final Object obj) {
        return !(NashornLinker.canLinkTypeStatic(obj.getClass()));
    }

    private static GuardedInvocation linkNull(final LinkRequest linkRequest) {
        final NashornCallSiteDescriptor desc = (NashornCallSiteDescriptor)linkRequest.getCallSiteDescriptor();
        final String operator = desc.getFirstOperator();
        switch (operator) {
        case "new":
        case "call":
            throw typeError("not.a.function", "null");
        case "callMethod":
        case "getMethod":
            throw typeError("no.such.function", getArgument(linkRequest), "null");
        case "getProp":
        case "getElem":
            throw typeError("cant.get.property", getArgument(linkRequest), "null");
        case "setProp":
        case "setElem":
            throw typeError("cant.set.property", getArgument(linkRequest), "null");
        default:
            break;
        }
        throw new AssertionError("unknown call type " + desc);
    }

    private static String getArgument(final LinkRequest linkRequest) {
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        if (desc.getNameTokenCount() > 2) {
            return desc.getNameToken(2);
        }
        return ScriptRuntime.safeToString(linkRequest.getArguments()[1]);
    }

    // cache of @FunctionalInterface method of implementor classes
    private static final ClassValue<Method> FUNCTIONAL_IFACE_METHOD = new ClassValue<Method>() {
        @Override
        protected Method computeValue(final Class<?> type) {
            return findFunctionalInterfaceMethod(type);
        }

        private Method findFunctionalInterfaceMethod(final Class<?> clazz) {
            if (clazz == null) {
                return null;
            }

            for (Class<?> iface : clazz.getInterfaces()) {
                // check accessiblity up-front
                if (! Context.isAccessibleClass(iface)) {
                    continue;
                }

                // check for @FunctionalInterface
                if (iface.isAnnotationPresent(FunctionalInterface.class)) {
                    // return the first abstract method
                    for (final Method m : iface.getMethods()) {
                        if (Modifier.isAbstract(m.getModifiers())) {
                            return m;
                        }
                    }
                }
            }

            // did not find here, try super class
            return findFunctionalInterfaceMethod(clazz.getSuperclass());
        }
    };

    // Returns @FunctionalInterface annotated interface's single abstract
    // method. If not found, returns null.
    static Method getFunctionalInterfaceMethod(final Class<?> clazz) {
        return FUNCTIONAL_IFACE_METHOD.get(clazz);
    }
}
