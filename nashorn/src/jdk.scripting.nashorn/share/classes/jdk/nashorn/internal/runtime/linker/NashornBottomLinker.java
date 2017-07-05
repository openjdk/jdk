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
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.NamedOperation;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.beans.BeansLinker;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.GuardingTypeConverterFactory;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.support.Lookup;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.UnwarrantedOptimismException;

/**
 * Nashorn bottom linker; used as a last-resort catch-all linker for all linking requests that fall through all other
 * linkers (see how {@link Bootstrap} class configures the dynamic linker in its static initializer). It will throw
 * appropriate ECMAScript errors for attempts to invoke operations on {@code null}, link no-op property getters and
 * setters for Java objects that couldn't be linked by any other linker, and throw appropriate ECMAScript errors for
 * attempts to invoke arbitrary Java objects as functions or constructors.
 */
final class NashornBottomLinker implements GuardingDynamicLinker, GuardingTypeConverterFactory {

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

        return linkBean(linkRequest);
    }

    private static final MethodHandle EMPTY_PROP_GETTER =
            MH.dropArguments(MH.constant(Object.class, UNDEFINED), 0, Object.class);
    private static final MethodHandle EMPTY_ELEM_GETTER =
            MH.dropArguments(EMPTY_PROP_GETTER, 0, Object.class);
    private static final MethodHandle EMPTY_PROP_SETTER =
            MH.asType(EMPTY_ELEM_GETTER, EMPTY_ELEM_GETTER.type().changeReturnType(void.class));
    private static final MethodHandle EMPTY_ELEM_SETTER =
            MH.dropArguments(EMPTY_PROP_SETTER, 0, Object.class);

    private static final MethodHandle THROW_NO_SUCH_FUNCTION;
    private static final MethodHandle THROW_STRICT_PROPERTY_SETTER;
    private static final MethodHandle THROW_OPTIMISTIC_UNDEFINED;

    static {
        final Lookup lookup = new Lookup(MethodHandles.lookup());
        THROW_NO_SUCH_FUNCTION = lookup.findOwnStatic("throwNoSuchFunction", Object.class, Object.class, Object.class);
        THROW_STRICT_PROPERTY_SETTER = lookup.findOwnStatic("throwStrictPropertySetter", void.class, Object.class, Object.class);
        THROW_OPTIMISTIC_UNDEFINED = lookup.findOwnStatic("throwOptimisticUndefined", Object.class, int.class);
    }

    private static GuardedInvocation linkBean(final LinkRequest linkRequest) throws Exception {
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        final Object self = linkRequest.getReceiver();
        switch (NashornCallSiteDescriptor.getFirstStandardOperation(desc)) {
        case NEW:
            if(BeansLinker.isDynamicConstructor(self)) {
                throw typeError("no.constructor.matches.args", ScriptRuntime.safeToString(self));
            }
            if(BeansLinker.isDynamicMethod(self)) {
                throw typeError("method.not.constructor", ScriptRuntime.safeToString(self));
            }
            throw typeError("not.a.function", NashornCallSiteDescriptor.getFunctionErrorMessage(desc, self));
        case CALL:
            if(BeansLinker.isDynamicConstructor(self)) {
                throw typeError("constructor.requires.new", ScriptRuntime.safeToString(self));
            }
            if(BeansLinker.isDynamicMethod(self)) {
                throw typeError("no.method.matches.args", ScriptRuntime.safeToString(self));
            }
            throw typeError("not.a.function", NashornCallSiteDescriptor.getFunctionErrorMessage(desc, self));
        default:
            // Everything else is supposed to have been already handled by Bootstrap.beansLinker
            // delegating to linkNoSuchBeanMember
            throw new AssertionError("unknown call type " + desc);
        }
    }

    static MethodHandle linkMissingBeanMember(final LinkRequest linkRequest, final LinkerServices linkerServices) throws Exception {
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        final StandardOperation op = NashornCallSiteDescriptor.getFirstStandardOperation(desc);
        if (op != null) {
            final String operand = NashornCallSiteDescriptor.getOperand(desc);
            switch (op) {
            case CALL_METHOD:
                return adaptThrower(bindOperand(THROW_NO_SUCH_FUNCTION, operand), desc);
            case GET_METHOD:
            case GET_PROPERTY:
            case GET_ELEMENT: {
                if (NashornCallSiteDescriptor.isOptimistic(desc)) {
                    return adaptThrower(MethodHandles.insertArguments(THROW_OPTIMISTIC_UNDEFINED, 0, NashornCallSiteDescriptor.getProgramPoint(desc)), desc);
                }
                if (NashornCallSiteDescriptor.getOperand(desc) != null) {
                    return getInvocation(EMPTY_PROP_GETTER, linkerServices, desc);
                }
                return getInvocation(EMPTY_ELEM_GETTER, linkerServices, desc);
            }
            case SET_PROPERTY:
            case SET_ELEMENT:
                final boolean strict = NashornCallSiteDescriptor.isStrict(desc);
                if (strict) {
                    return adaptThrower(bindOperand(THROW_STRICT_PROPERTY_SETTER, operand), desc);
                }
                if (NashornCallSiteDescriptor.getOperand(desc) != null) {
                    return getInvocation(EMPTY_PROP_SETTER, linkerServices, desc);
                }
                return getInvocation(EMPTY_ELEM_SETTER, linkerServices, desc);
            default:
            }
        }
        throw new AssertionError("unknown call type " + desc);
    }

    private static MethodHandle bindOperand(final MethodHandle handle, final String operand) {
        return operand == null ? handle : MethodHandles.insertArguments(handle, 1, operand);
    }

    private static MethodHandle adaptThrower(final MethodHandle handle, final CallSiteDescriptor desc) {
        final MethodType targetType = desc.getMethodType();
        final int paramCount = handle.type().parameterCount();
        return MethodHandles
                .dropArguments(handle, paramCount, targetType.parameterList().subList(paramCount, targetType.parameterCount()))
                .asType(targetType);
    }

    @SuppressWarnings("unused")
    private static Object throwNoSuchFunction(final Object self, final Object name) {
        throw createTypeError(self, name, "no.such.function");
    }

    @SuppressWarnings("unused")
    private static void throwStrictPropertySetter(final Object self, final Object name) {
        throw createTypeError(self, name, "cant.set.property");
    }

    private static ECMAException createTypeError(final Object self, final Object name, final String msg) {
        return typeError(msg, String.valueOf(name), ScriptRuntime.safeToString(self));
    }

    @SuppressWarnings("unused")
    private static Object throwOptimisticUndefined(final int programPoint) {
        throw new UnwarrantedOptimismException(UNDEFINED, programPoint, Type.OBJECT);
    }

    @Override
    public GuardedInvocation convertToType(final Class<?> sourceType, final Class<?> targetType, final Supplier<MethodHandles.Lookup> lookupSupplier) throws Exception {
        final GuardedInvocation gi = convertToTypeNoCast(sourceType, targetType);
        return gi == null ? null : gi.asType(MH.type(targetType, sourceType));
    }

    /**
     * Main part of the implementation of {@link GuardingTypeConverterFactory#convertToType} that doesn't
     * care about adapting the method signature; that's done by the invoking method. Returns conversion
     * from Object to String/number/boolean (JS primitive types).
     * @param sourceType the source type
     * @param targetType the target type
     * @return a guarded invocation that converts from the source type to the target type.
     * @throws Exception if something goes wrong
     */
    private static GuardedInvocation convertToTypeNoCast(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final MethodHandle mh = CONVERTERS.get(targetType);
        if (mh != null) {
            return new GuardedInvocation(mh);
        }

        return null;
    }

    private static MethodHandle getInvocation(final MethodHandle handle, final LinkerServices linkerServices, final CallSiteDescriptor desc) {
        return linkerServices.asTypeLosslessReturn(handle, desc.getMethodType());
    }

    // Used solely in an assertion to figure out if the object we get here is something we in fact expect. Objects
    // linked by NashornLinker should never reach here.
    private static boolean isExpectedObject(final Object obj) {
        return !(NashornLinker.canLinkTypeStatic(obj.getClass()));
    }

    private static GuardedInvocation linkNull(final LinkRequest linkRequest) {
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        switch (NashornCallSiteDescriptor.getFirstStandardOperation(desc)) {
        case NEW:
        case CALL:
            throw typeError("not.a.function", "null");
        case CALL_METHOD:
        case GET_METHOD:
            throw typeError("no.such.function", getArgument(linkRequest), "null");
        case GET_PROPERTY:
        case GET_ELEMENT:
            throw typeError("cant.get.property", getArgument(linkRequest), "null");
        case SET_PROPERTY:
        case SET_ELEMENT:
            throw typeError("cant.set.property", getArgument(linkRequest), "null");
        default:
            throw new AssertionError("unknown call type " + desc);
        }
    }

    private static final Map<Class<?>, MethodHandle> CONVERTERS = new HashMap<>();
    static {
        CONVERTERS.put(boolean.class, JSType.TO_BOOLEAN.methodHandle());
        CONVERTERS.put(double.class, JSType.TO_NUMBER.methodHandle());
        CONVERTERS.put(int.class, JSType.TO_INTEGER.methodHandle());
        CONVERTERS.put(long.class, JSType.TO_LONG.methodHandle());
        CONVERTERS.put(String.class, JSType.TO_STRING.methodHandle());
    }

    private static String getArgument(final LinkRequest linkRequest) {
        final Operation op = linkRequest.getCallSiteDescriptor().getOperation();
        if (op instanceof NamedOperation) {
            return ((NamedOperation)op).getName().toString();
        }
        return ScriptRuntime.safeToString(linkRequest.getArguments()[1]);
    }
}
