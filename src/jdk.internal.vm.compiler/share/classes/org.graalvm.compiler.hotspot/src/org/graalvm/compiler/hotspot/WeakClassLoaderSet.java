/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;

/**
 * A set of weak references to {@link ClassLoader}s.
 */
public final class WeakClassLoaderSet {

    /**
     * Copy-on-write set of loaders.
     */
    private volatile AtomicReference<EconomicSet<Reference<ClassLoader>>> loaders = new AtomicReference<>(EconomicSet.create(RefEquivalence.INSTANCE));

    public WeakClassLoaderSet(ClassLoader... initialEntries) {
        for (ClassLoader loader : initialEntries) {
            loaders.get().add(new WeakReference<>(loader));
        }
    }

    /**
     * Adds {@code loader} to this set.
     */
    public void add(ClassLoader loader) {
        Reference<ClassLoader> addNewRef = new WeakReference<>(loader);
        EconomicSet<Reference<ClassLoader>> currentLoaders = loaders.get();
        if (!currentLoaders.contains(addNewRef)) {
            this.loaders.getAndUpdate(new UnaryOperator<EconomicSet<Reference<ClassLoader>>>() {
                @Override
                public EconomicSet<Reference<ClassLoader>> apply(EconomicSet<Reference<ClassLoader>> t) {
                    EconomicSet<Reference<ClassLoader>> newLoaders = EconomicSet.create(RefEquivalence.INSTANCE, t);
                    newLoaders.add(addNewRef);
                    return newLoaders;
                }
            });
        }
    }

    /**
     * Tries to resolve {@code className} to {@link Class} instances with the loaders in this set.
     *
     * @param className name of a class to resolve
     * @param resolutionFailures all resolution failures are returned in this set
     * @return the set of classes successfully resolved
     */
    public EconomicSet<Class<?>> resolve(String className, EconomicSet<ClassNotFoundException> resolutionFailures) {
        EconomicSet<Class<?>> found = EconomicSet.create();
        Iterator<Reference<ClassLoader>> it = loaders.get().iterator();
        while (it.hasNext()) {
            Reference<ClassLoader> ref = it.next();
            ClassLoader loader = ref.get();
            if (loader == null) {
                it.remove();
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className, false, loader);
                found.add(clazz);
            } catch (ClassNotFoundException ex) {
                resolutionFailures.add(ex);
            }
        }
        return found;
    }

    private static final class RefEquivalence extends Equivalence {
        static final Equivalence INSTANCE = new RefEquivalence();

        private RefEquivalence() {
        }

        @Override
        public boolean equals(Object a, Object b) {
            Reference<?> refA = (Reference<?>) a;
            Reference<?> refB = (Reference<?>) b;
            Object referentA = refA.get();
            Object referentB = refB.get();
            return Objects.equals(referentA, referentB);
        }

        @Override
        public int hashCode(Object o) {
            Reference<?> ref = (Reference<?>) o;
            Object obj = ref.get();
            return obj == null ? 0 : obj.hashCode();
        }
    }
}
