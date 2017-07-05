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

package com.sun.xml.internal.ws.assembler.dev;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.ClientTubeAssemblerContext;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.policy.PolicyMap;

/**
 * @author Marek Potociar (marek.potociar at sun.com)
 */
public interface ClientTubelineAssemblyContext extends TubelineAssemblyContext {

    /**
     * The endpoint address. Always non-null. This parameter is taken separately
     * from {@link com.sun.xml.internal.ws.api.model.wsdl.WSDLPort} (even though there's {@link com.sun.xml.internal.ws.api.model.wsdl.WSDLPort#getAddress()})
     * because sometimes WSDL is not available.
     */
    @NotNull
    EndpointAddress getAddress();

    /**
     * The binding of the new pipeline to be created.
     */
    @NotNull
    WSBinding getBinding();

    /**
     * Gets the {@link Codec} that is set by {@link #setCodec} or the default codec
     * based on the binding.
     *
     * @return codec to be used for web service requests
     */
    @NotNull
    Codec getCodec();

    /**
     * Returns the Container in which the client is running
     *
     * @return Container in which client is running
     */
    Container getContainer();

    PolicyMap getPolicyMap();

    WSPortInfo getPortInfo();

    /**
     * The created pipeline will use seiModel to get java concepts for the endpoint
     *
     * @return Null if the service doesn't have SEI model e.g. Dispatch,
     *         and otherwise non-null.
     */
    @Nullable
    SEIModel getSEIModel();

    /**
     * The pipeline is created for this {@link com.sun.xml.internal.ws.api.WSService}.
     * Always non-null. (To be precise, the newly created pipeline
     * is owned by a proxy or a dispatch created from this {@link com.sun.xml.internal.ws.api.WSService}.)
     */
    @NotNull
    WSService getService();

    ClientTubeAssemblerContext getWrappedContext();

    /**
     * The created pipeline will be used to serve this port.
     * Null if the service isn't associated with any port definition in WSDL,
     * and otherwise non-null.
     * <p/>
     * Replaces {@link com.sun.xml.internal.ws.api.pipe.ClientTubeAssemblerContext#getWsdlModel()}
     */
    WSDLPort getWsdlPort();

    boolean isPolicyAvailable();

    /**
     * Interception point to change {@link Codec} during {@link com.sun.xml.internal.ws.api.pipe.Tube}line assembly. The
     * new codec will be used by jax-ws client runtime for encoding/decoding web service
     * request/response messages. The new codec should be used by the transport tubes.
     * <p/>
     * <p/>
     * the codec should correctly implement {@link Codec#copy} since it is used while
     * serving requests concurrently.
     *
     * @param codec codec to be used for web service requests
     */
    void setCodec(@NotNull
                  Codec codec);

}
