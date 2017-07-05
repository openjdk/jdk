/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.module;

import java.lang.reflect.Module;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Provides;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A services catalog. Each {@code ClassLoader} has an optional {@code
 * ServicesCatalog} for modules that provide services. This is to support
 * ClassLoader centric ServiceLoader.load methods.
 */
public class ServicesCatalog {

    // use RW locks as register is rare
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    /**
     * Represents a service provider in the services catalog.
     */
    public class ServiceProvider {
        private final Module module;
        private final String providerName;
        ServiceProvider(Module module, String providerName) {
            this.module = module;
            this.providerName = providerName;
        }
        public Module module() {
            return module;
        }
        public String providerName() {
            return providerName;
        }
    }

    // service providers
    private final Map<String, Set<ServiceProvider>> loaderServices = new HashMap<>();

    /**
     * Creates a new module catalog.
     */
    public ServicesCatalog() { }

    /**
     * Registers the module in this module catalog.
     */
    public void register(Module m) {
        ModuleDescriptor descriptor = m.getDescriptor();

        writeLock.lock();
        try {
            // extend the services map
            for (Provides ps : descriptor.provides().values()) {
                String service = ps.service();
                Set<String> providerNames = ps.providers();

                // create a new set to replace the existing
                Set<ServiceProvider> result = new HashSet<>();
                Set<ServiceProvider> providers = loaderServices.get(service);
                if (providers != null) {
                    result.addAll(providers);
                }
                for (String pn : providerNames) {
                    result.add(new ServiceProvider(m, pn));
                }
                loaderServices.put(service, Collections.unmodifiableSet(result));
            }

        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the (possibly empty) set of service providers that implement the
     * given service type.
     *
     * @see java.util.ServiceLoader
     */
    public Set<ServiceProvider> findServices(String service) {
        readLock.lock();
        try {
            return loaderServices.getOrDefault(service, Collections.emptySet());
        } finally {
            readLock.unlock();
        }
    }
}
