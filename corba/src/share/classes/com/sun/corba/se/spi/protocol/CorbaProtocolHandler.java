/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.spi.protocol;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.portable.UnknownException;

import com.sun.corba.se.pept.protocol.ProtocolHandler;

import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;

import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.impl.protocol.giopmsgheaders.LocateRequestMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.RequestMessage;

/**
 * @author Harold Carr
 */
public interface CorbaProtocolHandler
    extends ProtocolHandler
{
    public void handleRequest(RequestMessage header,
                              CorbaMessageMediator messageMediator);

    public void handleRequest(LocateRequestMessage header,
                              CorbaMessageMediator messageMediator);

    public CorbaMessageMediator createResponse(
        CorbaMessageMediator messageMediator,
        ServiceContexts svc);
    public CorbaMessageMediator createUserExceptionResponse(
        CorbaMessageMediator messageMediator,
        ServiceContexts svc);
    public CorbaMessageMediator createUnknownExceptionResponse(
        CorbaMessageMediator messageMediator,
        UnknownException ex);
    public CorbaMessageMediator createSystemExceptionResponse(
        CorbaMessageMediator messageMediator,
        SystemException ex,
        ServiceContexts svc);
    public CorbaMessageMediator createLocationForward(
        CorbaMessageMediator messageMediator,
        IOR ior,
        ServiceContexts svc);

    public void handleThrowableDuringServerDispatch(
        CorbaMessageMediator request,
        Throwable exception,
        CompletionStatus completionStatus);

}

// End of file.
