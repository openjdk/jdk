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
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.internal.dynalink.beans.StaticClass;
import jdk.internal.dynalink.support.LinkRequestImpl;
import jdk.nashorn.internal.objects.NativeJava;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * <p>A factory class that generates adapter classes. Adapter classes allow implementation of Java interfaces and
 * extending of Java classes from JavaScript. For every combination of a superclass to extend and interfaces to
 * implement (collectively: "original types"), exactly one adapter class is generated that extends the specified
 * superclass and implements the specified interfaces. (But see the discussion of class-based overrides for exceptions.)
 * </p><p>
 * The adapter class is generated in a new secure class loader that inherits Nashorn's protection domain, and has either
 * one of the original types' class loader or the Nashorn's class loader as its parent - the parent class loader
 * is chosen so that all the original types and the Nashorn core classes are visible from it (as the adapter will have
 * constant pool references to ScriptObject and ScriptFunction classes). In case none of the candidate class loaders has
 * visibility of all the required types, an error is thrown. The class uses {@link JavaAdapterBytecodeGenerator} to
 * generate the adapter class itself; see its documentation for details about the generated class.
 * </p><p>
 * You normally don't use this class directly, but rather either create adapters from script using
 * {@link NativeJava#extend(Object, Object...)}, using the {@code new} operator on abstract classes and interfaces (see
 * {@link NativeJava#type(Object, Object)}), or implicitly when passing script functions to Java methods expecting SAM
 * types.
 * </p>
 */

@SuppressWarnings("javadoc")
public final class JavaAdapterFactory {
    /**
     * A mapping from an original Class object to AdapterInfo representing the adapter for the class it represents.
     */
    private static final ClassValue<Map<List<Class<?>>, AdapterInfo>> ADAPTER_INFO_MAPS = new ClassValue<Map<List<Class<?>>, AdapterInfo>>() {
        @Override
        protected Map<List<Class<?>>, AdapterInfo> computeValue(final Class<?> type) {
            return new HashMap<>();
        }
    };

    /**
     * Returns an adapter class for the specified original types. The adapter class extends/implements the original
     * class/interfaces.
     * @param types the original types. The caller must pass at least one Java type representing either a public
     * interface or a non-final public class with at least one public or protected constructor. If more than one type is
     * specified, at most one can be a class and the rest have to be interfaces. The class can be in any position in the
     * array. Invoking the method twice with exactly the same types in the same order will return the same adapter
     * class, any reordering of types or even addition or removal of redundant types (i.e. interfaces that other types
     * in the list already implement/extend, or {@code java.lang.Object} in a list of types consisting purely of
     * interfaces) will result in a different adapter class, even though those adapter classes are functionally
     * identical; we deliberately don't want to incur the additional processing cost of canonicalizing type lists.
     * @param classOverrides a JavaScript object with functions serving as the class-level overrides and
     * implementations. These overrides are defined for all instances of the class, and can be further overridden on a
     * per-instance basis by passing additional objects in the constructor.
     * @return an adapter class. See this class' documentation for details on the generated adapter class.
     * @throws ECMAException with a TypeError if the adapter class can not be generated because the original class is
     * final, non-public, or has no public or protected constructors.
     */
    public static StaticClass getAdapterClassFor(final Class<?>[] types, ScriptObject classOverrides) {
        assert types != null && types.length > 0;
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            for (Class<?> type : types) {
                // check for restricted package access
                Context.checkPackageAccess(type.getName());
            }
        }
        return getAdapterInfo(types).getAdapterClassFor(classOverrides);
    }

    /**
     * Returns a method handle representing a constructor that takes a single argument of the source type (which,
     * really, should be one of {@link ScriptObject}, {@link ScriptFunction}, or {@link Object}, and returns an instance
     * of the adapter for the target type. Used to implement the function autoconverters as well as the Nashorn's
     * JSR-223 script engine's {@code getInterface()} method.
     * @param sourceType the source type; should be either {@link ScriptObject}, {@link ScriptFunction}, or
     * {@link Object}. In case of {@code Object}, it will return a method handle that dispatches to either the script
     * object or function constructor at invocation based on the actual argument.
     * @param targetType the target type, for which adapter instances will be created
     * @return the constructor method handle.
     * @throws Exception if anything goes wrong
     */
    public static MethodHandle getConstructor(final Class<?> sourceType, final Class<?> targetType) throws Exception {
        final StaticClass adapterClass = getAdapterClassFor(new Class<?>[] { targetType }, null);
        return AccessController.doPrivileged(new PrivilegedExceptionAction<MethodHandle>() {
            @Override
            public MethodHandle run() throws Exception {
                // NOTE: we use publicLookup(), but none of our adapter constructors are caller sensitive, so this is
                // okay, we won't artificially limit access.
                return  MH.bindTo(Bootstrap.getLinkerServices().getGuardedInvocation(new LinkRequestImpl(
                        NashornCallSiteDescriptor.get(MethodHandles.publicLookup(),  "dyn:new",
                                MethodType.methodType(targetType, StaticClass.class, sourceType), 0), false,
                                adapterClass, null)).getInvocation(), adapterClass);
            }
        });
    }

    /**
     * Returns whether an instance of the specified class/interface can be generated from a ScriptFunction. Returns true
     * iff: the adapter for the class/interface can be created, it is abstract (this includes interfaces), it has at
     * least one abstract method, all the abstract methods share the same name, and it has a public or protected default
     * constructor. Note that invoking this class will most likely result in the adapter class being defined in the JVM
     * if it hasn't been already.
     * @param clazz the inspected class
     * @return true iff an instance of the specified class/interface can be generated from a ScriptFunction.
     */
    static boolean isAutoConvertibleFromFunction(final Class<?> clazz) {
        return getAdapterInfo(new Class<?>[] { clazz }).autoConvertibleFromFunction;
    }

    private static AdapterInfo getAdapterInfo(final Class<?>[] types) {
        final ClassAndLoader definingClassAndLoader = ClassAndLoader.getDefiningClassAndLoader(types);

        final Map<List<Class<?>>, AdapterInfo> adapterInfoMap = ADAPTER_INFO_MAPS.get(definingClassAndLoader.getRepresentativeClass());
        final List<Class<?>> typeList = types.length == 1 ? getSingletonClassList(types[0]) : Arrays.asList(types.clone());
        AdapterInfo adapterInfo;
        synchronized(adapterInfoMap) {
            adapterInfo = adapterInfoMap.get(typeList);
            if(adapterInfo == null) {
                adapterInfo = createAdapterInfo(types, definingClassAndLoader);
                adapterInfoMap.put(typeList, adapterInfo);
            }
        }
        return adapterInfo;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<Class<?>> getSingletonClassList(final Class<?> clazz) {
        return (List)Collections.singletonList(clazz);
    }

    /**
     * For a given class, create its adapter class and associated info.
     * @param type the class for which the adapter is created
     * @return the adapter info for the class.
     */
    private static AdapterInfo createAdapterInfo(final Class<?>[] types, final ClassAndLoader definingClassAndLoader) {
        Class<?> superClass = null;
        final List<Class<?>> interfaces = new ArrayList<>(types.length);
        for(final Class<?> t: types) {
            final int mod = t.getModifiers();
            if(!t.isInterface()) {
                if(superClass != null) {
                    return new AdapterInfo(AdaptationResult.Outcome.ERROR_MULTIPLE_SUPERCLASSES, t.getCanonicalName() + " and " + superClass.getCanonicalName());
                }
                if (Modifier.isFinal(mod)) {
                    return new AdapterInfo(AdaptationResult.Outcome.ERROR_FINAL_CLASS, t.getCanonicalName());
                }
                superClass = t;
            } else {
                interfaces.add(t);
            }
            if(!Modifier.isPublic(mod)) {
                return new AdapterInfo(AdaptationResult.Outcome.ERROR_NON_PUBLIC_CLASS, t.getCanonicalName());
            }
        }
        final Class<?> effectiveSuperClass = superClass == null ? Object.class : superClass;
        return AccessController.doPrivileged(new PrivilegedAction<AdapterInfo>() {
            @Override
            public AdapterInfo run() {
                try {
                    return new AdapterInfo(effectiveSuperClass, interfaces, definingClassAndLoader);
                } catch (final AdaptationException e) {
                    return new AdapterInfo(e.getAdaptationResult());
                }
            }
        });
    }

    private static class AdapterInfo {
        private static final ClassAndLoader SCRIPT_OBJECT_LOADER = new ClassAndLoader(ScriptObject.class, true);

        private final ClassLoader commonLoader;
        private final JavaAdapterClassLoader adapterGenerator;
        // Cacheable adapter class that is shared by all adapter instances that don't have class overrides, only
        // instance overrides.
        final StaticClass instanceAdapterClass;
        final boolean autoConvertibleFromFunction;
        final AdaptationResult adaptationResult;

        AdapterInfo(Class<?> superClass, List<Class<?>> interfaces, ClassAndLoader definingLoader) throws AdaptationException {
            this.commonLoader = findCommonLoader(definingLoader);
            final JavaAdapterBytecodeGenerator gen = new JavaAdapterBytecodeGenerator(superClass, interfaces, commonLoader, false);
            this.autoConvertibleFromFunction = gen.isAutoConvertibleFromFunction();
            final JavaAdapterClassLoader jacl = gen.createAdapterClassLoader();
            this.instanceAdapterClass = jacl.generateClass(commonLoader);
            // loaded Class - no need to keep class bytes around
            jacl.clearClassBytes();
            this.adapterGenerator = new JavaAdapterBytecodeGenerator(superClass, interfaces, commonLoader, true).createAdapterClassLoader();
            this.adaptationResult = AdaptationResult.SUCCESSFUL_RESULT;
        }

        AdapterInfo(final AdaptationResult.Outcome outcome, final String classList) {
            this(new AdaptationResult(outcome, classList));
        }

        AdapterInfo(final AdaptationResult adaptationResult) {
            this.commonLoader = null;
            this.adapterGenerator = null;
            this.instanceAdapterClass = null;
            this.autoConvertibleFromFunction = false;
            this.adaptationResult = adaptationResult;
        }

        StaticClass getAdapterClassFor(ScriptObject classOverrides) {
            if(adaptationResult.getOutcome() != AdaptationResult.Outcome.SUCCESS) {
                throw adaptationResult.typeError();
            }
            if(classOverrides == null) {
                return instanceAdapterClass;
            }
            JavaAdapterServices.setClassOverrides(classOverrides);
            try {
                return adapterGenerator.generateClass(commonLoader);
            } finally {
                JavaAdapterServices.setClassOverrides(null);
            }
        }

        /**
         * Choose between the passed class loader and the class loader that defines the ScriptObject class, based on which
         * of the two can see the classes in both.
         * @param classAndLoader the loader and a representative class from it that will be used to add the generated
         * adapter to its ADAPTER_INFO_MAPS.
         * @return the class loader that sees both the specified class and Nashorn classes.
         * @throws IllegalStateException if no such class loader is found.
         */
        private static ClassLoader findCommonLoader(final ClassAndLoader classAndLoader) throws AdaptationException {
            if(classAndLoader.canSee(SCRIPT_OBJECT_LOADER)) {
                return classAndLoader.getLoader();
            }
            if (SCRIPT_OBJECT_LOADER.canSee(classAndLoader)) {
                return SCRIPT_OBJECT_LOADER.getLoader();
            }

            throw new AdaptationException(AdaptationResult.Outcome.ERROR_NO_COMMON_LOADER, classAndLoader.getRepresentativeClass().getCanonicalName());
        }
    }
}
