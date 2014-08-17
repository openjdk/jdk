/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * Represents a <CODE>get-bulk</CODE> PDU as defined in RFC 1448.
 * <P>
 * You will not usually need to use this class, except if you
 * decide to implement your own
 * {@link com.sun.jmx.snmp.SnmpPduFactory SnmpPduFactory} object.
 * <P>
 * The <CODE>SnmpPduBulk</CODE> extends {@link com.sun.jmx.snmp.SnmpPduPacket SnmpPduPacket}
 * and defines attributes specific to the <CODE>get-bulk</CODE> PDU (see RFC 1448).
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpPduBulk extends SnmpPduPacket
    implements SnmpPduBulkType {
    private static final long serialVersionUID = -7431306775883371046L;

    /**
     * The <CODE>non-repeaters</CODE> value.
     * @serial
     */
    public int            nonRepeaters ;


    /**
     * The <CODE>max-repetitions</CODE> value.
     * @serial
     */
    public int            maxRepetitions ;


    /**
     * Builds a new <CODE>get-bulk</CODE> PDU.
     * <BR><CODE>type</CODE> and <CODE>version</CODE> fields are initialized with
     * {@link com.sun.jmx.snmp.SnmpDefinitions#pduGetBulkRequestPdu pduGetBulkRequestPdu}
     * and {@link com.sun.jmx.snmp.SnmpDefinitions#snmpVersionTwo snmpVersionTwo}.
     */
    public SnmpPduBulk() {
        type = pduGetBulkRequestPdu ;
        version = snmpVersionTwo ;
    }
    /**
     * Implements the <CODE>SnmpPduBulkType</CODE> interface.
     *
     * @since 1.5
     */
    public void setMaxRepetitions(int i) {
        maxRepetitions = i;
    }
    /**
     * Implements the <CODE>SnmpPduBulkType</CODE> interface.
     *
     * @since 1.5
     */
    public void setNonRepeaters(int i) {
        nonRepeaters = i;
    }
    /**
     * Implements the <CODE>SnmpPduBulkType</CODE> interface.
     *
     * @since 1.5
     */
    public int getMaxRepetitions() { return maxRepetitions; }
    /**
     * Implements the <CODE>SnmpPduBulkType</CODE> interface.
     *
     * @since 1.5
     */
    public int getNonRepeaters() { return nonRepeaters; }
    /**
     * Implements the <CODE>SnmpAckPdu</CODE> interface.
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
