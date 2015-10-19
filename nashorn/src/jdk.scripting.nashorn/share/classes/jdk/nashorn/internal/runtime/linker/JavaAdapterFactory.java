/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jdk.internal.dynalink.beans.StaticClass;
import jdk.internal.dynalink.support.SimpleLinkRequest;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * A factory class that generates adapter classes. Adapter classes allow
 * implementation of Java interfaces and extending of Java classes from
 * JavaScript. For every combination of a superclass to extend and interfaces to
 * implement (collectively: "original types"), exactly one adapter class is
 * generated that extends the specified superclass and implements the specified
 * interfaces. (But see the discussion of class-based overrides for exceptions.)
 * <p>
 * The adapter class is generated in a new secure class loader that inherits
 * Nashorn's protection domain, and has either one of the original types' class
 * loader or the Nashorn's class loader as its parent - the parent class loader
 * is chosen so that all the original types and the Nashorn core classes are
 * visible from it (as the adapter will have constant pool references to
 * ScriptObject and ScriptFunction classes). In case none of the candidate class
 * loaders has visibility of all the required types, an error is thrown. The
 * class uses {@link JavaAdapterBytecodeGenerator} to generate the adapter class
 * itself; see its documentation for details about the generated class.
 * <p>
 * You normally don't use this class directly, but rather either create adapters
 * from script using {@link jdk.nashorn.internal.objects.NativeJava#extend(Object, Object...)},
 * using the {@code new} operator on abstract classes and interfaces (see
 * {@link jdk.nashorn.internal.objects.NativeJava#type(Object, Object)}), or
 * implicitly when passing script functions to Java methods expecting SAM types.
 */

@SuppressWarnings("javadoc")
public final class JavaAdapterFactory {
    private static final ProtectionDomain MINIMAL_PERMISSION_DOMAIN = createMinimalPermissionDomain();

    // context with permissions needs for AdapterInfo creation
    private static final AccessControlContext CREATE_ADAPTER_INFO_ACC_CTXT =
        ClassAndLoader.createPermAccCtxt("createClassLoader", "getClassLoader",
            "accessDeclaredMembers", "accessClassInPackage.jdk.nashorn.internal.runtime");

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
     * Returns an adapter class for the specified original types. The adapter
     * class extends/implements the original class/interfaces.
     *
     * @param types the original types. The caller must pass at least one Java
     *        type representing either a public interface or a non-final public
     *        class with at least one public or protected constructor. If more
     *        than one type is specified, at most one can be a class and the
     *        rest have to be interfaces. The class can be in any position in
     *        the array. Invoking the method twice with exactly the same types
     *        in the same order will return the same adapter class, any
     *        reordering of types or even addition or removal of redundant types
     *        (i.e., interfaces that other types in the list already
     *        implement/extend, or {@code java.lang.Object} in a list of types
     *        consisting purely of interfaces) will result in a different
     *        adapter class, even though those adapter classes are functionally
     *        identical; we deliberately don't want to incur the additional
     *        processing cost of canonicalizing type lists.
     * @param classOverrides a JavaScript object with functions serving as the
     *        class-level overrides and implementations. These overrides are
     *        defined for all instances of the class, and can be further
     *        overridden on a per-instance basis by passing additional objects
     *        in the constructor.
     * @param lookup the lookup object identifying the caller class. The
     *        generated adapter class will have the protection domain of the
     *        caller class iff the lookup object is full-strength, otherwise it
     *        will be completely unprivileged.
     *
     * @return an adapter class. See this class' documentation for details on
     *         the generated adapter class.
     *
     * @throws ECMAException with a TypeError if the adapter class can not be
     *         generated because the original class is final, non-public, or has
     *         no public or protected constructors.
     */
    public static StaticClass getAdapterClassFor(final Class<?>[] types, final ScriptObject classOverrides, final MethodHandles.Lookup lookup) {
        return getAdapterClassFor(types, classOverrides, getProtectionDomain(lookup));
    }

    private static StaticClass getAdapterClassFor(final Class<?>[] types, final ScriptObject classOverrides, final ProtectionDomain protectionDomain) {
        assert types != null && types.length > 0;
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            for (final Class<?> type : types) {
                // check for restricted package access
                Context.checkPackageAccess(type);
                // check for classes, interfaces in reflection
                ReflectionCheckLinker.checkReflectionAccess(type, true);
            }
        }
        return getAdapterInfo(types).getAdapterClass(classOverrides, protectionDomain);
    }

    private static ProtectionDomain getProtectionDomain(final MethodHandles.Lookup lookup) {
        if((lookup.lookupModes() & Lookup.PRIVATE) == 0) {
            return MINIMAL_PERMISSION_DOMAIN;
        }
        return getProtectionDomain(lookup.lookupClass());
    }

    private static ProtectionDomain getProtectionDomain(final Class<?> clazz) {
        return AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>() {
            @Override
            public ProtectionDomain run() {
                return clazz.getProtectionDomain();
            }
        });
    }

    /**
     * Returns a method handle representing a constructor that takes a single
     * argument of the source type (which, really, should be one of {@link ScriptObject},
     * {@link ScriptFunction}, or {@link Object}, and returns an instance of the
     * adapter for the target type. Used to implement the function autoconverters
     * as well as the Nashorn JSR-223 script engine's {@code getInterface()}
     * method.
     *
     * @param sourceType the source type; should be either {@link ScriptObject},
     *        {@link ScriptFunction}, or {@link Object}. In case of {@code Object},
     *        it will return a method handle that dispatches to either the script
     *        object or function constructor at invocation based on the actual
     *        argument.
     * @param targetType the target type, for which adapter instances will be created
     * @param lookup method handle lookup to use
     *
     * @return the constructor method handle.
     *
     * @throws Exception if anything goes wrong
     */
    public static MethodHandle getConstructor(final Class<?> sourceType, final Class<?> targetType, final MethodHandles.Lookup lookup) throws Exception {
        final StaticClass adapterClass = getAdapterClassFor(new Class<?>[] { targetType }, null, lookup);
        return MH.bindTo(Bootstrap.getLinkerServices().getGuardedInvocation(new SimpleLinkRequest(
                NashornCallSiteDescriptor.get(lookup, "dyn:new",
                        MethodType.methodType(targetType, StaticClass.class, sourceType), 0), false,
                        adapterClass, null)).getInvocation(), adapterClass);
    }

    /**
     * Returns whether an instance of the specified class/interface can be
     * generated from a ScriptFunction. Returns {@code true} iff: the adapter
     * for the class/interface can be created, it is abstract (this includes
     * interfaces), it has at least one abstract method, all the abstract
     * methods share the same name, and it has a public or protected default
     * constructor. Note that invoking this class will most likely result in the
     * adapter class being defined in the JVM if it hasn't been already.
     *
     * @param clazz the inspected class
     *
     * @return {@code true} iff an instance of the specified class/interface can
     *         be generated from a ScriptFunction.
     */
    static boolean isAutoConvertibleFromFunction(final Class<?> clazz) {
        return getAdapterInfo(new Class<?>[] { clazz }).autoConvertibleFromFunction;
    }

    private static AdapterInfo getAdapterInfo(final Class<?>[] types) {
        final ClassAndLoader definingClassAndLoader = ClassAndLoader.getDefiningClassAndLoader(types);

        final Map<List<Class<?>>, AdapterInfo> adapterInfoMap = ADAPTER_INFO_MAPS.get(definingClassAndLoader.getRepresentativeClass());
        final List<Class<?>> typeList = types.length == 1 ? Collections.<Class<?>>singletonList(types[0]) : Arrays.asList(types.clone());
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

   /**
     * For a given class, create its adapter class and associated info.
     *
     * @param type the class for which the adapter is created
     *
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
                if (interfaces.size() > 65535) {
                    throw new IllegalArgumentException("interface limit exceeded");
                }

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
                } catch (final RuntimeException e) {
                    return new AdapterInfo(new AdaptationResult(AdaptationResult.Outcome.ERROR_OTHER, Arrays.toString(types), e.toString()));
                }
            }
        }, CREATE_ADAPTER_INFO_ACC_CTXT);
    }

    private static class AdapterInfo {
        private static final ClassAndLoader SCRIPT_OBJECT_LOADER = new ClassAndLoader(ScriptFunction.class, true);

        private final ClassLoader commonLoader;
        // TODO: soft reference the JavaAdapterClassLoader objects. They can be recreated when needed.
        private final JavaAdapterClassLoader classAdapterGenerator;
        private final JavaAdapterClassLoader instanceAdapterGenerator;
        private final Map<CodeSource, StaticClass> instanceAdapters = new ConcurrentHashMap<>();
        final boolean autoConvertibleFromFunction;
        final AdaptationResult adaptationResult;

        AdapterInfo(final Class<?> superClass, final List<Class<?>> interfaces, final ClassAndLoader definingLoader) throws AdaptationException {
            this.commonLoader = findCommonLoader(definingLoader);
            final JavaAdapterBytecodeGenerator gen = new JavaAdapterBytecodeGenerator(superClass, interfaces, commonLoader, false);
            this.autoConvertibleFromFunction = gen.isAutoConvertibleFromFunction();
            instanceAdapterGenerator = gen.createAdapterClassLoader();
            this.classAdapterGenerator = new JavaAdapterBytecodeGenerator(superClass, interfaces, commonLoader, true).createAdapterClassLoader();
            this.adaptationResult = AdaptationResult.SUCCESSFUL_RESULT;
        }

        AdapterInfo(final AdaptationResult.Outcome outcome, final String classList) {
            this(new AdaptationResult(outcome, classList));
        }

        AdapterInfo(final AdaptationResult adaptationResult) {
            this.commonLoader = null;
            this.classAdapterGenerator = null;
            this.instanceAdapterGenerator = null;
            this.autoConvertibleFromFunction = false;
            this.adaptationResult = adaptationResult;
        }

        StaticClass getAdapterClass(final ScriptObject classOverrides, final ProtectionDomain protectionDomain) {
            if(adaptationResult.getOutcome() != AdaptationResult.Outcome.SUCCESS) {
                throw adaptationResult.typeError();
            }
            return classOverrides == null ? getInstanceAdapterClass(protectionDomain) :
                getClassAdapterClass(classOverrides, protectionDomain);
        }

        private StaticClass getInstanceAdapterClass(final ProtectionDomain protectionDomain) {
            CodeSource codeSource = protectionDomain.getCodeSource();
            if(codeSource == null) {
                codeSource = MINIMAL_PERMISSION_DOMAIN.getCodeSource();
            }
            StaticClass instanceAdapterClass = instanceAdapters.get(codeSource);
            if(instanceAdapterClass != null) {
                return instanceAdapterClass;
            }
            // Any "unknown source" code source will default to no permission domain.
            final ProtectionDomain effectiveDomain = codeSource.equals(MINIMAL_PERMISSION_DOMAIN.getCodeSource()) ?
                    MINIMAL_PERMISSION_DOMAIN : protectionDomain;

            instanceAdapterClass = instanceAdapterGenerator.generateClass(commonLoader, effectiveDomain);
            final StaticClass existing = instanceAdapters.putIfAbsent(codeSource, instanceAdapterClass);
            return existing == null ? instanceAdapterClass : existing;
        }

        private StaticClass getClassAdapterClass(final ScriptObject classOverrides, final ProtectionDomain protectionDomain) {
            JavaAdapterServices.setClassOverrides(classOverrides);
            try {
                return classAdapterGenerator.generateClass(commonLoader, protectionDomain);
            } finally {
                JavaAdapterServices.setClassOverrides(null);
            }
        }

        /**
         * Choose between the passed class loader and the class loader that defines the
         * ScriptObject class, based on which of the two can see the classes in both.
         *
         * @param classAndLoader the loader and a representative class from it that will
         *        be used to add the generated adapter to its ADAPTER_INFO_MAPS.
         *
         * @return the class loader that sees both the specified class and Nashorn classes.
         *
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

    private static ProtectionDomain createMinimalPermissionDomain() {
        // Generated classes need to have at least the permission to access Nashorn runtime and runtime.linker packages.
        final Permissions permissions = new Permissions();
        permissions.add(new RuntimePermission("accessClassInPackage.jdk.nashorn.internal.objects"));
        permissions.add(new RuntimePermission("accessClassInPackage.jdk.nashorn.internal.runtime"));
        permissions.add(new RuntimePermission("accessClassInPackage.jdk.nashorn.internal.runtime.linker"));
        return new ProtectionDomain(new CodeSource(null, (CodeSigner[])null), permissions);
    }
}
