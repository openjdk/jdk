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

import com.sun.jmx.snmp.SnmpSecurityParameters;
// java imports
//
import java.util.Vector;
import java.net.InetAddress;


import com.sun.jmx.snmp.SnmpStatusException;
/**
 * A partially decoded representation of an SNMP packet. It contains
 * the information contained in any SNMP message (SNMPv1, SNMPv2 or
 * SNMPv3).
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public abstract class SnmpMsg implements SnmpDefinitions {
    /**
     * The protocol version.
     * <P><CODE>decodeMessage</CODE> and <CODE>encodeMessage</CODE> do not
     * perform any check on this value.
     * <BR><CODE>decodeSnmpPdu</CODE> and <CODE>encodeSnmpPdu</CODE> only
     * accept  the values 0 (for SNMPv1), 1 (for SNMPv2) and 3 (for SNMPv3).
     */
    public int version = 0;

    /**
     * Encoding of the PDU.
     * <P>This is usually the BER encoding of the PDU's syntax
     * defined in RFC1157 and RFC1902. However, this can be authenticated
     * or encrypted data (but you need to implemented your own
     * <CODE>SnmpPduFactory</CODE> class).
     */
    public byte[] data = null;

    /**
     * Number of useful bytes in the <CODE>data</CODE> field.
     */
    public int dataLength = 0;

    /**
     * Source or destination address.
     * <BR>For an incoming message it's the source.
     * For an outgoing message it's the destination.
     */
    public InetAddress address = null;

    /**
     * Source or destination port.
     * <BR>For an incoming message it's the source.
     * For an outgoing message it's the destination.
     */
    public int port = 0;
    /**
     * Security parameters. Contain informations according to Security Model (Usm, community string based, ...).
     */
    public SnmpSecurityParameters securityParameters = null;
    /**
     * Returns the encoded SNMP version present in the passed byte array.
     * @param data The unmarshalled SNMP message.
     * @return The SNMP version (0, 1 or 3).
     */
    public static int getProtocolVersion(byte[] data)
        throws SnmpStatusException {
        int version = 0;
        BerDecoder bdec = null;
        try {
            bdec = new BerDecoder(data);
            bdec.openSequence();
            version = bdec.fetchInteger();
        }
        catch(BerException x) {
            throw new SnmpStatusException("Invalid encoding") ;
        }
        try {
            bdec.closeSequence();
        }
        catch(BerException x) {
        }
        return version;
    }

    /**
     * Returns the associated request ID.
     * @param data The flat message.
     * @return The request ID.
     */
    public abstract int getRequestId(byte[] data) throws SnmpStatusException;

    /**
     * Encodes this message and puts the result in the specified byte array.
     * For internal use only.
     *
     * @param outputBytes An array to receive the resulting encoding.
     *
     * @exception ArrayIndexOutOfBoundsException If the result does not fit
     *                                           into the specified array.
     */
    public abstract int encodeMessage(byte[] outputBytes)
        throws SnmpTooBigException;

     /**
     * Decodes the specified bytes and initializes this message.
     * For internal use only.
     *
     * @param inputBytes The bytes to be decoded.
     *
     * @exception SnmpStatusException If the specified bytes are not a valid encoding.
     */
    public abstract void decodeMessage(byte[] inputBytes, int byteCount)
        throws SnmpStatusException;

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
     */
    public abstract void encodeSnmpPdu(SnmpPdu pdu, int maxDataLength)
        throws SnmpStatusException, SnmpTooBigException;


    /**
     * Gets the PDU encoded in this message.
     * <P>
     * This method decodes the data field and returns the resulting PDU.
     *
     * @return The resulting PDU.
     * @exception SnmpStatusException If the encoding is not valid.
     */
    public abstract SnmpPdu decodeSnmpPdu()
        throws SnmpStatusException;

    /**
     * Dumps the content of a byte buffer using hexadecimal form.
     *
     * @param b The buffer to dump.
     * @param offset The position of the first byte to be dumped.
     * @param len The number of bytes to be dumped starting from offset.
     *
     * @return The string containing the dump.
     */
    public static String dumpHexBuffer(byte [] b, int offset, int len) {
        StringBuffer buf = new StringBuffer(len << 1) ;
        int k = 1 ;
        int flen = offset + len ;

        for (int i = offset; i < flen ; i++) {
            int j = b[i] & 0xFF ;
            buf.append(Character.forDigit((j >>> 4) , 16)) ;
            buf.append(Character.forDigit((j & 0x0F), 16)) ;
            k++ ;
            if (k%16 == 0) {
                buf.append('\n') ;
                k = 1 ;
            } else
                buf.append(' ') ;
        }
        return buf.toString() ;
    }

    /**
     * Dumps this message in a string.
     *
     * @return The string containing the dump.
     */
    public String printMessage() {
        StringBuffer sb = new StringBuffer() ;
        sb.append("Version: ") ;
        sb.append(version) ;
        sb.append("\n") ;
        if (data == null) {
            sb.append("Data: null") ;
        }
        else {
            sb.append("Data: {\n") ;
            sb.append(dumpHexBuffer(data, 0, dataLength)) ;
            sb.append("\n}\n") ;
        }

        return sb.toString() ;
    }

    /**
     * For SNMP Runtime private use only.
     */
    public void encodeVarBindList(BerEncoder benc,
                                  SnmpVarBind[] varBindList)
        throws SnmpStatusException, SnmpTooBigException {
        //
        // Remember: the encoder does backward encoding
        //
        int encodedVarBindCount = 0 ;
        try {
            benc.openSequence() ;
            if (varBindList != null) {
                for (int i = varBindList.length - 1 ; i >= 0 ; i--) {
                    SnmpVarBind bind = varBindList[i] ;
                    if (bind != null) {
                        benc.openSequence() ;
                        encodeVarBindValue(benc, bind.value) ;
                        benc.putOid(bind.oid.longValue()) ;
                        benc.closeSequence() ;
                        encodedVarBindCount++ ;
                    }
                }
            }
            benc.closeSequence() ;
        }
        catch(ArrayIndexOutOfBoundsException x) {
            throw new SnmpTooBigException(encodedVarBindCount) ;
        }
    }

    /**
     * For SNMP Runtime private use only.
     */
    void encodeVarBindValue(BerEncoder benc,
                            SnmpValue v)throws SnmpStatusException {
        if (v == null) {
            benc.putNull() ;
        }
        else if (v instanceof SnmpIpAddress) {
            benc.putOctetString(((SnmpIpAddress)v).byteValue(), SnmpValue.IpAddressTag) ;
        }
        else if (v instanceof SnmpCounter) {
            benc.putInteger(((SnmpCounter)v).longValue(), SnmpValue.CounterTag) ;
        }
        else if (v instanceof SnmpGauge) {
            benc.putInteger(((SnmpGauge)v).longValue(), SnmpValue.GaugeTag) ;
        }
        else if (v instanceof SnmpTimeticks) {
            benc.putInteger(((SnmpTimeticks)v).longValue(), SnmpValue.TimeticksTag) ;
        }
        else if (v instanceof SnmpOpaque) {
            benc.putOctetString(((SnmpOpaque)v).byteValue(), SnmpValue.OpaqueTag) ;
        }
        else if (v instanceof SnmpInt) {
            benc.putInteger(((SnmpInt)v).intValue()) ;
        }
        else if (v instanceof SnmpString) {
            benc.putOctetString(((SnmpString)v).byteValue()) ;
        }
        else if (v instanceof SnmpOid) {
            benc.putOid(((SnmpOid)v).longValue()) ;
        }
        else if (v instanceof SnmpCounter64) {
            if (version == snmpVersionOne) {
                throw new SnmpStatusException("Invalid value for SNMP v1 : " + v) ;
            }
            benc.putInteger(((SnmpCounter64)v).longValue(), SnmpValue.Counter64Tag) ;
        }
        else if (v instanceof SnmpNull) {
            int tag = ((SnmpNull)v).getTag() ;
            if ((version == snmpVersionOne) && (tag != SnmpValue.NullTag)) {
                throw new SnmpStatusException("Invalid value for SNMP v1 : " + v) ;
            }
            if ((version == snmpVersionTwo) &&
                (tag != SnmpValue.NullTag) &&
                (tag != SnmpVarBind.errNoSuchObjectTag) &&
                (tag != SnmpVarBind.errNoSuchInstanceTag) &&
                (tag != SnmpVarBind.errEndOfMibViewTag)) {
                throw new SnmpStatusException("Invalid value " + v) ;
            }
            benc.putNull(tag) ;
        }
        else {
            throw new SnmpStatusException("Invalid value " + v) ;
        }

    }


    /**
     * For SNMP Runtime private use only.
     */
    public SnmpVarBind[] decodeVarBindList(BerDecoder bdec)
        throws BerException {
            bdec.openSequence() ;
            Vector<SnmpVarBind> tmp = new Vector<SnmpVarBind>() ;
            while (bdec.cannotCloseSequence()) {
                SnmpVarBind bind = new SnmpVarBind() ;
                bdec.openSequence() ;
                bind.oid = new SnmpOid(bdec.fetchOid()) ;
                bind.setSnmpValue(decodeVarBindValue(bdec)) ;
                bdec.closeSequence() ;
                tmp.addElement(bind) ;
            }
            bdec.closeSequence() ;
            SnmpVarBind[] varBindList= new SnmpVarBind[tmp.size()] ;
            tmp.copyInto(varBindList);
            return varBindList ;
        }


    /**
     * For SNMP Runtime private use only.
     */
    SnmpValue decodeVarBindValue(BerDecoder bdec)
        throws BerException {
        SnmpValue result = null ;
        int tag = bdec.getTag() ;

        // bugId 4641696 : RuntimeExceptions must be transformed in
        //                 BerException.
        switch(tag) {

            //
            // Simple syntax
            //
        case BerDecoder.IntegerTag :
            try {
                result = new SnmpInt(bdec.fetchInteger()) ;
            } catch(RuntimeException r) {
                throw new BerException();
                // BerException("Can't build SnmpInt from decoded value.");
            }
            break ;
        case BerDecoder.OctetStringTag :
            try {
                result = new SnmpString(bdec.fetchOctetString()) ;
            } catch(RuntimeException r) {
                throw new BerException();
                // BerException("Can't build SnmpString from decoded value.");
            }
            break ;
        case BerDecoder.OidTag :
            try {
                result = new SnmpOid(bdec.fetchOid()) ;
            } catch(RuntimeException r) {
                throw new BerException();
                // BerException("Can't build SnmpOid from decoded value.");
            }
            break ;
        case BerDecoder.NullTag :
            bdec.fetchNull() ;
            try {
                result = new SnmpNull() ;
            } catch(RuntimeException r) {
                throw new BerException();
                // BerException("Can't build SnmpNull from decoded value.");
            }
            break ;

            //
            // Application syntax
            //
        case SnmpValue.IpAddressTag :
            try {
                result = new SnmpIpAddress(bdec.fetchOctetString(tag)) ;
            } catch (RuntimeException r) {
                throw new  BerException();
              // BerException("Can't build SnmpIpAddress from decoded value.");
            }
            break ;
        case SnmpValue.CounterTag :
            try {
                result = new SnmpCounter(bdec.fetchIntegerAsLong(tag)) ;
            } catch(RuntimeException r) {
                throw new BerException();
                // BerException("Can't build SnmpCounter from decoded value.");
            }
            break ;
        case SnmpValue.GaugeTag :
            try {
                result = new SnmpGauge(bdec.fetchIntegerAsLong(tag)) ;
            } catch(RuntimeException r) {
                throw new BerException();
                // BerException("Can't build SnmpGauge from decoded value.");
            }
            break ;
        case SnmpValue.TimeticksTag :
            try {
                result = new SnmpTimeticks(bdec.fetchIntegerAsLong(tag)) ;
            } catch(RuntimeException r) {
                throw new BerException();
             // BerException("Can't build SnmpTimeticks from decoded value.");
            }
            break ;
        case SnmpValue.OpaqueTag :
            try {
                result = new SnmpOpaque(bdec.fetchOctetString(tag)) ;
            } catch(RuntimeException r) {
                throw new BerException();
                // BerException("Can't build SnmpOpaque from decoded value.");
            }
            break ;

            //
            // V2 syntaxes
            //
        case SnmpValue.Counter64Tag :
            if (version == snmpVersionOne) {
                throw new BerException(BerException.BAD_VERSION) ;
            }
            try {
                result = new SnmpCounter64(bdec.fetchIntegerAsLong(tag)) ;
            } catch(RuntimeException r) {
                throw new BerException();
             // BerException("Can't build SnmpCounter64 from decoded value.");
            }
            break ;

        case SnmpVarBind.errNoSuchObjectTag :
            if (version == snmpVersionOne) {
                throw new BerException(BerException.BAD_VERSION) ;
            }
            bdec.fetchNull(tag) ;
            result = SnmpVarBind.noSuchObject ;
            break ;

        case SnmpVarBind.errNoSuchInstanceTag :
            if (version == snmpVersionOne) {
                throw new BerException(BerException.BAD_VERSION) ;
            }
            bdec.fetchNull(tag) ;
            result = SnmpVarBind.noSuchInstance ;
            break ;

        case SnmpVarBind.errEndOfMibViewTag :
            if (version == snmpVersionOne) {
                throw new BerException(BerException.BAD_VERSION) ;
            }
            bdec.fetchNull(tag) ;
            result = SnmpVarBind.endOfMibView ;
            break ;

        default:
            throw new BerException() ;

        }

        return result ;
    }

}
