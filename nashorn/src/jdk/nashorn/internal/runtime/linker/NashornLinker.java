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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.Deque;
import java.util.List;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.ConversionComparator;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardingTypeConverterFactory;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.internal.dynalink.support.Guards;
import jdk.nashorn.internal.objects.NativeArray;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * This is the main dynamic linker for Nashorn. It is used for linking all {@link ScriptObject} and its subclasses (this
 * includes {@link ScriptFunction} and its subclasses) as well as {@link Undefined}.
 */
final class NashornLinker implements TypeBasedGuardingDynamicLinker, GuardingTypeConverterFactory, ConversionComparator {
    private static final ClassValue<MethodHandle> ARRAY_CONVERTERS = new ClassValue<MethodHandle>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            return createArrayConverter(type);
        }
    };

    /**
     * Returns true if {@code ScriptObject} is assignable from {@code type}, or it is {@code Undefined}.
     */
    @Override
    public boolean canLinkType(final Class<?> type) {
        return canLinkTypeStatic(type);
    }

    static boolean canLinkTypeStatic(final Class<?> type) {
        return ScriptObject.class.isAssignableFrom(type) || Undefined.class == type;
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest request, final LinkerServices linkerServices) throws Exception {
        final LinkRequest requestWithoutContext = request.withoutRuntimeContext(); // Nashorn has no runtime context
        final Object self = requestWithoutContext.getReceiver();
        final CallSiteDescriptor desc = requestWithoutContext.getCallSiteDescriptor();

        if (desc.getNameTokenCount() < 2 || !"dyn".equals(desc.getNameToken(CallSiteDescriptor.SCHEME))) {
            // We only support standard "dyn:*[:*]" operations
            return null;
        }

        final GuardedInvocation inv;
        if (self instanceof ScriptObject) {
            inv = ((ScriptObject)self).lookup(desc, request);
        } else if (self instanceof Undefined) {
            inv = Undefined.lookup(desc);
        } else {
            throw new AssertionError(); // Should never reach here.
        }

        return Bootstrap.asType(inv, linkerServices, desc);
    }

    @Override
    public GuardedInvocation convertToType(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final GuardedInvocation gi = convertToTypeNoCast(sourceType, targetType);
        return gi == null ? null : gi.asType(MH.type(targetType, sourceType));
    }

    /**
     * Main part of the implementation of {@link GuardingTypeConverterFactory#convertToType(Class, Class)} that doesn't
     * care about adapting the method signature; that's done by the invoking method. Returns either a built-in
     * conversion to primitive (or primitive wrapper) Java types or to String, or a just-in-time generated converter to
     * a SAM type (if the target type is a SAM type).
     * @param sourceType the source type
     * @param targetType the target type
     * @return a guarded invocation that converts from the source type to the target type.
     * @throws Exception if something goes wrong
     */
    private static GuardedInvocation convertToTypeNoCast(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final MethodHandle mh = JavaArgumentConverters.getConverter(targetType);
        if (mh != null) {
            return new GuardedInvocation(mh, canLinkTypeStatic(sourceType) ? null : IS_NASHORN_OR_UNDEFINED_TYPE);
        }

        GuardedInvocation inv = getArrayConverter(sourceType, targetType);
        if(inv != null) {
            return inv;
        }

        return getSamTypeConverter(sourceType, targetType);
    }

    /**
     * Returns a guarded invocation that converts from a source type that is ScriptFunction, or a subclass or a
     * superclass of it) to a SAM type.
     * @param sourceType the source type (presumably ScriptFunction or a subclass or a superclass of it)
     * @param targetType the target type (presumably a SAM type)
     * @return a guarded invocation that converts from the source type to the target SAM type. null is returned if
     * either the source type is neither ScriptFunction, nor a subclass, nor a superclass of it, or if the target type
     * is not a SAM type.
     * @throws Exception if something goes wrong; generally, if there's an issue with creation of the SAM proxy type
     * constructor.
     */
    private static GuardedInvocation getSamTypeConverter(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        // If source type is more generic than ScriptFunction class, we'll need to use a guard
        final boolean isSourceTypeGeneric = sourceType.isAssignableFrom(ScriptFunction.class);

        if ((isSourceTypeGeneric || ScriptFunction.class.isAssignableFrom(sourceType)) && isAutoConvertibleFromFunction(targetType)) {
            final MethodHandle ctor = JavaAdapterFactory.getConstructor(ScriptFunction.class, targetType);
            assert ctor != null; // if isAutoConvertibleFromFunction() returned true, then ctor must exist.
            return new GuardedInvocation(ctor, isSourceTypeGeneric ? IS_SCRIPT_FUNCTION : null);
        }
        return null;
    }

    /**
     * Returns a guarded invocation that converts from a source type that is NativeArray to a Java array or List or
     * Deque type.
     * @param sourceType the source type (presumably NativeArray a superclass of it)
     * @param targetType the target type (presumably an array type, or List or Deque)
     * @return a guarded invocation that converts from the source type to the target type. null is returned if
     * either the source type is neither NativeArray, nor a superclass of it, or if the target type is not an array
     * type, List, or Deque.
     */
    private static GuardedInvocation getArrayConverter(final Class<?> sourceType, final Class<?> targetType) {
        final boolean isSourceTypeNativeArray = sourceType == NativeArray.class;
        // If source type is more generic than ScriptFunction class, we'll need to use a guard
        final boolean isSourceTypeGeneric = !isSourceTypeNativeArray && sourceType.isAssignableFrom(NativeArray.class);

        if (isSourceTypeNativeArray || isSourceTypeGeneric) {
            final MethodHandle guard = isSourceTypeGeneric ? IS_NATIVE_ARRAY : null;
            if(targetType.isArray()) {
                return new GuardedInvocation(ARRAY_CONVERTERS.get(targetType), guard);
            }
            if(targetType == List.class) {
                return new GuardedInvocation(JSType.TO_JAVA_LIST.methodHandle(), guard);
            }
            if(targetType == Deque.class) {
                return new GuardedInvocation(JSType.TO_JAVA_DEQUE.methodHandle(), guard);
            }
        }
        return null;
    }

    private static MethodHandle createArrayConverter(final Class<?> type) {
        assert type.isArray();
        final MethodHandle converter = MH.insertArguments(JSType.TO_JAVA_ARRAY.methodHandle(), 1, type.getComponentType());
        return MH.asType(converter, converter.type().changeReturnType(type));
    }

    private static boolean isAutoConvertibleFromFunction(final Class<?> clazz) {
        return isAbstractClass(clazz) && !ScriptObject.class.isAssignableFrom(clazz) &&
                JavaAdapterFactory.isAutoConvertibleFromFunction(clazz);
    }

    /**
     * Utility method used by few other places in the code. Tests if the class has the abstract modifier and is not an
     * array class. For some reason, array classes have the abstract modifier set in HotSpot JVM, and we don't want to
     * treat array classes as abstract.
     * @param clazz the inspected class
     * @return true if the class is abstract and is not an array type.
     */
    static boolean isAbstractClass(final Class<?> clazz) {
        return Modifier.isAbstract(clazz.getModifiers()) && !clazz.isArray();
    }


    @Override
    public Comparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
        if(sourceType == NativeArray.class) {
            // Prefer lists, as they're less costly to create than arrays.
            if(isList(targetType1)) {
                if(!isList(targetType2)) {
                    return Comparison.TYPE_1_BETTER;
                }
            } else if(isList(targetType2)) {
                return Comparison.TYPE_2_BETTER;
            }
            // Then prefer arrays
            if(targetType1.isArray()) {
                if(!targetType2.isArray()) {
                    return Comparison.TYPE_1_BETTER;
                }
            } else if(targetType2.isArray()) {
                return Comparison.TYPE_2_BETTER;
            }
        }
        if(ScriptObject.class.isAssignableFrom(sourceType)) {
            // Prefer interfaces
            if(targetType1.isInterface()) {
                if(!targetType2.isInterface()) {
                    return Comparison.TYPE_1_BETTER;
                }
            } else if(targetType2.isInterface()) {
                return Comparison.TYPE_2_BETTER;
            }
        }
        return Comparison.INDETERMINATE;
    }

    private static boolean isList(Class<?> clazz) {
        return clazz == List.class || clazz == Deque.class;
    }

    private static final MethodHandle IS_SCRIPT_FUNCTION = Guards.isInstance(ScriptFunction.class, MH.type(Boolean.TYPE, Object.class));
    private static final MethodHandle IS_NATIVE_ARRAY = Guards.isOfClass(NativeArray.class, MH.type(Boolean.TYPE, Object.class));

    private static final MethodHandle IS_NASHORN_OR_UNDEFINED_TYPE = findOwnMH("isNashornTypeOrUndefined",
            Boolean.TYPE, Object.class);

    @SuppressWarnings("unused")
    private static boolean isNashornTypeOrUndefined(final Object obj) {
        return obj instanceof ScriptObject || obj instanceof Undefined;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), NashornLinker.class, name, MH.type(rtype, types));
    }
}

