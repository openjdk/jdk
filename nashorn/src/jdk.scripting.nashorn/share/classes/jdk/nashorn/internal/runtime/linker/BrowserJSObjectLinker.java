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

package jdk.nashorn.internal.runtime.linker;

import static jdk.nashorn.internal.runtime.JSType.isString;
import static jdk.nashorn.internal.runtime.linker.BrowserJSObjectLinker.JSObjectHandles.JSOBJECT_CALL;
import static jdk.nashorn.internal.runtime.linker.BrowserJSObjectLinker.JSObjectHandles.JSOBJECT_GETMEMBER;
import static jdk.nashorn.internal.runtime.linker.BrowserJSObjectLinker.JSObjectHandles.JSOBJECT_GETSLOT;
import static jdk.nashorn.internal.runtime.linker.BrowserJSObjectLinker.JSObjectHandles.JSOBJECT_SETMEMBER;
import static jdk.nashorn.internal.runtime.linker.BrowserJSObjectLinker.JSObjectHandles.JSOBJECT_SETSLOT;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.lookup.MethodHandleFunctionality;
import jdk.nashorn.internal.runtime.JSType;

/**
 * A Dynalink linker to handle web browser built-in JS (DOM etc.) objects.
 */
final class BrowserJSObjectLinker implements TypeBasedGuardingDynamicLinker {
    private static ClassLoader extLoader;
    static {
        extLoader = BrowserJSObjectLinker.class.getClassLoader();
        // in case nashorn is loaded as bootstrap!
        if (extLoader == null) {
            extLoader = ClassLoader.getSystemClassLoader().getParent();
        }
    }

    private static final String JSOBJECT_CLASS = "netscape.javascript.JSObject";
    // not final because this is lazily initialized
    // when we hit a subclass for the first time.
    private static volatile Class<?> jsObjectClass;
    private final NashornBeansLinker nashornBeansLinker;

    BrowserJSObjectLinker(final NashornBeansLinker nashornBeansLinker) {
        this.nashornBeansLinker = nashornBeansLinker;
    }

    @Override
    public boolean canLinkType(final Class<?> type) {
        return canLinkTypeStatic(type);
    }

    static boolean canLinkTypeStatic(final Class<?> type) {
        if (jsObjectClass != null && jsObjectClass.isAssignableFrom(type)) {
            return true;
        }

        // check if this class is a subclass of JSObject
        Class<?> clazz = type;
        while (clazz != null) {
            if (clazz.getClassLoader() == extLoader &&
                clazz.getName().equals(JSOBJECT_CLASS)) {
                jsObjectClass = clazz;
                return true;
            }
            clazz = clazz.getSuperclass();
        }

        return false;
    }

    private static void checkJSObjectClass() {
        assert jsObjectClass != null : JSOBJECT_CLASS + " not found!";
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest request, final LinkerServices linkerServices) throws Exception {
        final LinkRequest requestWithoutContext = request.withoutRuntimeContext(); // Nashorn has no runtime context
        final Object self = requestWithoutContext.getReceiver();
        final CallSiteDescriptor desc = requestWithoutContext.getCallSiteDescriptor();
        checkJSObjectClass();

        if (desc.getNameTokenCount() < 2 || !"dyn".equals(desc.getNameToken(CallSiteDescriptor.SCHEME))) {
            // We only support standard "dyn:*[:*]" operations
            return null;
        }

        GuardedInvocation inv;
        if (jsObjectClass.isInstance(self)) {
            inv = lookup(desc, request, linkerServices);
            inv = inv.replaceMethods(linkerServices.filterInternalObjects(inv.getInvocation()), inv.getGuard());
        } else {
            throw new AssertionError(); // Should never reach here.
        }

        return Bootstrap.asTypeSafeReturn(inv, linkerServices, desc);
    }

    private GuardedInvocation lookup(final CallSiteDescriptor desc, final LinkRequest request, final LinkerServices linkerServices) throws Exception {
        final String operator = desc.tokenizeOperators().get(0);
        final int c = desc.getNameTokenCount();
        GuardedInvocation inv;
        try {
            inv = nashornBeansLinker.getGuardedInvocation(request, linkerServices);
        } catch (final Throwable th) {
            inv = null;
        }

        switch (operator) {
            case "getProp":
            case "getElem":
            case "getMethod":
                return c > 2? findGetMethod(desc, inv) : findGetIndexMethod(inv);
            case "setProp":
            case "setElem":
                return c > 2? findSetMethod(desc, inv) : findSetIndexMethod();
            case "call":
                return findCallMethod(desc);
            default:
                return null;
        }
    }

    private static GuardedInvocation findGetMethod(final CallSiteDescriptor desc, final GuardedInvocation inv) {
        if (inv != null) {
            return inv;
        }
        final String name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
        final MethodHandle getter = MH.insertArguments(JSOBJECT_GETMEMBER, 1, name);
        return new GuardedInvocation(getter, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findGetIndexMethod(final GuardedInvocation inv) {
        final MethodHandle getter = MH.insertArguments(JSOBJECTLINKER_GET, 0, inv.getInvocation());
        return inv.replaceMethods(getter, inv.getGuard());
    }

    private static GuardedInvocation findSetMethod(final CallSiteDescriptor desc, final GuardedInvocation inv) {
        if (inv != null) {
            return inv;
        }
        final MethodHandle getter = MH.insertArguments(JSOBJECT_SETMEMBER, 1, desc.getNameToken(2));
        return new GuardedInvocation(getter, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findSetIndexMethod() {
        return new GuardedInvocation(JSOBJECTLINKER_PUT, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findCallMethod(final CallSiteDescriptor desc) {
        final MethodHandle call = MH.insertArguments(JSOBJECT_CALL, 1, "call");
        return new GuardedInvocation(MH.asCollector(call, Object[].class, desc.getMethodType().parameterCount() - 1), IS_JSOBJECT_GUARD);
    }

    @SuppressWarnings("unused")
    private static boolean isJSObject(final Object self) {
        return jsObjectClass.isInstance(self);
    }

    @SuppressWarnings("unused")
    private static Object get(final MethodHandle fallback, final Object jsobj, final Object key) throws Throwable {
        if (key instanceof Integer) {
            return JSOBJECT_GETSLOT.invokeExact(jsobj, (int)key);
        } else if (key instanceof Number) {
            final int index = getIndex((Number)key);
            if (index > -1) {
                return JSOBJECT_GETSLOT.invokeExact(jsobj, index);
            }
        } else if (isString(key)) {
            final String name = key.toString();
            if (name.indexOf('(') != -1) {
                return fallback.invokeExact(jsobj, (Object) name);
            }
            return JSOBJECT_GETMEMBER.invokeExact(jsobj, name);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static void put(final Object jsobj, final Object key, final Object value) throws Throwable {
        if (key instanceof Integer) {
            JSOBJECT_SETSLOT.invokeExact(jsobj, (int)key, value);
        } else if (key instanceof Number) {
            JSOBJECT_SETSLOT.invokeExact(jsobj, getIndex((Number)key), value);
        } else if (isString(key)) {
            JSOBJECT_SETMEMBER.invokeExact(jsobj, key.toString(), value);
        }
    }

    private static int getIndex(final Number n) {
        final double value = n.doubleValue();
        return JSType.isRepresentableAsInt(value) ? (int)value : -1;
    }

    private static final MethodHandleFunctionality MH = MethodHandleFactory.getFunctionality();
    // method handles of the current class
    private static final MethodHandle IS_JSOBJECT_GUARD  = findOwnMH_S("isJSObject", boolean.class, Object.class);
    private static final MethodHandle JSOBJECTLINKER_GET = findOwnMH_S("get", Object.class, MethodHandle.class, Object.class, Object.class);
    private static final MethodHandle JSOBJECTLINKER_PUT = findOwnMH_S("put", Void.TYPE, Object.class, Object.class, Object.class);

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
            return MH.findStatic(MethodHandles.lookup(), BrowserJSObjectLinker.class, name, MH.type(rtype, types));
    }

    // method handles of netscape.javascript.JSObject class
    // These are in separate class as we lazily initialize these
    // method handles when we hit a subclass of JSObject first time.
    static class JSObjectHandles {
        // method handles of JSObject class
        static final MethodHandle JSOBJECT_GETMEMBER     = findJSObjectMH_V("getMember", Object.class, String.class).asType(MH.type(Object.class, Object.class, String.class));
        static final MethodHandle JSOBJECT_GETSLOT       = findJSObjectMH_V("getSlot", Object.class, int.class).asType(MH.type(Object.class, Object.class, int.class));
        static final MethodHandle JSOBJECT_SETMEMBER     = findJSObjectMH_V("setMember", Void.TYPE, String.class, Object.class).asType(MH.type(Void.TYPE, Object.class, String.class, Object.class));
        static final MethodHandle JSOBJECT_SETSLOT       = findJSObjectMH_V("setSlot", Void.TYPE, int.class, Object.class).asType(MH.type(Void.TYPE, Object.class, int.class, Object.class));
        static final MethodHandle JSOBJECT_CALL          = findJSObjectMH_V("call", Object.class, String.class, Object[].class).asType(MH.type(Object.class, Object.class, String.class, Object[].class));

        private static MethodHandle findJSObjectMH_V(final String name, final Class<?> rtype, final Class<?>... types) {
            checkJSObjectClass();
            return MH.findVirtual(MethodHandles.publicLookup(), jsObjectClass, name, MH.type(rtype, types));
        }
    }
}
