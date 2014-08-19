/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.util.ServiceFinder;

import javax.xml.ws.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates {@link ServiceInterceptor}.
 *
 * <p>
 * Code that wishes to inject {@link ServiceInterceptor} into {@link WSService}
 * must implement this class. There are two ways to have the JAX-WS RI
 * recognize your {@link ServiceInterceptor}s.
 *
 * <h3>Use {@link ServiceFinder}</h3>
 * <p>
 * {@link ServiceInterceptorFactory}s discovered via {@link ServiceFinder}
 * will be incorporated to all {@link WSService} instances.
 *
 * <h3>Register per-thread</h3>
 *
 *
 * @author Kohsuke Kawaguchi
 * @see ServiceInterceptor
 * @see 2.1 EA3
 */
public abstract class ServiceInterceptorFactory {
    public abstract ServiceInterceptor create(@NotNull WSService service);

    /**
     * Loads all {@link ServiceInterceptor}s and return aggregated one.
     */
    public static @NotNull ServiceInterceptor load(@NotNull WSService service, @Nullable ClassLoader cl) {
        List<ServiceInterceptor> l = new ArrayList<ServiceInterceptor>();

        // first service look-up
        for( ServiceInterceptorFactory f : ServiceFinder.find(ServiceInterceptorFactory.class))
            l.add(f.create(service));

        // then thread-local
        for( ServiceInterceptorFactory f : threadLocalFactories.get())
            l.add(f.create(service));

        return ServiceInterceptor.aggregate(l.toArray(new ServiceInterceptor[l.size()]));
    }

    private static ThreadLocal<Set<ServiceInterceptorFactory>> threadLocalFactories = new ThreadLocal<Set<ServiceInterceptorFactory>>() {
        protected Set<ServiceInterceptorFactory> initialValue() {
            return new HashSet<ServiceInterceptorFactory>();
        }
    };

    /**
     * Registers {@link ServiceInterceptorFactory} for this thread.
     *
     * <p>
     * Once registered, {@link ServiceInterceptorFactory}s are consulted for every
     * {@link Service} created in this thread, until it gets unregistered.
     */
    public static boolean registerForThread(ServiceInterceptorFactory factory) {
        return threadLocalFactories.get().add(factory);
    }

    /**
     * Removes previously registered {@link ServiceInterceptorFactory} for this thread.
     */
    public static boolean unregisterForThread(ServiceInterceptorFactory factory) {
        return threadLocalFactories.get().remove(factory);
    }
}
