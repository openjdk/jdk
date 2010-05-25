/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.util.Hashtable;

import com.sun.jmx.snmp.SnmpValue;
import com.sun.jmx.snmp.SnmpInt;

import com.sun.jmx.snmp.Enumerated;

/**
 * This class is an internal class which is used to represent RowStatus
 * codes as defined in RFC 2579.
 *
 * It defines an additional code, <i>unspecified</i>, which is
 * implementation specific, and is used to identify
 * unspecified actions (when for instance the RowStatus variable
 * is not present in the varbind list) or uninitialized values.
 *
 * mibgen does not generate objects of this class but any variable
 * using the RowStatus textual convention can be converted into an
 * object of this class thanks to the
 * <code>EnumRowStatus(Enumerated valueIndex)</code> constructor.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 **/

public class EnumRowStatus extends Enumerated implements Serializable {
    private static final long serialVersionUID = 8966519271130162420L;

    /**
     * This value is SNMP Runtime implementation specific, and is used to identify
     * unspecified actions (when for instance the RowStatus variable
     * is not present in the varbind list) or uninitialized values.
     */
    public final static int unspecified   = 0;

    /**
     * This value corresponds to the <i>active</i> RowStatus, as defined in
     * RFC 2579 from SMIv2:
     * <ul>
     * <i>active</i> indicates that the conceptual row is available for
     * use by the managed device;
     * </ul>
     */
    public final static int active        = 1;

    /**
     * This value corresponds to the <i>notInService</i> RowStatus, as
     * defined in RFC 2579 from SMIv2:
     * <ul>
     * <i>notInService</i> indicates that the conceptual
     * row exists in the agent, but is unavailable for use by
     * the managed device; <i>notInService</i> has
     * no implication regarding the internal consistency of
     * the row, availability of resources, or consistency with
     * the current state of the managed device;
     * </ul>
     **/
    public final static int notInService  = 2;

    /**
     * This value corresponds to the <i>notReady</i> RowStatus, as defined
     * in RFC 2579 from SMIv2:
     * <ul>
     * <i>notReady</i> indicates that the conceptual row
     * exists in the agent, but is missing information
     * necessary in order to be available for use by the
     * managed device (i.e., one or more required columns in
     * the conceptual row have not been instantiated);
     * </ul>
     */
    public final static int notReady      = 3;

    /**
     * This value corresponds to the <i>createAndGo</i> RowStatus,
     * as defined in RFC 2579 from SMIv2:
     * <ul>
     * <i>createAndGo</i> is supplied by a management
     * station wishing to create a new instance of a
     * conceptual row and to have its status automatically set
     * to active, making it available for use by the managed
     * device;
     * </ul>
     */
    public final static int createAndGo   = 4;

    /**
     * This value corresponds to the <i>createAndWait</i> RowStatus,
     * as defined in RFC 2579 from SMIv2:
     * <ul>
     * <i>createAndWait</i> is supplied by a management
     * station wishing to create a new instance of a
     * conceptual row (but not make it available for use by
     * the managed device);
     * </ul>
     */
    public final static int createAndWait = 5;

    /**
     * This value corresponds to the <i>destroy</i> RowStatus, as defined in
     * RFC 2579 from SMIv2:
     * <ul>
     * <i>destroy</i> is supplied by a management station
     * wishing to delete all of the instances associated with
     * an existing conceptual row.
     * </ul>
     */
    public final static int destroy       = 6;

    /**
     * Build an <code>EnumRowStatus</code> from an <code>int</code>.
     * @param valueIndex should be either 0 (<i>unspecified</i>), or one of
     *        the values defined in RFC 2579.
     * @exception IllegalArgumentException if the given
     *            <code>valueIndex</code> is not valid.
     **/
    public EnumRowStatus(int valueIndex)
        throws IllegalArgumentException {
        super(valueIndex);
    }

    /**
     * Build an <code>EnumRowStatus</code> from an <code>Enumerated</code>.
     * @param valueIndex should be either 0 (<i>unspecified</i>), or one of
     *        the values defined in RFC 2579.
     * @exception IllegalArgumentException if the given
     *            <code>valueIndex</code> is not valid.
     **/
    public EnumRowStatus(Enumerated valueIndex)
        throws IllegalArgumentException {
        this(valueIndex.intValue());
    }

    /**
     * Build an <code>EnumRowStatus</code> from a <code>long</code>.
     * @param valueIndex should be either 0 (<i>unspecified</i>), or one of
     *        the values defined in RFC 2579.
     * @exception IllegalArgumentException if the given
     *            <code>valueIndex</code> is not valid.
     **/
    public EnumRowStatus(long valueIndex)
        throws IllegalArgumentException {
        this((int)valueIndex);
    }

    /**
     * Build an <code>EnumRowStatus</code> from an <code>Integer</code>.
     * @param valueIndex should be either 0 (<i>unspecified</i>), or one of
     *        the values defined in RFC 2579.
     * @exception IllegalArgumentException if the given
     *            <code>valueIndex</code> is not valid.
     **/
    public EnumRowStatus(Integer valueIndex)
        throws IllegalArgumentException {
        super(valueIndex);
    }

    /**
     * Build an <code>EnumRowStatus</code> from a <code>Long</code>.
     * @param valueIndex should be either 0 (<i>unspecified</i>), or one of
     *        the values defined in RFC 2579.
     * @exception IllegalArgumentException if the given
     *            <code>valueIndex</code> is not valid.
     **/
    public EnumRowStatus(Long valueIndex)
        throws IllegalArgumentException {
        this(valueIndex.longValue());
    }

    /**
     * Build an <code>EnumRowStatus</code> with <i>unspecified</i> value.
     **/
    public EnumRowStatus()
        throws IllegalArgumentException {
        this(unspecified);
    }

    /**
     * Build an <code>EnumRowStatus</code> from a <code>String</code>.
     * @param x should be either "unspecified", or one of
     *        the values defined in RFC 2579 ("active", "notReady", etc...)
     * @exception IllegalArgumentException if the given String
     *            <code>x</code> is not valid.
     **/
    public EnumRowStatus(String x)
        throws IllegalArgumentException {
        super(x);
    }

    /**
     * Build an <code>EnumRowStatus</code> from an <code>SnmpInt</code>.
     * @param valueIndex should be either 0 (<i>unspecified</i>), or one of
     *        the values defined in RFC 2579.
     * @exception IllegalArgumentException if the given
     *            <code>valueIndex</code> is not valid.
     **/
    public EnumRowStatus(SnmpInt valueIndex)
        throws IllegalArgumentException {
        this(valueIndex.intValue());
    }

    /**
     * Build an SnmpValue from this object.
     *
     * @exception IllegalArgumentException if this object holds an
     *            <i>unspecified</i> value.
     * @return an SnmpInt containing this object value.
     **/
    public SnmpInt toSnmpValue()
        throws IllegalArgumentException {
        if (value == unspecified)
            throw new
        IllegalArgumentException("`unspecified' is not a valid SNMP value.");
        return new SnmpInt(value);
    }

    /**
     * Check that the given <code>value</code> is valid.
     *
     * Valid values are:
     * <ul><li><i>unspecified(0)</i></li>
     *     <li><i>active(1)</i></li>
     *     <li><i>notInService(2)</i></li>
     *     <li><i>notReady(3)</i></li>
     *     <li><i>createAndGo(4)</i></li>
     *     <li><i>createAndWait(5)</i></li>
     *     <li><i>destroy(6)</i></li>
     * </ul>
     *
     **/
    static public boolean isValidValue(int value) {
        if (value < 0) return false;
        if (value > 6) return false;
        return true;
    }

    // Documented in Enumerated
    //
    protected Hashtable getIntTable() {
        return EnumRowStatus.getRSIntTable();
    }

    // Documented in Enumerated
    //
    protected Hashtable getStringTable() {
        return  EnumRowStatus.getRSStringTable();
    }

    static final Hashtable getRSIntTable() {
        return intTable ;
    }

    static final Hashtable getRSStringTable() {
        return stringTable ;
    }

    // Initialize the mapping tables.
    //
    final static Hashtable<Integer, String> intTable =
            new Hashtable<Integer, String>();
    final static Hashtable<String, Integer> stringTable =
            new Hashtable<String, Integer>();
    static  {
        intTable.put(new Integer(0), "unspecified");
        intTable.put(new Integer(3), "notReady");
        intTable.put(new Integer(6), "destroy");
        intTable.put(new Integer(2), "notInService");
        intTable.put(new Integer(5), "createAndWait");
        intTable.put(new Integer(1), "active");
        intTable.put(new Integer(4), "createAndGo");
        stringTable.put("unspecified", new Integer(0));
        stringTable.put("notReady", new Integer(3));
        stringTable.put("destroy", new Integer(6));
        stringTable.put("notInService", new Integer(2));
        stringTable.put("createAndWait", new Integer(5));
        stringTable.put("active", new Integer(1));
        stringTable.put("createAndGo", new Integer(4));
    }


}
