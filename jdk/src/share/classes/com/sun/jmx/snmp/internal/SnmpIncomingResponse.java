/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.jmx.snmp.internal;

import java.net.InetAddress;
import com.sun.jmx.snmp.SnmpPduFactory;
import com.sun.jmx.snmp.SnmpSecurityParameters;
import com.sun.jmx.snmp.SnmpSecurityException;
import com.sun.jmx.snmp.SnmpTooBigException;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpMsg;

import com.sun.jmx.snmp.internal.SnmpSecurityCache;
import com.sun.jmx.snmp.SnmpBadSecurityLevelException;
/**
 * <P> An <CODE>SnmpIncomingResponse</CODE> handles the unmarshalling of the received response.</P>
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */

public interface SnmpIncomingResponse {
    /**
     * Returns the source address.
     * @return The source address.
     */
    public InetAddress getAddress();

    /**
     * Returns the source port.
     * @return The source port.
     */
    public int getPort();

    /**
     * Gets the incoming response security parameters.
     * @return The security parameters.
     **/
    public SnmpSecurityParameters getSecurityParameters();
    /**
     * Call this method in order to reuse <CODE>SnmpOutgoingRequest</CODE> cache.
     * @param cache The security cache.
     */
    public void setSecurityCache(SnmpSecurityCache cache);
    /**
     * Gets the incoming response security level. This level is defined in
     * {@link com.sun.jmx.snmp.SnmpEngine SnmpEngine}.
     * @return The security level.
     */
    public int getSecurityLevel();
    /**
     * Gets the incoming response security model.
     * @return The security model.
     */
    public int getSecurityModel();
    /**
     * Gets the incoming response context name.
     * @return The context name.
     */
    public byte[] getContextName();

    /**
     * Decodes the specified bytes and initializes itself with the received
     * response.
     *
     * @param inputBytes The bytes to be decoded.
     *
     * @exception SnmpStatusException If the specified bytes are not a valid encoding.
     */
    public SnmpMsg decodeMessage(byte[] inputBytes,
                                 int byteCount,
                                 InetAddress address,
                                 int port)
        throws SnmpStatusException, SnmpSecurityException;

    /**
     * Gets the request PDU encoded in the received response.
     * <P>
     * This method decodes the data field and returns the resulting PDU.
     *
     * @return The resulting PDU.
     * @exception SnmpStatusException If the encoding is not valid.
     */
    public SnmpPdu decodeSnmpPdu()
        throws SnmpStatusException;

    /**
     * Returns the response request Id.
     * @param data The flat message.
     * @return The request Id.
     */
    public int getRequestId(byte[] data) throws SnmpStatusException;

    /**
     * Returns a stringified form of the message to send.
     * @return The message state string.
     */
    public String printMessage();
}
