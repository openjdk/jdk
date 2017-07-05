/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.jmx.mbeanserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.loading.ClassLoaderRepository;

public class Util {
    static <K, V> Map<K, V> newMap() {
        return new HashMap<K, V>();
    }

    static <K, V> Map<K, V> newSynchronizedMap() {
        return Collections.synchronizedMap(Util.<K, V>newMap());
    }

    static <K, V> IdentityHashMap<K, V> newIdentityHashMap() {
        return new IdentityHashMap<K, V>();
    }

    static <K, V> Map<K, V> newSynchronizedIdentityHashMap() {
        Map<K, V> map = newIdentityHashMap();
        return Collections.synchronizedMap(map);
    }

    static <K, V> SortedMap<K, V> newSortedMap() {
        return new TreeMap<K, V>();
    }

    static <K, V> SortedMap<K, V> newSortedMap(Comparator<? super K> comp) {
        return new TreeMap<K, V>(comp);
    }

    static <K, V> Map<K, V> newInsertionOrderMap() {
        return new LinkedHashMap<K, V>();
    }

    static <K, V> WeakHashMap<K, V> newWeakHashMap() {
        return new WeakHashMap<K, V>();
    }

    static <E> Set<E> newSet() {
        return new HashSet<E>();
    }

    static <E> Set<E> newSet(Collection<E> c) {
        return new HashSet<E>(c);
    }

    static <E> List<E> newList() {
        return new ArrayList<E>();
    }

    static <E> List<E> newList(Collection<E> c) {
        return new ArrayList<E>(c);
    }

    public static ObjectName newObjectName(String s) {
        try {
            return new ObjectName(s);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /* This method can be used by code that is deliberately violating the
     * allowed checked casts.  Rather than marking the whole method containing
     * the code with @SuppressWarnings, you can use a call to this method for
     * the exact place where you need to escape the constraints.  Typically
     * you will "import static" this method and then write either
     *    X x = cast(y);
     * or, if that doesn't work (e.g. X is a type variable)
     *    Util.<X>cast(y);
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object x) {
        return (T) x;
    }

    /**
     * Computes a descriptor hashcode from its names and values.
     * @param names  the sorted array of descriptor names.
     * @param values the array of descriptor values.
     * @return a hash code value, as described in {@link #hashCode(Descriptor)}
     */
    public static int hashCode(String[] names, Object[] values) {
        int hash = 0;
        for (int i = 0; i < names.length; i++) {
            Object v = values[i];
            int h;
            if (v == null) {
                h = 0;
            } else if (v instanceof Object[]) {
                h = Arrays.deepHashCode((Object[]) v);
            } else if (v.getClass().isArray()) {
                h = Arrays.deepHashCode(new Object[]{v}) - 31;
            // hashcode of a list containing just v is
            // v.hashCode() + 31, see List.hashCode()
            } else {
                h = v.hashCode();
            }
            hash += names[i].toLowerCase().hashCode() ^ h;
        }
        return hash;
    }

    /**
     * Filters a set of ObjectName according to a given pattern.
     *
     * @param pattern the pattern that the returned names must match.
     * @param all     the set of names to filter.
     * @return a set of ObjectName from which non matching names
     *         have been removed.
     */
    public static Set<ObjectName> filterMatchingNames(ObjectName pattern,
                                        Set<ObjectName> all) {
        // If no pattern, just return all names
        if (pattern == null
                || all.isEmpty()
                || ObjectName.WILDCARD.equals(pattern))
            return all;

        // If there's a pattern, do the matching.
        final Set<ObjectName> res = equivalentEmptySet(all);
        for (ObjectName n : all) if (pattern.apply(n)) res.add(n);
        return res;
    }

    /**
     * An abstract ClassLoaderRepository that contains a single class loader.
     **/
    private final static class SingleClassLoaderRepository
            implements ClassLoaderRepository {
        private final ClassLoader singleLoader;

        SingleClassLoaderRepository(ClassLoader loader) {
            this.singleLoader = loader;
        }

        ClassLoader getSingleClassLoader() {
           return singleLoader;
        }

        private Class<?> loadClass(String className, ClassLoader loader)
                throws ClassNotFoundException {
            return Class.forName(className, false, loader);
        }

        public Class<?> loadClass(String className)
                throws ClassNotFoundException {
            return loadClass(className, getSingleClassLoader());
        }

        public Class<?> loadClassWithout(ClassLoader exclude,
                String className) throws ClassNotFoundException {
            final ClassLoader loader = getSingleClassLoader();
            if (exclude != null && exclude.equals(loader))
                throw new ClassNotFoundException(className);
            return loadClass(className, loader);
        }

        public Class<?> loadClassBefore(ClassLoader stop, String className)
                throws ClassNotFoundException {
            return loadClassWithout(stop, className);
        }
    }

    /**
     * Returns a ClassLoaderRepository that contains a single class loader.
     * @param loader the class loader contained in the returned repository.
     * @return a ClassLoaderRepository that contains the single loader.
     */
    public static ClassLoaderRepository getSingleClassLoaderRepository(
            final ClassLoader loader) {
        return new SingleClassLoaderRepository(loader);
    }

    public static <T> Set<T> cloneSet(Set<T> set) {
        if (set instanceof SortedSet) {
            @SuppressWarnings("unchecked")
            SortedSet<T> sset = (SortedSet<T>) set;
            set = new TreeSet<T>(sset.comparator());
            set.addAll(sset);
        } else
            set = new HashSet<T>(set);
        return set;
    }

    public static <T> Set<T> equivalentEmptySet(Set<T> set) {
        if (set instanceof SortedSet) {
            @SuppressWarnings("unchecked")
            SortedSet<T> sset = (SortedSet<T>) set;
            set = new TreeSet<T>(sset.comparator());
        } else if (set != null) {
            set = new HashSet<T>(set.size());
        } else
            set = new HashSet<T>();
        return set;
    }
}
