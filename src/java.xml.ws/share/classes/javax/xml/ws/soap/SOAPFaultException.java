/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws.soap;

import javax.xml.soap.SOAPFault;

/** The {@code SOAPFaultException} exception represents a
 *  SOAP 1.1 or 1.2 fault.
 *
 *  <p>A {@code SOAPFaultException} wraps a SAAJ {@code SOAPFault}
 *  that manages the SOAP-specific representation of faults.
 *  The {@code createFault} method of
 *  {@code javax.xml.soap.SOAPFactory} may be used to create an instance
 *  of {@code javax.xml.soap.SOAPFault} for use with the
 *  constructor. {@code SOAPBinding} contains an accessor for the
 *  {@code SOAPFactory} used by the binding instance.
 *
 *  <p>Note that the value of {@code getFault} is the only part of the
 *  exception used when searializing a SOAP fault.
 *
 *  <p>Refer to the SOAP specification for a complete
 *  description of SOAP faults.
 *
 *  @see javax.xml.soap.SOAPFault
 *  @see javax.xml.ws.soap.SOAPBinding#getSOAPFactory
 *  @see javax.xml.ws.ProtocolException
 *
 *  @since 1.6, JAX-WS 2.0
 **/
public class SOAPFaultException extends javax.xml.ws.ProtocolException  {

    private SOAPFault fault;

    /** Constructor for SOAPFaultException
     *  @param fault   {@code SOAPFault} representing the fault
     *
     *  @see javax.xml.soap.SOAPFactory#createFault
     **/
    public SOAPFaultException(SOAPFault fault) {
        super(fault.getFaultString());
        this.fault = fault;
    }

    /** Gets the embedded {@code SOAPFault} instance.
     *
     *  @return {@code javax.xml.soap.SOAPFault} SOAP
     *          fault element
     **/
    public javax.xml.soap.SOAPFault getFault() {
        return this.fault;
    }
}
