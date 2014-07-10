/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * Represents an SNMP 64bits counter.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpCounter64 extends SnmpValue {
    private static final long serialVersionUID = 8784850650494679937L;

    // CONSTRUCTORS
    //-------------
    /**
     * Constructs a new <CODE>SnmpCounter64</CODE> from the specified long value.
     * @param v The initialization value.
     * @exception IllegalArgumentException The specified value is negative
     * or larger than <CODE>Long.MAX_VALUE</CODE>.
     */
    public SnmpCounter64(long v) throws IllegalArgumentException {

        // NOTE:
        // The max value for a counter64 variable is 2^64 - 1.
        // The max value for a Long is 2^63 - 1.
        // All the allowed values for a conuter64 variable cannot be covered !!!
        //
        if ((v < 0) || (v > Long.MAX_VALUE)) {
            throw new IllegalArgumentException() ;
        }
        value = v ;
    }

    /**
     * Constructs a new <CODE>SnmpCounter64</CODE> from the specified <CODE>Long</CODE> value.
     * @param v The initialization value.
     * @exception IllegalArgumentException The specified value is negative
     * or larger than <CODE>Long.MAX_VALUE</CODE>.
     */
    public SnmpCounter64(Long v) throws IllegalArgumentException {
        this(v.longValue()) ;
    }

    // PUBLIC METHODS
    //---------------
    /**
     * Returns the counter value of this <CODE>SnmpCounter64</CODE>.
     * @return The value.
     */
    public long longValue() {
        return value ;
    }

    /**
     * Converts the counter value to its <CODE>Long</CODE> form.
     * @return The <CODE>Long</CODE> representation of the value.
     */
    public Long toLong() {
        return value;
    }

    /**
     * Converts the counter value to its integer form.
     * @return The integer representation of the value.
     */
    public int intValue() {
        return (int)value ;
    }

    /**
     * Converts the counter value to its <CODE>Integer</CODE> form.
     * @return The <CODE>Integer</CODE> representation of the value.
     */
    public Integer toInteger() {
        return new Integer((int)value) ;
    }

    /**
     * Converts the counter value to its <CODE>String</CODE> form.
     * @return The <CODE>String</CODE> representation of the value.
     */
    public String toString() {
        return String.valueOf(value) ;
    }

    /**
     * Converts the counter value to its <CODE>SnmpOid</CODE> form.
     * @return The OID representation of the value.
     */
    public SnmpOid toOid() {
        return new SnmpOid(value) ;
    }

    /**
     * Extracts the counter from an index OID and returns its
     * value converted as an <CODE>SnmpOid</CODE>.
     * @param index The index array.
     * @param start The position in the index array.
     * @return The OID representing the counter value.
     * @exception SnmpStatusException There is no counter value
     * available at the start position.
     */
    public static SnmpOid toOid(long[] index, int start) throws SnmpStatusException {
        try {
            return new SnmpOid(index[start]) ;
        }
        catch(IndexOutOfBoundsException e) {
            throw new SnmpStatusException(SnmpStatusException.noSuchName) ;
        }
    }

    /**
     * Scans an index OID, skips the counter value and returns the position
     * of the next value.
     * @param index The index array.
     * @param start The position in the index array.
     * @return The position of the next value.
     * @exception SnmpStatusException There is no counter value
     * available at the start position.
     */
    public static int nextOid(long[] index, int start) throws SnmpStatusException {
        if (start >= index.length) {
            throw new SnmpStatusException(SnmpStatusException.noSuchName) ;
        }
        else {
            return start + 1 ;
        }
    }

    /**
     * Appends an <CODE>SnmpOid</CODE> representing an <CODE>SnmpCounter64</CODE> to another OID.
     * @param source An OID representing an <CODE>SnmpCounter64</CODE> value.
     * @param dest Where source should be appended.
     */
    public static void appendToOid(SnmpOid source, SnmpOid dest) {
        if (source.getLength() != 1) {
            throw new IllegalArgumentException() ;
        }
        dest.append(source) ;
    }

    /**
     * Performs a clone action. This provides a workaround for the
     * <CODE>SnmpValue</CODE> interface.
     * @return The SnmpValue clone.
     */
    final synchronized public SnmpValue duplicate() {
        return (SnmpValue)clone() ;
    }

    /**
     * Clones the <CODE>SnmpCounter64</CODE> object, making a copy of its data.
     * @return The object clone.
     */
    final synchronized public Object clone() {
        SnmpCounter64  newclone = null ;
        try {
            newclone = (SnmpCounter64) super.clone() ;
            newclone.value = value ;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e) ; // vm bug.
        }
        return newclone ;
    }

    /**
     * Returns a textual description of the type object.
     * @return ASN.1 textual description.
     */
    final public String getTypeName() {
        return name ;
    }

    // VARIABLES
    //----------
    /**
     * Name of the type.
     */
    final static String name = "Counter64" ;

    /**
     * This is where the value is stored. This long is positive.
     * @serial
     */
    private long value = 0 ;
}
