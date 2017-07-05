/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.fault;

import javax.xml.soap.SOAPFault;
import javax.xml.ws.soap.SOAPFaultException;

/**
 * Represents the SOAPFaultException that has occurred on the server side.
 *
 * <p>
 * When an exception occurs on the server, JAX-WS RI sends the SOAPFaultException
 * to the client. On the client side, instances of this class are used to represent
 * SOAPFaultException that adds diagnostic information to error message for easily
 * identifying the cause of exception.
 *
 * @author chinmay.patel
 *
 */
public class ServerSOAPFaultException extends SOAPFaultException {

    public ServerSOAPFaultException(SOAPFault soapFault) {
        super(soapFault);
    }

    public String getMessage() {
        return "Client received SOAP Fault from server: "
                + super.getMessage()
                + " Please see the server log to find more detail regarding exact cause of the failure.";
    }
}
