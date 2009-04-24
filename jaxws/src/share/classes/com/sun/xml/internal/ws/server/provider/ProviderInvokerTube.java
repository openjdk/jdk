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
package com.sun.xml.internal.ws.server.provider;

import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.server.AsyncProvider;
import com.sun.xml.internal.ws.api.server.Invoker;
import com.sun.xml.internal.ws.server.InvokerTube;

import javax.xml.ws.Provider;

/**
 * This {@link Tube} is used to invoke the {@link Provider} and {@link AsyncProvider} endpoints.
 *
 * @author Jitendra Kotamraju
 */
public abstract class ProviderInvokerTube<T> extends InvokerTube<Provider<T>> {

    protected ProviderArgumentsBuilder<T> argsBuilder;

    /*package*/ ProviderInvokerTube(Invoker invoker, ProviderArgumentsBuilder<T> argsBuilder) {
        super(invoker);
        this.argsBuilder = argsBuilder;
    }

    public static <T> ProviderInvokerTube<T>
    create(Class<T> implType, WSBinding binding, Invoker invoker) {

        ProviderEndpointModel<T> model = new ProviderEndpointModel<T>(implType, binding);
        ProviderArgumentsBuilder<?> argsBuilder = ProviderArgumentsBuilder.create(model, binding);
        return model.isAsync ? new AsyncProviderInvokerTube(invoker, argsBuilder)
            : new SyncProviderInvokerTube(invoker, argsBuilder);
    }
}
