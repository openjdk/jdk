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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.xml.internal.ws.assembler;

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
import com.sun.xml.internal.ws.assembler.dev.ClientTubelineAssemblyContext;
import com.sun.xml.internal.ws.policy.PolicyMap;

/**
 * The context is a wrapper around the existing JAX-WS {@link ClientTubeAssemblerContext} with additional features
 *
 * @author Marek Potociar (marek.potociar at sun.com)
 */
class DefaultClientTubelineAssemblyContext extends TubelineAssemblyContextImpl implements ClientTubelineAssemblyContext {

    private final @NotNull ClientTubeAssemblerContext wrappedContext;
    private final PolicyMap policyMap;
    private final WSPortInfo portInfo; // TODO: is this really needed?
    private final WSDLPort wsdlPort;
    // TODO: replace the PipeConfiguration

    public DefaultClientTubelineAssemblyContext(@NotNull ClientTubeAssemblerContext context) {
        this.wrappedContext = context;
        this.wsdlPort = context.getWsdlModel();
        this.portInfo = context.getPortInfo();
        this.policyMap = context.getPortInfo().getPolicyMap();
    }

    public PolicyMap getPolicyMap() {
        return policyMap;
    }

    public boolean isPolicyAvailable() {
        return policyMap != null && !policyMap.isEmpty();
    }

    /**
     * The created pipeline will be used to serve this port.
     * Null if the service isn't associated with any port definition in WSDL,
     * and otherwise non-null.
     *
     * Replaces {@link com.sun.xml.internal.ws.api.pipe.ClientTubeAssemblerContext#getWsdlModel()}
     */
    public WSDLPort getWsdlPort() {
        return wsdlPort;
    }

    public WSPortInfo getPortInfo() {
        return portInfo;
    }

    /**
     * The endpoint address. Always non-null. This parameter is taken separately
     * from {@link com.sun.xml.internal.ws.api.model.wsdl.WSDLPort} (even though there's {@link com.sun.xml.internal.ws.api.model.wsdl.WSDLPort#getAddress()})
     * because sometimes WSDL is not available.
     */
    public @NotNull EndpointAddress getAddress() {
        return wrappedContext.getAddress();
    }

    /**
     * The pipeline is created for this {@link com.sun.xml.internal.ws.api.WSService}.
     * Always non-null. (To be precise, the newly created pipeline
     * is owned by a proxy or a dispatch created from this {@link com.sun.xml.internal.ws.api.WSService}.)
     */
    public @NotNull WSService getService() {
        return wrappedContext.getService();
    }

    /**
     * The binding of the new pipeline to be created.
     */
    public @NotNull WSBinding getBinding() {
        return wrappedContext.getBinding();
    }

    /**
     * The created pipeline will use seiModel to get java concepts for the endpoint
     *
     * @return Null if the service doesn't have SEI model e.g. Dispatch,
     *         and otherwise non-null.
     */
    public @Nullable SEIModel getSEIModel() {
        return wrappedContext.getSEIModel();
    }

    /**
     * Returns the Container in which the client is running
     *
     * @return Container in which client is running
     */
    public Container getContainer() {
        return wrappedContext.getContainer();
    }

    /**
     * Gets the {@link Codec} that is set by {@link #setCodec} or the default codec
     * based on the binding.
     *
     * @return codec to be used for web service requests
     */
    public @NotNull Codec getCodec() {
        return wrappedContext.getCodec();
    }

    /**
     * Interception point to change {@link Codec} during {@link com.sun.xml.internal.ws.api.pipe.Tube}line assembly. The
     * new codec will be used by jax-ws client runtime for encoding/decoding web service
     * request/response messages. The new codec should be used by the transport tubes.
     *
     * <p>
     * the codec should correctly implement {@link Codec#copy} since it is used while
     * serving requests concurrently.
     *
     * @param codec codec to be used for web service requests
     */
    public void setCodec(@NotNull Codec codec) {
        wrappedContext.setCodec(codec);
    }

    public ClientTubeAssemblerContext getWrappedContext() {
        return wrappedContext;
    }
}
