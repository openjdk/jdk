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
import jdk.internal.dynalink.linker.ConversionComparator;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardedTypeConversion;
import jdk.internal.dynalink.linker.GuardingTypeConverterFactory;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.internal.dynalink.support.TypeUtilities;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.GlobalObject;

/**
 * Internal linker for String, Boolean, and Number objects, only ever used by Nashorn engine and not exposed to other
 * engines. It is used for treatment of strings, boolean, and numbers as JavaScript primitives. Also provides ECMAScript
 * primitive type conversions for these types when linking to Java methods.
 */
final class NashornPrimitiveLinker implements TypeBasedGuardingDynamicLinker, GuardingTypeConverterFactory, ConversionComparator {
    @Override
    public boolean canLinkType(final Class<?> type) {
        return canLinkTypeStatic(type);
    }

    private static boolean canLinkTypeStatic(final Class<?> type) {
        return type == String.class || type == Boolean.class || type == ConsString.class || Number.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest origRequest, final LinkerServices linkerServices)
            throws Exception {
        final LinkRequest request = origRequest.withoutRuntimeContext(); // Nashorn has no runtime context

        final Object self = request.getReceiver();
        final GlobalObject global = (GlobalObject) Context.getGlobal();
        final NashornCallSiteDescriptor desc = (NashornCallSiteDescriptor) request.getCallSiteDescriptor();

        return Bootstrap.asType(global.primitiveLookup(request, self), linkerServices, desc);
    }

    /**
     * This implementation of type converter factory will pretty much allow implicit conversions of anything to anything
     * else that's allowed among JavaScript primitive types (string to number, boolean to string, etc.)
     * @param sourceType the type to convert from
     * @param targetType the type to convert to
     * @return a conditional converter from source to target type
     */
    @Override
    public GuardedTypeConversion convertToType(final Class<?> sourceType, final Class<?> targetType) {
        final MethodHandle mh = JavaArgumentConverters.getConverter(targetType);
        if (mh == null) {
            return null;
        }

        return new GuardedTypeConversion(new GuardedInvocation(mh, canLinkTypeStatic(sourceType) ? null : GUARD_PRIMITIVE).asType(mh.type().changeParameterType(0, sourceType)), true);
    }

    /**
     * Implements the somewhat involved prioritization of JavaScript primitive types conversions. Instead of explaining
     * it here in prose, just follow the source code comments.
     * @param sourceType the source type to convert from
     * @param targetType1 one candidate target type
     * @param targetType2 another candidate target type
     * @return one of {@link jdk.internal.dynalink.linker.ConversionComparator.Comparison} values signifying which
     * target type should be favored for conversion.
     */
    @Override
    public Comparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
        final Class<?> wrapper1 = getWrapperTypeOrSelf(targetType1);
        if (sourceType == wrapper1) {
            // Source type exactly matches target 1
            return Comparison.TYPE_1_BETTER;
        }
        final Class<?> wrapper2 = getWrapperTypeOrSelf(targetType2);
        if (sourceType == wrapper2) {
            // Source type exactly matches target 2
            return Comparison.TYPE_2_BETTER;
        }

        if (Number.class.isAssignableFrom(sourceType)) {
            // If exactly one of the targets is a number, pick it.
            if (Number.class.isAssignableFrom(wrapper1)) {
                if (!Number.class.isAssignableFrom(wrapper2)) {
                    return Comparison.TYPE_1_BETTER;
                }
            } else if (Number.class.isAssignableFrom(wrapper2)) {
                return Comparison.TYPE_2_BETTER;
            }

            // If exactly one of the targets is a character, pick it. Numbers can be reasonably converted to chars using
            // the UTF-16 values.
            if (Character.class == wrapper1) {
                return Comparison.TYPE_1_BETTER;
            } else if (Character.class == wrapper2) {
                return Comparison.TYPE_2_BETTER;
            }

            // For all other cases, we fall through to the next if statement - not that we repeat the condition in it
            // too so if we entered this branch, we'll enter the below if statement too.
        }

        if (sourceType == String.class || sourceType == Boolean.class || Number.class.isAssignableFrom(sourceType)) {
            // Treat wrappers as primitives.
            final Class<?> primitiveType1 = getPrimitiveTypeOrSelf(targetType1);
            final Class<?> primitiveType2 = getPrimitiveTypeOrSelf(targetType2);
            // Basically, choose the widest possible primitive type. (First "if" returning TYPE_2_BETTER is correct;
            // when faced with a choice between double and int, choose double).
            if (TypeUtilities.isMethodInvocationConvertible(primitiveType1, primitiveType2)) {
                return Comparison.TYPE_2_BETTER;
            } else if (TypeUtilities.isMethodInvocationConvertible(primitiveType2, primitiveType1)) {
                return Comparison.TYPE_1_BETTER;
            }
            // Ok, at this point we're out of possible number conversions, so try strings. A String can represent any
            // value without loss, so if one of the potential targets is string, go for it.
            if (targetType1 == String.class) {
                return Comparison.TYPE_1_BETTER;
            }
            if (targetType2 == String.class) {
                return Comparison.TYPE_2_BETTER;
            }
        }

        return Comparison.INDETERMINATE;
    }

    private static Class<?> getPrimitiveTypeOrSelf(final Class<?> type) {
        final Class<?> primitive = TypeUtilities.getPrimitiveType(type);
        return primitive == null ? type : primitive;
    }

    private static Class<?> getWrapperTypeOrSelf(final Class<?> type) {
        final Class<?> wrapper = TypeUtilities.getWrapperType(type);
        return wrapper == null ? type : wrapper;
    }

    @SuppressWarnings("unused")
    private static boolean isJavaScriptPrimitive(final Object o) {
        return o instanceof String || o instanceof Boolean || o instanceof Number || o instanceof ConsString || o == null;
    }

    private static final MethodHandle GUARD_PRIMITIVE = findOwnMH("isJavaScriptPrimitive", boolean.class, Object.class);

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), NashornPrimitiveLinker.class, name, MH.type(rtype, types));
    }
}
