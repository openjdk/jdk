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

package sun.tracing;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.sun.tracing.ProviderFactory;
import com.sun.tracing.Provider;
import com.sun.tracing.Probe;

/**
 * Factory class to create tracing Providers.
 *
 * This factory creates a "multiplex provider", which is a provider that
 * encapsulates a list of providers and whose probes trigger a corresponding
 * trigger in each of the encapsulated providers' probes.
 *
 * This is used when there are multiple tracing frameworks activated at once.
 * A user-defined provider gets implementation for each of the activated
 * frameworks and this multiplex framework is what is ultimately passed
 * back to the user.  All probe triggers are multiplexed to each
 * active framework.
 *
 * @since 1.7
 */
public class MultiplexProviderFactory extends ProviderFactory {

    private Set<ProviderFactory> factories;

    public MultiplexProviderFactory(Set<ProviderFactory> factories) {
        this.factories = factories;
    }

    public <T extends Provider> T createProvider(Class<T> cls) {
        HashSet<Provider> providers = new HashSet<Provider>();
        for (ProviderFactory factory : factories) {
            providers.add(factory.createProvider(cls));
        }
        MultiplexProvider provider = new MultiplexProvider(cls, providers);
        provider.init();
        return provider.newProxyInstance();
    }
}

class MultiplexProvider extends ProviderSkeleton {

    private Set<Provider> providers;

    protected ProbeSkeleton createProbe(Method m) {
        return new MultiplexProbe(m, providers);
    }

    MultiplexProvider(Class<? extends Provider> type, Set<Provider> providers) {
        super(type);
        this.providers = providers;
    }

    public void dispose() {
        for (Provider p : providers) {
            p.dispose();
        }
        super.dispose();
    }
}

class MultiplexProbe extends ProbeSkeleton {

    private Set<Probe> probes;

    MultiplexProbe(Method m, Set<Provider> providers) {
        super(m.getParameterTypes());
        probes = new HashSet<Probe>();
        for (Provider p : providers) {
            Probe probe = p.getProbe(m);
            if (probe != null) {
                probes.add(probe);
            }
        }
    }

    public boolean isEnabled() {
        for (Probe p : probes) {
            if (p.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    public void uncheckedTrigger(Object[] args) {
        for (Probe p : probes) {
            try {
                // try the fast path
                ProbeSkeleton ps = (ProbeSkeleton)p;
                ps.uncheckedTrigger(args);
            } catch (ClassCastException e) {
                // Probe.trigger takes an "Object ..." varargs parameter,
                // so we can't call it directly.
                try {
                    Method m = Probe.class.getMethod(
                        "trigger", Class.forName("[java.lang.Object"));
                    m.invoke(p, args);
                } catch (Exception e1) {
                    assert false; // This shouldn't happen
                }
            }
        }
    }
}

