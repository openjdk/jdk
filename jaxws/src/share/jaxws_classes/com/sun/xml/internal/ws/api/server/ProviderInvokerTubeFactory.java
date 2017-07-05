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

package com.sun.xml.internal.ws.api.server;


import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.server.provider.AsyncProviderInvokerTube;
import com.sun.xml.internal.ws.server.provider.ProviderArgumentsBuilder;
import com.sun.xml.internal.ws.server.provider.ProviderInvokerTube;
import com.sun.xml.internal.ws.server.provider.SyncProviderInvokerTube;
import com.sun.xml.internal.ws.util.ServiceFinder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for Provider invoker tubes that know how to handle specific
 * types of Providers (i.e., javax.xml.ws.Provider).
 *
 */

public abstract class ProviderInvokerTubeFactory<T> {
    /**
     *
     */
    protected abstract ProviderInvokerTube<T> doCreate(@NotNull final Class<T> implType,
                                                       @NotNull final Invoker invoker,
                                                       @NotNull final ProviderArgumentsBuilder<?> argsBuilder,
                                                                final boolean isAsync);

    private static final ProviderInvokerTubeFactory DEFAULT = new DefaultProviderInvokerTubeFactory();

    private static class DefaultProviderInvokerTubeFactory<T> extends ProviderInvokerTubeFactory<T> {
        @Override
        public ProviderInvokerTube<T> doCreate(@NotNull final Class<T> implType,
                                               @NotNull final Invoker invoker,
                                               @NotNull final ProviderArgumentsBuilder<?> argsBuilder,
                                                        final boolean isAsync)
        {
            return createDefault(implType, invoker, argsBuilder, isAsync);
        }
    }

    /**
     * @param classLoader
     * @param container
     * @param implType
     * @param invoker
     * @param argsBuilder
     * @param isAsync
     *
     * @return
     */
    public static <T> ProviderInvokerTube<T> create(@Nullable final ClassLoader classLoader,
                                                    @NotNull  final Container container,
                                                    @NotNull  final Class<T> implType,
                                                    @NotNull  final Invoker invoker,
                                                    @NotNull  final ProviderArgumentsBuilder<?> argsBuilder,
                                                              final boolean isAsync)
    {
        for (ProviderInvokerTubeFactory factory : ServiceFinder.find(ProviderInvokerTubeFactory.class,
                                                                     classLoader, container))
        {
            ProviderInvokerTube<T> tube = factory.doCreate(implType, invoker, argsBuilder, isAsync);
            if (tube != null) {
                if (logger.isLoggable(Level.FINE)) {
                    ProviderInvokerTubeFactory.logger.log(Level.FINE, "{0} successfully created {1}", new Object[]{factory.getClass(), tube});
                }
                return tube;
            }
        }
        return DEFAULT.createDefault(implType, invoker, argsBuilder, isAsync);
    }

    protected ProviderInvokerTube<T> createDefault(@NotNull final Class<T> implType,
                                                   @NotNull final Invoker invoker,
                                                   @NotNull final ProviderArgumentsBuilder<?> argsBuilder,
                                                            final boolean isAsync)
    {
        return
            isAsync
            ? new AsyncProviderInvokerTube(invoker, argsBuilder)
            : new SyncProviderInvokerTube (invoker, argsBuilder);
    }

    private static final Logger logger = Logger.getLogger(ProviderInvokerTubeFactory.class.getName());
}
