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

import com.sun.tracing.ProviderFactory;
import com.sun.tracing.Provider;

/**
 * Factory class to create tracing Providers.
 *
 * This factory will create tracing instances that do nothing.
 * It is used when no tracing is desired, but Provider instances still
 * must be generated so that tracing calls in the application continue to
 * run.
 *
 * @since 1.7
 */
public class NullProviderFactory extends ProviderFactory {

    /**
     * Creates and returns a Null provider.
     *
     * See comments at {@code ProviderSkeleton.createProvider()} for more
     * details.
     *
     * @return a provider whose probe trigger are no-ops.
     */
    public <T extends Provider> T createProvider(Class<T> cls) {
        NullProvider provider = new NullProvider(cls);
        provider.init();
        return provider.newProxyInstance();
    }
}

class NullProvider extends ProviderSkeleton {

    NullProvider(Class<? extends Provider> type) {
        super(type);
    }

    protected ProbeSkeleton createProbe(Method m) {
        return new NullProbe(m.getParameterTypes());
    }
}

class NullProbe extends ProbeSkeleton {

    public NullProbe(Class<?>[] parameters) {
        super(parameters);
    }

    public boolean isEnabled() {
        return false;
    }

    public void uncheckedTrigger(Object[] args) {
    }
}

