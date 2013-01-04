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
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.Undefined;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.ConversionComparator;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.GuardingTypeConverterFactory;
import org.dynalang.dynalink.linker.LinkRequest;
import org.dynalang.dynalink.linker.LinkerServices;
import org.dynalang.dynalink.linker.TypeBasedGuardingDynamicLinker;
import org.dynalang.dynalink.support.Guards;

/**
 * This is the main dynamic linker for Nashorn. It is used for linking all {@link ScriptObject} and its subclasses (this
 * includes {@link ScriptFunction} and its subclasses) as well as {@link Undefined}. This linker is exported to other
 * language runtimes by being declared in {@code META-INF/services/org.dynalang.dynalink.linker.GuardingDynamicLinker}
 * file of Nashorn's distribution.
 */
public class NashornLinker implements TypeBasedGuardingDynamicLinker, GuardingTypeConverterFactory, ConversionComparator {
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
            inv = ((ScriptObject)self).lookup(desc, request.isCallSiteUnstable());
        } else if (self instanceof Undefined) {
            inv = Undefined.lookup(desc);
        } else {
            throw new AssertionError(); // Should never reach here.
        }

        return Bootstrap.asType(inv, linkerServices, desc);
    }

    @Override
    public GuardedInvocation convertToType(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final MethodHandle mh = JavaArgumentConverters.getConverter(targetType);
        final GuardedInvocation gi;
        if (mh != null) {
            gi = new GuardedInvocation(mh, canLinkTypeStatic(sourceType) ? null : IS_NASHORN_OR_UNDEFINED_TYPE);
        } else if (isAutoConvertibleFromFunction(targetType)) {
            final MethodHandle ctor = JavaAdapterFactory.getConstructor(ScriptFunction.class, targetType);
            assert ctor != null; // if JavaAdapterFactory.isAutoConvertible() returned true, then ctor must exist.
            if(ScriptFunction.class.isAssignableFrom(sourceType)) {
                gi = new GuardedInvocation(ctor, null);
            } else if(sourceType == Object.class) {
                gi = new GuardedInvocation(ctor, IS_SCRIPT_FUNCTION);
            } else {
                return null;
            }
        } else {
            return null;
        }
        return gi.asType(MH.type(targetType, sourceType));
    }

    private static boolean isAutoConvertibleFromFunction(final Class<?> clazz) {
        return JavaAdapterFactory.isAbstractClass(clazz) && !ScriptObject.class.isAssignableFrom(clazz) &&
                JavaAdapterFactory.isAutoConvertibleFromFunction(clazz);
    }

    @Override
    public Comparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
        if(ScriptObject.class.isAssignableFrom(sourceType)) {
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

    private static final MethodHandle IS_SCRIPT_FUNCTION = Guards.isInstance(ScriptFunction.class, MH.type(Boolean.TYPE, Object.class));

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

