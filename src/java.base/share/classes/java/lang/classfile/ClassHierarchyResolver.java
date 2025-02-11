/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile.StackMapsOption;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.internal.classfile.impl.ClassHierarchyImpl;
import jdk.internal.classfile.impl.ClassHierarchyImpl.ClassLoadingClassHierarchyResolver;
import jdk.internal.classfile.impl.ClassHierarchyImpl.StaticClassHierarchyResolver;
import jdk.internal.classfile.impl.Util;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.util.Objects.requireNonNull;

/**
 * Provides class hierarchy information for {@linkplain StackMapsOption stack
 * maps generation} and {@linkplain ClassFile#verify(byte[]) verification}.
 * A class hierarchy resolver must be able to process all classes and interfaces
 * encountered during these workloads.
 *
 * @see ClassFile.ClassHierarchyResolverOption
 * @see StackMapTableAttribute
 * @jvms 4.10.1.2 Verification Type System
 * @since 24
 */
@FunctionalInterface
public interface ClassHierarchyResolver {

    /**
     * {@return the default instance of {@code ClassHierarchyResolver} that
     * gets {@link ClassHierarchyInfo} from system class loader with reflection}
     * This default instance cannot load classes from other class loaders, such
     * as the caller's class loader; it also loads the system classes if they
     * are not yet loaded, which makes it unsuitable for instrumentation.
     */
    static ClassHierarchyResolver defaultResolver() {
        return ClassHierarchyImpl.DEFAULT_RESOLVER;
    }

    /**
     * {@return the {@code ClassHierarchyInfo} for a given class name, or {@code
     * null} if the name is unknown to the resolver}
     * <p>
     * This method is called by the Class-File API to obtain the hierarchy
     * information of a class or interface; users should not call this method.
     * The symbolic descriptor passed by the Class-File API always represents
     * a class or interface.
     *
     * @param classDesc descriptor of the class
     * @throws IllegalArgumentException if a class shouldn't be queried for
     *         hierarchy, such as when it is inaccessible
     */
    ClassHierarchyInfo getClassInfo(ClassDesc classDesc);

    /**
     * Information about a resolved class.
     *
     * @since 24
     */
    sealed interface ClassHierarchyInfo permits ClassHierarchyImpl.ClassHierarchyInfoImpl {

        /**
         * Indicates that a class is a declared class, with the specific given super class.
         *
         * @param superClass descriptor of the super class, may be {@code null}
         * @return the info indicating the super class
         * @see Superclass
         */
        static ClassHierarchyInfo ofClass(ClassDesc superClass) {
            return new ClassHierarchyImpl.ClassHierarchyInfoImpl(superClass, false);
        }

        /**
         * Indicates that a class is an interface.
         *
         * @return the info indicating an interface
         */
        static ClassHierarchyInfo ofInterface() {
            return new ClassHierarchyImpl.ClassHierarchyInfoImpl(CD_Object, true);
        }
    }

    /**
     * Chains this {@code ClassHierarchyResolver} with another to be consulted
     * if this resolver does not know about the specified class.
     *
     * @implSpec
     * The default implementation returns resolver implemented to query {@code
     * other} resolver in case this resolver returns {@code null}.
     *
     * @param other the other resolver
     * @return the chained resolver
     */
    default ClassHierarchyResolver orElse(ClassHierarchyResolver other) {
        requireNonNull(other);
        return new ClassHierarchyResolver() {
            @Override
            public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
                var chi = ClassHierarchyResolver.this.getClassInfo(classDesc);
                if (chi == null)
                    return other.getClassInfo(classDesc);
                return chi;
            }
        };
    }

    /**
     * {@return a {@code ClassHierarchyResolver} that caches class hierarchy
     * information from this resolver}  The returned resolver will not update if
     * the query results from this resolver changed over time.  The thread
     * safety of the returned resolver depends on the thread safety of the map
     * returned by the {@code cacheFactory}.
     *
     * @implSpec
     * The default implementation returns a resolver holding an instance of the
     * cache map provided by the {@code cacheFactory}.  It looks up in the cache
     * map, or if a class name has not yet been queried, queries this resolver
     * and caches the result, including a {@code null} that indicates unknown
     * class names.  The cache map may refuse {@code null} keys and values.
     *
     * @param cacheFactory the factory for the cache
     */
    default ClassHierarchyResolver cached(Supplier<Map<ClassDesc, ClassHierarchyInfo>> cacheFactory) {
        return new ClassHierarchyImpl.CachedClassHierarchyResolver(this, cacheFactory.get());
    }

    /**
     * {@return a {@code ClassHierarchyResolver} that caches class hierarchy
     * information from this resolver}  The returned resolver will not update if
     * the query results from this resolver changed over time.  The returned
     * resolver is not thread-safe.
     * {@snippet file="PackageSnippets.java" region="lookup-class-hierarchy-resolver"}
     *
     * @implSpec The default implementation calls {@link #cached(Supplier)} with
     *           {@link HashMap} supplier as {@code cacheFactory}.
     */
    default ClassHierarchyResolver cached() {
        record Factory() implements Supplier<Map<ClassDesc, ClassHierarchyInfo>> {
            // uses a record as we cannot declare a local class static
            static final Factory INSTANCE = new Factory();

            @Override
            public Map<ClassDesc, ClassHierarchyInfo> get() {
                return new HashMap<>();
            }
        }
        return cached(Factory.INSTANCE);
    }

    /**
     * {@return a {@code ClassHierarchyResolver} that extracts class hierarchy
     * information from {@code class} files returned by a mapping function}  The
     * mapping function should return {@code null} if it cannot provide a
     * {@code class} file for a class name.  Any {@link IOException} from the
     * provided input stream is rethrown as an {@link UncheckedIOException}
     * in {@link #getClassInfo(ClassDesc)}.
     *
     * @param classStreamResolver maps class descriptors to {@code class} file input streams
     */
    static ClassHierarchyResolver ofResourceParsing(Function<ClassDesc, InputStream> classStreamResolver) {
        return new ClassHierarchyImpl.ResourceParsingClassHierarchyResolver(requireNonNull(classStreamResolver));
    }

    /**
     * {@return a {@code ClassHierarchyResolver} that extracts class hierarchy
     * information from {@code class} files located by a class loader}
     *
     * @param loader the class loader, to find class files
     */
    static ClassHierarchyResolver ofResourceParsing(ClassLoader loader) {
        requireNonNull(loader);
        return ofResourceParsing(new Function<>() {
            @Override
            public InputStream apply(ClassDesc classDesc) {
                return loader.getResourceAsStream(Util.toInternalName(classDesc) + ".class");
            }
        });
    }

    /**
     * {@return a {@code ClassHierarchyResolver} that extracts class hierarchy
     * information from collections of class hierarchy metadata}
     *
     * @param interfaces a collection of classes known to be interfaces
     * @param classToSuperClass a map from classes to their super classes
     */
    static ClassHierarchyResolver of(Collection<ClassDesc> interfaces,
                                     Map<ClassDesc, ClassDesc> classToSuperClass) {
        return new StaticClassHierarchyResolver(interfaces, classToSuperClass);
    }

    /**
     * {@return a {@code ClassHierarchyResolver} that extracts class hierarchy
     * information via classes loaded by a class loader with reflection}
     *
     * @param loader the class loader
     */
    static ClassHierarchyResolver ofClassLoading(ClassLoader loader) {
        requireNonNull(loader);
        return new ClassLoadingClassHierarchyResolver(new Function<>() {
            @Override
            public Class<?> apply(ClassDesc cd) {
                try {
                    return Class.forName(Util.toBinaryName(cd), false, loader);
                } catch (ClassNotFoundException ex) {
                    return null;
                }
            }
        });
    }

    /**
     * {@return a {@code ClassHierarchyResolver} that extracts class hierarchy
     * information via classes accessible to a {@link MethodHandles.Lookup}
     * with reflection} If the class resolved is inaccessible to the given
     * lookup, {@link #getClassInfo(ClassDesc)} throws {@link
     * IllegalArgumentException} instead of returning {@code null}.
     *
     * @param lookup the lookup, must be able to access classes to resolve
     */
    static ClassHierarchyResolver ofClassLoading(MethodHandles.Lookup lookup) {
        requireNonNull(lookup);
        return new ClassLoadingClassHierarchyResolver(new Function<>() {
            @Override
            public Class<?> apply(ClassDesc cd) {
                try {
                    return cd.resolveConstantDesc(lookup);
                } catch (IllegalAccessException ex) {
                    throw new IllegalArgumentException(ex);
                } catch (ReflectiveOperationException ex) {
                    return null;
                }
            }
        });
    }
}
