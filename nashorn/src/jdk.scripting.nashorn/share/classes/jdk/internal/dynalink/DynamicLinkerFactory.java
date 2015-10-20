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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file, and Oracle licenses the original version of this file under the BSD
 * license:
 */
/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jdk.internal.dynalink;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import jdk.internal.dynalink.beans.BeansLinker;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardedInvocationTransformer;
import jdk.internal.dynalink.linker.GuardingDynamicLinker;
import jdk.internal.dynalink.linker.GuardingTypeConverterFactory;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.MethodHandleTransformer;
import jdk.internal.dynalink.linker.MethodTypeConversionStrategy;
import jdk.internal.dynalink.linker.support.CompositeGuardingDynamicLinker;
import jdk.internal.dynalink.linker.support.CompositeTypeBasedGuardingDynamicLinker;
import jdk.internal.dynalink.linker.support.DefaultInternalObjectFilter;
import jdk.internal.dynalink.linker.support.TypeUtilities;

/**
 * A factory class for creating {@link DynamicLinker} objects. Dynamic linkers
 * are the central objects in Dynalink; these are composed of several
 * {@link GuardingDynamicLinker} objects and coordinate linking of call sites
 * with them. The usual dynamic linker is a linker
 * composed of all {@link GuardingDynamicLinker} objects explicitly pre-created
 * by the user of the factory and configured with
 * {@link #setPrioritizedLinkers(List)}, as well as any
 * {@link #setClassLoader(ClassLoader) automatically discovered} ones, and
 * finally the ones configured with {@link #setFallbackLinkers(List)}; this last
 * category usually includes {@link BeansLinker}.
 */
public final class DynamicLinkerFactory {
    /**
     * Default value for {@link #setUnstableRelinkThreshold(int) unstable relink
     * threshold}.
     */
    public static final int DEFAULT_UNSTABLE_RELINK_THRESHOLD = 8;

    private boolean classLoaderExplicitlySet = false;
    private ClassLoader classLoader;

    private List<? extends GuardingDynamicLinker> prioritizedLinkers;
    private List<? extends GuardingDynamicLinker> fallbackLinkers;
    private boolean syncOnRelink = false;
    private int unstableRelinkThreshold = DEFAULT_UNSTABLE_RELINK_THRESHOLD;
    private GuardedInvocationTransformer prelinkTransformer;
    private MethodTypeConversionStrategy autoConversionStrategy;
    private MethodHandleTransformer internalObjectsFilter;

    /**
     * Creates a new dynamic linker factory with default configuration. Upon
     * creation, the factory can be configured using various {@code setXxx()}
     * methods and used to create one or more dynamic linkers according to its
     * current configuration using {@link #createLinker()}.
     */
    public DynamicLinkerFactory() {
    }

    /**
     * Sets the class loader for automatic discovery of available guarding
     * dynamic linkers. Guarding dynamic linker classes declared in
     * {@code /META-INF/services/jdk.internal.dynalink.linker.GuardingDynamicLinker}
     * resources available through this class loader will be automatically
     * instantiated using the {@link ServiceLoader} mechanism and incorporated
     * into {@code DynamicLinker}s that this factory creates. This allows for
     * cross-language interoperability where call sites belonging to this language
     * runtime can be linked by linkers from these autodiscovered runtimes if
     * their native objects are passed to this runtime. If class loader is not
     * set explicitly, then the thread context class loader of the thread
     * invoking {@link #createLinker()} will be used. Can be invoked with null
     * to signify system class loader.
     *
     * @param classLoader the class loader used for the automatic discovery of
     * available linkers.
     */
    public void setClassLoader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        classLoaderExplicitlySet = true;
    }

    /**
     * Sets the prioritized guarding dynamic linkers. Language runtimes using
     * Dynalink will usually have at least one linker for their own language.
     * These linkers will be consulted first by the resulting dynamic linker
     * when it is linking call sites, before any autodiscovered and fallback
     * linkers. If the factory also autodiscovers a linker class matching one
     * of the prioritized linkers, the autodiscovered class will be ignored and
     * the explicit prioritized instance will be used.
     *
     * @param prioritizedLinkers the list of prioritized linkers. Can be null.
     * @throws NullPointerException if any of the list elements are null.
     */
    public void setPrioritizedLinkers(final List<? extends GuardingDynamicLinker> prioritizedLinkers) {
        this.prioritizedLinkers = reqireNonNullElements(prioritizedLinkers);
    }

    /**
     * Sets the prioritized guarding dynamic linkers. Identical to calling
     * {@link #setPrioritizedLinkers(List)} with
     * {@code Arrays.asList(prioritizedLinkers)}.
     *
     * @param prioritizedLinkers an array of prioritized linkers. Can be null.
     * @throws NullPointerException if any of the array elements are null.
     */
    public void setPrioritizedLinkers(final GuardingDynamicLinker... prioritizedLinkers) {
        setPrioritizedLinkers(prioritizedLinkers == null ? null : Arrays.asList(prioritizedLinkers));
    }

    /**
     * Sets a single prioritized linker. Identical to calling
     * {@link #setPrioritizedLinkers(List)} with a single-element list.
     *
     * @param prioritizedLinker the single prioritized linker. Must not be null.
     * @throws NullPointerException if null is passed.
     */
    public void setPrioritizedLinker(final GuardingDynamicLinker prioritizedLinker) {
        this.prioritizedLinkers = Collections.singletonList(Objects.requireNonNull(prioritizedLinker));
    }

    /**
     * Sets the fallback guarding dynamic linkers. These linkers will be
     * consulted last by the resulting dynamic linker when it is linking call
     * sites, after any autodiscovered and prioritized linkers. If the factory
     * also autodiscovers a linker class matching one of the fallback linkers,
     * the autodiscovered class will be ignored and the explicit fallback
     * instance will be used.
     *
     * @param fallbackLinkers the list of fallback linkers. Can be empty to
     * indicate the caller wishes to set no fallback linkers. Note that if this
     * method is not invoked explicitly or is passed null, then the factory
     * will create an instance of {@link BeansLinker} to serve as the default
     * fallback linker.
     * @throws NullPointerException if any of the list elements are null.
     */
    public void setFallbackLinkers(final List<? extends GuardingDynamicLinker> fallbackLinkers) {
        this.fallbackLinkers = reqireNonNullElements(fallbackLinkers);
    }

    /**
     * Sets the fallback guarding dynamic linkers. Identical to calling
     * {@link #setFallbackLinkers(List)} with
     * {@code Arrays.asList(fallbackLinkers)}.
     *
     * @param fallbackLinkers an array of fallback linkers. Can be empty to
     * indicate the caller wishes to set no fallback linkers. Note that if this
     * method is not invoked explicitly or is passed null, then the factory
     * will create an instance of {@link BeansLinker} to serve as the default
     * fallback linker.
     * @throws NullPointerException if any of the array elements are null.
     */
    public void setFallbackLinkers(final GuardingDynamicLinker... fallbackLinkers) {
        setFallbackLinkers(fallbackLinkers == null ? null : Arrays.asList(fallbackLinkers));
    }

    /**
     * Sets whether the dynamic linker created by this factory will invoke
     * {@link MutableCallSite#syncAll(MutableCallSite[])} after a call site is
     * relinked. Defaults to false. You probably want to set it to true if your
     * runtime supports multithreaded execution of dynamically linked code.
     * @param syncOnRelink true for invoking sync on relink, false otherwise.
     */
    public void setSyncOnRelink(final boolean syncOnRelink) {
        this.syncOnRelink = syncOnRelink;
    }

    /**
     * Sets the unstable relink threshold; the number of times a call site is
     * relinked after which it will be considered unstable, and subsequent link
     * requests for it will indicate this.
     * @param unstableRelinkThreshold the new threshold. Must not be less than
     * zero. The value of zero means that call sites will never be considered
     * unstable.
     * @see LinkRequest#isCallSiteUnstable()
     */
    public void setUnstableRelinkThreshold(final int unstableRelinkThreshold) {
        if(unstableRelinkThreshold < 0) {
            throw new IllegalArgumentException("unstableRelinkThreshold < 0");
        }
        this.unstableRelinkThreshold = unstableRelinkThreshold;
    }

    /**
     * Set the pre-link transformer. This is a
     * {@link GuardedInvocationTransformer} that will get the final chance to
     * modify the guarded invocation after it has been created by a component
     * linker and before the dynamic linker links it into the call site. It is
     * normally used to adapt the return value type of the invocation to the
     * type of the call site. When not set explicitly, a default pre-link
     * transformer will be used that simply calls
     * {@link GuardedInvocation#asType(LinkerServices, MethodType)}. Customized
     * pre-link transformers are rarely needed; they are mostly used as a
     * building block for implementing advanced techniques such as code
     * deoptimization strategies.
     * @param prelinkTransformer the pre-link transformer for the dynamic
     * linker. Can be null to have the factory use the default transformer.
     */
    public void setPrelinkTransformer(final GuardedInvocationTransformer prelinkTransformer) {
        this.prelinkTransformer = prelinkTransformer;
    }

    /**
     * Sets an object representing the conversion strategy for automatic type
     * conversions. After
     * {@link LinkerServices#asType(MethodHandle, MethodType)} has applied all
     * custom conversions to a method handle, it still needs to effect
     * {@link TypeUtilities#isMethodInvocationConvertible(Class, Class) method
     * invocation conversions} that can usually be automatically applied as per
     * {@link MethodHandle#asType(MethodType)}. However, sometimes language
     * runtimes will want to customize even those conversions for their own call
     * sites. A typical example is allowing unboxing of null return values,
     * which is by default prohibited by ordinary
     * {@code MethodHandles.asType()}. In this case, a language runtime can
     * install its own custom automatic conversion strategy, that can deal with
     * null values. Note that when the strategy's
     * {@link MethodTypeConversionStrategy#asType(MethodHandle, MethodType)}
     * is invoked, the custom language conversions will already have been
     * applied to the method handle, so by design the difference between the
     * handle's current method type and the desired final type will always only
     * be ones that can be subjected to method invocation conversions. The
     * strategy also doesn't need to invoke a final
     * {@code MethodHandle.asType()} as that will be done internally as the
     * final step.
     * @param autoConversionStrategy the strategy for applying method invocation
     * conversions for the linker created by this factory. Can be null for no
     * custom strategy.
     */
    public void setAutoConversionStrategy(final MethodTypeConversionStrategy autoConversionStrategy) {
        this.autoConversionStrategy = autoConversionStrategy;
    }

    /**
     * Sets a method handle transformer that is supposed to act as the
     * implementation of
     * {@link LinkerServices#filterInternalObjects(MethodHandle)} for linker
     * services of dynamic linkers created by this factory. Some language
     * runtimes can have internal objects that should not escape their scope.
     * They can add a transformer here that will modify the method handle so
     * that any parameters that can receive potentially internal language
     * runtime objects will have a filter added on them to prevent them from
     * escaping, potentially by wrapping them. The transformer can also
     * potentially add an unwrapping filter to the return value.
     * {@link DefaultInternalObjectFilter} is provided as a convenience class
     * for easily creating such filtering transformers.
     * @param internalObjectsFilter a method handle transformer filtering out
     * internal objects, or null.
     */
    public void setInternalObjectsFilter(final MethodHandleTransformer internalObjectsFilter) {
        this.internalObjectsFilter = internalObjectsFilter;
    }

    /**
     * Creates a new dynamic linker based on the current configuration. This
     * method can be invoked more than once to create multiple dynamic linkers.
     * Autodiscovered linkers are newly instantiated on every invocation of this
     * method. It is allowed to change the factory's configuration between
     * invocations. The method is not thread safe.
     * @return the new dynamic Linker
     */
    public DynamicLinker createLinker() {
        // Treat nulls appropriately
        if(prioritizedLinkers == null) {
            prioritizedLinkers = Collections.emptyList();
        }
        if(fallbackLinkers == null) {
            fallbackLinkers = Collections.singletonList(new BeansLinker());
        }

        // Gather classes of all precreated (prioritized and fallback) linkers.
        // We'll filter out any discovered linkers of the same class.
        final Set<Class<? extends GuardingDynamicLinker>> knownLinkerClasses =
                new HashSet<>();
        addClasses(knownLinkerClasses, prioritizedLinkers);
        addClasses(knownLinkerClasses, fallbackLinkers);

        final ClassLoader effectiveClassLoader = classLoaderExplicitlySet ? classLoader : getThreadContextClassLoader();
        final List<GuardingDynamicLinker> discovered = new LinkedList<>();
        final ServiceLoader<GuardingDynamicLinker> linkerLoader = ServiceLoader.load(GuardingDynamicLinker.class, effectiveClassLoader);
        for(final GuardingDynamicLinker linker: linkerLoader) {
            discovered.add(linker);
        }

        // Now, concatenate ...
        final List<GuardingDynamicLinker> linkers =
                new ArrayList<>(prioritizedLinkers.size() + discovered.size()
                        + fallbackLinkers.size());
        // ... prioritized linkers, ...
        linkers.addAll(prioritizedLinkers);
        // ... filtered discovered linkers, ...
        for(final GuardingDynamicLinker linker: discovered) {
            if(!knownLinkerClasses.contains(linker.getClass())) {
                linkers.add(linker);
            }
        }
        // ... and finally fallback linkers.
        linkers.addAll(fallbackLinkers);
        final List<GuardingDynamicLinker> optimized = CompositeTypeBasedGuardingDynamicLinker.optimize(linkers);
        final GuardingDynamicLinker composite;
        switch(linkers.size()) {
            case 0: {
                composite = (r, s) -> null; // linker that can't link anything
                break;
            }
            case 1: {
                composite = optimized.get(0);
                break;
            }
            default: {
                composite = new CompositeGuardingDynamicLinker(optimized);
                break;
            }
        }

        final List<GuardingTypeConverterFactory> typeConverters = new LinkedList<>();
        for(final GuardingDynamicLinker linker: linkers) {
            if(linker instanceof GuardingTypeConverterFactory) {
                typeConverters.add((GuardingTypeConverterFactory)linker);
            }
        }

        if(prelinkTransformer == null) {
            prelinkTransformer = (inv, request, linkerServices) -> inv.asType(linkerServices, request.getCallSiteDescriptor().getMethodType());
        }

        return new DynamicLinker(new LinkerServicesImpl(new TypeConverterFactory(typeConverters,
                autoConversionStrategy), composite, internalObjectsFilter), prelinkTransformer,
                syncOnRelink, unstableRelinkThreshold);
    }

    private static ClassLoader getThreadContextClassLoader() {
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        }, ClassLoaderGetterContextProvider.GET_CLASS_LOADER_CONTEXT);
    }

    private static void addClasses(final Set<Class<? extends GuardingDynamicLinker>> knownLinkerClasses,
            final List<? extends GuardingDynamicLinker> linkers) {
        for(final GuardingDynamicLinker linker: linkers) {
            knownLinkerClasses.add(linker.getClass());
        }
    }

    private static <T> List<T> reqireNonNullElements(final List<T> list) {
        if (list == null) {
            return null;
        }
        for(final T t: list) {
            Objects.requireNonNull(t);
        }
        return new ArrayList<>(list);
    }

}
