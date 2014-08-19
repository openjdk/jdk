/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.tracing.dtrace;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.security.Permission;

import com.sun.tracing.ProviderFactory;
import com.sun.tracing.Provider;

/**
 * Factory class to create JSDT Providers.
 *
 * This class contains methods to create an instance of a Provider
 * interface which can be used to place tracepoints in an application.
 * Method calls upon that instance trigger DTrace probes that
 * are visible from DTrace scripts.   Such calls have no other
 * side effects in the application.
 * <p>
 * The DTrace script mechanisms for listing and matching probes will not see
 * nor match any probes until the provider they reside in is created by a
 * call to {@code createProvider()} (or {@code createProviders()}).
 * <p>
 * Providers that are created should be disposed of when they are no longer
 * needed to free up system resources, at which point the associated
 * DTrace probes will no longer be available to DTrace.  One disposes a
 * provider by calling
 * {@link com.sun.tracing.Provider#dispose Provider.dispose()} on a
 * created provider instance.
 *
 * @since 1.7
 */
public final class DTraceProviderFactory extends ProviderFactory {
    /**
     * Creates an instance of a provider which can then be used to trigger
     * DTrace probes.
     *
     * The provider specification, provided as an argument, should only
     * contain methods which have a 'void' return type and String or
     * integer-based typed arguments (long, int, short, char, byte, or boolean).
     *
     * @param cls A user-defined interface which extends {@code Provider}.
     * @return An instance of the interface which is used to trigger
     * the DTrace probes.
     * @throws java.lang.SecurityException if a security manager has been
     * installed and it denies
     * RuntimePermission("com.sun.dtrace.jsdt.createProvider")
     * @throws java.lang.IllegalArgumentException if the interface contains
     * methods that do not return null, or that contain arguments that are
     * not String or integer types.
     */
    public <T extends Provider> T createProvider(Class<T> cls) {
        DTraceProvider jsdt = new DTraceProvider(cls);
        T proxy = jsdt.newProxyInstance();
        jsdt.setProxy(proxy);
        jsdt.init();
        new Activation(jsdt.getModuleName(), new DTraceProvider[] { jsdt });
        return proxy;
    }

    /**
     * Creates multiple providers at once.
     *
     * This method batches together a number of provider instantiations.
     * It works similarly
     * to {@code createProvider}, but operates on a set of providers instead
     * of one at a time.  This method is in place since some DTrace
     * implementations limit the number of times that providers can be
     * created.  When numerous providers can be created at once with this
     * method, it will count only as a single creation point to DTrace, thus
     * it uses less system resources.
     * <p>
     * All of the probes in the providers will be visible to DTrace after
     * this call and all will remain visible until all of the providers
     * are disposed.
     * <p>
     * The {@code moduleName} parameter will override any {@code ModuleName}
     * annotation associated with any of the providers in the set.
     * All of the probes created by this call will share the same
     * module name.
     * <p>
     * @param providers a set of provider specification interfaces
     * @param moduleName the module name to associate with all probes
     * @return A map which maps the provider interface specification to an
     * implementing instance.
     * @throws java.lang.SecurityException if a security manager has been
     * installed and it denies
     * RuntimePermission("com.sun.dtrace.jsdt.createProvider")
     * @throws java.lang.IllegalArgumentException if any of the interface
     * contains methods that do not return null, or that contain arguments
     * that are not String or integer types.
     */
    public Map<Class<? extends Provider>,Provider> createProviders(
            Set<Class<? extends Provider>> providers, String moduleName) {
        HashMap<Class<? extends Provider>,Provider> map =
            new HashMap<Class<? extends Provider>,Provider>();
        HashSet<DTraceProvider> jsdts = new HashSet<DTraceProvider>();
        for (Class<? extends Provider> cls : providers) {
            DTraceProvider jsdt = new DTraceProvider(cls);
            jsdts.add(jsdt);
            map.put(cls, jsdt.newProxyInstance());
        }
        new Activation(moduleName, jsdts.toArray(new DTraceProvider[0]));
        return map;
    }

    /**
     * Used to check the status of DTrace support in the underlying JVM and
     * operating system.
     *
     * This is an informative method only - the Java-level effects of
     * creating providers and triggering probes will not change whether or
     * not DTrace is supported by the underlying systems.
     *
     * @return true if DTrace is supported
     */
    public static boolean isSupported() {
        try {
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                Permission perm = new RuntimePermission(
                        "com.sun.tracing.dtrace.createProvider");
                security.checkPermission(perm);
            }
            return JVM.isSupported();
        } catch (SecurityException e) {
            return false;
        }
    }
}
