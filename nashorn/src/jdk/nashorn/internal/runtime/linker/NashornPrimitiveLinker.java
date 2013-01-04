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
import java.lang.invoke.MethodHandles;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.GlobalObject;
import org.dynalang.dynalink.beans.BeansLinker;
import org.dynalang.dynalink.beans.StaticClass;
import org.dynalang.dynalink.linker.ConversionComparator;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingDynamicLinker;
import org.dynalang.dynalink.linker.GuardingTypeConverterFactory;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.Guards;
import org.dynalang.dynalink.support.TypeUtilities;

/**
 * Internal linker for String, Boolean, Number, and {@link StaticClass} objects, only ever used by Nashorn engine and
 * not exposed to other engines. It is used for treatment of strings, boolean, and numbers, as JavaScript primitives,
 * as well as for extending the "new" operator on StaticClass in order to be able to instantiate interfaces and abstract
 * classes by passing a ScriptObject or ScriptFunction as their implementation, e.g.:
 * <pre>
 *   var r = new Runnable() { run: function() { print("Hello World" } }
 * </pre>
 * or even, for SAM types:
 * <pre>
 *   var r = new Runnable(function() { print("Hello World" })
 * </pre>
 */
class NashornPrimitiveLinker implements TypeBasedGuardingDynamicLinker, GuardingTypeConverterFactory, ConversionComparator {
    private static final GuardingDynamicLinker staticClassLinker = BeansLinker.getLinkerForClass(StaticClass.class);

    @Override
    public boolean canLinkType(final Class<?> type) {
        return canLinkTypeStatic(type);
    }

    private static boolean canLinkTypeStatic(final Class<?> type) {
        return type == String.class || type == Boolean.class || type == StaticClass.class ||
                type == ConsString.class || Number.class.isAssignableFrom(type);
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest origRequest, final LinkerServices linkerServices)
            throws Exception {
        final LinkRequest request = origRequest.withoutRuntimeContext(); // Nashorn has no runtime context

        final Object self = request.getReceiver();
        final GlobalObject global = (GlobalObject) Context.getGlobal();
        final NashornCallSiteDescriptor desc = (NashornCallSiteDescriptor) request.getCallSiteDescriptor();

        if (self instanceof Number) {
            return Bootstrap.asType(global.numberLookup(desc, (Number) self), linkerServices, desc);
        } else if (self instanceof String || self instanceof ConsString) {
           return Bootstrap.asType(global.stringLookup(desc, (CharSequence) self), linkerServices, desc);
        } else if (self instanceof Boolean) {
            return Bootstrap.asType(global.booleanLookup(desc, (Boolean) self), linkerServices, desc);
        } else if (self instanceof StaticClass) {
            // We intercept "new" on StaticClass instances to provide additional capabilities
            if ("new".equals(desc.getOperator())) {
                final Class<?> receiverClass = ((StaticClass) self).getRepresentedClass();
                // Is the class abstract? (This includes interfaces.)
                if (JavaAdapterFactory.isAbstractClass(receiverClass)) {
                    // Change this link request into a link request on the adapter class.
                    final Object[] args = request.getArguments();
                    args[0] = JavaAdapterFactory.getAdapterClassFor(receiverClass);
                    final LinkRequest adapterRequest = request.replaceArguments(request.getCallSiteDescriptor(), args);
                    final GuardedInvocation gi = checkNullConstructor(
                            staticClassLinker.getGuardedInvocation(adapterRequest, linkerServices), receiverClass);
                    // Finally, modify the guard to test for the original abstract class.
                    return gi.replaceMethods(gi.getInvocation(), Guards.getIdentityGuard(self));
                }
                // If the class was not abstract, just link to the ordinary constructor. Make an additional check to
                // ensure we have a constructor. We could just fall through to the next "return" statement, except we
                // also insert a call to checkNullConstructor() which throws an error with a more intuitive message when
                // no suitable constructor is found.
                return checkNullConstructor(staticClassLinker.getGuardedInvocation(request, linkerServices), receiverClass);
            }
            // In case this was not a "new" operation, just link with the standard StaticClass linker.
            return staticClassLinker.getGuardedInvocation(request, linkerServices);
        }

        throw new AssertionError(); // Can't reach here
    }

    private static GuardedInvocation checkNullConstructor(final GuardedInvocation ctorInvocation, final Class<?> receiverClass) {
        if(ctorInvocation == null) {
            ECMAErrors.typeError(Context.getGlobal(), "no.constructor.matches.args", receiverClass.getName());
        }
        return ctorInvocation;
    }

    /**
     * This implementation of type converter factory will pretty much allow implicit conversions of anything to anything
     * else that's allowed among JavaScript primitive types (string to number, boolean to string, etc.)
     * @param sourceType the type to convert from
     * @param targetType the type to convert to
     * @return a conditional converter from source to target type
     */
    @Override
    public GuardedInvocation convertToType(final Class<?> sourceType, final Class<?> targetType) {
        final MethodHandle mh = JavaArgumentConverters.getConverter(targetType);
        if (mh == null) {
            return null;
        }

        return new GuardedInvocation(mh, canLinkTypeStatic(sourceType) ? null : GUARD_PRIMITIVE).asType(mh.type().changeParameterType(0, sourceType));
    }

    /**
     * Implements the somewhat involved prioritization of JavaScript primitive types conversions. Instead of explaining
     * it here in prose, just follow the source code comments.
     * @param sourceType the source type to convert from
     * @param targetType1 one candidate target type
     * @param targetType2 another candidate target type
     * @return one of {@link org.dynalang.dynalink.linker.ConversionComparator.Comparison} values signifying which target type should be favored for conversion.
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
