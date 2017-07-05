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


package com.sun.jmx.snmp.agent;



// jmx imports
//
import javax.management.Notification;
import javax.management.ObjectName;

/**
 * Represents a notification emitted when an
 * entry is added or deleted from an SNMP table.
 * <P>
 * The <CODE>SnmpTableEntryNotification</CODE> object contains
 * the reference to the entry added or removed from the table.
 * <P>
 * The list of notifications fired by the <CODE>SnmpMibTable</CODE> is
 * the following:
 * <UL>
 * <LI>A new entry has been added to the SNMP table.
 * <LI>An existing entry has been removed from the SNMP table.
  </UL>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpTableEntryNotification extends Notification {

    /**
     * Creates and initializes a table entry notification object.
     *
     * @param type The notification type.
     * @param source The notification producer.
     * @param sequenceNumber The notification sequence number within the
     *                  source object.
     * @param timeStamp The notification emission date.
     * @param entry     The entry object (may be null if the entry is
     *                  registered in the MBeanServer).
     * @param entryName The ObjectName entry object (may be null if the
     *                  entry is not registered in the MBeanServer).
     * @since 1.5
     */
    SnmpTableEntryNotification(String type, Object source,
                               long sequenceNumber, long timeStamp,
                               Object entry, ObjectName entryName) {

        super(type, source, sequenceNumber, timeStamp);
        this.entry = entry;
        this.name  = entryName;
    }

    /**
     * Gets the entry object.
     * May be null if the entry is registered in the MBeanServer, and the
     * MIB is using the generic MetaData (see mibgen).
     *
     * @return The entry.
     */
    public Object getEntry() {
        return entry;
    }

    /**
     * Gets the ObjectName of the entry.
     * May be null if the entry is not registered in the MBeanServer.
     *
     * @return The ObjectName of the entry.
     * @since 1.5
     */
    public ObjectName getEntryName() {
        return name;
    }

    // PUBLIC VARIABLES
    //-----------------

    /**
     * Notification type denoting that a new entry has been added to the
     * SNMP table.
     * <BR>The value of this notification type is
     * <CODE>jmx.snmp.table.entry.added</CODE>.
     */
    public static final String SNMP_ENTRY_ADDED =
        "jmx.snmp.table.entry.added";

    /**
     * Notification type denoting that an entry has been removed from the
     * SNMP table.
     * <BR>The value of this notification type is
     * <CODE>jmx.snmp.table.entry.removed</CODE>.
     */
    public static final String SNMP_ENTRY_REMOVED =
        "jmx.snmp.table.entry.removed";

    // PRIVATE VARIABLES
    //------------------

    /**
     * The entry object.
     * @serial
     */
    private final Object entry;

    /**
     * The entry name.
     * @serial
     * @since 1.5
     */
    private final ObjectName name;

    // Ensure compatibility
    //
    private static final long serialVersionUID = 5832592016227890252L;
}
