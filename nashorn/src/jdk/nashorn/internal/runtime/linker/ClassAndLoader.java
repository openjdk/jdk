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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A tuple of a class loader and a single class representative of the classes that can be loaded through it. Its
 * equals/hashCode is defined in terms of the identity of the class loader. The rationale for this class is that it
 * couples a class loader with a random representative class coming from that loader - this representative class is then
 * used to determine if one loader can see the other loader's classes.
 */
final class ClassAndLoader {
    static AccessControlContext createPermAccCtxt(final String... permNames) {
        final Permissions perms = new Permissions();
        for (final String permName : permNames) {
            perms.add(new RuntimePermission(permName));
        }
        return new AccessControlContext(new ProtectionDomain[] { new ProtectionDomain(null, perms) });
    }

    private static final AccessControlContext GET_LOADER_ACC_CTXT = createPermAccCtxt("getClassLoader");

    private final Class<?> representativeClass;
    // Don't access this directly; most of the time, use getRetrievedLoader(), or if you know what you're doing,
    // getLoader().
    private ClassLoader loader;
    // We have mild affinity against eagerly retrieving the loader, as we need to do it in a privileged block. For
    // the most basic case of looking up an already-generated adapter info for a single type, we avoid it.
    private boolean loaderRetrieved;

    ClassAndLoader(final Class<?> representativeClass, final boolean retrieveLoader) {
        this.representativeClass = representativeClass;
        if(retrieveLoader) {
            retrieveLoader();
        }
    }

    Class<?> getRepresentativeClass() {
        return representativeClass;
    }

    boolean canSee(ClassAndLoader other) {
        try {
            final Class<?> otherClass = other.getRepresentativeClass();
            return Class.forName(otherClass.getName(), false, getLoader()) == otherClass;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    ClassLoader getLoader() {
        if(!loaderRetrieved) {
            retrieveLoader();
        }
        return getRetrievedLoader();
    }

    ClassLoader getRetrievedLoader() {
        assert loaderRetrieved;
        return loader;
    }

    private void retrieveLoader() {
        loader = representativeClass.getClassLoader();
        loaderRetrieved = true;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof ClassAndLoader && ((ClassAndLoader)obj).getRetrievedLoader() == getRetrievedLoader();
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(getRetrievedLoader());
    }

    /**
     * Given a list of types that define the superclass/interfaces for an adapter class, returns a single type from the
     * list that will be used to attach the adapter to its ClassValue. The first type in the array that is defined in a
     * class loader that can also see all other types is returned. If there is no such loader, an exception is thrown.
     * @param types the input types
     * @return the first type from the array that is defined in a class loader that can also see all other types.
     */
    static ClassAndLoader getDefiningClassAndLoader(final Class<?>[] types) {
        // Short circuit the cheap case
        if(types.length == 1) {
            return new ClassAndLoader(types[0], false);
        }

        return AccessController.doPrivileged(new PrivilegedAction<ClassAndLoader>() {
            @Override
            public ClassAndLoader run() {
                return getDefiningClassAndLoaderPrivileged(types);
            }
        }, GET_LOADER_ACC_CTXT);
    }

    static ClassAndLoader getDefiningClassAndLoaderPrivileged(final Class<?>[] types) {
        final Collection<ClassAndLoader> maximumVisibilityLoaders = getMaximumVisibilityLoaders(types);

        final Iterator<ClassAndLoader> it = maximumVisibilityLoaders.iterator();
        if(maximumVisibilityLoaders.size() == 1) {
            // Fortunate case - single maximally specific class loader; return its representative class.
            return it.next();
        }

        // Ambiguity; throw an error.
        assert maximumVisibilityLoaders.size() > 1; // basically, can't be zero
        final StringBuilder b = new StringBuilder();
        b.append(it.next().getRepresentativeClass().getCanonicalName());
        while(it.hasNext()) {
            b.append(", ").append(it.next().getRepresentativeClass().getCanonicalName());
        }
        throw typeError("extend.ambiguous.defining.class", b.toString());
    }

    /**
     * Given an array of types, return a subset of their class loaders that are maximal according to the
     * "can see other loaders' classes" relation, which is presumed to be a partial ordering.
     * @param types types
     * @return a collection of maximum visibility class loaders. It is guaranteed to have at least one element.
     */
    private static Collection<ClassAndLoader> getMaximumVisibilityLoaders(final Class<?>[] types) {
        final List<ClassAndLoader> maximumVisibilityLoaders = new LinkedList<>();
        outer:  for(final ClassAndLoader maxCandidate: getClassLoadersForTypes(types)) {
            final Iterator<ClassAndLoader> it = maximumVisibilityLoaders.iterator();
            while(it.hasNext()) {
                final ClassAndLoader existingMax = it.next();
                final boolean candidateSeesExisting = maxCandidate.canSee(existingMax);
                final boolean exitingSeesCandidate = existingMax.canSee(maxCandidate);
                if(candidateSeesExisting) {
                    if(!exitingSeesCandidate) {
                        // The candidate sees the the existing maximum, so drop the existing one as it's no longer maximal.
                        it.remove();
                    }
                    // NOTE: there's also the anomalous case where both loaders see each other. Not sure what to do
                    // about that one, as two distinct class loaders both seeing each other's classes is weird and
                    // violates the assumption that the relation "sees others' classes" is a partial ordering. We'll
                    // just not do anything, and treat them as incomparable; hopefully some later class loader that
                    // comes along can eliminate both of them, if it can not, we'll end up with ambiguity anyway and
                    // throw an error at the end.
                } else if(exitingSeesCandidate) {
                    // Existing sees the candidate, so drop the candidate.
                    continue outer;
                }
            }
            // If we get here, no existing maximum visibility loader could see the candidate, so the candidate is a new
            // maximum.
            maximumVisibilityLoaders.add(maxCandidate);
        }
        return maximumVisibilityLoaders;
    }

    private static Collection<ClassAndLoader> getClassLoadersForTypes(final Class<?>[] types) {
        final Map<ClassAndLoader, ClassAndLoader> classesAndLoaders = new LinkedHashMap<>();
        for(final Class<?> c: types) {
            final ClassAndLoader cl = new ClassAndLoader(c, true);
            if(!classesAndLoaders.containsKey(cl)) {
                classesAndLoaders.put(cl, cl);
            }
        }
        return classesAndLoaders.keySet();
    }
}
