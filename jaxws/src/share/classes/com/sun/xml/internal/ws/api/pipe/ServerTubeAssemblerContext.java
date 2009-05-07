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
import com.sun.xml.internal.ws.addressing.WsaServerTube;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.helper.PipeAdapter;
import com.sun.xml.internal.ws.api.server.ServerPipelineHook;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.handler.HandlerTube;
import com.sun.xml.internal.ws.handler.ServerLogicalHandlerTube;
import com.sun.xml.internal.ws.handler.ServerSOAPHandlerTube;
import com.sun.xml.internal.ws.protocol.soap.ServerMUTube;
import com.sun.xml.internal.ws.util.pipe.DumpTube;

import javax.xml.ws.soap.SOAPBinding;
import java.io.PrintStream;

/**
 * Factory for well-known server {@link Tube} implementations
 * that the {@link TubelineAssembler} needs to use
 * to satisfy JAX-WS requirements.
 *
 * @author Jitendra Kotamraju
 */
public class ServerTubeAssemblerContext {

    private final SEIModel seiModel;
    private final WSDLPort wsdlModel;
    private final WSEndpoint endpoint;
    private final BindingImpl binding;
    private final Tube terminal;
    private final boolean isSynchronous;
    private @NotNull Codec codec;

    public ServerTubeAssemblerContext(@Nullable SEIModel seiModel,
                                      @Nullable WSDLPort wsdlModel, @NotNull WSEndpoint endpoint,
                                      @NotNull Tube terminal, boolean isSynchronous) {
        this.seiModel = seiModel;
        this.wsdlModel = wsdlModel;
        this.endpoint = endpoint;
        this.terminal = terminal;
        // WSBinding is actually BindingImpl
        this.binding = (BindingImpl)endpoint.getBinding();
        this.isSynchronous = isSynchronous;
        this.codec = this.binding.createCodec();
    }

    /**
     * The created pipeline will use seiModel to get java concepts for the endpoint
     *
     * @return Null if the service doesn't have SEI model e.g. Provider endpoints,
     *         and otherwise non-null.
     */
    public @Nullable SEIModel getSEIModel() {
        return seiModel;
    }

    /**
     * The created pipeline will be used to serve this port.
     *
     * @return Null if the service isn't associated with any port definition in WSDL,
     *         and otherwise non-null.
     */
    public @Nullable WSDLPort getWsdlModel() {
        return wsdlModel;
    }

    /**
     *
     * The created pipeline is used to serve this {@link com.sun.xml.internal.ws.api.server.WSEndpoint}.
     * Specifically, its {@link com.sun.xml.internal.ws.api.WSBinding} should be of interest to  many
     * {@link com.sun.xml.internal.ws.api.pipe.Pipe}s.
     *  @return Always non-null.
     */
    public @NotNull WSEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * The last {@link com.sun.xml.internal.ws.api.pipe.Pipe} in the pipeline. The assembler is expected to put
     * additional {@link com.sun.xml.internal.ws.api.pipe.Pipe}s in front of it.
     *
     * <p>
     * (Just to give you the idea how this is used, normally the terminal pipe
     * is the one that invokes the user application or {@link javax.xml.ws.Provider}.)
     *
     * @return always non-null terminal pipe
     */
     public @NotNull Tube getTerminalTube() {
         return terminal;
    }

    /**
     * If this server pipeline is known to be used for serving synchronous transport,
     * then this method returns true. This can be potentially use as an optimization
     * hint, since often synchronous versions are cheaper to execute than asycnhronous
     * versions.
     */
    public boolean isSynchronous() {
        return isSynchronous;
    }

    /**
     * Creates a {@link Tube} that performs SOAP mustUnderstand processing.
     * This pipe should be before HandlerPipes.
     */
    public @NotNull Tube createServerMUTube(@NotNull Tube next) {
        if (binding instanceof SOAPBinding)
            return new ServerMUTube(binding,next);
        else
            return next;
    }

    /**
     * Creates a {@link Tube} that invokes protocol and logical handlers.
     */
    public @NotNull Tube createHandlerTube(@NotNull Tube next) {
        if (!binding.getHandlerChain().isEmpty()) {
            HandlerTube cousin = new ServerLogicalHandlerTube(binding, wsdlModel, next);
            next = cousin;
            if (binding instanceof SOAPBinding) {
                return new ServerSOAPHandlerTube(binding, next, cousin);
            }
        }
        return next;
    }

    /**
     * Creates a {@link Tube} that does the monitoring of the invocation for a
     * container
     */
    public @NotNull Tube createMonitoringTube(@NotNull Tube next) {
        ServerPipelineHook hook = endpoint.getContainer().getSPI(ServerPipelineHook.class);
        if (hook != null) {
            ServerPipeAssemblerContext ctxt = new ServerPipeAssemblerContext(seiModel, wsdlModel, endpoint, terminal, isSynchronous);
            return PipeAdapter.adapt(hook.createMonitoringPipe(ctxt, PipeAdapter.adapt(next)));
        }
        return next;
    }

    /**
     * Creates a {@link Tube} that adds container specific security
     */
    public @NotNull Tube createSecurityTube(@NotNull Tube next) {
        ServerPipelineHook hook = endpoint.getContainer().getSPI(ServerPipelineHook.class);
        if (hook != null) {
            ServerPipeAssemblerContext ctxt = new ServerPipeAssemblerContext(seiModel, wsdlModel, endpoint, terminal, isSynchronous);
            return PipeAdapter.adapt(hook.createSecurityPipe(ctxt, PipeAdapter.adapt(next)));
        }
        return next;
    }

    /**
     * creates a {@link Tube} that dumps messages that pass through.
     */
    public Tube createDumpTube(String name, PrintStream out, Tube next) {
        return new DumpTube(name, out, next);
    }

    /**
     * Creates WS-Addressing pipe
     */
    public Tube createWsaTube(Tube next) {
        if (binding instanceof SOAPBinding && AddressingVersion.isEnabled(binding) && wsdlModel!=null)
            return new WsaServerTube(wsdlModel, binding, next);
        else
            return next;
    }

    /**
     * Gets the {@link Codec} that is set by {@link #setCodec} or the default codec
     * based on the binding. The codec is a full codec that is responsible for
     * encoding/decoding entire protocol message(for e.g: it is responsible to
     * encode/decode entire MIME messages in SOAP binding)
     *
     * @return codec to be used for web service requests
     * @see {@link Codecs}
     */
    public @NotNull Codec getCodec() {
        return codec;
    }

    /**
     * Interception point to change {@link Codec} during {@link Tube}line assembly. The
     * new codec will be used by jax-ws server runtime for encoding/decoding web service
     * request/response messages. {@link WSEndpoint#createCodec()} will return a copy
     * of this new codec and will be used in the server runtime.
     *
     * <p>
     * The codec is a full codec that is responsible for
     * encoding/decoding entire protocol message(for e.g: it is responsible to
     * encode/decode entire MIME messages in SOAP binding)
     *
     * <p>
     * the codec should correctly implement {@link Codec#copy} since it is used while
     * serving requests concurrently.
     *
     * @param codec codec to be used for web service requests
     * @see {@link Codecs}
     */
    public void setCodec(@NotNull Codec codec) {
        this.codec = codec;
    }

}
