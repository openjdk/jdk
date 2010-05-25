/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.jmx.snmp;




/**
 * Is used to represent <CODE>get</CODE>, <CODE>get-next</CODE>, <CODE>set</CODE>, <CODE>response</CODE> and <CODE>SNMPv2-trap</CODE> PDUs.
 * <P>
 * You will not usually need to use this class, except if you
 * decide to implement your own
 * {@link com.sun.jmx.snmp.SnmpPduFactory SnmpPduFactory} object.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpPduRequest extends SnmpPduPacket
    implements SnmpPduRequestType {
    private static final long serialVersionUID = 2218754017025258979L;


    /**
     * Error status. Statuses are defined in
     * {@link com.sun.jmx.snmp.SnmpDefinitions SnmpDefinitions}.
     * @serial
     */
    public int errorStatus=0 ;


    /**
     * Error index. Remember that SNMP indices start from 1.
     * Thus the corresponding <CODE>SnmpVarBind</CODE> is
     * <CODE>varBindList[errorIndex-1]</CODE>.
     * @serial
     */
    public int errorIndex=0 ;
    /**
     * Implements <CODE>SnmpPduRequestType</CODE> interface.
     *
     * @since 1.5
     */
    public void setErrorIndex(int i) {
        errorIndex = i;
    }
    /**
     * Implements <CODE>SnmpPduRequestType</CODE> interface.
     *
     * @since 1.5
     */
    public void setErrorStatus(int i) {
        errorStatus = i;
    }
    /**
     * Implements <CODE>SnmpPduRequestType</CODE> interface.
     *
     * @since 1.5
     */
    public int getErrorIndex() { return errorIndex; }
    /**
     * Implements <CODE>SnmpPduRequestType</CODE> interface.
     *
     * @since 1.5
     */
    public int getErrorStatus() { return errorStatus; }
    /**
     * Implements <CODE>SnmpAckPdu</CODE> interface.
     *
     * @since 1.5
     */
    public SnmpPdu getResponsePdu() {
        SnmpPduRequest result = new SnmpPduRequest();
        result.address = address;
        result.port = port;
        result.version = version;
        result.community = community;
        result.type = SnmpDefinitions.pduGetResponsePdu;
        result.requestId = requestId;
        result.errorStatus = SnmpDefinitions.snmpRspNoError;
        result.errorIndex = 0;

        return result;
    }
}
