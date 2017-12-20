/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Objects;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.lookup.MethodHandleFunctionality;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.objects.Global;

/**
 * A Dynalink linker to handle web browser built-in JS (DOM etc.) objects as well
 * as ScriptObjects from other Nashorn contexts.
 */
final class JSObjectLinker implements TypeBasedGuardingDynamicLinker {
    private final NashornBeansLinker nashornBeansLinker;

    JSObjectLinker(final NashornBeansLinker nashornBeansLinker) {
        this.nashornBeansLinker = nashornBeansLinker;
    }

    @Override
    public boolean canLinkType(final Class<?> type) {
        return canLinkTypeStatic(type);
    }

    private static boolean canLinkTypeStatic(final Class<?> type) {
        // can link JSObject also handles Map (this includes Bindings) to make
        // sure those are not JSObjects.
        return Map.class.isAssignableFrom(type) ||
               JSObject.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest request, final LinkerServices linkerServices) throws Exception {
        final Object self = request.getReceiver();
        final CallSiteDescriptor desc = request.getCallSiteDescriptor();
        if (self == null || !canLinkTypeStatic(self.getClass())) {
            return null;
        }

        GuardedInvocation inv;
        if (self instanceof JSObject) {
            inv = lookup(desc, request, linkerServices);
            inv = inv.replaceMethods(linkerServices.filterInternalObjects(inv.getInvocation()), inv.getGuard());
        } else if (self instanceof Map) {
            // guard to make sure the Map or Bindings does not turn into JSObject later!
            final GuardedInvocation beanInv = nashornBeansLinker.getGuardedInvocation(request, linkerServices);
            inv = new GuardedInvocation(beanInv.getInvocation(),
                NashornGuards.combineGuards(beanInv.getGuard(), NashornGuards.getNotJSObjectGuard()));
        } else {
            throw new AssertionError("got instanceof: " + self.getClass()); // Should never reach here.
        }

        return Bootstrap.asTypeSafeReturn(inv, linkerServices, desc);
    }

    private GuardedInvocation lookup(final CallSiteDescriptor desc, final LinkRequest request, final LinkerServices linkerServices) throws Exception {
        final Operation op = NashornCallSiteDescriptor.getBaseOperation(desc);
        if (op instanceof StandardOperation) {
            final String name = NashornCallSiteDescriptor.getOperand(desc);
            switch ((StandardOperation)op) {
            case GET:
                if (NashornCallSiteDescriptor.hasStandardNamespace(desc)) {
                    if (name != null) {
                        return findGetMethod(name);
                    }
                    // For indexed get, we want get GuardedInvocation beans linker and pass it.
                    // JSObjectLinker.get uses this fallback getter for explicit signature method access.
                    return findGetIndexMethod(nashornBeansLinker.getGuardedInvocation(request, linkerServices));
                }
                break;
            case SET:
                if (NashornCallSiteDescriptor.hasStandardNamespace(desc)) {
                    return name != null ? findSetMethod(name) : findSetIndexMethod();
                }
                break;
            case REMOVE:
                if (NashornCallSiteDescriptor.hasStandardNamespace(desc)) {
                    return new GuardedInvocation(
                            name == null ? JSOBJECTLINKER_DEL : MH.insertArguments(JSOBJECTLINKER_DEL, 1, name),
                            IS_JSOBJECT_GUARD);
                }
                break;
            case CALL:
                return findCallMethod(desc);
            case NEW:
                return findNewMethod(desc);
            default:
            }
        }
        return null;
    }

    private static GuardedInvocation findGetMethod(final String name) {
        final MethodHandle getter = MH.insertArguments(JSOBJECT_GETMEMBER, 1, name);
        return new GuardedInvocation(getter, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findGetIndexMethod(final GuardedInvocation inv) {
        final MethodHandle getter = MH.insertArguments(JSOBJECTLINKER_GET, 0, inv.getInvocation());
        return inv.replaceMethods(getter, inv.getGuard());
    }

    private static GuardedInvocation findSetMethod(final String name) {
        final MethodHandle getter = MH.insertArguments(JSOBJECT_SETMEMBER, 1, name);
        return new GuardedInvocation(getter, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findSetIndexMethod() {
        return new GuardedInvocation(JSOBJECTLINKER_PUT, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findCallMethod(final CallSiteDescriptor desc) {
        MethodHandle mh = NashornCallSiteDescriptor.isScope(desc)? JSOBJECT_SCOPE_CALL : JSOBJECT_CALL;
        if (NashornCallSiteDescriptor.isApplyToCall(desc)) {
            mh = MH.insertArguments(JSOBJECT_CALL_TO_APPLY, 0, mh);
        }
        final MethodType type = desc.getMethodType();
        mh = type.parameterType(type.parameterCount() - 1) == Object[].class ?
                mh :
                MH.asCollector(mh, Object[].class, type.parameterCount() - 2);
        return new GuardedInvocation(mh, IS_JSOBJECT_GUARD);
    }

    private static GuardedInvocation findNewMethod(final CallSiteDescriptor desc) {
        final MethodHandle func = MH.asCollector(JSOBJECT_NEW, Object[].class, desc.getMethodType().parameterCount() - 1);
        return new GuardedInvocation(func, IS_JSOBJECT_GUARD);
    }

    @SuppressWarnings("unused")
    private static boolean isJSObject(final Object self) {
        return self instanceof JSObject;
    }

    @SuppressWarnings("unused")
    private static Object get(final MethodHandle fallback, final Object jsobj, final Object key)
        throws Throwable {
        if (key instanceof Integer) {
            return ((JSObject)jsobj).getSlot((Integer)key);
        } else if (key instanceof Number) {
            final int index = getIndex((Number)key);
            if (index > -1) {
                return ((JSObject)jsobj).getSlot(index);
            } else {
                return ((JSObject)jsobj).getMember(JSType.toString(key));
            }
        } else if (isString(key)) {
            final String name = key.toString();
            // get with method name and signature. delegate it to beans linker!
            if (name.indexOf('(') != -1) {
                return fallback.invokeExact(jsobj, (Object) name);
            }
            return ((JSObject)jsobj).getMember(name);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static void put(final Object jsobj, final Object key, final Object value) {
        if (key instanceof Integer) {
            ((JSObject)jsobj).setSlot((Integer)key, value);
        } else if (key instanceof Number) {
            final int index = getIndex((Number)key);
            if (index > -1) {
                ((JSObject)jsobj).setSlot(index, value);
            } else {
                ((JSObject)jsobj).setMember(JSType.toString(key), value);
            }
        } else if (isString(key)) {
            ((JSObject)jsobj).setMember(key.toString(), value);
        }
    }

    @SuppressWarnings("unused")
    private static boolean del(final Object jsobj, final Object key) {
        if (jsobj instanceof ScriptObjectMirror) {
            return ((ScriptObjectMirror)jsobj).delete(key);
        }
        ((JSObject) jsobj).removeMember(Objects.toString(key));
        return true;
    }

    private static int getIndex(final Number n) {
        final double value = n.doubleValue();
        return JSType.isRepresentableAsInt(value) ? (int)value : -1;
    }

    @SuppressWarnings("unused")
    private static Object callToApply(final MethodHandle mh, final JSObject obj, final Object thiz, final Object... args) {
        assert args.length >= 1;
        final Object   receiver  = args[0];
        final Object[] arguments = new Object[args.length - 1];
        System.arraycopy(args, 1, arguments, 0, arguments.length);
        try {
            return mh.invokeExact(obj, thiz, new Object[] { receiver, arguments });
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // This is used when a JSObject is called as scope call to do undefined -> Global this translation.
    @SuppressWarnings("unused")
    private static Object jsObjectScopeCall(final JSObject jsObj, final Object thiz, final Object[] args) {
        final Object modifiedThiz;
        if (thiz == ScriptRuntime.UNDEFINED && !jsObj.isStrictFunction()) {
            final Global global = Context.getGlobal();
            modifiedThiz = ScriptObjectMirror.wrap(global, global);
        } else {
            modifiedThiz = thiz;
        }
        return jsObj.call(modifiedThiz, args);
    }

    private static final MethodHandleFunctionality MH = MethodHandleFactory.getFunctionality();

    // method handles of the current class
    private static final MethodHandle IS_JSOBJECT_GUARD  = findOwnMH_S("isJSObject", boolean.class, Object.class);
    private static final MethodHandle JSOBJECTLINKER_GET = findOwnMH_S("get", Object.class, MethodHandle.class, Object.class, Object.class);
    private static final MethodHandle JSOBJECTLINKER_PUT = findOwnMH_S("put", Void.TYPE, Object.class, Object.class, Object.class);
    private static final MethodHandle JSOBJECTLINKER_DEL = findOwnMH_S("del", boolean.class, Object.class, Object.class);

    // method handles of JSObject class
    private static final MethodHandle JSOBJECT_GETMEMBER     = findJSObjectMH_V("getMember", Object.class, String.class);
    private static final MethodHandle JSOBJECT_SETMEMBER     = findJSObjectMH_V("setMember", Void.TYPE, String.class, Object.class);
    private static final MethodHandle JSOBJECT_CALL          = findJSObjectMH_V("call", Object.class, Object.class, Object[].class);
    private static final MethodHandle JSOBJECT_SCOPE_CALL    = findOwnMH_S("jsObjectScopeCall", Object.class, JSObject.class, Object.class, Object[].class);
    private static final MethodHandle JSOBJECT_CALL_TO_APPLY = findOwnMH_S("callToApply", Object.class, MethodHandle.class, JSObject.class, Object.class, Object[].class);
    private static final MethodHandle JSOBJECT_NEW           = findJSObjectMH_V("newObject", Object.class, Object[].class);

    private static MethodHandle findJSObjectMH_V(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findVirtual(MethodHandles.lookup(), JSObject.class, name, MH.type(rtype, types));
    }

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), JSObjectLinker.class, name, MH.type(rtype, types));
    }
}
