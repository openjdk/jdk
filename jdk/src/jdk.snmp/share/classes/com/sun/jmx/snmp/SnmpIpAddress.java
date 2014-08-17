/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * Represents an SNMP IpAddress.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpIpAddress extends SnmpOid {
    private static final long serialVersionUID = 7204629998270874474L;

    // CONSTRUCTORS
    //-------------
    /**
     * Constructs a new <CODE>SnmpIpAddress</CODE> from the specified bytes array.
     * @param bytes The four bytes composing the address.
     * @exception IllegalArgumentException The length of the array is not equal to four.
     */
    public SnmpIpAddress(byte[] bytes) throws IllegalArgumentException {
        buildFromByteArray(bytes);
    }

    /**
     * Constructs a new <CODE>SnmpIpAddress</CODE> from the specified long value.
     * @param addr The initialization value.
     */
    public SnmpIpAddress(long addr) {
        int address = (int)addr ;
        byte[] ipaddr = new byte[4];

        ipaddr[0] = (byte) ((address >>> 24) & 0xFF);
        ipaddr[1] = (byte) ((address >>> 16) & 0xFF);
        ipaddr[2] = (byte) ((address >>> 8) & 0xFF);
        ipaddr[3] = (byte) (address & 0xFF);

        buildFromByteArray(ipaddr);
    }

    /**
     * Constructs a new <CODE>SnmpIpAddress</CODE> from a dot-formatted <CODE>String</CODE>.
     * The dot-formatted <CODE>String</CODE> is formulated x.x.x.x .
     * @param dotAddress The initialization value.
     * @exception IllegalArgumentException The string does not correspond to an ip address.
     */
    public SnmpIpAddress(String dotAddress) throws IllegalArgumentException {
        super(dotAddress) ;
        if ((componentCount > 4) ||
            (components[0] > 255) ||
            (components[1] > 255) ||
            (components[2] > 255) ||
            (components[3] > 255)) {
            throw new IllegalArgumentException(dotAddress) ;
        }
    }

    /**
     * Constructs a new <CODE>SnmpIpAddress</CODE> from four long values.
     * @param b1 Byte 1.
     * @param b2 Byte 2.
     * @param b3 Byte 3.
     * @param b4 Byte 4.
     * @exception IllegalArgumentException A value is outside of [0-255].
     */
    public SnmpIpAddress(long b1, long b2, long b3, long b4) {
        super(b1, b2, b3, b4) ;
        if ((components[0] > 255) ||
            (components[1] > 255) ||
            (components[2] > 255) ||
            (components[3] > 255)) {
            throw new IllegalArgumentException() ;
        }
    }

    // PUBLIC METHODS
    //---------------
    /**
     * Converts the address value to its byte array form.
     * @return The byte array representation of the value.
     */
    public byte[] byteValue() {
        byte[] result = new byte[4] ;
        result[0] = (byte)components[0] ;
        result[1] = (byte)components[1] ;
        result[2] = (byte)components[2] ;
        result[3] = (byte)components[3] ;

        return result ;
    }

    /**
     * Converts the address to its <CODE>String</CODE> form.
     * Same as <CODE>toString()</CODE>. Exists only to follow a naming scheme.
     * @return The <CODE>String</CODE> representation of the value.
     */
    public String stringValue() {
        return toString() ;
    }

    /**
     * Extracts the ip address from an index OID and returns its
     * value converted as an <CODE>SnmpOid</CODE>.
     * @param index The index array.
     * @param start The position in the index array.
     * @return The OID representing the ip address value.
     * @exception SnmpStatusException There is no ip address value
     * available at the start position.
     */
    public static SnmpOid toOid(long[] index, int start) throws SnmpStatusException {
        if (start + 4 <= index.length) {
            try {
                return new SnmpOid(
                                   index[start],
                                   index[start+1],
                                   index[start+2],
                                   index[start+3]) ;
            }
            catch(IllegalArgumentException e) {
                throw new SnmpStatusException(SnmpStatusException.noSuchName) ;
            }
        }
        else {
            throw new SnmpStatusException(SnmpStatusException.noSuchName) ;
        }
    }

    /**
     * Scans an index OID, skips the address value and returns the position
     * of the next value.
     * @param index The index array.
     * @param start The position in the index array.
     * @return The position of the next value.
     * @exception SnmpStatusException There is no address value
     * available at the start position.
     */
    public static int nextOid(long[] index, int start) throws SnmpStatusException {
        if (start + 4 <= index.length) {
            return start + 4 ;
        }
        else {
            throw new SnmpStatusException(SnmpStatusException.noSuchName) ;
        }
    }

    /**
     * Appends an <CODE>SnmpOid</CODE> representing an <CODE>SnmpIpAddress</CODE> to another OID.
     * @param source An OID representing an <CODE>SnmpIpAddress</CODE> value.
     * @param dest Where source should be appended.
     */
    public static void appendToOid(SnmpOid source, SnmpOid dest) {
        if (source.getLength() != 4) {
            throw new IllegalArgumentException() ;
        }
        dest.append(source) ;
    }

    /**
     * Returns a textual description of the type object.
     * @return ASN.1 textual description.
     */
    final public String getTypeName() {
        return name ;
    }

    // PRIVATE METHODS
    //----------------
    /**
     * Build Ip address from byte array.
     */
    private void buildFromByteArray(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException() ;
        }
        components = new long[4] ;
        componentCount= 4;
        components[0] = (bytes[0] >= 0) ? bytes[0] : bytes[0] + 256 ;
        components[1] = (bytes[1] >= 0) ? bytes[1] : bytes[1] + 256 ;
        components[2] = (bytes[2] >= 0) ? bytes[2] : bytes[2] + 256 ;
        components[3] = (bytes[3] >= 0) ? bytes[3] : bytes[3] + 256 ;
    }

    // VARIABLES
    //----------
    /**
     * Name of the type.
     */
    final static String name = "IpAddress" ;
}
