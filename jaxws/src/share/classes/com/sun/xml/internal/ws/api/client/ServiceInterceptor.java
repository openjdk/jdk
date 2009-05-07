/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.xml.internal.ws.api.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.developer.WSBindingProvider;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.WebServiceFeature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Interception point for inner working of {@link WSService}.
 *
 * <p>
 * System-level code could hook an implementation of this to
 * {@link WSService} to augument its behavior.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.1 EA3
 * @see ServiceInterceptorFactory
 */
public abstract class ServiceInterceptor {
    /**
     * Called before {@link WSBinding} is created, to allow interceptors
     * to add {@link WebServiceFeature}s to the created {@link WSBinding}.
     *
     * @param port
     *      Information about the port for which dispatch/proxy will be created.
     * @param serviceEndpointInterface
     *      Null if the created binding is for {@link Dispatch}. Otheriwse
     *      it represents the port interface of the proxy to be created.
     * @param defaultFeatures
     *      The list of features that are currently scheduled to be set for
     *      the newly created {@link WSBinding}.
     *
     * @return
     *      A set of features to be added to the newly created {@link WSBinding}.
     *      Can be empty but never null.
     *      <tt>defaultFeatures</tt> will take precedence over what this method
     *      would return (because it includes user-specified ones which will
     *      take the at-most priority), but features you return from this method
     *      will take precedence over {@link BindingID}'s
     *      {@link BindingID#createBuiltinFeatureList() implicit features}.
     */
    public List<WebServiceFeature> preCreateBinding(@NotNull WSPortInfo port, @Nullable Class<?> serviceEndpointInterface, @NotNull WSFeatureList defaultFeatures) {
        return Collections.emptyList();
    }


    /**
     * A callback to notify the event of creation of proxy object for SEI endpoint. The
     * callback could set some properties on the {@link BindingProvider}.
     *
     * @param bp created proxy instance
     * @param serviceEndpointInterface SEI of the endpoint
     */
    public void postCreateProxy(@NotNull WSBindingProvider bp,@NotNull Class<?> serviceEndpointInterface) {
    }


    /**
     * A callback to notify that a {@link Dispatch} object is created. The callback
     * could set some properties on the {@link BindingProvider}.
     *
     * @param bp BindingProvider of dispatch object
     */
    public void postCreateDispatch(@NotNull WSBindingProvider bp) {
    }

    /**
     * Aggregates multiple interceptors into one facade.
     */
    public static ServiceInterceptor aggregate(final ServiceInterceptor... interceptors) {
        if(interceptors.length==1)
            return interceptors[0];
        return new ServiceInterceptor() {
            public List<WebServiceFeature> preCreateBinding(@NotNull WSPortInfo port, @Nullable Class<?> portInterface, @NotNull WSFeatureList defaultFeatures) {
                List<WebServiceFeature> r = new ArrayList<WebServiceFeature>();
                for (ServiceInterceptor si : interceptors)
                    r.addAll(si.preCreateBinding(port,portInterface,defaultFeatures));
                return r;
            }

            public void postCreateProxy(@NotNull WSBindingProvider bp, @NotNull Class<?> serviceEndpointInterface) {
                for (ServiceInterceptor si : interceptors)
                    si.postCreateProxy(bp,serviceEndpointInterface);
            }

            public void postCreateDispatch(@NotNull WSBindingProvider bp) {
                for (ServiceInterceptor si : interceptors)
                    si.postCreateDispatch(bp);
            }
        };
    }
}
