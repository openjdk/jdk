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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.pipe.helper.PipeAdapter;
import com.sun.xml.internal.ws.transport.http.client.HttpTransportPipe;
import com.sun.xml.internal.ws.util.ServiceFinder;
import com.sun.xml.internal.ws.util.pipe.StandaloneTubeAssembler;

import javax.xml.ws.WebServiceException;
import java.util.logging.Logger;

/**
 * Factory for transport tubes that enables transport pluggability.
 *
 * <p>
 * At runtime, on the client side, JAX-WS (more specifically the default {@link TubelineAssembler}
 * of JAX-WS client runtime) relies on this factory to create a suitable transport {@link Tube}
 * that can handle the given {@link EndpointAddress endpoint address}.
 *
 * <p>
 * JAX-WS extensions that provide additional transport support can
 * extend this class and implement the {@link #doCreate} method.
 * They are expected to check the scheme of the endpoint address
 * (and possibly some other settings from bindings), and create
 * their transport tube implementations accordingly.
 * For example,
 *
 * <pre>
 * class MyTransportTubeFactoryImpl {
 *   Tube doCreate(...) {
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
 * {@link TransportTubeFactory} look-up follows the standard service
 * discovery mechanism, so you need
 * {@code META-INF/services/com.sun.xml.internal.ws.api.pipe.TransportTubeFactory}.
 *
 * @author Jitendra Kotamraju
 * @see StandaloneTubeAssembler
 */
public abstract class TransportTubeFactory {
    /**
     * Creates a transport {@link Tube} for the given port, if this factory can do so,
     * or return null.
     *
     * @param context
     *      Object that captures various contextual information
     *      that can be used to determine the tubeline to be assembled.
     *
     * @return
     *      null to indicate that this factory isn't capable of creating a transport
     *      for this port (which causes the caller to search for other {@link TransportTubeFactory}s
     *      that can. Or non-null.
     *
     * @throws WebServiceException
     *      if this factory is capable of creating a transport tube but some fatal
     *      error prevented it from doing so. This exception will be propagated
     *      back to the user application, and no further {@link TransportTubeFactory}s
     *      are consulted.
     */
    public abstract Tube doCreate(@NotNull ClientTubeAssemblerContext context);

    /**
     * Locates {@link TransportTubeFactory}s and create a suitable transport {@link Tube}.
     *
     * @param classLoader
     *      used to locate {@code META-INF/servces} files.
     * @return
     *      Always non-null, since we fall back to our default {@link Tube}.
     */
    public static Tube create(@Nullable ClassLoader classLoader, @NotNull ClientTubeAssemblerContext context) {
        for (TransportTubeFactory factory : ServiceFinder.find(TransportTubeFactory.class,classLoader)) {
            Tube tube = factory.doCreate(context);
            if(tube !=null) {
                TransportTubeFactory.logger.fine(factory.getClass()+" successfully created "+tube);
                return tube;
            }
        }

        // See if there is a {@link TransportPipeFactory} out there and use it for compatibility.
        ClientPipeAssemblerContext ctxt = new ClientPipeAssemblerContext(
                context.getAddress(), context.getWsdlModel(), context.getService(),
                context.getBinding(), context.getContainer());
        for (TransportPipeFactory factory : ServiceFinder.find(TransportPipeFactory.class,classLoader)) {
            Pipe pipe = factory.doCreate(ctxt);
            if (pipe!=null) {
                logger.fine(factory.getClass()+" successfully created "+pipe);
                return PipeAdapter.adapt(pipe);
            }
        }

        // default built-in transports
        String scheme = context.getAddress().getURI().getScheme();
        if (scheme != null) {
            if(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                return new HttpTransportPipe(context.getCodec());
        }

        throw new WebServiceException("Unsupported endpoint address: "+context.getAddress());    // TODO: i18n
    }

    private static final Logger logger = Logger.getLogger(TransportTubeFactory.class.getName());
}
