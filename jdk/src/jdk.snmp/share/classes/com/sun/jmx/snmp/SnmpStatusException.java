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
 * Reports an error which occurred during a get/set operation on a mib node.
 *
 * This exception includes a status error code as defined in the SNMP protocol.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpStatusException extends Exception implements SnmpDefinitions {
    private static final long serialVersionUID = 5809485694133115675L;

    /**
     * Error code as defined in RFC 1448 for: <CODE>noSuchName</CODE>.
     */
    public static final int noSuchName         = 2 ;

    /**
     * Error code as defined in RFC 1448 for: <CODE>badValue</CODE>.
     */
    public static final int badValue           = 3 ;

    /**
     * Error code as defined in RFC 1448 for: <CODE>readOnly</CODE>.
     */
    public static final int readOnly           = 4 ;


    /**
     * Error code as defined in RFC 1448 for: <CODE>noAccess</CODE>.
     */
    public static final int noAccess           = 6 ;

    /**
     * Error code for reporting a no such instance error.
     */
    public static final int noSuchInstance     = 0xE0;

    /**
     * Error code for reporting a no such object error.
     */
    public static final int noSuchObject     = 0xE1;

    /**
     * Constructs a new <CODE>SnmpStatusException</CODE> with the specified status error.
     * @param status The error status.
     */
    public SnmpStatusException(int status) {
        errorStatus = status ;
    }

    /**
     * Constructs a new <CODE>SnmpStatusException</CODE> with the specified status error and status index.
     * @param status The error status.
     * @param index The error index.
     */
    public SnmpStatusException(int status, int index) {
        errorStatus = status ;
        errorIndex = index ;
    }

    /**
     * Constructs a new <CODE>SnmpStatusException</CODE> with an error message.
     * The error status is set to 0 (noError) and the index to -1.
     * @param s The error message.
     */
    public SnmpStatusException(String s) {
        super(s);
    }

    /**
     * Constructs a new <CODE>SnmpStatusException</CODE> with an error index.
     * @param x The original <CODE>SnmpStatusException</CODE>.
     * @param index The error index.
     */
    public SnmpStatusException(SnmpStatusException x, int index) {
        super(x.getMessage());
        errorStatus= x.errorStatus;
        errorIndex= index;
    }

    /**
     * Return the error status.
     * @return The error status.
     */
    public int getStatus() {
        return errorStatus ;
    }

    /**
     * Returns the index of the error.
     * A value of -1 means that the index is not known/applicable.
     * @return The error index.
     */
    public int getErrorIndex() {
        return errorIndex;
    }


    // PRIVATE VARIABLES
    //--------------------

    /**
     * Status of the error.
     * @serial
     */
    private int errorStatus = 0 ;

    /**
     * Index of the error.
     * If different from -1, indicates the index where the error occurs.
     * @serial
     */
    private int errorIndex= -1;

}
