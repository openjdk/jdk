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



// java imports
//
import java.util.logging.Level;
import java.util.Vector;
import java.net.InetAddress;

import static com.sun.jmx.defaults.JmxProperties.SNMP_LOGGER;

/**
 * Is a partially decoded representation of an SNMP packet.
 * <P>
 * You will not normally need to use this class unless you decide to
 * implement your own {@link com.sun.jmx.snmp.SnmpPduFactory SnmpPduFactory} object.
 * <P>
 * The <CODE>SnmpMessage</CODE> class is directly mapped onto the
 * <CODE>Message</CODE> syntax defined in RFC1157 and RFC1902.
 * <BLOCKQUOTE>
 * <PRE>
 * Message ::= SEQUENCE {
 *    version       INTEGER { version(1) }, -- for SNMPv2
 *    community     OCTET STRING,           -- community name
 *    data          ANY                     -- an SNMPv2 PDU
 * }
 * </PRE>
 * </BLOCKQUOTE>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @see SnmpPduFactory
 * @see SnmpPduPacket
 *
 */

public class SnmpMessage extends SnmpMsg implements SnmpDefinitions {
    /**
     * Community name.
     */
    public byte[] community ;

    /**
     * Encodes this message and puts the result in the specified byte array.
     * For internal use only.
     *
     * @param outputBytes An array to receive the resulting encoding.
     *
     * @exception ArrayIndexOutOfBoundsException If the result does not fit
     *                                           into the specified array.
     */
    public int encodeMessage(byte[] outputBytes) throws SnmpTooBigException {
        int encodingLength = 0 ;
        if (data == null)
            throw new IllegalArgumentException("Data field is null") ;

        //
        // Reminder: BerEncoder does backward encoding !
        //
        try {
            BerEncoder benc = new BerEncoder(outputBytes) ;
            benc.openSequence() ;
            benc.putAny(data, dataLength) ;
            benc.putOctetString((community != null) ? community : new byte[0]) ;
            benc.putInteger(version) ;
            benc.closeSequence() ;
            encodingLength = benc.trim() ;
        }
        catch(ArrayIndexOutOfBoundsException x) {
            throw new SnmpTooBigException() ;
        }

        return encodingLength ;
    }
    /**
     * Returns the associated request ID.
     * @param inputBytes The flat message.
     * @return The request ID.
     *
     * @since 1.5
     */
    public int getRequestId(byte[] inputBytes) throws SnmpStatusException {
        int requestId = 0;
        BerDecoder bdec = null;
        BerDecoder bdec2 = null;
        byte[] any = null;
        try {
            bdec = new BerDecoder(inputBytes);
            bdec.openSequence();
            bdec.fetchInteger();
            bdec.fetchOctetString();
            any = bdec.fetchAny();
            bdec2 = new BerDecoder(any);
            int type = bdec2.getTag();
            bdec2.openSequence(type);
            requestId = bdec2.fetchInteger();
        }
        catch(BerException x) {
            throw new SnmpStatusException("Invalid encoding") ;
        }
        try {
            bdec.closeSequence();
        }
        catch(BerException x) {
        }
        try {
            bdec2.closeSequence();
        }
        catch(BerException x) {
        }
        return requestId;
    }
    /**
     * Decodes the specified bytes and initializes this message.
     * For internal use only.
     *
     * @param inputBytes The bytes to be decoded.
     *
     * @exception SnmpStatusException If the specified bytes are not a valid encoding.
     */
    public void decodeMessage(byte[] inputBytes, int byteCount)
        throws SnmpStatusException {
        try {
            BerDecoder bdec = new BerDecoder(inputBytes/*, byteCount */) ; // FIXME
            bdec.openSequence() ;
            version = bdec.fetchInteger() ;
            community = bdec.fetchOctetString() ;
            data = bdec.fetchAny() ;
            dataLength = data.length ;
            bdec.closeSequence() ;
        }
        catch(BerException x) {
            throw new SnmpStatusException("Invalid encoding") ;
        }
    }

    /**
     * Initializes this message with the specified <CODE>pdu</CODE>.
     * <P>
     * This method initializes the data field with an array of
     * <CODE>maxDataLength</CODE> bytes. It encodes the <CODE>pdu</CODE>.
     * The resulting encoding is stored in the data field
     * and the length of the encoding is stored in <CODE>dataLength</CODE>.
     * <p>
     * If the encoding length exceeds <CODE>maxDataLength</CODE>,
     * the method throws an exception.
     *
     * @param pdu The PDU to be encoded.
     * @param maxDataLength The maximum length permitted for the data field.
     *
     * @exception SnmpStatusException If the specified <CODE>pdu</CODE> is not valid.
     * @exception SnmpTooBigException If the resulting encoding does not fit
     * into <CODE>maxDataLength</CODE> bytes.
     * @exception ArrayIndexOutOfBoundsException If the encoding exceeds <CODE>maxDataLength</CODE>.
     *
     * @since 1.5
     */
    public void encodeSnmpPdu(SnmpPdu pdu, int maxDataLength)
        throws SnmpStatusException, SnmpTooBigException {
        //
        // The easy work
        //
        SnmpPduPacket pdupacket = (SnmpPduPacket) pdu;
        version = pdupacket.version ;
        community = pdupacket.community ;
        address = pdupacket.address ;
        port = pdupacket.port ;

        //
        // Allocate the array to receive the encoding.
        //
        data = new byte[maxDataLength] ;

        //
        // Encode the pdupacket
        // Reminder: BerEncoder does backward encoding !
        //

        try {
            BerEncoder benc = new BerEncoder(data) ;
            benc.openSequence() ;
            encodeVarBindList(benc, pdupacket.varBindList) ;

            switch(pdupacket.type) {

            case pduGetRequestPdu :
            case pduGetNextRequestPdu :
            case pduInformRequestPdu :
            case pduGetResponsePdu :
            case pduSetRequestPdu :
            case pduV2TrapPdu :
            case pduReportPdu :
                SnmpPduRequest reqPdu = (SnmpPduRequest)pdupacket ;
                benc.putInteger(reqPdu.errorIndex) ;
                benc.putInteger(reqPdu.errorStatus) ;
                benc.putInteger(reqPdu.requestId) ;
                break ;

            case pduGetBulkRequestPdu :
                SnmpPduBulk bulkPdu = (SnmpPduBulk)pdupacket ;
                benc.putInteger(bulkPdu.maxRepetitions) ;
                benc.putInteger(bulkPdu.nonRepeaters) ;
                benc.putInteger(bulkPdu.requestId) ;
                break ;

            case pduV1TrapPdu :
                SnmpPduTrap trapPdu = (SnmpPduTrap)pdupacket ;
                benc.putInteger(trapPdu.timeStamp, SnmpValue.TimeticksTag) ;
                benc.putInteger(trapPdu.specificTrap) ;
                benc.putInteger(trapPdu.genericTrap) ;
                if(trapPdu.agentAddr != null)
                    benc.putOctetString(trapPdu.agentAddr.byteValue(), SnmpValue.IpAddressTag) ;
                else
                    benc.putOctetString(new byte[0], SnmpValue.IpAddressTag);
                benc.putOid(trapPdu.enterprise.longValue()) ;
                break ;

            default:
                throw new SnmpStatusException("Invalid pdu type " + String.valueOf(pdupacket.type)) ;
            }
            benc.closeSequence(pdupacket.type) ;
            dataLength = benc.trim() ;
        }
        catch(ArrayIndexOutOfBoundsException x) {
            throw new SnmpTooBigException() ;
        }
    }
    /**
     * Gets the PDU encoded in this message.
     * <P>
     * This method decodes the data field and returns the resulting PDU.
     *
     * @return The resulting PDU.
     * @exception SnmpStatusException If the encoding is not valid.
     *
     * @since 1.5
     */
    public SnmpPdu decodeSnmpPdu()
        throws SnmpStatusException {
        //
        // Decode the pdu
        //
        SnmpPduPacket pdu = null ;
        BerDecoder bdec = new BerDecoder(data) ;
        try {
            int type = bdec.getTag() ;
            bdec.openSequence(type) ;
            switch(type) {

            case pduGetRequestPdu :
            case pduGetNextRequestPdu :
            case pduInformRequestPdu :
            case pduGetResponsePdu :
            case pduSetRequestPdu :
            case pduV2TrapPdu :
            case pduReportPdu :
                SnmpPduRequest reqPdu = new SnmpPduRequest() ;
                reqPdu.requestId = bdec.fetchInteger() ;
                reqPdu.errorStatus = bdec.fetchInteger() ;
                reqPdu.errorIndex = bdec.fetchInteger() ;
                pdu = reqPdu ;
                break ;

            case pduGetBulkRequestPdu :
                SnmpPduBulk bulkPdu = new SnmpPduBulk() ;
                bulkPdu.requestId = bdec.fetchInteger() ;
                bulkPdu.nonRepeaters = bdec.fetchInteger() ;
                bulkPdu.maxRepetitions = bdec.fetchInteger() ;
                pdu = bulkPdu ;
                break ;

            case pduV1TrapPdu :
                SnmpPduTrap trapPdu = new SnmpPduTrap() ;
                trapPdu.enterprise = new SnmpOid(bdec.fetchOid()) ;
                byte []b = bdec.fetchOctetString(SnmpValue.IpAddressTag);
                if(b.length != 0)
                    trapPdu.agentAddr = new SnmpIpAddress(b) ;
                else
                    trapPdu.agentAddr = null;
                trapPdu.genericTrap = bdec.fetchInteger() ;
                trapPdu.specificTrap = bdec.fetchInteger() ;
                trapPdu.timeStamp = bdec.fetchInteger(SnmpValue.TimeticksTag) ;
                pdu = trapPdu ;
                break ;

            default:
                throw new SnmpStatusException(snmpRspWrongEncoding) ;
            }
            pdu.type = type ;
            pdu.varBindList = decodeVarBindList(bdec) ;
            bdec.closeSequence() ;
        } catch(BerException e) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, SnmpMessage.class.getName(),
                        "decodeSnmpPdu", "BerException", e);
            }
            throw new SnmpStatusException(snmpRspWrongEncoding);
        } catch(IllegalArgumentException e) {
            // bug id 4654066
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, SnmpMessage.class.getName(),
                        "decodeSnmpPdu", "IllegalArgumentException", e);
            }
            throw new SnmpStatusException(snmpRspWrongEncoding);
        }

        //
        // The easy work
        //
        pdu.version = version ;
        pdu.community = community ;
        pdu.address = address ;
        pdu.port = port ;

        return pdu;
    }
    /**
     * Dumps this message in a string.
     *
     * @return The string containing the dump.
     */
    public String printMessage() {
        StringBuilder sb = new StringBuilder();
        if (community == null) {
            sb.append("Community: null") ;
        }
        else {
            sb.append("Community: {\n") ;
            sb.append(dumpHexBuffer(community, 0, community.length)) ;
            sb.append("\n}\n") ;
        }
        return sb.append(super.printMessage()).toString();
    }

}
