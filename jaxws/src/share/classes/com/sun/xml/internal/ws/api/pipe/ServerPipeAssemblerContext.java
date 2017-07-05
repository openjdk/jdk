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
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.helper.PipeAdapter;
import com.sun.xml.internal.ws.api.server.WSEndpoint;

import java.io.PrintStream;

/**
 * Factory for well-known server {@link Pipe} implementations
 * that the {@link PipelineAssembler} needs to use
 * to satisfy JAX-WS requirements.
 *
 * @author Jitendra Kotamraju
 * @deprecated Use {@link ServerTubeAssemblerContext}.
 */
public final class ServerPipeAssemblerContext extends ServerTubeAssemblerContext {

    public ServerPipeAssemblerContext(@Nullable SEIModel seiModel, @Nullable WSDLPort wsdlModel, @NotNull WSEndpoint endpoint, @NotNull Tube terminal, boolean isSynchronous) {
        super(seiModel, wsdlModel, endpoint, terminal, isSynchronous);
    }

    /**
     * Creates a {@link Pipe} that performs SOAP mustUnderstand processing.
     * This pipe should be before HandlerPipes.
     */
    public @NotNull Pipe createServerMUPipe(@NotNull Pipe next) {
        return PipeAdapter.adapt(super.createServerMUTube(PipeAdapter.adapt(next)));
    }

    /**
     * creates a {@link Pipe} that dumps messages that pass through.
     */
    public Pipe createDumpPipe(String name, PrintStream out, Pipe next) {
        return PipeAdapter.adapt(super.createDumpTube(name, out, PipeAdapter.adapt(next)));
    }

    /**
     * Creates a {@link Pipe} that does the monitoring of the invocation for a
     * container
     */
    public @NotNull Pipe createMonitoringPipe(@NotNull Pipe next) {
        return PipeAdapter.adapt(super.createMonitoringTube(PipeAdapter.adapt(next)));
    }

    /**
     * Creates a {@link Pipe} that adds container specific security
     */
    public @NotNull Pipe createSecurityPipe(@NotNull Pipe next) {
        return PipeAdapter.adapt(super.createSecurityTube(PipeAdapter.adapt(next)));
    }

    /**
     * creates a {@link Pipe} that validates messages against schema
     */
    public @NotNull Pipe createValidationPipe(@NotNull Pipe next) {
       return PipeAdapter.adapt(super.createValidationTube(PipeAdapter.adapt(next)));
    }

    /**
     * Creates a {@link Pipe} that invokes protocol and logical handlers.
     */
    public @NotNull Pipe createHandlerPipe(@NotNull Pipe next) {
        return PipeAdapter.adapt(super.createHandlerTube(PipeAdapter.adapt(next)));
    }

    /**
     * The last {@link Pipe} in the pipeline. The assembler is expected to put
     * additional {@link Pipe}s in front of it.
     *
     * <p>
     * (Just to give you the idea how this is used, normally the terminal pipe
     * is the one that invokes the user application or {@link javax.xml.ws.Provider}.)
     *
     * @return always non-null terminal pipe
     */
     public @NotNull Pipe getTerminalPipe() {
         return PipeAdapter.adapt(super.getTerminalTube());
    }

     /**
     * Creates WS-Addressing pipe
     */
    public Pipe createWsaPipe(Pipe next) {
        return PipeAdapter.adapt(super.createWsaTube(PipeAdapter.adapt(next)));
    }
}
