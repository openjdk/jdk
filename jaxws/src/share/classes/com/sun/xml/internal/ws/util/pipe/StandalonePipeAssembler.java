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

/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.sun.xml.internal.ws.util.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.pipe.ClientPipeAssemblerContext;
import com.sun.xml.internal.ws.api.pipe.Pipe;
import com.sun.xml.internal.ws.api.pipe.PipelineAssembler;
import com.sun.xml.internal.ws.api.pipe.ServerPipeAssemblerContext;

/**
 * Default Pipeline assembler for JAX-WS client and server side runtimes. It
 * assembles various pipes into a pipeline that a message needs to be passed
 * through.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public class StandalonePipeAssembler implements PipelineAssembler {

    @NotNull
    public Pipe createClient(ClientPipeAssemblerContext context) {
        Pipe head = context.createTransportPipe();
        head = context.createSecurityPipe(head);

        if (dump) {
            // for debugging inject a dump pipe. this is left in the production code,
            // as it would be very handy for a trouble-shooting at the production site.
            head = context.createDumpPipe("client", System.out, head);
        }
        head = context.createWsaPipe(head);
        head = context.createClientMUPipe(head);
        return context.createHandlerPipe(head);
    }

    /**
     * On Server-side, HandlerChains cannot be changed after it is deployed.
     * During assembling the Pipelines, we can decide if we really need a
     * SOAPHandlerPipe and LogicalHandlerPipe for a particular Endpoint.
     */
    public Pipe createServer(ServerPipeAssemblerContext context) {
        Pipe head = context.getTerminalPipe();
        head = context.createHandlerPipe(head);
        head = context.createMonitoringPipe(head);
        head = context.createServerMUPipe(head);
        head = context.createWsaPipe(head);
        head = context.createSecurityPipe(head);
        return head;
    }

    /**
     * Are we going to dump the message to System.out?
     */
    private static final boolean dump;

    static {
        boolean b = false;
        try {
            b = Boolean.getBoolean(StandalonePipeAssembler.class.getName()+".dump");
        } catch (Throwable t) {
            // treat it as false
        }
        dump = b;
    }
}
