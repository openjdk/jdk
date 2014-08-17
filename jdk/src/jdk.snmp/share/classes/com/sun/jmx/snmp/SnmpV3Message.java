/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Vector;
import java.util.logging.Level;
import java.net.InetAddress;

// import debug stuff
//
import static com.sun.jmx.defaults.JmxProperties.SNMP_LOGGER;
import com.sun.jmx.snmp.internal.SnmpMsgProcessingSubSystem;
import com.sun.jmx.snmp.internal.SnmpSecurityModel;
import com.sun.jmx.snmp.internal.SnmpDecryptedPdu;
import com.sun.jmx.snmp.internal.SnmpSecurityCache;

import com.sun.jmx.snmp.SnmpMsg;
import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpTooBigException;
import com.sun.jmx.snmp.SnmpScopedPduBulk;
import com.sun.jmx.snmp.BerException;
import com.sun.jmx.snmp.SnmpScopedPduRequest;
import com.sun.jmx.snmp.BerDecoder;
import com.sun.jmx.snmp.SnmpDefinitions;
import com.sun.jmx.snmp.SnmpEngineId;
import com.sun.jmx.snmp.SnmpScopedPduPacket;
import com.sun.jmx.snmp.BerEncoder;
import com.sun.jmx.snmp.SnmpPduRequestType;
import com.sun.jmx.snmp.SnmpPduBulkType;

/**
 * Is a partially decoded representation of an SNMP V3 packet.
 * <P>
 * This class can be used when developing customized manager or agent.
 * <P>
 * The <CODE>SnmpV3Message</CODE> class is directly mapped onto the
 * message syntax defined in RFC 2572.
 * <BLOCKQUOTE>
 * <PRE>
 * SNMPv3Message ::= SEQUENCE {
 *          msgVersion INTEGER ( 0 .. 2147483647 ),
 *          -- administrative parameters
 *          msgGlobalData HeaderData,
 *          -- security model-specific parameters
 *          -- format defined by Security Model
 *          msgSecurityParameters OCTET STRING,
 *          msgData  ScopedPduData
 *      }
 *     HeaderData ::= SEQUENCE {
 *         msgID      INTEGER (0..2147483647),
 *         msgMaxSize INTEGER (484..2147483647),
 *
 *         msgFlags   OCTET STRING (SIZE(1)),
 *                    --  .... ...1   authFlag
 *                    --  .... ..1.   privFlag
 *                    --  .... .1..   reportableFlag
 *                    --              Please observe:
 *                    --  .... ..00   is OK, means noAuthNoPriv
 *                    --  .... ..01   is OK, means authNoPriv
 *                    --  .... ..10   reserved, must NOT be used.
 *                    --  .... ..11   is OK, means authPriv
 *
 *         msgSecurityModel INTEGER (1..2147483647)
 *     }
 * </BLOCKQUOTE>
 * </PRE>
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public class SnmpV3Message extends SnmpMsg {

    /**
     * Message identifier.
     */
    public int msgId = 0;

    /**
     * Message max size the pdu sender can deal with.
     */
    public int msgMaxSize = 0;
    /**
     * Message flags. Reportable flag  and security level.</P>
     *<PRE>
     * --  .... ...1   authFlag
     * --  .... ..1.   privFlag
     * --  .... .1..   reportableFlag
     * --              Please observe:
     * --  .... ..00   is OK, means noAuthNoPriv
     * --  .... ..01   is OK, means authNoPriv
     * --  .... ..10   reserved, must NOT be used.
     * --  .... ..11   is OK, means authPriv
     *</PRE>
     */
    public byte msgFlags = 0;
    /**
     * The security model the security sub system MUST use in order to deal with this pdu (eg: User based Security Model Id = 3).
     */
    public int msgSecurityModel = 0;
    /**
     * The unmarshalled security parameters.
     */
    public byte[] msgSecurityParameters = null;
    /**
     * The context engine Id in which the pdu must be handled (Generaly the local engine Id).
     */
    public byte[] contextEngineId = null;
    /**
     * The context name in which the OID has to be interpreted.
     */
    public byte[] contextName = null;
    /** The encrypted form of the scoped pdu (Only relevant when dealing with privacy).
     */
    public byte[] encryptedPdu = null;

    /**
     * Constructor.
     *
     */
    public SnmpV3Message() {
    }
    /**
     * Encodes this message and puts the result in the specified byte array.
     * For internal use only.
     *
     * @param outputBytes An array to receive the resulting encoding.
     *
     * @exception ArrayIndexOutOfBoundsException If the result does not fit
     *                                           into the specified array.
     */
    public int encodeMessage(byte[] outputBytes)
        throws SnmpTooBigException {
        int encodingLength = 0;
        if (SNMP_LOGGER.isLoggable(Level.FINER)) {
            SNMP_LOGGER.logp(Level.FINER, SnmpV3Message.class.getName(),
                    "encodeMessage",
                    "Can't encode directly V3Message! Need a SecuritySubSystem");
        }
        throw new IllegalArgumentException("Can't encode");
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
            BerDecoder bdec = new BerDecoder(inputBytes);
            bdec.openSequence();
            version = bdec.fetchInteger();
            bdec.openSequence();
            msgId = bdec.fetchInteger();
            msgMaxSize = bdec.fetchInteger();
            msgFlags = bdec.fetchOctetString()[0];
            msgSecurityModel =bdec.fetchInteger();
            bdec.closeSequence();
            msgSecurityParameters = bdec.fetchOctetString();
            if( (msgFlags & SnmpDefinitions.privMask) == 0 ) {
                bdec.openSequence();
                contextEngineId = bdec.fetchOctetString();
                contextName = bdec.fetchOctetString();
                data = bdec.fetchAny();
                dataLength = data.length;
                bdec.closeSequence();
            }
            else {
                encryptedPdu = bdec.fetchOctetString();
            }
            bdec.closeSequence() ;
        }
        catch(BerException x) {
            x.printStackTrace();
            throw new SnmpStatusException("Invalid encoding") ;
        }

        if (SNMP_LOGGER.isLoggable(Level.FINER)) {
            final StringBuilder strb = new StringBuilder()
            .append("Unmarshalled message : \n")
            .append("version : ").append(version)
            .append("\n")
            .append("msgId : ").append(msgId)
            .append("\n")
            .append("msgMaxSize : ").append(msgMaxSize)
            .append("\n")
            .append("msgFlags : ").append(msgFlags)
            .append("\n")
            .append("msgSecurityModel : ").append(msgSecurityModel)
            .append("\n")
            .append("contextEngineId : ").append(contextEngineId == null ? null :
                SnmpEngineId.createEngineId(contextEngineId))
            .append("\n")
            .append("contextName : ").append(contextName)
            .append("\n")
            .append("data : ").append(data)
            .append("\n")
            .append("dat len : ").append((data == null) ? 0 : data.length)
            .append("\n")
            .append("encryptedPdu : ").append(encryptedPdu)
            .append("\n");
            SNMP_LOGGER.logp(Level.FINER, SnmpV3Message.class.getName(),
                    "decodeMessage", strb.toString());
        }
    }

    /**
     * Returns the associated request Id.
     * @param data The flat message.
     * @return The request Id.
     */
    public int getRequestId(byte[] data) throws SnmpStatusException {
        BerDecoder bdec = null;
        int msgId = 0;
        try {
            bdec = new BerDecoder(data);
            bdec.openSequence();
            bdec.fetchInteger();
            bdec.openSequence();
            msgId = bdec.fetchInteger();
        }catch(BerException x) {
            throw new SnmpStatusException("Invalid encoding") ;
        }
        try {
            bdec.closeSequence();
        }
        catch(BerException x) {
        }

        return msgId;
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
     * @param p The PDU to be encoded.
     * @param maxDataLength The maximum length permitted for the data field.
     *
     * @exception SnmpStatusException If the specified <CODE>pdu</CODE>
     *   is not valid.
     * @exception SnmpTooBigException If the resulting encoding does not fit
     * into <CODE>maxDataLength</CODE> bytes.
     * @exception ArrayIndexOutOfBoundsException If the encoding exceeds
     *    <CODE>maxDataLength</CODE>.
     */
    public void encodeSnmpPdu(SnmpPdu p,
                              int maxDataLength)
        throws SnmpStatusException, SnmpTooBigException {

        SnmpScopedPduPacket pdu = (SnmpScopedPduPacket) p;

        if (SNMP_LOGGER.isLoggable(Level.FINER)) {
            final StringBuilder strb = new StringBuilder()
            .append("PDU to marshall: \n")
            .append("security parameters : ").append(pdu.securityParameters)
            .append("\n")
            .append("type : ").append(pdu.type)
            .append("\n")
            .append("version : ").append(pdu.version)
            .append("\n")
            .append("requestId : ").append(pdu.requestId)
            .append("\n")
            .append("msgId : ").append(pdu.msgId)
            .append("\n")
            .append("msgMaxSize : ").append(pdu.msgMaxSize)
            .append("\n")
            .append("msgFlags : ").append(pdu.msgFlags)
            .append("\n")
            .append("msgSecurityModel : ").append(pdu.msgSecurityModel)
            .append("\n")
            .append("contextEngineId : ").append(pdu.contextEngineId)
            .append("\n")
            .append("contextName : ").append(pdu.contextName)
            .append("\n");
            SNMP_LOGGER.logp(Level.FINER, SnmpV3Message.class.getName(),
                    "encodeSnmpPdu", strb.toString());
        }

        version = pdu.version;
        address = pdu.address;
        port = pdu.port;
        msgId = pdu.msgId;
        msgMaxSize = pdu.msgMaxSize;
        msgFlags = pdu.msgFlags;
        msgSecurityModel = pdu.msgSecurityModel;

        contextEngineId = pdu.contextEngineId;
        contextName = pdu.contextName;

        securityParameters = pdu.securityParameters;

        //
        // Allocate the array to receive the encoding.
        //
        data = new byte[maxDataLength];

        //
        // Encode the pdu
        // Reminder: BerEncoder does backward encoding !
        //

        try {
            BerEncoder benc = new BerEncoder(data) ;
            benc.openSequence() ;
            encodeVarBindList(benc, pdu.varBindList) ;

            switch(pdu.type) {

            case pduGetRequestPdu :
            case pduGetNextRequestPdu :
            case pduInformRequestPdu :
            case pduGetResponsePdu :
            case pduSetRequestPdu :
            case pduV2TrapPdu :
            case pduReportPdu :
                SnmpPduRequestType reqPdu = (SnmpPduRequestType) pdu;
                benc.putInteger(reqPdu.getErrorIndex());
                benc.putInteger(reqPdu.getErrorStatus());
                benc.putInteger(pdu.requestId);
                break;

            case pduGetBulkRequestPdu :
                SnmpPduBulkType bulkPdu = (SnmpPduBulkType) pdu;
                benc.putInteger(bulkPdu.getMaxRepetitions());
                benc.putInteger(bulkPdu.getNonRepeaters());
                benc.putInteger(pdu.requestId);
                break ;

            default:
                throw new SnmpStatusException("Invalid pdu type " + String.valueOf(pdu.type)) ;
            }
            benc.closeSequence(pdu.type) ;
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
     */

    public SnmpPdu decodeSnmpPdu()
        throws SnmpStatusException {

        SnmpScopedPduPacket pdu = null;

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
                SnmpScopedPduRequest reqPdu = new SnmpScopedPduRequest() ;
                reqPdu.requestId = bdec.fetchInteger() ;
                reqPdu.setErrorStatus(bdec.fetchInteger());
                reqPdu.setErrorIndex(bdec.fetchInteger());
                pdu = reqPdu ;
                break ;

            case pduGetBulkRequestPdu :
                SnmpScopedPduBulk bulkPdu = new SnmpScopedPduBulk() ;
                bulkPdu.requestId = bdec.fetchInteger() ;
                bulkPdu.setNonRepeaters(bdec.fetchInteger());
                bulkPdu.setMaxRepetitions(bdec.fetchInteger());
                pdu = bulkPdu ;
                break ;
            default:
                throw new SnmpStatusException(snmpRspWrongEncoding) ;
            }
            pdu.type = type;
            pdu.varBindList = decodeVarBindList(bdec);
            bdec.closeSequence() ;
        } catch(BerException e) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, SnmpV3Message.class.getName(),
                        "decodeSnmpPdu", "BerException", e);
            }
            throw new SnmpStatusException(snmpRspWrongEncoding);
        }

        //
        // The easy work.
        //
        pdu.address = address;
        pdu.port = port;
        pdu.msgFlags = msgFlags;
        pdu.version = version;
        pdu.msgId = msgId;
        pdu.msgMaxSize = msgMaxSize;
        pdu.msgSecurityModel = msgSecurityModel;
        pdu.contextEngineId = contextEngineId;
        pdu.contextName = contextName;

        pdu.securityParameters = securityParameters;

        if (SNMP_LOGGER.isLoggable(Level.FINER)) {
            final StringBuilder strb = new StringBuilder()
            .append("Unmarshalled PDU : \n")
            .append("type : ").append(pdu.type)
            .append("\n")
            .append("version : ").append(pdu.version)
            .append("\n")
            .append("requestId : ").append(pdu.requestId)
            .append("\n")
            .append("msgId : ").append(pdu.msgId)
            .append("\n")
            .append("msgMaxSize : ").append(pdu.msgMaxSize)
            .append("\n")
            .append("msgFlags : ").append(pdu.msgFlags)
            .append("\n")
            .append("msgSecurityModel : ").append(pdu.msgSecurityModel)
            .append("\n")
            .append("contextEngineId : ").append(pdu.contextEngineId)
            .append("\n")
            .append("contextName : ").append(pdu.contextName)
            .append("\n");
            SNMP_LOGGER.logp(Level.FINER, SnmpV3Message.class.getName(),
                    "decodeSnmpPdu", strb.toString());
        }
        return pdu ;
    }

    /**
     * Dumps this message in a string.
     *
     * @return The string containing the dump.
     */
    public String printMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("msgId : " + msgId + "\n");
        sb.append("msgMaxSize : " + msgMaxSize + "\n");
        sb.append("msgFlags : " + msgFlags + "\n");
        sb.append("msgSecurityModel : " + msgSecurityModel + "\n");

        if (contextEngineId == null) {
            sb.append("contextEngineId : null");
        }
        else {
            sb.append("contextEngineId : {\n");
            sb.append(dumpHexBuffer(contextEngineId,
                                    0,
                                    contextEngineId.length));
            sb.append("\n}\n");
        }

        if (contextName == null) {
            sb.append("contextName : null");
        }
        else {
            sb.append("contextName : {\n");
            sb.append(dumpHexBuffer(contextName,
                                    0,
                                    contextName.length));
            sb.append("\n}\n");
        }
        return sb.append(super.printMessage()).toString();
    }

}
