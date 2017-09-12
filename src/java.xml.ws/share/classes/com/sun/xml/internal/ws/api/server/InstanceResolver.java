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
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.resources.WsservletMessages;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.server.SingletonResolver;

import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Determines the instance that serves
 * the given request packet.
 *
 * <p>
 * The JAX-WS spec always use a singleton instance
 * to serve all the requests, but this hook provides
 * a convenient way to route messages to a proper receiver.
 *
 * <p>
 * Externally, an instance of {@link InstanceResolver} is
 * associated with {@link WSEndpoint}.
 *
 * <h2>Possible Uses</h2>
 * <p>
 * One can use WS-Addressing message properties to
 * decide which instance to deliver a message. This
 * would be an important building block for a stateful
 * web services.
 *
 * <p>
 * One can associate an instance of a service
 * with a specific WS-RM session.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class InstanceResolver<T> {
    /**
     * Decides which instance of 'T' serves the given request message.
     *
     * <p>
     * This method is called concurrently by multiple threads.
     * It is also on a criticail path that affects the performance.
     * A good implementation should try to avoid any synchronization,
     * and should minimize the amount of work as much as possible.
     *
     * @param request
     *      Always non-null. Represents the request message to be served.
     *      The caller may not consume the {@link Message}.
     */
    public abstract @NotNull T resolve(@NotNull Packet request);

    /**
     * Called by the default {@link Invoker} after the method call is done.
     * This gives {@link InstanceResolver} a chance to do clean up.
     *
     * <p>
     * Alternatively, one could override {@link #createInvoker()} to
     * create a custom invoker to do this in more flexible way.
     *
     * <p>
     * The default implementation is a no-op.
     *
     * @param request
     *      The same request packet given to {@link #resolve(Packet)} method.
     * @param servant
     *      The object returned from the {@link #resolve(Packet)} method.
     * @since 2.1.2
     */
    public void postInvoke(@NotNull Packet request, @NotNull T servant) {
    }

    /**
     * Called by {@link WSEndpoint} when it's set up.
     *
     * <p>
     * This is an opportunity for {@link InstanceResolver}
     * to do a endpoint-specific initialization process.
     *
     * @param wsc
     *      The {@link WebServiceContext} instance to be injected
     *      to the user instances (assuming {@link InstanceResolver}
     */
    public void start(@NotNull WSWebServiceContext wsc, @NotNull WSEndpoint endpoint) {
        // backward compatibility
        start(wsc);
    }

    /**
     * @deprecated
     *      Use {@link #start(WSWebServiceContext,WSEndpoint)}.
     */
    public void start(@NotNull WebServiceContext wsc) {}

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
     * Creates a {@link InstanceResolver} implementation that always
     * returns the specified singleton instance.
     */
    public static <T> InstanceResolver<T> createSingleton(T singleton) {
        assert singleton!=null;
        InstanceResolver ir = createFromInstanceResolverAnnotation(singleton.getClass());
        if(ir==null)
            ir = new SingletonResolver<T>(singleton);
        return ir;
    }

    /**
     * @deprecated
     *      This is added here because a Glassfish integration happened
     *      with this signature. Please do not use this. Will be removed
     *      after the next GF integration.
     */
    public static <T> InstanceResolver<T> createDefault(@NotNull Class<T> clazz, boolean bool) {
        return createDefault(clazz);
    }

    /**
     * Creates a default {@link InstanceResolver} that serves the given class.
     */
    public static <T> InstanceResolver<T> createDefault(@NotNull Class<T> clazz) {
        InstanceResolver<T> ir = createFromInstanceResolverAnnotation(clazz);
        if(ir==null)
            ir = new SingletonResolver<T>(createNewInstance(clazz));
        return ir;
    }

    /**
     * Checks for {@link InstanceResolverAnnotation} and creates an instance resolver from it if any.
     * Otherwise null.
     */
    public static <T> InstanceResolver<T> createFromInstanceResolverAnnotation(@NotNull Class<T> clazz) {
        for( Annotation a : clazz.getAnnotations() ) {
            InstanceResolverAnnotation ira = a.annotationType().getAnnotation(InstanceResolverAnnotation.class);
            if(ira==null)   continue;
            Class<? extends InstanceResolver> ir = ira.value();
            try {
                return ir.getConstructor(Class.class).newInstance(clazz);
            } catch (InstantiationException e) {
                throw new WebServiceException(ServerMessages.FAILED_TO_INSTANTIATE_INSTANCE_RESOLVER(
                    ir.getName(),a.annotationType(),clazz.getName()));
            } catch (IllegalAccessException e) {
                throw new WebServiceException(ServerMessages.FAILED_TO_INSTANTIATE_INSTANCE_RESOLVER(
                    ir.getName(),a.annotationType(),clazz.getName()));
            } catch (InvocationTargetException e) {
                throw new WebServiceException(ServerMessages.FAILED_TO_INSTANTIATE_INSTANCE_RESOLVER(
                    ir.getName(),a.annotationType(),clazz.getName()));
            } catch (NoSuchMethodException e) {
                throw new WebServiceException(ServerMessages.FAILED_TO_INSTANTIATE_INSTANCE_RESOLVER(
                    ir.getName(),a.annotationType(),clazz.getName()));
            }
        }

        return null;
    }

    protected static <T> T createNewInstance(Class<T> cl) {
        try {
            return cl.newInstance();
        } catch (InstantiationException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new ServerRtException(
                WsservletMessages.ERROR_IMPLEMENTOR_FACTORY_NEW_INSTANCE_FAILED(cl));
        } catch (IllegalAccessException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new ServerRtException(
                WsservletMessages.ERROR_IMPLEMENTOR_FACTORY_NEW_INSTANCE_FAILED(cl));
        }
    }

    /**
     * Wraps this {@link InstanceResolver} into an {@link Invoker}.
     */
    public @NotNull Invoker createInvoker() {
        return new Invoker() {
            @Override
            public void start(@NotNull WSWebServiceContext wsc, @NotNull WSEndpoint endpoint) {
                InstanceResolver.this.start(wsc,endpoint);
            }

            @Override
            public void dispose() {
                InstanceResolver.this.dispose();
            }

            @Override
            public Object invoke(Packet p, Method m, Object... args) throws InvocationTargetException, IllegalAccessException {
                T t = resolve(p);
                try {
                    return MethodUtil.invoke(t, m, args );
                } finally {
                    postInvoke(p,t);
                }
            }

            @Override
            public <U> U invokeProvider(@NotNull Packet p, U arg) {
                T t = resolve(p);
                try {
                    return ((Provider<U>) t).invoke(arg);
                } finally {
                    postInvoke(p,t);
                }
            }

            public String toString() {
                return "Default Invoker over "+InstanceResolver.this.toString();
            }
        };
    }

    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server");
}
