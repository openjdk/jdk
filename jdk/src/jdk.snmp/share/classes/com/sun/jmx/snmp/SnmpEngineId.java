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

import java.net.InetAddress;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.Arrays;
import java.util.NoSuchElementException;

import com.sun.jmx.snmp.internal.SnmpTools;

/**
 * This class is handling an <CODE>SnmpEngineId</CODE> data. It copes with binary as well as <CODE>String</CODE> representation of an engine Id. A string format engine is an hex string starting with 0x.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public class SnmpEngineId implements Serializable {
    private static final long serialVersionUID = 5434729655830763317L;

    byte[] engineId = null;
    String hexString = null;
    String humanString = null;
    /**
     * New <CODE>SnmpEngineId</CODE> with an hex string value. Can handle engine Id format &lt;host&gt:&lt;port&gt.
     * @param hexString Hexa string.
     */
    SnmpEngineId(String hexString) {
        engineId = SnmpTools.ascii2binary(hexString);
        this.hexString = hexString.toLowerCase();
    }
    /**
     * New <CODE>SnmpEngineId</CODE> with a binary value. You can use <CODE> SnmpTools </CODE> to convert from hex string to binary format.
     * @param bin Binary value
     */
    SnmpEngineId(byte[] bin) {
        engineId = bin;
        hexString = SnmpTools.binary2ascii(bin).toLowerCase();
    }

    /**
     * If a string of the format &lt;address&gt;:&lt;port&gt;:&lt;IANA number&gt; has been provided at creation time, this string is returned.
     * @return The Id as a readable string or null if not provided.
     */
    public String getReadableId() {
        return humanString;
    }

    /**
     * Returns a string format engine Id.
     * @return String format value.
     */
    public String toString() {
        return hexString;
    }
    /**
     * Returns a binary engine Id.
     * @return Binary value.
     */
    public byte[] getBytes() {
        return engineId;
    }

    /**
     * In order to store the string used to create the engineId.
     */
    void setStringValue(String val) {
        humanString = val;
    }

    static void validateId(String str) throws IllegalArgumentException {
        byte[] arr = SnmpTools.ascii2binary(str);
        validateId(arr);
    }

    static void validateId(byte[] arr) throws IllegalArgumentException {

        if(arr.length < 5) throw new IllegalArgumentException("Id size lower than 5 bytes.");
        if(arr.length > 32) throw new IllegalArgumentException("Id size greater than 32 bytes.");

        //octet strings with very first bit = 0 and length != 12 octets
        if( ((arr[0] & 0x80) == 0) && arr.length != 12)
            throw new IllegalArgumentException("Very first bit = 0 and length != 12 octets");

        byte[] zeroedArrays = new byte[arr.length];
        if(Arrays.equals(zeroedArrays, arr)) throw new IllegalArgumentException("Zeroed Id.");
        byte[] FFArrays = new byte[arr.length];
        Arrays.fill(FFArrays, (byte)0xFF);
        if(Arrays.equals(FFArrays, arr)) throw new IllegalArgumentException("0xFF Id.");

    }

    /**
     * Generates an engine Id based on the passed array.
     * @return The created engine Id or null if given arr is null or its length == 0;
     * @exception IllegalArgumentException when:
     * <ul>
     *  <li>octet string lower than 5 bytes.</li>
     *  <li>octet string greater than 32 bytes.</li>
     *  <li>octet string = all zeros.</li>
     *  <li>octet string = all 'ff'H.</li>
     *  <li>octet strings with very first bit = 0 and length != 12 octets</li>
     * </ul>
     */
    public static SnmpEngineId createEngineId(byte[] arr) throws IllegalArgumentException {
        if( (arr == null) || arr.length == 0) return null;
        validateId(arr);
        return new SnmpEngineId(arr);
    }

    /**
     * Generates an engine Id that is unique to the host the agent is running on. The engine Id unicity is system time based. The creation algorithm uses the SUN Microsystems IANA number (42).
     * @return The generated engine Id.
     */
    public static SnmpEngineId createEngineId() {
        byte[] address = null;
        byte[] engineid = new byte[13];
        int iana = 42;
        long mask = 0xFF;
        long time = System.currentTimeMillis();

        engineid[0] = (byte) ( (iana & 0xFF000000) >> 24 );
        engineid[0] |= 0x80;
        engineid[1] = (byte) ( (iana & 0x00FF0000) >> 16 );
        engineid[2] = (byte) ( (iana & 0x0000FF00) >> 8 );
        engineid[3] = (byte) (iana & 0x000000FF);
        engineid[4] = 0x05;

        engineid[5] =  (byte) ( (time & (mask << 56)) >>> 56 );
        engineid[6] =  (byte) ( (time & (mask << 48) ) >>> 48 );
        engineid[7] =  (byte) ( (time & (mask << 40) ) >>> 40 );
        engineid[8] =  (byte) ( (time & (mask << 32) ) >>> 32 );
        engineid[9] =  (byte) ( (time & (mask << 24) ) >>> 24 );
        engineid[10] = (byte) ( (time & (mask << 16) ) >>> 16 );
        engineid[11] = (byte) ( (time & (mask << 8) ) >>> 8 );
        engineid[12] = (byte) (time & mask);

        return new SnmpEngineId(engineid);
    }

    /**
     * Translates an engine Id in an SnmpOid format. This is useful when dealing with USM MIB indexes.
     * The oid format is : <engine Id length>.<engine Id binary octet1>....<engine Id binary octetn - 1>.<engine Id binary octetn>
     * Eg: "0x8000002a05819dcb6e00001f96" ==> 13.128.0.0.42.5.129.157.203.110.0.0.31.150
     *
     * @return SnmpOid The oid.
     */
    public SnmpOid toOid() {
        long[] oid = new long[engineId.length + 1];
        oid[0] = engineId.length;
        for(int i = 1; i <= engineId.length; i++)
            oid[i] = (long) (engineId[i-1] & 0xFF);
        return new SnmpOid(oid);
    }

   /**
    * <P>Generates a unique engine Id. Hexadecimal strings as well as a textual description are supported. The textual format is as follow:
    * <BR>  &lt;address&gt;:&lt;port&gt;:&lt;IANA number&gt;</P>
    * <P>The allowed formats :</P>
    * <ul>
    * <li> &lt;address&gt;:&lt;port&gt;:&lt;IANA number&gt
    * <BR>   All these parameters are used to generate the Id. WARNING, this method is not compliant with IPv6 address format. Use { @link com.sun.jmx.snmp.SnmpEngineId#createEngineId(java.lang.String,java.lang.String) } instead.</li>
    * <li> &lt;address&gt;:&lt;port&gt;
    * <BR>   The IANA number will be the SUN Microsystems one (42). </li>
    * <li> address
    * <BR>   The port 161 will be used to generate the Id. IANA number will be the SUN Microsystems one (42). </li>
    * <li> :port
    * <BR>   The host to use is localhost. IANA number will be the SUN Microsystems one (42). </li>
    * <li> ::&lt;IANA number&gt &nbsp;&nbsp;&nbsp;
    * <BR>   The port 161 and localhost will be used to generate the Id. </li>
    * <li> :&lt;port&gt;:&lt;IANA number&gt;
    * <BR>   The host to use is localhost. </li>
    * <li> &lt;address&gt;::&lt;IANA number&gt
    * <BR>   The port 161 will be used to generate the Id. </li>
    * <li> :: &nbsp;&nbsp;&nbsp;
    * <BR>   The port 161, localhost and the SUN Microsystems IANA number will be used to generate the Id. </li>
    * </ul>
    * @exception UnknownHostException if the host name contained in the textual format is unknown.
    * @exception IllegalArgumentException when :
    * <ul>
    *  <li>octet string lower than 5 bytes.</li>
    *  <li>octet string greater than 32 bytes.</li>
    *  <li>octet string = all zeros.</li>
    *  <li>octet string = all 'ff'H.</li>
    *  <li>octet strings with very first bit = 0 and length != 12 octets</li>
    *  <li>An IPv6 address format is used in conjonction with the ":" separator</li>
    * </ul>
    * @param str The string to parse.
    * @return The generated engine Id or null if the passed string is null.
    *
    */
    public static SnmpEngineId createEngineId(String str)
        throws IllegalArgumentException, UnknownHostException {
        return createEngineId(str, null);
    }

    /**
     * Idem { @link
     * com.sun.jmx.snmp.SnmpEngineId#createEngineId(java.lang.String) }
     * with the ability to provide your own separator. This allows IPv6
     * address format handling (eg: providing @ as separator).
     * @param str The string to parse.
     * @param separator the separator to use. If null is provided, the default
     * separator ":" is used.
     * @return The generated engine Id or null if the passed string is null.
     * @exception UnknownHostException if the host name contained in the
     * textual format is unknown.
     * @exception IllegalArgumentException when :
     * <ul>
     *  <li>octet string lower than 5 bytes.</li>
     *  <li>octet string greater than 32 bytes.</li>
     *  <li>octet string = all zeros.</li>
     *  <li>octet string = all 'ff'H.</li>
     *  <li>octet strings with very first bit = 0 and length != 12 octets</li>
     *  <li>An IPv6 address format is used in conjonction with the ":"
     *      separator</li>
     * </ul>
     * @since 1.5
     */
    public static SnmpEngineId createEngineId(String str, String separator)
        throws IllegalArgumentException, UnknownHostException {
        if(str == null) return null;

        if(str.startsWith("0x") || str.startsWith("0X")) {
            validateId(str);
            return new SnmpEngineId(str);
        }
        separator = separator == null ? ":" : separator;
        StringTokenizer token = new StringTokenizer(str,
                                                    separator,
                                                    true);

        String address = null;
        String port = null;
        String iana = null;
        int objPort = 161;
        int objIana = 42;
        InetAddress objAddress = null;
        SnmpEngineId eng = null;
        try {
            //Deal with address
            try {
                address = token.nextToken();
            }catch(NoSuchElementException e) {
                throw new IllegalArgumentException("Passed string is invalid : ["+str+"]");
            }
            if(!address.equals(separator)) {
                objAddress = InetAddress.getByName(address);
                try {
                    token.nextToken();
                }catch(NoSuchElementException e) {
                    //No need to go further, no port.
                    eng = SnmpEngineId.createEngineId(objAddress,
                                                      objPort,
                                                      objIana);
                    eng.setStringValue(str);
                    return eng;
                }
            }
            else
                objAddress = InetAddress.getLocalHost();

            //Deal with port
            try {
                port = token.nextToken();
            }catch(NoSuchElementException e) {
                //No need to go further, no port.
                eng = SnmpEngineId.createEngineId(objAddress,
                                                  objPort,
                                                  objIana);
                eng.setStringValue(str);
                return eng;
            }

            if(!port.equals(separator)) {
                objPort = Integer.parseInt(port);
                try {
                    token.nextToken();
                }catch(NoSuchElementException e) {
                    //No need to go further, no iana.
                    eng = SnmpEngineId.createEngineId(objAddress,
                                                      objPort,
                                                      objIana);
                    eng.setStringValue(str);
                    return eng;
                }
            }

            //Deal with iana
            try {
                iana = token.nextToken();
            }catch(NoSuchElementException e) {
                //No need to go further, no port.
                eng = SnmpEngineId.createEngineId(objAddress,
                                                  objPort,
                                                  objIana);
                eng.setStringValue(str);
                return eng;
            }

            if(!iana.equals(separator))
                objIana = Integer.parseInt(iana);

            eng = SnmpEngineId.createEngineId(objAddress,
                                              objPort,
                                              objIana);
            eng.setStringValue(str);

            return eng;

        } catch(Exception e) {
            throw new IllegalArgumentException("Passed string is invalid : ["+str+"]. Check that the used separator ["+ separator + "] is compatible with IPv6 address format.");
        }

    }

    /**
     * Generates a unique engine Id. The engine Id unicity is based on
     * the host IP address and port. The IP address used is the
     * localhost one. The creation algorithm uses the SUN Microsystems IANA
     * number (42).
     * @param port The TCP/IP port the SNMPv3 Adaptor Server is listening to.
     * @return The generated engine Id.
     * @exception UnknownHostException if the local host name
     *            used to calculate the id is unknown.
     */
    public static SnmpEngineId createEngineId(int port)
        throws UnknownHostException {
        int suniana = 42;
        InetAddress address = null;
        address = InetAddress.getLocalHost();
        return createEngineId(address, port, suniana);
    }
    /**
     * Generates a unique engine Id. The engine Id unicity is based on
     * the host IP address and port. The IP address used is the passed
     * one. The creation algorithm uses the SUN Microsystems IANA
     * number (42).
     * @param address The IP address the SNMPv3 Adaptor Server is listening to.
     * @param port The TCP/IP port the SNMPv3 Adaptor Server is listening to.
     * @return The generated engine Id.
     * @exception UnknownHostException. if the provided address is null.
     */
    public static SnmpEngineId createEngineId(InetAddress address, int port)
        throws IllegalArgumentException {
        int suniana = 42;
        if(address == null)
            throw new IllegalArgumentException("InetAddress is null.");
        return createEngineId(address, port, suniana);
    }

    /**
     * Generates a unique engine Id. The engine Id unicity is based on
     * the host IP address and port. The IP address is the localhost one.
     * The creation algorithm uses the passed IANA number.
     * @param port The TCP/IP port the SNMPv3 Adaptor Server is listening to.
     * @param iana Your enterprise IANA number.
     * @exception UnknownHostException if the local host name used to calculate the id is unknown.
     * @return The generated engine Id.
     */
    public static SnmpEngineId createEngineId(int port, int iana) throws UnknownHostException {
        InetAddress address = null;
        address = InetAddress.getLocalHost();
        return createEngineId(address, port, iana);
    }

    /**
     * Generates a unique engine Id. The engine Id unicity is based on the host IP address and port. The IP address is the passed one, it handles IPv4 and IPv6 hosts. The creation algorithm uses the passed IANA number.
     * @param addr The IP address the SNMPv3 Adaptor Server is listening to.
     * @param port The TCP/IP port the SNMPv3 Adaptor Server is listening to.
     * @param iana Your enterprise IANA number.
     * @return The generated engine Id.
     * @exception UnknownHostException if the provided <CODE>InetAddress </CODE> is null.
     */
    public static SnmpEngineId createEngineId(InetAddress addr,
                                              int port,
                                              int iana) {
        if(addr == null) throw new IllegalArgumentException("InetAddress is null.");
        byte[] address = addr.getAddress();
        byte[] engineid = new byte[9 + address.length];
        engineid[0] = (byte) ( (iana & 0xFF000000) >> 24 );
        engineid[0] |= 0x80;
        engineid[1] = (byte) ( (iana & 0x00FF0000) >> 16 );
        engineid[2] = (byte) ( (iana & 0x0000FF00) >> 8 );

engineid[3] = (byte) (iana & 0x000000FF);
        engineid[4] = 0x05;

        if(address.length == 4)
            engineid[4] = 0x01;

        if(address.length == 16)
            engineid[4] = 0x02;

        for(int i = 0; i < address.length; i++) {
            engineid[i + 5] = address[i];
        }

        engineid[5 + address.length] = (byte)  ( (port & 0xFF000000) >> 24 );
        engineid[6 + address.length] = (byte) ( (port & 0x00FF0000) >> 16 );
        engineid[7 + address.length] = (byte) ( (port & 0x0000FF00) >> 8 );
        engineid[8 + address.length] = (byte) (  port & 0x000000FF );

        return new SnmpEngineId(engineid);
    }

     /**
     * Generates an engine Id based on an InetAddress. Handles IPv4 and IPv6 addresses. The creation algorithm uses the passed IANA number.
     * @param iana Your enterprise IANA number.
     * @param addr The IP address the SNMPv3 Adaptor Server is listening to.
     * @return The generated engine Id.
     * @since 1.5
     * @exception UnknownHostException if the provided <CODE>InetAddress </CODE> is null.
     */
    public static SnmpEngineId createEngineId(int iana, InetAddress addr)
    {
        if(addr == null) throw new IllegalArgumentException("InetAddress is null.");
        byte[] address = addr.getAddress();
        byte[] engineid = new byte[5 + address.length];
        engineid[0] = (byte) ( (iana & 0xFF000000) >> 24 );
        engineid[0] |= 0x80;
        engineid[1] = (byte) ( (iana & 0x00FF0000) >> 16 );
        engineid[2] = (byte) ( (iana & 0x0000FF00) >> 8 );

        engineid[3] = (byte) (iana & 0x000000FF);
        if(address.length == 4)
            engineid[4] = 0x01;

        if(address.length == 16)
            engineid[4] = 0x02;

        for(int i = 0; i < address.length; i++) {
            engineid[i + 5] = address[i];
        }

        return new SnmpEngineId(engineid);
    }

    /**
     * Generates an engine Id based on an InetAddress. Handles IPv4 and IPv6
     * addresses. The creation algorithm uses the sun IANA number (42).
     * @param addr The IP address the SNMPv3 Adaptor Server is listening to.
     * @return The generated engine Id.
     * @since 1.5
     * @exception UnknownHostException if the provided
     *            <CODE>InetAddress</CODE> is null.
     */
    public static SnmpEngineId createEngineId(InetAddress addr) {
        return createEngineId(42, addr);
    }


    /**
     * Tests <CODE>SnmpEngineId</CODE> instance equality. Two <CODE>SnmpEngineId</CODE> are equal if they have the same value.
     * @return <CODE>true</CODE> if the two <CODE>SnmpEngineId</CODE> are equals, <CODE>false</CODE> otherwise.
     */
    public boolean equals(Object a) {
        if(!(a instanceof SnmpEngineId) ) return false;
        return hexString.equals(((SnmpEngineId) a).toString());
    }

    public int hashCode() {
        return hexString.hashCode();
    }
}
