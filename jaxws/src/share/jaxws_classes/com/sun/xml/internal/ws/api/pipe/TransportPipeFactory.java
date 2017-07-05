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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.pipe.helper.PipeAdapter;
import com.sun.xml.internal.ws.util.pipe.StandalonePipeAssembler;

import javax.xml.ws.WebServiceException;

/**
 * Factory for transport pipes that enables transport pluggability.
 *
 * <p>
 * At runtime, on the client side, JAX-WS (more specifically the default {@link PipelineAssembler}
 * of JAX-WS client runtime) relies on this factory to create a suitable transport {@link Pipe}
 * that can handle the given {@link EndpointAddress endpoint address}.
 *
 * <p>
 * JAX-WS extensions that provide additional transport support can
 * extend this class and implement the {@link #doCreate} method.
 * They are expected to check the scheme of the endpoint address
 * (and possibly some other settings from bindings), and create
 * their transport pipe implementations accordingly.
 * For example,
 *
 * <pre>
 * class MyTransportPipeFactoryImpl {
 *   Pipe doCreate(...) {
 *     String scheme = address.getURI().getScheme();
 *     if(scheme.equals("foo"))
 *       return new MyTransport(...);
 *     else
 *       return null;
 *   }
 * }
 * </pre>
 *
 * <p>
 * {@link TransportPipeFactory} look-up follows the standard service
 * discovery mechanism, so you need
 * {@code META-INF/services/com.sun.xml.internal.ws.api.pipe.TransportPipeFactory}.
 *
 *
 *
 * <h2>TODO</h2>
 * <p>
 * One of the JAX-WS operation mode is supposedly where it doesn't have no WSDL whatsoever.
 * How do we identify the endpoint in such case?
 *
 * @author Kohsuke Kawaguchi
 * @see StandalonePipeAssembler
 */
public abstract class TransportPipeFactory {
    /**
     * Creates a transport {@link Pipe} for the given port, if this factory can do so,
     * or return null.
     *
     * @param context
     *      Object that captures various contextual information
     *      that can be used to determine the pipeline to be assembled.
     *
     * @return
     *      null to indicate that this factory isn't capable of creating a transport
     *      for this port (which causes the caller to search for other {@link TransportPipeFactory}s
     *      that can. Or non-null.
     *
     * @throws WebServiceException
     *      if this factory is capable of creating a transport pipe but some fatal
     *      error prevented it from doing so. This exception will be propagated
     *      back to the user application, and no further {@link TransportPipeFactory}s
     *      are consulted.
     */
    public abstract Pipe doCreate(@NotNull ClientPipeAssemblerContext context);

    /**
     * Locates {@link PipelineAssemblerFactory}s and create
     * a suitable {@link PipelineAssembler}.
     *
     * @param classLoader
     *      used to locate {@code META-INF/servces} files.
     * @return
     *      Always non-null, since we fall back to our default {@link PipelineAssembler}.
     *
     * @deprecated
     *      Use {@link TransportTubeFactory#create(ClassLoader, ClientTubeAssemblerContext)}
     */
    public static Pipe create(@Nullable ClassLoader classLoader, @NotNull ClientPipeAssemblerContext context) {
        return PipeAdapter.adapt(TransportTubeFactory.create(classLoader,context));
    }
}
