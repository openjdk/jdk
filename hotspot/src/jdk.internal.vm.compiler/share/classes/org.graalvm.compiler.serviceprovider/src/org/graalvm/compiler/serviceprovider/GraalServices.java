/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.serviceprovider;

import static org.graalvm.compiler.serviceprovider.JDK9Method.Java8OrEarlier;
import static org.graalvm.compiler.serviceprovider.JDK9Method.addOpens;
import static org.graalvm.compiler.serviceprovider.JDK9Method.getModule;
import static org.graalvm.compiler.serviceprovider.JDK9Method.getPackages;
import static org.graalvm.compiler.serviceprovider.JDK9Method.isOpenTo;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import jdk.vm.ci.services.JVMCIPermission;
import jdk.vm.ci.services.Services;

/**
 * A mechanism for accessing service providers that abstracts over whether Graal is running on
 * JVMCI-8 or JVMCI-9. In JVMCI-8, a JVMCI specific mechanism is used to lookup services via the
 * hidden JVMCI class loader. In JVMCI-9, the standard {@link ServiceLoader} mechanism is used.
 */
public final class GraalServices {

    private GraalServices() {
    }

    /**
     * Opens all JVMCI packages to the module of a given class. This relies on JVMCI already having
     * opened all its packages to the module defining {@link GraalServices}.
     *
     * @param other all JVMCI packages will be opened to the module defining this class
     */
    public static void openJVMCITo(Class<?> other) {
        Object jvmci = getModule.invoke(Services.class);
        Object otherModule = getModule.invoke(other);
        if (jvmci != otherModule) {
            Set<String> packages = getPackages.invoke(jvmci);
            for (String pkg : packages) {
                boolean opened = isOpenTo.invoke(jvmci, pkg, otherModule);
                if (!opened) {
                    addOpens.invoke(jvmci, pkg, otherModule);
                }
            }
        }
    }

    /**
     * Gets an {@link Iterable} of the providers available for a given service.
     *
     * @throws SecurityException if on JDK8 and a security manager is present and it denies
     *             {@link JVMCIPermission}
     */
    public static <S> Iterable<S> load(Class<S> service) {
        assert !service.getName().startsWith("jdk.vm.ci") : "JVMCI services must be loaded via " + Services.class.getName();
        if (Java8OrEarlier) {
            return load8(service);
        }
        Iterable<S> iterable = ServiceLoader.load(service);
        return new Iterable<S>() {
            @Override
            public Iterator<S> iterator() {
                Iterator<S> iterator = iterable.iterator();
                return new Iterator<S>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public S next() {
                        S provider = iterator.next();
                        // Allow Graal extensions to access JVMCI
                        openJVMCITo(provider.getClass());
                        return provider;
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                    }
                };
            }
        };
    }

    /**
     * {@code Services.load(Class)} is only defined in JVMCI-8.
     */
    private static volatile Method loadMethod;

    @SuppressWarnings("unchecked")
    private static <S> Iterable<S> load8(Class<S> service) throws InternalError {
        try {
            if (loadMethod == null) {
                loadMethod = Services.class.getMethod("load", Class.class);
            }
            return (Iterable<S>) loadMethod.invoke(null, service);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Gets the provider for a given service for which at most one provider must be available.
     *
     * @param service the service whose provider is being requested
     * @param required specifies if an {@link InternalError} should be thrown if no provider of
     *            {@code service} is available
     * @return the requested provider if available else {@code null}
     * @throws SecurityException if on JDK8 and a security manager is present and it denies
     *             {@link JVMCIPermission}
     */
    public static <S> S loadSingle(Class<S> service, boolean required) {
        assert !service.getName().startsWith("jdk.vm.ci") : "JVMCI services must be loaded via " + Services.class.getName();
        Iterable<S> providers = load(service);
        S singleProvider = null;
        try {
            for (Iterator<S> it = providers.iterator(); it.hasNext();) {
                singleProvider = it.next();
                if (it.hasNext()) {
                    S other = it.next();
                    throw new InternalError(String.format("Multiple %s providers found: %s, %s", service.getName(), singleProvider.getClass().getName(), other.getClass().getName()));
                }
            }
        } catch (ServiceConfigurationError e) {
            // If the service is required we will bail out below.
        }
        if (singleProvider == null) {
            if (required) {
                throw new InternalError(String.format("No provider for %s found", service.getName()));
            }
        }
        return singleProvider;
    }
}
