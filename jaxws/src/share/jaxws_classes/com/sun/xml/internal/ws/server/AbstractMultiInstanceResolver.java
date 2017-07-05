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

package com.sun.xml.internal.ws.server;

import com.sun.xml.internal.ws.api.server.AbstractInstanceResolver;
import com.sun.xml.internal.ws.api.server.InstanceResolver;
import com.sun.xml.internal.ws.api.server.ResourceInjector;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.server.WSWebServiceContext;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Method;

/**
 * Partial implementation of {@link InstanceResolver} with code
 * to handle multiple instances per server.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractMultiInstanceResolver<T> extends AbstractInstanceResolver<T> {
    protected final Class<T> clazz;

    // fields for resource injection.
    private /*almost final*/ WSWebServiceContext webServiceContext;
    protected /*almost final*/ WSEndpoint owner;
    private final Method postConstructMethod;
    private final Method preDestroyMethod;
    private /*almost final*/ ResourceInjector resourceInjector;

    public AbstractMultiInstanceResolver(Class<T> clazz) {
        this.clazz = clazz;

        postConstructMethod = findAnnotatedMethod(clazz, PostConstruct.class);
        preDestroyMethod = findAnnotatedMethod(clazz, PreDestroy.class);
    }

    /**
     * Perform resource injection on the given instance.
     */
    protected final void prepare(T t) {
        // we can only start creating new instances after the start method is invoked.
        assert webServiceContext!=null;

        resourceInjector.inject(webServiceContext,t);
        invokeMethod(postConstructMethod,t);
    }

    /**
     * Creates a new instance via the default constructor.
     */
    protected final T create() {
        T t = createNewInstance(clazz);
        prepare(t);
        return t;
    }

    @Override
    public void start(WSWebServiceContext wsc, WSEndpoint endpoint) {
        resourceInjector = getResourceInjector(endpoint);
        this.webServiceContext = wsc;
        this.owner = endpoint;
    }

    protected final void dispose(T instance) {
        invokeMethod(preDestroyMethod,instance);
    }
}
