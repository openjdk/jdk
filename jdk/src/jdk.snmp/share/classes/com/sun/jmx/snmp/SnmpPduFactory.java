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
 * Defines the interface of the object in charge of encoding and decoding SNMP packets.
 * <P>
 * You will not usually need to use this interface, except if you
 * decide to replace the default implementation <CODE>SnmpPduFactoryBER</CODE>.
 * <P>
 * An <CODE>SnmpPduFactory</CODE> object is attached to an
 * {@link com.sun.jmx.snmp.daemon.SnmpAdaptorServer SNMP protocol adaptor}
 * or an {@link com.sun.jmx.snmp.SnmpPeer SnmpPeer}.
 * It is used each time an SNMP packet needs to be encoded or decoded.
 * <BR>{@link com.sun.jmx.snmp.SnmpPduFactoryBER SnmpPduFactoryBER} is the default
 * implementation.
 * It simply applies the standard ASN.1 encoding and decoding
 * on the bytes of the SNMP packet.
 * <P>
 * It's possible to implement your own <CODE>SnmpPduFactory</CODE>
 * object and to add authentication and/or encryption to the
 * default encoding/decoding process.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @see SnmpPduFactory
 * @see SnmpPduPacket
 * @see SnmpMessage
 *
 */

public interface SnmpPduFactory {

    /**
     * Decodes the specified <CODE>SnmpMsg</CODE> and returns the
     * resulting <CODE>SnmpPdu</CODE>. If this method returns
     * <CODE>null</CODE>, the message will be considered unsafe
     * and will be dropped.
     *
     * @param msg The <CODE>SnmpMsg</CODE> to be decoded.
     * @return Null or a fully initialized <CODE>SnmpPdu</CODE>.
     * @exception SnmpStatusException If the encoding is invalid.
     *
     * @since 1.5
     */
    public SnmpPdu decodeSnmpPdu(SnmpMsg msg) throws SnmpStatusException ;

    /**
     * Encodes the specified <CODE>SnmpPdu</CODE> and
     * returns the resulting <CODE>SnmpMsg</CODE>. If this
     * method returns null, the specified <CODE>SnmpPdu</CODE>
     * will be dropped and the current SNMP request will be
     * aborted.
     *
     * @param p The <CODE>SnmpPdu</CODE> to be encoded.
     * @param maxDataLength The size limit of the resulting encoding.
     * @return Null or a fully encoded <CODE>SnmpMsg</CODE>.
     * @exception SnmpStatusException If <CODE>pdu</CODE> contains
     *            illegal values and cannot be encoded.
     * @exception SnmpTooBigException If the resulting encoding does not
     *            fit into <CODE>maxPktSize</CODE> bytes.
     *
     * @since 1.5
     */
    public SnmpMsg encodeSnmpPdu(SnmpPdu p, int maxDataLength)
        throws SnmpStatusException, SnmpTooBigException ;
}
