/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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



// java imports
//
import java.io.Serializable;
import java.util.Date;
import java.util.Vector;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;

// jmx imports
//
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.NotificationBroadcaster;
import javax.management.MBeanNotificationInfo;
import javax.management.ListenerNotFoundException;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpValue;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpStatusException;

/**
 * This class is an abstraction for an SNMP table.
 * It is the base class for implementing SNMP tables in the
 * MBean world.
 *
 * <p>
 * Its responsibility is to synchronize the MBean view of the table
 * (Table of entries) with the MIB view (array of OID indexes). Each
 * object of this class will be bound to the Metadata object which
 * manages the same SNMP Table within the MIB.
 * </p>
 *
 * <p>
 * For each table defined in a MIB, mibgen will generate a specific
 * class called Table<i>TableName</i> that will subclass this class, and
 * a corresponding <i>TableName</i>Meta class extending SnmpMibTable
 * and corresponding to the MIB view of the same table.
 * </p>
 *
 * <p>
 * Objects of this class are instantiated by MBeans representing
 * the SNMP Group to which the table belong.
 * </p>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @see com.sun.jmx.snmp.agent.SnmpTableEntryFactory
 * @see com.sun.jmx.snmp.agent.SnmpMibTable
 *
 */
public abstract class SnmpTableSupport implements SnmpTableEntryFactory,
// NPCTE fix for bugId 4499265, esc 0, MR 04 sept 2001
//  SnmpTableCallbackHandler {
    SnmpTableCallbackHandler, Serializable {
// end of NPCTE fix for bugId 4499265

    //-----------------------------------------------------------------
    //
    //  Protected Variables
    //
    //-----------------------------------------------------------------

    /**
     * The list of entries
     **/
    protected List<Object> entries;

    /**
     * The associated metadata object
     **/
    protected SnmpMibTable meta;

    /**
     * The MIB to which this table belongs
     **/
    protected SnmpMib      theMib;

    //-----------------------------------------------------------------
    //
    //  Private Variables
    //
    //-----------------------------------------------------------------

    /**
     * This variable is initialized while binding this object to its
     * corresponding meta object.
     **/
    private boolean registrationRequired = false;



    //-----------------------------------------------------------------
    //
    //  Constructor
    //
    //-----------------------------------------------------------------

    /**
     * Initializes the table.
     * The steps are these:
     * <ul><li> allocate an array for storing entry object,</li>
     *     <li> retrieve the corresponding metadata object
     *          from the MIB,
     *     <li> bind this object to the corresponding metadata object
     *          from the MIB.</li>
     * </ul>
     *
     * @param mib The MIB to which this table belong.
     *
     **/
    protected SnmpTableSupport(SnmpMib mib) {
        theMib  = mib;
        meta    = getRegisteredTableMeta(mib);
        bindWithTableMeta();
        entries = allocateTable();
    }


    //-----------------------------------------------------------------
    //
    //  Implementation of the SnmpTableEntryFactory interface
    //
    //-----------------------------------------------------------------

    /**
     * Creates a new entry in the table.
     *
     * This factory method is generated by mibgen and used internally.
     * It is part of the
     * {@link com.sun.jmx.snmp.agent.SnmpTableEntryFactory} interface.
     * You may subclass this method to implement any specific behaviour
     * your application requires.
     *
     * @exception SnmpStatusException if the entry cannot be created.
     **/
    public abstract void createNewEntry(SnmpMibSubRequest request,
                                        SnmpOid rowOid, int depth,
                                        SnmpMibTable meta)
        throws SnmpStatusException;


    //-----------------------------------------------------------------
    //
    //  Public methods
    //
    //-----------------------------------------------------------------

    /**
     * Returns the entry located at the given position in the table.
     *
     * @return The entry located at the given position, <code>null</code>
     *         if no entry can be found at this position.
     **/
    // XXXX xxxx zzz ZZZZ => public? or protected?
    public Object getEntry(int pos) {
        if (entries == null) return null;
        return entries.get(pos);
    }

    /**
     * Returns the number of entries registered in the table.
     *
     * @return The number of entries registered in the table.
     **/
    public int getSize() {
        return meta.getSize();
    }

    /**
     * This method lets you dynamically switch the creation policy.
     *
     * <CODE>setCreationEnabled()</CODE> will switch the policy of
     *      remote entry creation via SET operations, by calling
     *      <code>setCreationEnabled()</code> on the metadata object
     *      associated with this table.
     * <BR> By default remote entry creation via SET operation is disabled.
     *
     * @param remoteCreationFlag Tells whether remote entry creation must
     *        be enabled or disabled.
     * <li>
     * <CODE>setCreationEnabled(true)</CODE> will enable remote entry
     *      creation via SET operations.</li>
     * <li>
     * <CODE>setCreationEnabled(false)</CODE> will disable remote entry
     *      creation via SET operations.</li>
     * <p> By default remote entry creation via SET operation is disabled.
     * </p>
     *
     * @see com.sun.jmx.snmp.agent.SnmpMibTable
     *
     **/
    public void setCreationEnabled(boolean remoteCreationFlag) {
        meta.setCreationEnabled(remoteCreationFlag);
    }

    /**
     * Tells whether a new entry should be created when a SET operation
     * is received for an entry that does not exist yet.
     * This method calls <code>isCreationEnabled()</code> on the metadata
     * object associated with this table.
     *
     * @return true if a new entry must be created, false otherwise.<br>
     *         [default: returns <CODE>false</CODE>]
     *
     * @see com.sun.jmx.snmp.agent.SnmpMibTable
     **/
    public boolean isCreationEnabled() {
        return meta.isCreationEnabled();
    }

    /**
     * Tells whether the metadata object to which this table is linked
     * requires entries to be registered. In this case passing an
     * ObjectName when registering entries will be mandatory.
     *
     * @return <code>true</code> if the associated metadata requires entries
     *         to be registered (mibgen generated generic metadata).
     **/
    public boolean isRegistrationRequired() {
        return registrationRequired;
    }

    /**
     * Builds an entry SnmpIndex from its row OID.
     *
     * This method is generated by mibgen and used internally.
     *
     * @param rowOid The SnmpOid object identifying a table entry.
     *
     * @return The SnmpIndex of the entry identified by <code>rowOid</code>.
     *
     * @exception SnmpStatusException if the index cannot be built from the
     *            given OID.
     **/
    public SnmpIndex buildSnmpIndex(SnmpOid rowOid)
        throws SnmpStatusException {
        return buildSnmpIndex(rowOid.longValue(false), 0);
    }

    /**
     * Builds an SnmpOid from an SnmpIndex object.
     *
     * This method is generated by mibgen and used internally.
     *
     * @param index An SnmpIndex object identifying a table entry.
     *
     * @return The SnmpOid form of the given entry index.
     *
     * @exception SnmpStatusException if the given index is not valid.
     **/
    public abstract SnmpOid buildOidFromIndex(SnmpIndex index)
        throws SnmpStatusException;

    /**
     * Builds the default ObjectName of an entry from the SnmpIndex
     * identifying this entry. No access is made on the entry itself.
     *
     * This method is generated by mibgen and used internally.
     * You can subclass this method if you want to change the default
     * ObjectName policy. This is only meaningfull when entries
     * are registered MBeans.
     *
     * @param index The SnmpIndex identifying the entry from which we
     *              want to build the default ObjectName.
     *
     * @return The default ObjectName for the entry identified by
     *         the given index.
     *
     * @exception SnmpStatusException if the given index is not valid.
     **/
    public abstract ObjectName buildNameFromIndex(SnmpIndex index)
        throws SnmpStatusException;


    //-----------------------------------------------------------------
    //
    //  Implementation of the SnmpTableEntryFactory interface
    //
    //-----------------------------------------------------------------

    /**
     * This callback is called by  the associated metadata object
     * when a new table entry has been registered in the
     * table metadata.
     *
     * This method will update the <code>entries</code> list.
     *
     * @param pos   The position at which the new entry was inserted
     *              in the table.
     * @param row   The row OID of the new entry
     * @param name  The ObjectName of the new entry (as specified by the
     *              factory)
     * @param entry The new entry (as returned by the factory)
     * @param meta  The table metadata object.
     *
     **/
    public void addEntryCb(int pos, SnmpOid row, ObjectName name,
                           Object entry, SnmpMibTable meta)
        throws SnmpStatusException {
        try {
            if (entries != null) entries.add(pos,entry);
        } catch (Exception e) {
            throw new SnmpStatusException(SnmpStatusException.noSuchName);
        }
    }

    /**
     * This callback is called by  the associated metadata object
     * when a new table entry has been removed from the
     * table metadata.
     *
     * This method will update the <code>entries</code> list.
     *
     * @param pos   The position from which the entry was deleted
     * @param row   The row OID of the deleted entry
     * @param name  The ObjectName of the deleted entry (may be null if
     *              ObjectName's were not required)
     * @param entry The deleted entry (may be null if only ObjectName's
     *              were required)
     * @param meta  The table metadata object.
     *
     **/
    public void removeEntryCb(int pos, SnmpOid row, ObjectName name,
                              Object entry, SnmpMibTable meta)
        throws SnmpStatusException {
        try {
            if (entries != null) entries.remove(pos);
        } catch (Exception e) {
        }
    }



    /**
     * Enables to add an SNMP entry listener to this
     * <CODE>SnmpMibTable</CODE>.
     *
     * @param listener The listener object which will handle the
     *    notifications emitted by the registered MBean.
     *
     * @param filter The filter object. If filter is null, no filtering
     *    will be performed before handling notifications.
     *
     * @param handback The context to be sent to the listener when a
     *    notification is emitted.
     *
     * @exception IllegalArgumentException Listener parameter is null.
     */
    public void
        addNotificationListener(NotificationListener listener,
                                NotificationFilter filter, Object handback) {
        meta.addNotificationListener(listener,filter,handback);
    }

    /**
     * Enables to remove an SNMP entry listener from this
     * <CODE>SnmpMibTable</CODE>.
     *
     * @param listener The listener object which will handle the
     *    notifications emitted by the registered MBean.
     *    This method will remove all the information related to this
     *    listener.
     *
     * @exception ListenerNotFoundException The listener is not registered
     *    in the MBean.
     */
    public synchronized void
        removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
        meta.removeNotificationListener(listener);
    }

    /**
     * Returns a <CODE>NotificationInfo</CODE> object containing the
     * notification class and the notification type sent by the
     * <CODE>SnmpMibTable</CODE>.
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
        return meta.getNotificationInfo();
    }

    //-----------------------------------------------------------------
    //
    //  Protected Abstract methods
    //
    //-----------------------------------------------------------------

    /**
     * Builds an SnmpIndex object from the index part of an OID.
     *
     * This method is generated by mibgen and used internally.
     *
     * @param oid The OID from which to build the index, represented
     *        as an array of long.
     * @param start The position where to start from in the OID array.
     *
     * @return The SnmpOid form of the given entry index.
     *
     * @exception SnmpStatusException if the given index is not valid.
     **/
    protected abstract SnmpIndex buildSnmpIndex(long oid[], int start )
        throws SnmpStatusException;

    /**
     * Returns the metadata object associated with this table.
     *
     * This method is generated by mibgen and used internally.
     *
     * @param mib The SnmpMib object holding the Metadata corresponding
     *            to this table.
     *
     * @return The metadata object associated with this table.
     *         Returns <code>null</code> if this implementation of the
     *         MIB doesn't support this table.
     **/
    protected abstract SnmpMibTable getRegisteredTableMeta(SnmpMib mib);


    //-----------------------------------------------------------------
    //
    //  Protected methods
    //
    //-----------------------------------------------------------------

    /**
     * Allocates an ArrayList for storing table entries.
     *
     * This method is called within the constructor at object creation.
     * Any object implementing the {@link java.util.List} interface can
     * be used.
     *
     * @return A new list in which to store entries. If <code>null</code>
     *         is returned then no entry will be stored in the list
     *         and getEntry() will always return null.
     **/
    protected List<Object> allocateTable() {
        return new ArrayList<Object>();
    }

    /**
     * Add an entry in this table.
     *
     * This method registers an entry in the table and perform
     * synchronization with the associated table metadata object.
     *
     * This method assumes that the given entry will not be registered,
     * or will be registered with its default ObjectName built from the
     * associated  SnmpIndex.
     * <p>
     * If the entry is going to be registered, then
     * {@link com.sun.jmx.snmp.agent.SnmpTableSupport#addEntry(SnmpIndex, ObjectName, Object)} should be preferred.
     * <br> This function is mainly provided for backward compatibility.
     *
     * @param index The SnmpIndex built from the given entry.
     * @param entry The entry that should be added in the table.
     *
     * @exception SnmpStatusException if the entry cannot be registered with
     *            the given index.
     **/
    protected void addEntry(SnmpIndex index, Object entry)
        throws SnmpStatusException {
        SnmpOid oid = buildOidFromIndex(index);
        ObjectName name = null;
        if (isRegistrationRequired()) {
            name = buildNameFromIndex(index);
        }
        meta.addEntry(oid,name,entry);
    }

    /**
     * Add an entry in this table.
     *
     * This method registers an entry in the table and performs
     * synchronization with the associated table metadata object.
     *
     * @param index The SnmpIndex built from the given entry.
     * @param name  The ObjectName with which this entry will be registered.
     * @param entry The entry that should be added in the table.
     *
     * @exception SnmpStatusException if the entry cannot be registered with
     *            the given index.
     **/
    protected void addEntry(SnmpIndex index, ObjectName name, Object entry)
        throws SnmpStatusException {
        SnmpOid oid = buildOidFromIndex(index);
        meta.addEntry(oid,name,entry);
    }

    /**
     * Remove an entry from this table.
     *
     * This method unregisters an entry from the table and performs
     * synchronization with the associated table metadata object.
     *
     * @param index The SnmpIndex identifying the entry.
     * @param entry The entry that should be removed in the table. This
     *              parameter is optional and can be omitted if it doesn't
     *              need to be passed along to the
     *              <code>removeEntryCb()</code> callback defined in the
     *              {@link com.sun.jmx.snmp.agent.SnmpTableCallbackHandler}
     *              interface.
     *
     * @exception SnmpStatusException if the entry cannot be unregistered.
     **/
    protected void removeEntry(SnmpIndex index, Object entry)
        throws SnmpStatusException {
        SnmpOid oid = buildOidFromIndex(index);
        meta.removeEntry(oid,entry);
    }

    // protected void removeEntry(ObjectName name, Object entry)
    //  throws SnmpStatusException {
    //  meta.removeEntry(name,entry);
    // }

    /**
     * Returns the entries in the table.
     *
     * @return An Object[] array containing the entries registered in the
     *         table.
     **/
    protected Object[] getBasicEntries() {
        if (entries == null) return null;
        Object[] array= new Object[entries.size()];
        entries.toArray(array);
        return array;
    }

    /**
     * Binds this table with its associated metadata, registering itself
     * as an SnmpTableEntryFactory.
     **/
    protected void bindWithTableMeta() {
        if (meta == null) return;
        registrationRequired = meta.isRegistrationRequired();
        meta.registerEntryFactory(this);
    }

}
