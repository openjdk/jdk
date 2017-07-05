/*
 * Copyright 2001-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.jmx.snmp;


import java.io.Serializable;
import java.net.InetAddress;
/**
 * Is the fully decoded representation of an SNMP packet.
 * <P>
 * Classes are derived from <CODE>SnmpPdu</CODE> to
 * represent the different forms of SNMP packets
 * ({@link com.sun.jmx.snmp.SnmpPduPacket SnmpPduPacket},
 * {@link com.sun.jmx.snmp.SnmpScopedPduPacket SnmpScopedPduPacket})
 * <BR>The <CODE>SnmpPdu</CODE> class defines the attributes
 * common to every form of SNMP packets.
 *
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @see SnmpMessage
 * @see SnmpPduFactory
 *
 * @since 1.5
 */
public abstract class SnmpPdu implements SnmpDefinitions, Serializable {

    /**
     * PDU type. Types are defined in
     * {@link com.sun.jmx.snmp.SnmpDefinitions SnmpDefinitions}.
     * @serial
     */
    public int type=0 ;

    /**
     * Protocol version. Versions are defined in
     * {@link com.sun.jmx.snmp.SnmpDefinitions SnmpDefinitions}.
     * @serial
     */
    public int version=0 ;

    /**
     * List of variables.
     * @serial
     */
    public SnmpVarBind[] varBindList ;


    /**
     * Request identifier.
     * Note that this field is not used by <CODE>SnmpPduTrap</CODE>.
     * @serial
     */
    public int requestId=0 ;

    /**
     * Source or destination address.
     * <P>For an incoming PDU it's the source.
     * <BR>For an outgoing PDU it's the destination.
     * @serial
     */
    public InetAddress address ;

    /**
     * Source or destination port.
     * <P>For an incoming PDU it's the source.
     * <BR>For an outgoing PDU it's the destination.
     * @serial
     */
    public int port=0 ;

    /**
     * Returns the <CODE>String</CODE> representation of a PDU type.
     * For instance, if the PDU type is <CODE>SnmpDefinitions.pduGetRequestPdu</CODE>,
     * the method will return "SnmpGet".
     * @param cmd The integer representation of the PDU type.
     * @return The <CODE>String</CODE> representation of the PDU type.
     */
    public static String pduTypeToString(int cmd) {
        switch (cmd) {
        case pduGetRequestPdu :
            return "SnmpGet" ;
        case pduGetNextRequestPdu :
            return "SnmpGetNext" ;
        case pduWalkRequest :
            return "SnmpWalk(*)" ;
        case pduSetRequestPdu :
            return "SnmpSet" ;
        case pduGetResponsePdu :
            return "SnmpResponse" ;
        case pduV1TrapPdu :
            return "SnmpV1Trap" ;
        case pduV2TrapPdu :
            return "SnmpV2Trap" ;
        case pduGetBulkRequestPdu :
            return "SnmpGetBulk" ;
        case pduInformRequestPdu :
            return "SnmpInform" ;
        }
        return "Unknown Command = " + cmd ;
    }
}
