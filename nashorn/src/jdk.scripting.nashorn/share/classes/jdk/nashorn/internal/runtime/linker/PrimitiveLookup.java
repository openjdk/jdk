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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.support.CallSiteDescriptorFactory;
import jdk.internal.dynalink.support.Guards;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.FindProperty;
import jdk.nashorn.internal.runtime.GlobalConstants;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.UserAccessorProperty;

/**
 * Implements lookup of methods to link for dynamic operations on JavaScript primitive values (booleans, strings, and
 * numbers). This class is only public so it can be accessed by classes in the {@code jdk.nashorn.internal.objects}
 * package.
 */
public final class PrimitiveLookup {

    /** Method handle to link setters on primitive base. See ES5 8.7.2. */
    private static final MethodHandle PRIMITIVE_SETTER = findOwnMH("primitiveSetter",
            MH.type(void.class, ScriptObject.class, Object.class, Object.class, boolean.class, Object.class));


    private PrimitiveLookup() {
    }

    /**
     * Returns a guarded invocation representing the linkage for a dynamic operation on a primitive Java value.
     * @param request the link request for the dynamic call site.
     * @param receiverClass the class of the receiver value (e.g., {@link java.lang.Boolean}, {@link java.lang.String} etc.)
     * @param wrappedReceiver a transient JavaScript native wrapper object created as the object proxy for the primitive
     * value; see ECMAScript 5.1, section 8.7.1 for discussion of using {@code [[Get]]} on a property reference with a
     * primitive base value. This instance will be used to delegate actual lookup.
     * @param wrapFilter A method handle that takes a primitive value of type specified in the {@code receiverClass} and
     * creates a transient native wrapper of the same type as {@code wrappedReceiver} for subsequent invocations of the
     * method - it will be combined into the returned invocation as an argument filter on the receiver.
     * @return a guarded invocation representing the operation at the call site when performed on a JavaScript primitive
     * @param protoFilter A method handle that walks up the proto chain of this receiver object
     * type {@code receiverClass}.
     */
    public static GuardedInvocation lookupPrimitive(final LinkRequest request, final Class<?> receiverClass,
                                                    final ScriptObject wrappedReceiver, final MethodHandle wrapFilter,
                                                    final MethodHandle protoFilter) {
        return lookupPrimitive(request, Guards.getInstanceOfGuard(receiverClass), wrappedReceiver, wrapFilter, protoFilter);
    }

    /**
     * Returns a guarded invocation representing the linkage for a dynamic operation on a primitive Java value.
     * @param request the link request for the dynamic call site.
     * @param guard an explicit guard that will be used for the returned guarded invocation.
     * @param wrappedReceiver a transient JavaScript native wrapper object created as the object proxy for the primitive
     * value; see ECMAScript 5.1, section 8.7.1 for discussion of using {@code [[Get]]} on a property reference with a
     * primitive base value. This instance will be used to delegate actual lookup.
     * @param wrapFilter A method handle that takes a primitive value of type guarded by the {@code guard} and
     * creates a transient native wrapper of the same type as {@code wrappedReceiver} for subsequent invocations of the
     * method - it will be combined into the returned invocation as an argument filter on the receiver.
     * @param protoFilter A method handle that walks up the proto chain of this receiver object
     * @return a guarded invocation representing the operation at the call site when performed on a JavaScript primitive
     * type (that is implied by both {@code guard} and {@code wrappedReceiver}).
     */
    public static GuardedInvocation lookupPrimitive(final LinkRequest request, final MethodHandle guard,
                                                    final ScriptObject wrappedReceiver, final MethodHandle wrapFilter,
                                                    final MethodHandle protoFilter) {
        final CallSiteDescriptor desc = request.getCallSiteDescriptor();
        final String name;
        final FindProperty find;

        if (desc.getNameTokenCount() > 2) {
            name = desc.getNameToken(CallSiteDescriptor.NAME_OPERAND);
            find = wrappedReceiver.findProperty(name, true);
        } else {
            name = null;
            find = null;
        }

        final String firstOp = CallSiteDescriptorFactory.tokenizeOperators(desc).get(0);

        switch (firstOp) {
        case "getProp":
        case "getElem":
        case "getMethod":
            //checks whether the property name is hard-coded in the call-site (i.e. a getProp vs a getElem, or setProp vs setElem)
            //if it is we can make assumptions on the property: that if it is not defined on primitive wrapper itself it never will be.
            //so in that case we can skip creation of primitive wrapper and start our search with the prototype.
            if (name != null) {
                if (find == null) {
                    // Give up early, give chance to BeanLinker and NashornBottomLinker to deal with it.
                    return null;
                }

                final SwitchPoint sp = find.getProperty().getBuiltinSwitchPoint(); //can use this instead of proto filter
                if (sp instanceof Context.BuiltinSwitchPoint && !sp.hasBeenInvalidated()) {
                    return new GuardedInvocation(GlobalConstants.staticConstantGetter(find.getObjectValue()), guard, sp, null);
                }

                if (find.isInherited() && !(find.getProperty() instanceof UserAccessorProperty)) {
                    // If property is found in the prototype object bind the method handle directly to
                    // the proto filter instead of going through wrapper instantiation below.
                    final ScriptObject proto = wrappedReceiver.getProto();
                    final GuardedInvocation link = proto.lookup(desc, request);

                    if (link != null) {
                        final MethodHandle invocation = link.getInvocation(); //this contains the builtin switchpoint
                        final MethodHandle adaptedInvocation = MH.asType(invocation, invocation.type().changeParameterType(0, Object.class));
                        final MethodHandle method = MH.filterArguments(adaptedInvocation, 0, protoFilter);
                        final MethodHandle protoGuard = MH.filterArguments(link.getGuard(), 0, protoFilter);
                        return new GuardedInvocation(method, NashornGuards.combineGuards(guard, protoGuard));
                    }
                }
            }
            break;
        case "setProp":
        case "setElem":
            return getPrimitiveSetter(name, guard, wrapFilter, NashornCallSiteDescriptor.isStrict(desc));
        default:
            break;
        }

        final GuardedInvocation link = wrappedReceiver.lookup(desc, request);
        if (link != null) {
            MethodHandle method = link.getInvocation();
            final Class<?> receiverType = method.type().parameterType(0);
            if (receiverType != Object.class) {
                final MethodType wrapType = wrapFilter.type();
                assert receiverType.isAssignableFrom(wrapType.returnType());
                method = MH.filterArguments(method, 0, MH.asType(wrapFilter, wrapType.changeReturnType(receiverType)));
            }

            return new GuardedInvocation(method, guard, link.getSwitchPoints(), null);
        }

        return null;
    }

    private static GuardedInvocation getPrimitiveSetter(final String name, final MethodHandle guard,
                                                        final MethodHandle wrapFilter, final boolean isStrict) {
        MethodHandle filter = MH.asType(wrapFilter, wrapFilter.type().changeReturnType(ScriptObject.class));
        final MethodHandle target;

        if (name == null) {
            filter = MH.dropArguments(filter, 1, Object.class, Object.class);
            target = MH.insertArguments(PRIMITIVE_SETTER, 3, isStrict);
        } else {
            filter = MH.dropArguments(filter, 1, Object.class);
            target = MH.insertArguments(PRIMITIVE_SETTER, 2, name, isStrict);
        }

        return new GuardedInvocation(MH.foldArguments(target, filter), guard);
    }


    @SuppressWarnings("unused")
    private static void primitiveSetter(final ScriptObject wrappedSelf, final Object self, final Object key,
                                        final boolean strict, final Object value) {
        // See ES5.1 8.7.2 PutValue (V, W)
        final String name = JSType.toString(key);
        final FindProperty find = wrappedSelf.findProperty(name, true);
        if (find == null || !(find.getProperty() instanceof UserAccessorProperty) || !find.getProperty().isWritable()) {
            if (strict) {
                throw typeError("property.not.writable", name, ScriptRuntime.safeToString(self));
            }
            return;
        }
        // property found and is a UserAccessorProperty
        find.setValue(value, strict);
    }

    private static MethodHandle findOwnMH(final String name, final MethodType type) {
        return MH.findStatic(MethodHandles.lookup(), PrimitiveLookup.class, name, type);
    }
}
