/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A <em>services catalog</em>. Each {@code ClassLoader} and {@code Layer} has
 * an optional {@code ServicesCatalog} for modules that provide services.
 *
 * @see java.util.ServiceLoader
 */
public interface ServicesCatalog {

    /**
     * Represents a service provider in the services catalog.
     */
    public final class ServiceProvider {
        private final Module module;
        private final String providerName;

        public ServiceProvider(Module module, String providerName) {
            this.module = module;
            this.providerName = providerName;
        }

        public Module module() {
            return module;
        }

        public String providerName() {
            return providerName;
        }

        @Override
        public int hashCode() {
            return Objects.hash(module, providerName);
        }

        @Override
        public boolean equals(Object ob) {
            if (!(ob instanceof ServiceProvider))
                return false;
            ServiceProvider that = (ServiceProvider)ob;
            return Objects.equals(this.module, that.module)
                    && Objects.equals(this.providerName, that.providerName);
        }
    }

    /**
     * Registers the providers in the given module in this services catalog.
     *
     * @throws UnsupportedOperationException
     *         If this services catalog is immutable
     */
    void register(Module module);

    /**
     * Returns the (possibly empty) set of service providers that implement the
     * given service type.
     */
    Set<ServiceProvider> findServices(String service);

    /**
     * Creates a ServicesCatalog that supports concurrent registration and
     * and lookup.
     */
    static ServicesCatalog create() {
        return new ServicesCatalog() {

            private Map<String, Set<ServiceProvider>> map = new ConcurrentHashMap<>();

            @Override
            public void register(Module m) {
                ModuleDescriptor descriptor = m.getDescriptor();

                for (Provides provides : descriptor.provides().values()) {
                    String service = provides.service();
                    Set<String> providerNames = provides.providers();

                    // create a new set to replace the existing
                    Set<ServiceProvider> result = new HashSet<>();
                    Set<ServiceProvider> providers = map.get(service);
                    if (providers != null) {
                        result.addAll(providers);
                    }
                    for (String pn : providerNames) {
                        result.add(new ServiceProvider(m, pn));
                    }
                    map.put(service, Collections.unmodifiableSet(result));
                }

            }

            @Override
            public Set<ServiceProvider> findServices(String service) {
                return map.getOrDefault(service, Collections.emptySet());
            }

        };
    }
}