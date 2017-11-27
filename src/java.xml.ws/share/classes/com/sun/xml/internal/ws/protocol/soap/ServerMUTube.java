/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.protocol.soap;

import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.*;
import com.sun.xml.internal.ws.client.HandlerConfiguration;
import javax.xml.namespace.QName;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Rama Pulavarthi
 */

public class ServerMUTube extends MUTube {

    private ServerTubeAssemblerContext tubeContext;
    private final Set<String> roles;
    private final Set<QName> handlerKnownHeaders;
    private final Lock lock = new ReentrantLock();

    public ServerMUTube(ServerTubeAssemblerContext tubeContext, Tube next) {
        super(tubeContext.getEndpoint().getBinding(), next);

        this.tubeContext = tubeContext;

        //On Server, HandlerConfiguration does n't change after publish, so store locally
        HandlerConfiguration handlerConfig = binding.getHandlerConfig();
        roles = handlerConfig.getRoles();
        handlerKnownHeaders = binding.getKnownHeaders();
    }

    protected ServerMUTube(ServerMUTube that, TubeCloner cloner) {
        super(that,cloner);
        tubeContext = that.tubeContext;
        roles = that.roles;
        handlerKnownHeaders = that.handlerKnownHeaders;
    }

    /**
     * Do MU Header Processing on incoming message (request)
     * @return
     *      if all the headers in the packet are understood, returns action such that
     *      next pipe will be inovked.
     *      if all the headers in the packet are not understood, returns action such that
     *      SOAPFault Message is sent to previous pipes.
     */
    @Override
    public NextAction processRequest(Packet request) {
        Set<QName> misUnderstoodHeaders=null;
        lock.lock();
        try{
            misUnderstoodHeaders = getMisUnderstoodHeaders(request.getMessage().getHeaders(),roles, handlerKnownHeaders);
        } finally {
            lock.unlock();
        }
        if((misUnderstoodHeaders == null)  || misUnderstoodHeaders.isEmpty()) {
            return doInvoke(super.next, request);
        }
        return doReturnWith(request.createServerResponse(createMUSOAPFaultMessage(misUnderstoodHeaders),
                tubeContext.getWsdlModel(), tubeContext.getSEIModel(), tubeContext.getEndpoint().getBinding()));
    }

    public ServerMUTube copy(TubeCloner cloner) {
        return new ServerMUTube(this,cloner);
    }

}
