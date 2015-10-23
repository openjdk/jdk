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
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.beans.BeansLinker;
import jdk.internal.dynalink.linker.ConversionComparator.Comparison;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardingDynamicLinker;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.MethodHandleTransformer;
import jdk.internal.dynalink.support.DefaultInternalObjectFilter;
import jdk.internal.dynalink.support.Lookup;
import jdk.nashorn.api.scripting.ScriptUtils;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * This linker delegates to a {@code BeansLinker} but passes it a special linker services object that has a modified
 * {@code compareConversion} method that favors conversion of {@link ConsString} to either {@link String} or
 * {@link CharSequence}. It also provides a {@link #createHiddenObjectFilter()} method for use with bootstrap that will
 * ensure that we never pass internal engine objects that should not be externally observable (currently ConsString and
 * ScriptObject) to Java APIs, but rather that we flatten it into a String. We can't just add this functionality as
 * custom converters via {@code GuaardingTypeConverterFactory}, since they are not consulted when
 * the target method handle parameter signature is {@code Object}. This linker also makes sure that primitive
 * {@link String} operations can be invoked on a {@link ConsString}, and allows invocation of objects implementing
 * the {@link FunctionalInterface} attribute.
 */
public class NashornBeansLinker implements GuardingDynamicLinker {
    // System property to control whether to wrap ScriptObject->ScriptObjectMirror for
    // Object type arguments of Java method calls, field set and array set.
    private static final boolean MIRROR_ALWAYS = Options.getBooleanProperty("nashorn.mirror.always", true);

    private static final MethodHandle EXPORT_ARGUMENT;
    private static final MethodHandle IMPORT_RESULT;
    private static final MethodHandle FILTER_CONSSTRING;

    static {
        final Lookup lookup  = new Lookup(MethodHandles.lookup());
        EXPORT_ARGUMENT      = lookup.findOwnStatic("exportArgument", Object.class, Object.class);
        IMPORT_RESULT        = lookup.findOwnStatic("importResult", Object.class, Object.class);
        FILTER_CONSSTRING    = lookup.findOwnStatic("consStringFilter", Object.class, Object.class);
    }

    // cache of @FunctionalInterface method of implementor classes
    private static final ClassValue<String> FUNCTIONAL_IFACE_METHOD_NAME = new ClassValue<String>() {
        @Override
        protected String computeValue(final Class<?> type) {
            return findFunctionalInterfaceMethodName(type);
        }
    };

    private final BeansLinker beansLinker = new BeansLinker();

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest linkRequest, final LinkerServices linkerServices) throws Exception {
        final Object self = linkRequest.getReceiver();
        final CallSiteDescriptor desc = linkRequest.getCallSiteDescriptor();
        if (self instanceof ConsString) {
            // In order to treat ConsString like a java.lang.String we need a link request with a string receiver.
            final Object[] arguments = linkRequest.getArguments();
            arguments[0] = "";
            final LinkRequest forgedLinkRequest = linkRequest.replaceArguments(desc, arguments);
            final GuardedInvocation invocation = getGuardedInvocation(beansLinker, forgedLinkRequest, linkerServices);
            // If an invocation is found we add a filter that makes it work for both Strings and ConsStrings.
            return invocation == null ? null : invocation.filterArguments(0, FILTER_CONSSTRING);
        }

        if (self != null && "call".equals(desc.getNameToken(CallSiteDescriptor.OPERATOR))) {
            // Support dyn:call on any object that supports some @FunctionalInterface
            // annotated interface. This way Java method, constructor references or
            // implementations of java.util.function.* interfaces can be called as though
            // those are script functions.
            final String name = getFunctionalInterfaceMethodName(self.getClass());
            if (name != null) {
                final MethodType callType = desc.getMethodType();
                // drop callee (Undefined ScriptFunction) and change the request to be dyn:callMethod:<name>
                final NashornCallSiteDescriptor newDesc = NashornCallSiteDescriptor.get(desc.getLookup(),
                        "dyn:callMethod:" + name, desc.getMethodType().dropParameterTypes(1, 2),
                        NashornCallSiteDescriptor.getFlags(desc));
                final GuardedInvocation gi = getGuardedInvocation(beansLinker,
                        linkRequest.replaceArguments(newDesc, linkRequest.getArguments()),
                        new NashornBeansLinkerServices(linkerServices));

                // drop 'thiz' passed from the script.
                return gi.replaceMethods(
                    MH.dropArguments(linkerServices.filterInternalObjects(gi.getInvocation()), 1, callType.parameterType(1)),
                    gi.getGuard());
            }
        }
        return getGuardedInvocation(beansLinker, linkRequest, linkerServices);
    }

    /**
     * Delegates to the specified linker but injects its linker services wrapper so that it will apply all special
     * conversions that this class does.
     * @param delegateLinker the linker to which the actual work is delegated to.
     * @param linkRequest the delegated link request
     * @param linkerServices the original link services that will be augmented with special conversions
     * @return the guarded invocation from the delegate, possibly augmented with special conversions
     * @throws Exception if the delegate throws an exception
     */
    public static GuardedInvocation getGuardedInvocation(final GuardingDynamicLinker delegateLinker, final LinkRequest linkRequest, final LinkerServices linkerServices) throws Exception {
        return delegateLinker.getGuardedInvocation(linkRequest, new NashornBeansLinkerServices(linkerServices));
    }

    @SuppressWarnings("unused")
    private static Object exportArgument(final Object arg) {
        return exportArgument(arg, MIRROR_ALWAYS);
    }

    static Object exportArgument(final Object arg, final boolean mirrorAlways) {
        if (arg instanceof ConsString) {
            return arg.toString();
        } else if (mirrorAlways && arg instanceof ScriptObject) {
            return ScriptUtils.wrap((ScriptObject)arg);
        } else {
            return arg;
        }
    }

    @SuppressWarnings("unused")
    private static Object importResult(final Object arg) {
        return ScriptUtils.unwrap(arg);
    }

    @SuppressWarnings("unused")
    private static Object consStringFilter(final Object arg) {
        return arg instanceof ConsString ? arg.toString() : arg;
    }

    private static String findFunctionalInterfaceMethodName(final Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        for (final Class<?> iface : clazz.getInterfaces()) {
            // check accessibility up-front
            if (! Context.isAccessibleClass(iface)) {
                continue;
            }

            // check for @FunctionalInterface
            if (iface.isAnnotationPresent(FunctionalInterface.class)) {
                // return the first abstract method
                for (final Method m : iface.getMethods()) {
                    if (Modifier.isAbstract(m.getModifiers())) {
                        return m.getName();
                    }
                }
            }
        }

        // did not find here, try super class
        return findFunctionalInterfaceMethodName(clazz.getSuperclass());
    }

    // Returns @FunctionalInterface annotated interface's single abstract
    // method name. If not found, returns null.
    static String getFunctionalInterfaceMethodName(final Class<?> clazz) {
        return FUNCTIONAL_IFACE_METHOD_NAME.get(clazz);
    }

    static MethodHandleTransformer createHiddenObjectFilter() {
        return new DefaultInternalObjectFilter(EXPORT_ARGUMENT, MIRROR_ALWAYS ? IMPORT_RESULT : null);
    }

    private static class NashornBeansLinkerServices implements LinkerServices {
        private final LinkerServices linkerServices;

        NashornBeansLinkerServices(final LinkerServices linkerServices) {
            this.linkerServices = linkerServices;
        }

        @Override
        public MethodHandle asType(final MethodHandle handle, final MethodType fromType) {
            return linkerServices.asType(handle, fromType);
        }

        @Override
        public MethodHandle getTypeConverter(final Class<?> sourceType, final Class<?> targetType) {
            return linkerServices.getTypeConverter(sourceType, targetType);
        }

        @Override
        public boolean canConvert(final Class<?> from, final Class<?> to) {
            return linkerServices.canConvert(from, to);
        }

        @Override
        public GuardedInvocation getGuardedInvocation(final LinkRequest linkRequest) throws Exception {
            return linkerServices.getGuardedInvocation(linkRequest);
        }

        @Override
        public Comparison compareConversion(final Class<?> sourceType, final Class<?> targetType1, final Class<?> targetType2) {
            if (sourceType == ConsString.class) {
                if (String.class == targetType1 || CharSequence.class == targetType1) {
                    return Comparison.TYPE_1_BETTER;
                }

                if (String.class == targetType2 || CharSequence.class == targetType2) {
                    return Comparison.TYPE_2_BETTER;
                }
            }
            return linkerServices.compareConversion(sourceType, targetType1, targetType2);
        }

        @Override
        public MethodHandle filterInternalObjects(final MethodHandle target) {
            return linkerServices.filterInternalObjects(target);
        }
    }
}
