/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile;

import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.internal.classfile.impl.Util;

import jdk.internal.classfile.impl.ClassHierarchyImpl;

/**
 * Provides class hierarchy information for generating correct stack maps
 * during code building.
 */
@FunctionalInterface
public interface ClassHierarchyResolver {

    /**
     * Default singleton instance of {@linkplain ClassHierarchyResolver}
     * using {@link ClassLoader#getSystemResourceAsStream(String)}
     * as the {@code ClassStreamResolver}
     */
    ClassHierarchyResolver DEFAULT_CLASS_HIERARCHY_RESOLVER
            = ofCached(ofParsing(ClassLoader.getSystemClassLoader())
                    .orElse(ofReflection(ClassLoader.getSystemClassLoader())),
            new Supplier<Map<ClassDesc, ClassHierarchyInfo>>() {
                @Override
                public Map<ClassDesc, ClassHierarchyInfo> get() {
                    return new ConcurrentHashMap<>();
                }
            });

    /**
     * {@return the {@link ClassHierarchyInfo} for a given class name, or null
     * if the name is unknown to the resolver}
     * @param classDesc descriptor of the class
     */
    ClassHierarchyInfo getClassInfo(ClassDesc classDesc);

    /**
     * Chains this {@linkplain ClassHierarchyResolver} with another to be
     * consulted if this resolver does not know about the specified class.
     *
     * @param other the other resolver
     * @return the chained resolver
     */
    default ClassHierarchyResolver orElse(ClassHierarchyResolver other) {
        return new ClassHierarchyResolver() {
            @Override
            public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
                var chi = ClassHierarchyResolver.this.getClassInfo(classDesc);
                if (chi == null)
                    chi = other.getClassInfo(classDesc);
                return chi;
            }
        };
    }

    /**
     * Information about a resolved class.
     * @param thisClass descriptor of this class
     * @param isInterface whether this class is an interface
     * @param superClass descriptor of the superclass (not relevant for interfaces)
     */
    record ClassHierarchyInfo(ClassDesc thisClass, boolean isInterface, ClassDesc superClass) {
    }

    /**
     * Returns a ClassHierarchyResolver that caches class hierarchy information from another
     * resolver. The returned resolver will not update if delegate resolver returns differently.
     * The returned resolver is not thread-safe.
     * {@snippet file="PackageSnippets.java" region="lookup-class-hierarchy-resolver"}
     *
     * @param delegate the resolver to pull information from
     * @return the ClassHierarchyResolver
     */
    static ClassHierarchyResolver ofCached(ClassHierarchyResolver delegate) {
        class Factory implements Supplier<Map<ClassDesc, ClassHierarchyInfo>> {
            static final Factory INSTANCE = new Factory();

            @Override
            public Map<ClassDesc, ClassHierarchyInfo> get() {
                return new HashMap<>();
            }
        }
        return ofCached(delegate, Factory.INSTANCE);
    }

    /**
     * Returns a ClassHierarchyResolver that caches class hierarchy information from another
     * resolver. The returned resolver will not update if delegate resolver returns differently.
     * The thread safety of the returned resolver depends on the thread safety of the map
     * returned by the {@code cacheFactory}.
     *
     * @param delegate the resolver to pull information from
     * @param cacheFactory the factory for the cache
     * @return the ClassHierarchyResolver
     */
    static ClassHierarchyResolver ofCached(ClassHierarchyResolver delegate,
                                           Supplier<Map<ClassDesc, ClassHierarchyInfo>> cacheFactory) {
        return new ClassHierarchyImpl.CachedClassHierarchyResolver(delegate, cacheFactory.get());
    }

    /**
     * Returns a {@linkplain ClassHierarchyResolver} that extracts class hierarchy
     * information from classfiles located by a mapping function. The mapping function
     * should return null if it cannot provide a mapping for a classfile. Any IOException
     * from the provided input stream is rethrown as an UncheckedIOException.
     *
     * @param classStreamResolver maps class descriptors to classfile input streams
     * @return the {@linkplain ClassHierarchyResolver}
     */
    static ClassHierarchyResolver ofParsing(Function<ClassDesc, InputStream> classStreamResolver) {
        return new ClassHierarchyImpl.ParsingClassHierarchyResolver(classStreamResolver);
    }

    /**
     * Returns a {@linkplain ClassHierarchyResolver} that extracts class hierarchy
     * information from classfiles located by a class loader.
     *
     * @param loader the class loader, to find class files
     * @return the {@linkplain ClassHierarchyResolver}
     */
    static ClassHierarchyResolver ofParsing(ClassLoader loader) {
        return ofParsing(new Function<ClassDesc, InputStream>() {
            @Override
            public InputStream apply(ClassDesc classDesc) {
                return loader.getResourceAsStream(Util.toInternalName(classDesc) + ".class");
            }
        });
    }

    /**
     * Returns a {@linkplain  ClassHierarchyResolver} that extracts class hierarchy
     * information from collections of class hierarchy metadata
     *
     * @param interfaces a collection of classes known to be interfaces
     * @param classToSuperClass a map from classes to their super classes
     * @return the {@linkplain ClassHierarchyResolver}
     */
    static ClassHierarchyResolver of(Collection<ClassDesc> interfaces,
                                            Map<ClassDesc, ClassDesc> classToSuperClass) {
        return new ClassHierarchyImpl.StaticClassHierarchyResolver(interfaces, classToSuperClass);
    }

    /**
     * Returns a ClassHierarchyResolver that extracts class hierarchy information via
     * the Reflection API with a {@linkplain ClassLoader}.
     *
     * @param loader the class loader
     * @return the class hierarchy resolver
     */
    static ClassHierarchyResolver ofReflection(ClassLoader loader) {
        return new ClassHierarchyImpl.ReflectionClassHierarchyResolver() {
            @Override
            protected Class<?> resolve(ClassDesc cd) {
                try {
                    return Class.forName(Util.toBinaryName(cd.descriptorString()), false, loader);
                } catch (ClassNotFoundException ex) {
                    return null;
                }
            }
        };
    }

    /**
     * Returns a ClassHierarchyResolver that extracts class hierarchy information via
     * the Reflection API with a {@linkplain MethodHandles.Lookup Lookup}. If the class
     * resolved is inaccessible to the given lookup, it throws {@link
     * IllegalArgumentException} instead of returning {@code null}.
     *
     * @param lookup the lookup, must be able to access classes to resolve
     * @return the class hierarchy resolver
     */
    static ClassHierarchyResolver ofReflection(MethodHandles.Lookup lookup) {
        return new ClassHierarchyImpl.ReflectionClassHierarchyResolver() {
            @Override
            protected Class<?> resolve(ClassDesc cd) {
                try {
                    return (Class<?>) cd.resolveConstantDesc(lookup);
                } catch (IllegalAccessException ex) {
                    throw new IllegalArgumentException(ex);
                } catch (ReflectiveOperationException ex) {
                    return null;
                }
            }
        };
    }
}
