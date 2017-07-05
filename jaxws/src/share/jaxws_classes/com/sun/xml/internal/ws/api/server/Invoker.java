/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.xml.internal.ws.api.message.Packet;

import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Hides the detail of calling into application endpoint implementation.
 *
 * <p>
 * Typical host of the JAX-WS RI would want to use
 * {@link InstanceResolver#createDefault(Class)} and then
 * use <tt>{@link InstanceResolver#createInvoker()} to obtain
 * the default invoker implementation.
 *
 *
 * @author Jitendra Kotamraju
 * @author Kohsuke Kawaguchi
 */
public abstract class Invoker extends com.sun.xml.internal.ws.server.sei.Invoker {
    /**
     * Called by {@link WSEndpoint} when it's set up.
     *
     * <p>
     * This is an opportunity for {@link Invoker}
     * to do a endpoint-specific initialization process.
     *
     * @param wsc
     *      The {@link WebServiceContext} instance that can be injected
     *      to the user instances.
     * @param endpoint
     */
    public void start(@NotNull WSWebServiceContext wsc, @NotNull WSEndpoint endpoint) {
        // backward compatibility
        start(wsc);
    }

    /**
     * @deprecated
     *      Use {@link #start(WSWebServiceContext,WSEndpoint)}
     */
    public void start(@NotNull WebServiceContext wsc) {
        throw new IllegalStateException("deprecated version called");
    }

    /**
     * Called by {@link WSEndpoint}
     * when {@link WSEndpoint#dispose()} is called.
     *
     * This allows {@link InstanceResolver} to do final clean up.
     *
     * <p>
     * This method is guaranteed to be only called once by {@link WSEndpoint}.
     */
    public void dispose() {}

    /**
     * Invokes {@link Provider#invoke(Object)}
     */
    public <T> T invokeProvider( @NotNull Packet p, T arg ) throws IllegalAccessException, InvocationTargetException {
        // default slow implementation that delegates to the other invoke method.
        return (T)invoke(p,invokeMethod,arg);
    }

    /**
     * Invokes {@link AsyncProvider#invoke(Object, AsyncProviderCallback, WebServiceContext)}
     */
    public <T> void invokeAsyncProvider( @NotNull Packet p, T arg, AsyncProviderCallback cbak, WebServiceContext ctxt ) throws IllegalAccessException, InvocationTargetException {
        // default slow implementation that delegates to the other invoke method.
        invoke(p, asyncInvokeMethod, arg, cbak, ctxt);
    }

    private static final Method invokeMethod;

    static {
        try {
            invokeMethod = Provider.class.getMethod("invoke",Object.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static final Method asyncInvokeMethod;

    static {
        try {
            asyncInvokeMethod = AsyncProvider.class.getMethod("invoke",Object.class, AsyncProviderCallback.class, WebServiceContext.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
