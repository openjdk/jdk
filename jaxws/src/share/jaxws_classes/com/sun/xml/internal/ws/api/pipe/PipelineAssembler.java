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

import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import javax.xml.ws.Dispatch;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

/**
 * Creates a pipeline.
 *
 * <p>
 * This pluggability layer enables the upper layer to
 * control exactly how the pipeline is composed.
 *
 * <p>
 * JAX-WS is going to have its own default implementation
 * when used all by itself, but it can be substituted by
 * other implementations.
 *
 * <p>
 * See {@link PipelineAssemblerFactory} for how {@link PipelineAssembler}s
 * are located.
 *
 * <p>
 * TODO: the JAX-WS team felt that no {@link Pipe} should be relying
 * on the {@link SEIModel}, so it is no longer given to the assembler.
 * Talk to us if you need it.
 *
 * @see ClientPipeAssemblerContext
 *
 * @author Kohsuke Kawaguchi
 */
public interface PipelineAssembler {
    /**
     * Creates a new pipeline for clients.
     *
     * <p>
     * When a JAX-WS client creates a proxy or a {@link Dispatch} from
     * a {@link Service}, JAX-WS runtime internally uses this method
     * to create a new pipeline as a part of the initilization.
     *
     * @param context
     *      Object that captures various contextual information
     *      that can be used to determine the pipeline to be assembled.
     *
     * @return
     *      non-null freshly created pipeline.
     *
     * @throws WebServiceException
     *      if there's any configuration error that prevents the
     *      pipeline from being constructed. This exception will be
     *      propagated into the application, so it must have
     *      a descriptive error.
     */
    @NotNull Pipe createClient(@NotNull ClientPipeAssemblerContext context);

    /**
     * Creates a new pipeline for servers.
     *
     * <p>
     * When a JAX-WS server deploys a new endpoint, it internally
     * uses this method to create a new pipeline as a part of the
     * initialization.
     *
     * <p>
     * Note that this method is called only once to set up a
     * 'master pipeline', and it gets {@link Pipe#copy(PipeCloner) copied}
     * from it.
     *
     * @param context
     *      Object that captures various contextual information
     *      that can be used to determine the pipeline to be assembled.
     *
     * @return
     *      non-null freshly created pipeline.
     *
     * @throws WebServiceException
     *      if there's any configuration error that prevents the
     *      pipeline from being constructed. This exception will be
     *      propagated into the container, so it must have
     *      a descriptive error.
     *
     */
    @NotNull Pipe createServer(@NotNull ServerPipeAssemblerContext context);
}
