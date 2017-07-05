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
 * Represents an SNMP null value.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpNull extends SnmpValue {
    private static final long serialVersionUID = 1783782515994279177L;

    // CONSTRUCTORS
    //-------------
    /**
     * Constructs a new <CODE>SnmpNull</CODE>.
     */
    public SnmpNull() {
        tag = NullTag ;
    }

    /**
     * Constructs a new <CODE>SnmpNull</CODE>.
     * <BR>For mibgen private use only.
     */
    public SnmpNull(String dummy) {
        this();
    }

    /**
     * Constructs a new <CODE>SnmpNull</CODE> from the specified tag value.
     * @param t The initialization value.
     */
    public SnmpNull(int t) {
        tag = t ;
    }

    // PUBLIC METHODS
    //---------------
    /**
     * Returns the tag value of this <CODE>SnmpNull</CODE>.
     * @return The value.
     */
    public int getTag() {
        return tag ;
    }

    /**
     * Converts the <CODE>NULL</CODE> value to its ASN.1 <CODE>String</CODE> form.
     * When the tag is not the universal one, it is preprended
     * to the <CODE>String</CODE> form.
     * @return The <CODE>String</CODE> representation of the value.
     */
    public String toString() {
        String result = "" ;
        if (tag != 5) {
            result += "[" + tag + "] " ;
        }
        result += "NULL" ;
        switch(tag) {
        case errNoSuchObjectTag :
            result += " (noSuchObject)" ;
            break ;

        case errNoSuchInstanceTag :
            result += " (noSuchInstance)" ;
            break ;

        case errEndOfMibViewTag :
            result += " (endOfMibView)" ;
            break ;
        }
        return result ;
    }

    /**
     * Converts the <CODE>NULL</CODE> value to its <CODE>SnmpOid</CODE> form.
     * Normally, a <CODE>NULL</CODE> value cannot be used as an index value,
     * this method triggers an exception.
     * @return The OID representation of the value.
     */
    public SnmpOid toOid() {
        throw new IllegalArgumentException() ;
    }

    /**
     * Performs a clone action. This provides a workaround for the
     * <CODE>SnmpValue</CODE> interface.
     * @return The SnmpValue clone.
     */
    final synchronized public SnmpValue duplicate() {
        return (SnmpValue) clone() ;
    }

    /**
     * Clones the <CODE>SnmpNull</CODE> object, making a copy of its data.
     * @return The object clone.
     */
    final synchronized public Object clone() {
        SnmpNull  newclone = null ;
        try {
            newclone = (SnmpNull) super.clone() ;
            newclone.tag = tag ;
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

    /**
     * Checks if this <CODE>SnmpNull</CODE> object corresponds to a <CODE>noSuchObject</CODE> value.
     * @return <CODE>true</CODE> if the tag equals {@link com.sun.jmx.snmp.SnmpDataTypeEnums#errNoSuchObjectTag},
     * <CODE>false</CODE> otherwise.
     */
    public boolean isNoSuchObjectValue() {
        return (tag == SnmpDataTypeEnums.errNoSuchObjectTag);
    }

    /**
     * Checks if this <CODE>SnmpNull</CODE> object corresponds to a <CODE>noSuchInstance</CODE> value.
     * @return <CODE>true</CODE> if the tag equals {@link com.sun.jmx.snmp.SnmpDataTypeEnums#errNoSuchInstanceTag},
     * <CODE>false</CODE> otherwise.
     */
    public boolean isNoSuchInstanceValue() {
        return (tag == SnmpDataTypeEnums.errNoSuchInstanceTag);
    }

    /**
     * Checks if this <CODE>SnmpNull</CODE> object corresponds to an <CODE>endOfMibView</CODE> value.
     * @return <CODE>true</CODE> if the tag equals {@link com.sun.jmx.snmp.SnmpDataTypeEnums#errEndOfMibViewTag},
     * <CODE>false</CODE> otherwise.
     */
    public boolean isEndOfMibViewValue() {
        return (tag == SnmpDataTypeEnums.errEndOfMibViewTag);
    }

    // VARIABLES
    //----------
    /**
     * Name of the type.
     */
    final static String name = "Null" ;

    /**
     * This is the tag of the NULL value. By default, it is the universal tag value.
     */
    private int tag = 5 ;
}
