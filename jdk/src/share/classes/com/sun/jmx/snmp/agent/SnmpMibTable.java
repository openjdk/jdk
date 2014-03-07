/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import static com.sun.jmx.defaults.JmxProperties.SNMP_ADAPTOR_LOGGER;
import com.sun.jmx.snmp.EnumRowStatus;
import com.sun.jmx.snmp.SnmpInt;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpValue;
import com.sun.jmx.snmp.SnmpVarBind;

/**
 * This class is the base class for SNMP table metadata.
 * <p>
 * Its responsibility is to manage a sorted array of OID indexes
 * according to the SNMP indexing scheme over the "real" table.
 * Each object of this class can be bound to an
 * {@link com.sun.jmx.snmp.agent.SnmpTableEntryFactory} to which it will
 * forward remote entry creation requests, and invoke callbacks
 * when an entry has been successfully added to / removed from
 * the OID index array.
 * </p>
 *
 * <p>
 * For each table defined in the MIB, mibgen will generate a specific
 * class called Table<i>TableName</i> that will implement the
 * SnmpTableEntryFactory interface, and a corresponding
 * <i>TableName</i>Meta class that will extend this class. <br>
 * The Table<i>TableName</i> class corresponds to the MBean view of the
 * table while the <i>TableName</i>Meta class corresponds to the
 * MIB metadata view of the same table.
 * </p>
 *
 * <p>
 * Objects of this class are instantiated by the generated
 * whole MIB class extending {@link com.sun.jmx.snmp.agent.SnmpMib}
 * You should never need to instantiate this class directly.
 * </p>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @see com.sun.jmx.snmp.agent.SnmpMib
 * @see com.sun.jmx.snmp.agent.SnmpMibEntry
 * @see com.sun.jmx.snmp.agent.SnmpTableEntryFactory
 * @see com.sun.jmx.snmp.agent.SnmpTableSupport
 *
 */

public abstract class SnmpMibTable extends SnmpMibNode
    implements NotificationBroadcaster, Serializable {

    /**
     * Create a new <CODE>SnmpMibTable</CODE> metadata node.
     *
     * <p>
     * @param mib The SNMP MIB to which the metadata will be linked.
     */
    public SnmpMibTable(SnmpMib mib) {
        this.theMib= mib;
        setCreationEnabled(false);
    }

    // -------------------------------------------------------------------
    // PUBLIC METHODS
    // -------------------------------------------------------------------

    /**
     * This method is invoked when the creation of a new entry is requested
     * by a remote SNMP manager.
     * <br>By default, remote entry creation is disabled - and this method
     * will not be called. You can dynamically switch the entry creation
     * policy by calling <code>setCreationEnabled(true)</code> and <code>
     * setCreationEnabled(false)</code> on this object.
     * <p><b><i>
     * This method is called internally by the SNMP runtime and you
     * should never need to call it directly. </b></i>However you might want
     * to extend it in order to implement your own specific application
     * behaviour, should the default behaviour not be at your convenience.
     * </p>
     * <p>
     * @param req   The SNMP  subrequest requesting this creation
     * @param rowOid  The OID indexing the conceptual row (entry) for which
     *                the creation was requested.
     * @param depth The position of the columnar object arc in the OIDs
     *              from the varbind list.
     *
     * @exception SnmpStatusException if the entry cannot be created.
     */
    public abstract void createNewEntry(SnmpMibSubRequest req, SnmpOid rowOid,
                                        int depth)
        throws SnmpStatusException;

    /**
     * Tell whether the specific version of this metadata generated
     * by <code>mibgen</code> requires entries to be registered with
     * the MBeanServer. In this case an ObjectName will have to be
     * passed to addEntry() in order for the table to behave correctly
     * (case of the generic metadata).
     * <p>
     * If that version of the metadata does not require entry to be
     * registered, then passing an ObjectName becomes optional (null
     * can be passed instead).
     *
     * @return <code>true</code> if registration is required by this
     *         version of the metadata.
     */
    public abstract boolean isRegistrationRequired();

    /**
     * Tell whether a new entry should be created when a SET operation
     * is received for an entry that does not exist yet.
     *
     * @return true if a new entry must be created, false otherwise.<br>
     *         [default: returns <CODE>false</CODE>]
     **/
    public boolean isCreationEnabled() {
        return creationEnabled;
    }

    /**
     * This method lets you dynamically switch the creation policy.
     *
     * <p>
     * @param remoteCreationFlag Tells whether remote entry creation must
     *        be enabled or disabled.
     * <ul><li>
     * <CODE>setCreationEnabled(true)</CODE> will enable remote entry
     *      creation via SET operations.</li>
     * <li>
     * <CODE>setCreationEnabled(false)</CODE> will disable remote entry
     *      creation via SET operations.</li>
     * <p> By default remote entry creation via SET operation is disabled.
     * </p>
     * </ul>
     **/
    public void setCreationEnabled(boolean remoteCreationFlag) {
        creationEnabled = remoteCreationFlag;
    }

    /**
     * Return <code>true</code> if the conceptual row contains a columnar
     * object used to control creation/deletion of rows in this table.
     * <p>
     * This  columnar object can be either a variable with RowStatus
     * syntax as defined by RFC 2579, or a plain variable whose
     * semantics is table specific.
     * <p>
     * By default, this function returns <code>false</code>, and it is
     * assumed that the table has no such control variable.<br>
     * When <code>mibgen</code> is used over SMIv2 MIBs, it will generate
     * an <code>hasRowStatus()</code> method returning <code>true</code>
     * for each table containing an object with RowStatus syntax.
     * <p>
     * When this method returns <code>false</code> the default mechanism
     * for remote entry creation is used.
     * Otherwise, creation/deletion is performed as specified
     * by the control variable (see getRowAction() for more details).
     * <p>
     * This method is called internally when a SET request involving
     * this table is processed.
     * <p>
     * If you need to implement a control variable which do not use
     * the RowStatus convention as defined by RFC 2579, you should
     * subclass the generated table metadata class in order to redefine
     * this method and make it returns <code>true</code>.<br>
     * You will then have to redefine the isRowStatus(), mapRowStatus(),
     * isRowReady(), and setRowStatus() methods to suit your specific
     * implementation.
     * <p>
     * @return <li><code>true</code> if this table contains a control
     *         variable (eg: a variable with RFC 2579 RowStatus syntax),
     *         </li>
     *         <li><code>false</code> if this table does not contain
     *         any control variable.</li>
     *
     **/
    public boolean hasRowStatus() {
        return false;
    }

    // ---------------------------------------------------------------------
    //
    // Implements the method defined in SnmpMibNode.
    //
    // ---------------------------------------------------------------------
    /**
     * Generic handling of the <CODE>get</CODE> operation.
     * <p> The default implementation of this method is to
     * <ul>
     * <li> check whether the entry exists, and if not register an
     *      exception for each varbind in the list.
     * <li> call the generated
     *      <CODE>get(req,oid,depth+1)</CODE> method. </li>
     * </ul>
     * <p>
     * <pre>
     * public void get(SnmpMibSubRequest req, int depth)
     *    throws SnmpStatusException {
     *    boolean         isnew  = req.isNewEntry();
     *
     *    // if the entry does not exists, then registers an error for
     *    // each varbind involved (nb: this should not happen, since
     *    // the error should already have been detected earlier)
     *    //
     *    if (isnew) {
     *        SnmpVarBind     var = null;
     *        for (Enumeration e= req.getElements(); e.hasMoreElements();) {
     *            var = (SnmpVarBind) e.nextElement();
     *            req.registerGetException(var,noSuchNameException);
     *        }
     *    }
     *
     *    final SnmpOid oid = req.getEntryOid();
     *    get(req,oid,depth+1);
     * }
     * </pre>
     * <p> You should not need to override this method in any cases, because
     * it will eventually call
     * <CODE>get(SnmpMibSubRequest req, int depth)</CODE> on the generated
     * derivative of <CODE>SnmpMibEntry</CODE>. If you need to implement
     * specific policies for minimizing the accesses made to some remote
     * underlying resources, or if you need to implement some consistency
     * checks between the different values provided in the varbind list,
     * you should then rather override
     * <CODE>get(SnmpMibSubRequest req, int depth)</CODE> on the generated
     * derivative of <CODE>SnmpMibEntry</CODE>.
     * <p>
     *
     */
    @Override
    public void get(SnmpMibSubRequest req, int depth)
        throws SnmpStatusException {

        final boolean         isnew  = req.isNewEntry();
        final SnmpMibSubRequest  r      = req;

        // if the entry does not exists, then registers an error for
        // each varbind involved (nb: should not happen, the error
        // should have been registered earlier)
        if (isnew) {
            SnmpVarBind var;
            for (Enumeration<SnmpVarBind> e= r.getElements(); e.hasMoreElements();) {
                var = e.nextElement();
                r.registerGetException(var,new SnmpStatusException(SnmpStatusException.noSuchInstance));
            }
        }

        final SnmpOid     oid    = r.getEntryOid();

        // SnmpIndex   index  = buildSnmpIndex(oid.longValue(false), 0);
        // get(req,index,depth+1);
        //
        get(req,oid,depth+1);
    }

    // ---------------------------------------------------------------------
    //
    // Implements the method defined in SnmpMibNode.
    //
    // ---------------------------------------------------------------------
    /**
     * Generic handling of the <CODE>check</CODE> operation.
     * <p> The default implementation of this method is to
     * <ul>
     * <li> check whether a new entry must be created, and if remote
     *      creation of entries is enabled, create it. </li>
     * <li> call the generated
     *      <CODE>check(req,oid,depth+1)</CODE> method. </li>
     * </ul>
     * <p>
     * <pre>
     * public void check(SnmpMibSubRequest req, int depth)
     *    throws SnmpStatusException {
     *    final SnmpOid     oid    = req.getEntryOid();
     *    final int         action = getRowAction(req,oid,depth+1);
     *
     *    beginRowAction(req,oid,depth+1,action);
     *    check(req,oid,depth+1);
     * }
     * </pre>
     * <p> You should not need to override this method in any cases, because
     * it will eventually call
     * <CODE>check(SnmpMibSubRequest req, int depth)</CODE> on the generated
     * derivative of <CODE>SnmpMibEntry</CODE>. If you need to implement
     * specific policies for minimizing the accesses made to some remote
     * underlying resources, or if you need to implement some consistency
     * checks between the different values provided in the varbind list,
     * you should then rather override
     * <CODE>check(SnmpMibSubRequest req, int depth)</CODE> on the generated
     * derivative of <CODE>SnmpMibEntry</CODE>.
     * <p>
     *
     */
    @Override
    public void check(SnmpMibSubRequest req, int depth)
        throws SnmpStatusException {
        final SnmpOid     oid    = req.getEntryOid();
        final int         action = getRowAction(req,oid,depth+1);

        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpMibTable.class.getName(),
                    "check", "Calling beginRowAction");
        }

        beginRowAction(req,oid,depth+1,action);

        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpMibTable.class.getName(),
                    "check",
                    "Calling check for " + req.getSize() + " varbinds");
        }

        check(req,oid,depth+1);

        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpMibTable.class.getName(),
                    "check", "check finished");
        }
    }

    // ---------------------------------------------------------------------
    //
    // Implements the method defined in SnmpMibNode.
    //
    // ---------------------------------------------------------------------
    /**
     * Generic handling of the <CODE>set</CODE> operation.
     * <p> The default implementation of this method is to
     * call the generated
     * <CODE>set(req,oid,depth+1)</CODE> method.
     * <p>
     * <pre>
     * public void set(SnmpMibSubRequest req, int depth)
     *    throws SnmpStatusException {
     *    final SnmpOid oid = req.getEntryOid();
     *    final int  action = getRowAction(req,oid,depth+1);
     *
     *    set(req,oid,depth+1);
     *    endRowAction(req,oid,depth+1,action);
     * }
     * </pre>
     * <p> You should not need to override this method in any cases, because
     * it will eventually call
     * <CODE>set(SnmpMibSubRequest req, int depth)</CODE> on the generated
     * derivative of <CODE>SnmpMibEntry</CODE>. If you need to implement
     * specific policies for minimizing the accesses made to some remote
     * underlying resources, or if you need to implement some consistency
     * checks between the different values provided in the varbind list,
     * you should then rather override
     * <CODE>set(SnmpMibSubRequest req, int depth)</CODE> on the generated
     * derivative of <CODE>SnmpMibEntry</CODE>.
     * <p>
     *
     */
    @Override
    public void set(SnmpMibSubRequest req, int depth)
        throws SnmpStatusException {


        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpMibTable.class.getName(),
                    "set", "Entering set");
        }

        final SnmpOid     oid    = req.getEntryOid();
        final int         action = getRowAction(req,oid,depth+1);

        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpMibTable.class.getName(),
                    "set", "Calling set for " + req.getSize() + " varbinds");
        }

        set(req,oid,depth+1);

        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpMibTable.class.getName(),
                    "set", "Calling endRowAction");
        }

        endRowAction(req,oid,depth+1,action);

        if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_ADAPTOR_LOGGER.logp(Level.FINEST, SnmpMibTable.class.getName(),
                    "set", "RowAction finished");
        }

    }

    /**
     * Add a new entry in this <CODE>SnmpMibTable</CODE>.
     * Also triggers the addEntryCB() callback of the
     * {@link com.sun.jmx.snmp.agent.SnmpTableEntryFactory} interface
     * if this node is bound to a factory.
     *
     * This method assumes that the given entry will not be registered.
     * If the entry is going to be registered, or if ObjectName's are
     * required, then
     * {@link com.sun.jmx.snmp.agent.SnmpMibTable#addEntry(SnmpOid,
     * ObjectName, Object)} should be preferred.
     * <br> This function is mainly provided for backward compatibility.
     *
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row to be added.
     * @param entry The entry to add.
     *
     * @exception SnmpStatusException The entry couldn't be added
     *            at the position identified by the given
     *            <code>rowOid</code>, or this version of the metadata
     *            requires ObjectName's.
     */
     // public void addEntry(SnmpIndex index, Object entry)
     public void addEntry(SnmpOid rowOid, Object entry)
        throws SnmpStatusException {

         addEntry(rowOid, null, entry);
    }

    /**
     * Add a new entry in this <CODE>SnmpMibTable</CODE>.
     * Also triggers the addEntryCB() callback of the
     * {@link com.sun.jmx.snmp.agent.SnmpTableEntryFactory} interface
     * if this node is bound to a factory.
     *
     * <p>
     * @param oid    The <CODE>SnmpOid</CODE> identifying the table
     *               row to be added.
     *
     * @param name  The ObjectName with which this entry is registered.
     *              This parameter can be omitted if isRegistrationRequired()
     *              return false.
     *
     * @param entry The entry to add.
     *
     * @exception SnmpStatusException The entry couldn't be added
     *            at the position identified by the given
     *            <code>rowOid</code>, or if this version of the metadata
     *            requires ObjectName's, and the given name is null.
     */
    // protected synchronized void addEntry(SnmpIndex index, ObjectName name,
    //                                      Object entry)
    public synchronized void addEntry(SnmpOid oid, ObjectName name,
                                      Object entry)
        throws SnmpStatusException {

        if (isRegistrationRequired() == true && name == null)
            throw new SnmpStatusException(SnmpStatusException.badValue);

        if (size == 0) {
            //            indexes.addElement(index);
            // XX oids.addElement(oid);
            insertOid(0,oid);
            if (entries != null)
                entries.addElement(entry);
            if (entrynames != null)
                entrynames.addElement(name);
            size++;

            // triggers callbacks on the entry factory
            //
            if (factory != null) {
                try {
                    factory.addEntryCb(0,oid,name,entry,this);
                } catch (SnmpStatusException x) {
                    removeOid(0);
                    if (entries != null)
                        entries.removeElementAt(0);
                    if (entrynames != null)
                        entrynames.removeElementAt(0);
                    throw x;
                }
            }

            // sends the notifications
            //
            sendNotification(SnmpTableEntryNotification.SNMP_ENTRY_ADDED,
                             (new Date()).getTime(), entry, name);
            return;
        }

        // Get the insertion position ...
        //
        int pos= 0;
        // bug jaw.00356.B : use oid rather than index to get the
        // insertion point.
        //
        pos= getInsertionPoint(oid,true);
        if (pos == size) {
            // Add a new element in the vectors ...
            //
            //            indexes.addElement(index);
            // XX oids.addElement(oid);
            insertOid(tablecount,oid);
            if (entries != null)
                entries.addElement(entry);
            if (entrynames != null)
                entrynames.addElement(name);
            size++;
        } else {
            // Insert new element ...
            //
            try {
                //                indexes.insertElementAt(index, pos);
                // XX oids.insertElementAt(oid, pos);
                insertOid(pos,oid);
                if (entries != null)
                    entries.insertElementAt(entry, pos);
                if (entrynames != null)
                    entrynames.insertElementAt(name,pos);
                size++;
            } catch(ArrayIndexOutOfBoundsException e) {
            }
        }

        // triggers callbacks on the entry factory
        //
        if (factory != null) {
            try {
                factory.addEntryCb(pos,oid,name,entry,this);
            } catch (SnmpStatusException x) {
                removeOid(pos);
                if (entries != null)
                    entries.removeElementAt(pos);
                if (entrynames != null)
                    entrynames.removeElementAt(pos);
                throw x;
            }
        }

        // sends the notifications
        //
        sendNotification(SnmpTableEntryNotification.SNMP_ENTRY_ADDED,
                         (new Date()).getTime(), entry, name);
    }

    /**
     * Remove the specified entry from the table.
     * Also triggers the removeEntryCB() callback of the
     * {@link com.sun.jmx.snmp.agent.SnmpTableEntryFactory} interface
     * if this node is bound to a factory.
     *
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row to remove.
     *
     * @param entry The entry to be removed. This parameter is not used
     *              internally, it is simply passed along to the
     *              removeEntryCB() callback.
     *
     * @exception SnmpStatusException if the specified entry couldn't
     *            be removed (if the given <code>rowOid</code> is not
     *            valid for instance).
     */
    public synchronized void removeEntry(SnmpOid rowOid, Object entry)
        throws SnmpStatusException {
        int pos = findObject(rowOid);
        if (pos == -1)
            return;
        removeEntry(pos,entry);
    }

    /**
     * Remove the specified entry from the table.
     * Also triggers the removeEntryCB() callback of the
     * {@link com.sun.jmx.snmp.agent.SnmpTableEntryFactory} interface
     * if this node is bound to a factory.
     *
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row to remove.
     *
     * @exception SnmpStatusException if the specified entry couldn't
     *            be removed (if the given <code>rowOid</code> is not
     *            valid for instance).
     */
    public void removeEntry(SnmpOid rowOid)
        throws SnmpStatusException {
        int pos = findObject(rowOid);
        if (pos == -1)
            return;
        removeEntry(pos,null);
    }

    /**
     * Remove the specified entry from the table.
     * Also triggers the removeEntryCB() callback of the
     * {@link com.sun.jmx.snmp.agent.SnmpTableEntryFactory} interface
     * if this node is bound to a factory.
     *
     * <p>
     * @param pos The position of the entry in the table.
     *
     * @param entry The entry to be removed. This parameter is not used
     *              internally, it is simply passed along to the
     *              removeEntryCB() callback.
     *
     * @exception SnmpStatusException if the specified entry couldn't
     *            be removed.
     */
    public synchronized void removeEntry(int pos, Object entry)
        throws SnmpStatusException {
        if (pos == -1)
            return;
        if (pos >= size) return;

        Object obj = entry;
        if (entries != null && entries.size() > pos) {
            obj = entries.elementAt(pos);
            entries.removeElementAt(pos);
        }

        ObjectName name = null;
        if (entrynames != null && entrynames.size() > pos) {
            name = entrynames.elementAt(pos);
            entrynames.removeElementAt(pos);
        }

        final SnmpOid rowOid = tableoids[pos];
        removeOid(pos);
        size --;

        if (obj == null) obj = entry;

        if (factory != null)
            factory.removeEntryCb(pos,rowOid,name,obj,this);

        sendNotification(SnmpTableEntryNotification.SNMP_ENTRY_REMOVED,
                         (new Date()).getTime(), obj, name);
    }

    /**
     * Get the entry corresponding to the specified rowOid.
     *
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the
     *        row to be retrieved.
     *
     * @return The entry.
     *
     * @exception SnmpStatusException There is no entry with the specified
     *      <code>rowOid</code> in the table.
     */
    public synchronized Object getEntry(SnmpOid rowOid)
        throws SnmpStatusException {
        int pos= findObject(rowOid);
        if (pos == -1)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        return entries.elementAt(pos);
    }

    /**
     * Get the ObjectName of the entry corresponding to the
     * specified rowOid.
     * The result of this method is only meaningful if
     * isRegistrationRequired() yields true.
     *
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *        row whose ObjectName we want to retrieve.
     *
     * @return The object name of the entry.
     *
     * @exception SnmpStatusException There is no entry with the specified
     *      <code>rowOid</code> in the table.
     */
    public synchronized ObjectName getEntryName(SnmpOid rowOid)
        throws SnmpStatusException {
        int pos = findObject(rowOid);
        if (entrynames == null) return null;
        if (pos == -1 || pos >= entrynames.size())
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        return entrynames.elementAt(pos);
    }

    /**
     * Return the entries stored in this table <CODE>SnmpMibTable</CODE>.
     * <p>
     * If the subclass generated by mibgen uses the generic way to access
     * the entries (i.e. if it goes through the MBeanServer) then some of
     * the entries may be <code>null</code>. It all depends whether a non
     * <code>null</code> entry was passed to addEntry().<br>
     * Otherwise, if it uses the standard way (access the entry directly
     * through their standard MBean interface) this array will contain all
     * the entries.
     * <p>
     * @return The entries array.
     */
    public Object[] getBasicEntries() {
        Object[] array= new Object[size];
        entries.copyInto(array);
        return array;
    }

    /**
     * Get the size of the table.
     *
     * @return The number of entries currently registered in this table.
     */
    public int getSize() {
        return size;
    }

    // EVENT STUFF
    //------------

    /**
     * Enable to add an SNMP entry listener to this
     * <CODE>SnmpMibTable</CODE>.
     *
     * <p>
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
    @Override
    public synchronized void
        addNotificationListener(NotificationListener listener,
                                NotificationFilter filter, Object handback)  {

        // Check listener
        //
        if (listener == null) {
            throw new java.lang.IllegalArgumentException
                ("Listener can't be null") ;
        }

        // looking for listener in handbackTable
        //
        Vector<Object> handbackList = handbackTable.get(listener) ;
        Vector<NotificationFilter> filterList = filterTable.get(listener) ;
        if ( handbackList == null ) {
            handbackList = new Vector<>() ;
            filterList = new Vector<>() ;
            handbackTable.put(listener, handbackList) ;
            filterTable.put(listener, filterList) ;
        }

        // Add the handback and the filter
        //
        handbackList.addElement(handback) ;
        filterList.addElement(filter) ;
    }

    /**
     * Enable to remove an SNMP entry listener from this
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
    @Override
    public synchronized void
        removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {

        // looking for listener in handbackTable
        //
        java.util.Vector<?> handbackList = handbackTable.get(listener) ;
        if ( handbackList == null ) {
            throw new ListenerNotFoundException("listener");
        }

        // If handback is null, remove the listener entry
        //
        handbackTable.remove(listener) ;
        filterTable.remove(listener) ;
    }

    /**
     * Return a <CODE>NotificationInfo</CODE> object containing the
     * notification class and the notification type sent by the
     * <CODE>SnmpMibTable</CODE>.
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {

        String[] types = {SnmpTableEntryNotification.SNMP_ENTRY_ADDED,
                          SnmpTableEntryNotification.SNMP_ENTRY_REMOVED};

        MBeanNotificationInfo[] notifsInfo = {
            new MBeanNotificationInfo
            (types, "com.sun.jmx.snmp.agent.SnmpTableEntryNotification",
             "Notifications sent by the SnmpMibTable")
        };

        return notifsInfo;
    }


    /**
     * Register the factory through which table entries should
     * be created when remote entry creation is enabled.
     *
     * <p>
     * @param factory The
     *        {@link com.sun.jmx.snmp.agent.SnmpTableEntryFactory} through
     *        which entries will be created when a remote SNMP manager
     *        request the creation of a new entry via an SNMP SET request.
     */
    public void registerEntryFactory(SnmpTableEntryFactory factory) {
        this.factory = factory;
    }

    // ----------------------------------------------------------------------
    // PROTECTED METHODS - RowStatus
    // ----------------------------------------------------------------------

    /**
     * Return true if the columnar object identified by <code>var</code>
     * is used to control the addition/deletion of rows in this table.
     *
     * <p>
     * By default, this method assumes that there is no control variable
     * and always return <code>false</code>
     * <p>
     * If this table was defined using SMIv2, and if it contains a
     * control variable with RowStatus syntax, <code>mibgen</code>
     * will generate a non default implementation for this method
     * that will identify the RowStatus control variable.
     * <p>
     * You will have to redefine this method if you need to implement
     * control variables that do not conform to RFC 2579 RowStatus
     * TEXTUAL-CONVENTION.
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param var The OID arc identifying the involved columnar object.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     **/
    protected boolean isRowStatus(SnmpOid rowOid, long var,
                                    Object  userData) {
        return false;
    }


    /**
     * Return the RowStatus code value specified in this request.
     * <p>
     * The RowStatus code value should be one of the values defined
     * by {@link com.sun.jmx.snmp.EnumRowStatus}. These codes correspond
     * to RowStatus codes as defined in RFC 2579, plus the <i>unspecified</i>
     * value which is SNMP Runtime specific.
     * <p>
     *
     * @param req    The sub-request that must be handled by this node.
     *
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param depth  The depth reached in the OID tree.
     *
     * @return The RowStatus code specified in this request, if any:
     * <ul>
     * <li>If the specified row does not exist and this table do
     *     not use any variable to control creation/deletion of
     *     rows, then default creation mechanism is assumed and
     *     <i>createAndGo</i> is returned</li>
     * <li>Otherwise, if the row exists and this table do not use any
     *     variable to control creation/deletion of rows,
     *     <i>unspecified</i> is returned.</li>
     * <li>Otherwise, if the request does not contain the control variable,
     *     <i>unspecified</i> is returned.</li>
     * <li>Otherwise, mapRowStatus() is called to extract the RowStatus
     *     code from the SnmpVarBind that contains the control variable.</li>
     * </ul>
     *
     * @exception SnmpStatusException if the value of the control variable
     *            could not be mapped to a RowStatus code.
     *
     * @see com.sun.jmx.snmp.EnumRowStatus
     **/
    protected int getRowAction(SnmpMibSubRequest req, SnmpOid rowOid,
                               int depth)
        throws SnmpStatusException {
        final boolean     isnew  = req.isNewEntry();
        final SnmpVarBind vb = req.getRowStatusVarBind();
        if (vb == null) {
            if (isnew && ! hasRowStatus())
                return EnumRowStatus.createAndGo;
            else return EnumRowStatus.unspecified;
        }

        try {
            return mapRowStatus(rowOid, vb, req.getUserData());
        } catch( SnmpStatusException x) {
            checkRowStatusFail(req, x.getStatus());
        }
        return EnumRowStatus.unspecified;
    }

    /**
     * Map the value of the <code>vbstatus</code> varbind to the
     * corresponding RowStatus code defined in
     * {@link com.sun.jmx.snmp.EnumRowStatus}.
     * These codes correspond to RowStatus codes as defined in RFC 2579,
     * plus the <i>unspecified</i> value which is SNMP Runtime specific.
     * <p>
     * By default, this method assumes that the control variable is
     * an Integer, and it simply returns its value without further
     * analysis.
     * <p>
     * If this table was defined using SMIv2, and if it contains a
     * control variable with RowStatus syntax, <code>mibgen</code>
     * will generate a non default implementation for this method.
     * <p>
     * You will have to redefine this method if you need to implement
     * control variables that do not conform to RFC 2579 RowStatus
     * TEXTUAL-CONVENTION.
     *
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param vbstatus The SnmpVarBind containing the value of the control
     *           variable, as identified by the isRowStatus() method.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @return The RowStatus code mapped from the value contained
     *     in <code>vbstatus</code>.
     *
     * @exception SnmpStatusException if the value of the control variable
     *            could not be mapped to a RowStatus code.
     *
     * @see com.sun.jmx.snmp.EnumRowStatus
     **/
    protected int mapRowStatus(SnmpOid rowOid, SnmpVarBind vbstatus,
                               Object userData)
        throws SnmpStatusException {
        final SnmpValue rsvalue = vbstatus.value;

        if (rsvalue instanceof SnmpInt)
            return ((SnmpInt)rsvalue).intValue();
        else
            throw new SnmpStatusException(
                       SnmpStatusException.snmpRspInconsistentValue);
    }

    /**
     * Set the control variable to the specified <code>newStatus</code>
     * value.
     *
     * <p>
     * This method maps the given <code>newStatus</code> to the appropriate
     * value for the control variable, then sets the control variable in
     * the entry identified by <code>rowOid</code>. It returns the new
     * value of the control variable.
     * <p>
     * By default, it is assumed that there is no control variable so this
     * method does nothing and simply returns <code>null</code>.
     * <p>
     * If this table was defined using SMIv2, and if it contains a
     * control variable with RowStatus syntax, <code>mibgen</code>
     * will generate a non default implementation for this method.
     * <p>
     * You will have to redefine this method if you need to implement
     * control variables that do not conform to RFC 2579 RowStatus
     * TEXTUAL-CONVENTION.
     *
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param newStatus The new status for the row: one of the
     *        RowStatus code defined in
     *        {@link com.sun.jmx.snmp.EnumRowStatus}. These codes
     *        correspond to RowStatus codes as defined in RFC 2579,
     *        plus the <i>unspecified</i> value which is SNMP Runtime specific.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @return The new value of the control variable (usually
     *         <code>new SnmpInt(newStatus)</code>) or <code>null</code>
     *         if the table do not have any control variable.
     *
     * @exception SnmpStatusException If the given <code>newStatus</code>
     *            could not be set on the specified entry, or if the
     *            given <code>newStatus</code> is not valid.
     *
     * @see com.sun.jmx.snmp.EnumRowStatus
     **/
    protected SnmpValue setRowStatus(SnmpOid rowOid, int newStatus,
                                     Object userData)
        throws SnmpStatusException {
        return null;
    }

    /**
     * Tell whether the specified row is ready and can be put in the
     * <i>notInService</i> state.
     * <p>
     * This method is called only once, after all the varbind have been
     * set on a new entry for which <i>createAndWait</i> was specified.
     * <p>
     * If the entry is not yet ready, this method should return false.
     * It will then be the responsibility of the entry to switch its
     * own state to <i>notInService</i> when it becomes ready.
     * No further call to <code>isRowReady()</code> will be made.
     * <p>
     * By default, this method always return true. <br>
     * <code>mibgen</code> will not generate any specific implementation
     * for this method - meaning that by default, a row created using
     * <i>createAndWait</i> will always be placed in <i>notInService</i>
     * state at the end of the request.
     * <p>
     * If this table was defined using SMIv2, and if it contains a
     * control variable with RowStatus syntax, <code>mibgen</code>
     * will generate an implementation for this method that will
     * delegate the work to the metadata class modelling the conceptual
     * row, so that you can override the default behaviour by subclassing
     * that metadata class.
     * <p>
     * You will have to redefine this method if this default mechanism
     * does not suit your needs.
     *
     * <p>
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @return <code>true</code> if the row can be placed in
     *         <i>notInService</i> state.
     *
     * @exception SnmpStatusException An error occurred while trying
     *            to retrieve the row status, and the operation should
     *            be aborted.
     *
     * @see com.sun.jmx.snmp.EnumRowStatus
     **/
    protected boolean isRowReady(SnmpOid rowOid, Object userData)
        throws SnmpStatusException {
        return true;
    }

    /**
     * Check whether the control variable of the given row can be
     * switched to the new specified <code>newStatus</code>.
     * <p>
     * This method is called during the <i>check</i> phase of a SET
     * request when the control variable specifies <i>active</i> or
     * <i>notInService</i>.
     * <p>
     * By default it is assumed that nothing prevents putting the
     * row in the requested state, and this method does nothing.
     * It is simply provided as a hook so that specific checks can
     * be implemented.
     * <p>
     * Note that if the actual row deletion fails afterward, the
     * atomicity of the request is no longer guaranteed.
     *
     * <p>
     * @param req    The sub-request that must be handled by this node.
     *
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param depth  The depth reached in the OID tree.
     *
     * @param newStatus The new status for the row: one of the
     *        RowStatus code defined in
     *        {@link com.sun.jmx.snmp.EnumRowStatus}. These codes
     *        correspond to RowStatus codes as defined in RFC 2579,
     *        plus the <i>unspecified</i> value which is SNMP Runtime specific.
     *
     * @exception SnmpStatusException if switching to this new state
     *            would fail.
     *
     **/
    protected void checkRowStatusChange(SnmpMibSubRequest req,
                                        SnmpOid rowOid, int depth,
                                        int newStatus)
        throws SnmpStatusException {

    }

    /**
     * Check whether the specified row can be removed from the table.
     * <p>
     * This method is called during the <i>check</i> phase of a SET
     * request when the control variable specifies <i>destroy</i>
     * <p>
     * By default it is assumed that nothing prevents row deletion
     * and this method does nothing. It is simply provided as a hook
     * so that specific checks can be implemented.
     * <p>
     * Note that if the actual row deletion fails afterward, the
     * atomicity of the request is no longer guaranteed.
     *
     * <p>
     * @param req    The sub-request that must be handled by this node.
     *
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param depth  The depth reached in the OID tree.
     *
     * @exception SnmpStatusException if the row deletion must be
     *            rejected.
     **/
    protected void checkRemoveTableRow(SnmpMibSubRequest req, SnmpOid rowOid,
                                       int depth)
        throws SnmpStatusException {

    }

    /**
     * Remove a table row upon a remote manager request.
     *
     * This method is called internally when <code>getRowAction()</code>
     * yields <i>destroy</i> - i.e.: it is only called when a remote
     * manager requests the removal of a table row.<br>
     * You should never need to call this function directly.
     * <p>
     * By default, this method simply calls <code>removeEntry(rowOid)
     * </code>.
     * <p>
     * You can redefine this method if you need to implement some
     * specific behaviour when a remote row deletion is invoked.
     * <p>
     * Note that specific checks should not be implemented in this
     * method, but rather in <code>checkRemoveTableRow()</code>.
     * If <code>checkRemoveTableRow()</code> succeeds and this method
     * fails afterward, the atomicity of the original SET request can no
     * longer be guaranteed.
     * <p>
     *
     * @param req    The sub-request that must be handled by this node.
     *
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param depth  The depth reached in the OID tree.
     *
     * @exception SnmpStatusException if the actual row deletion fails.
     *            This should not happen since it would break the
     *            atomicity of the SET request. Specific checks should
     *            be implemented in <code>checkRemoveTableRow()</code>
     *            if needed. If the entry does not exists, no exception
     *            is generated and the method simply returns.
     *
     **/
    protected void removeTableRow(SnmpMibSubRequest req, SnmpOid rowOid,
                                  int depth)
        throws SnmpStatusException {

        removeEntry(rowOid);
    }

    /**
     * This method takes care of initial RowStatus handling during the
     * check() phase of a SET request.
     *
     * In particular it will:
     * <ul><li>check that the given <code>rowAction</code> returned by
     *         <code>getRowAction()</code> is valid.</li>
     * <li>Then depending on the <code>rowAction</code> specified it will:
     *     <ul><li>either call <code>createNewEntry()</code> (<code>
     *         rowAction = <i>createAndGo</i> or <i>createAndWait</i>
     *         </code>),</li>
     *     <li>or call <code>checkRemoveTableRow()</code> (<code>
     *         rowAction = <i>destroy</i></code>),</li>
     *     <li>or call <code>checkRowStatusChange()</code> (<code>
     *         rowAction = <i>active</i> or <i>notInService</i></code>),</li>
     *     <li>or generate a SnmpStatusException if the passed <code>
     *         rowAction</code> is not correct.</li>
     * </ul></li></ul>
     * <p>
     * In principle, you should not need to redefine this method.
     * <p>
     * <code>beginRowAction()</code> is called during the check phase
     * of a SET request, before actual checking on the varbind list
     * is performed.
     *
     * <p>
     * @param req    The sub-request that must be handled by this node.
     *
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param depth  The depth reached in the OID tree.
     *
     * @param rowAction The requested action as returned by <code>
     *        getRowAction()</code>: one of the RowStatus codes defined in
     *        {@link com.sun.jmx.snmp.EnumRowStatus}. These codes
     *        correspond to RowStatus codes as defined in RFC 2579,
     *        plus the <i>unspecified</i> value which is SNMP Runtime specific.
     *
     * @exception SnmpStatusException if the specified <code>rowAction</code>
     *            is not valid or cannot be executed.
     *            This should not happen since it would break the
     *            atomicity of the SET request. Specific checks should
     *            be implemented in <code>beginRowAction()</code> if needed.
     *
     * @see com.sun.jmx.snmp.EnumRowStatus
     **/
    protected synchronized void beginRowAction(SnmpMibSubRequest req,
                              SnmpOid rowOid, int depth, int rowAction)
        throws SnmpStatusException {
        final boolean     isnew  = req.isNewEntry();
        final SnmpOid     oid    = rowOid;
        final int         action = rowAction;

        switch (action) {
        case EnumRowStatus.unspecified:
            if (isnew) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "beginRowAction", "Failed to create row[" +
                            rowOid + "] : RowStatus = unspecified");
                }
                checkRowStatusFail(req,SnmpStatusException.snmpRspNoAccess);
            }
            break;
        case EnumRowStatus.createAndGo:
        case EnumRowStatus.createAndWait:
            if (isnew) {
                if (isCreationEnabled()) {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                                SnmpMibTable.class.getName(),
                                "beginRowAction", "Creating row[" + rowOid +
                                "] : RowStatus = createAndGo | createAndWait");
                    }
                    createNewEntry(req,oid,depth);
                } else {
                    if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                        SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                                SnmpMibTable.class.getName(),
                                "beginRowAction", "Can't create row[" + rowOid +
                                "] : RowStatus = createAndGo | createAndWait " +
                                "but creation is disabled");
                    }
                    checkRowStatusFail(req,
                       SnmpStatusException.snmpRspNoAccess);
                }
            } else {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "beginRowAction", "Can't create row[" + rowOid +
                            "] : RowStatus = createAndGo | createAndWait " +
                            "but row already exists");
                }
                checkRowStatusFail(req,
                       SnmpStatusException.snmpRspInconsistentValue);
            }
            break;
        case EnumRowStatus.destroy:
            if (isnew) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "beginRowAction",
                            "Warning: can't destroy row[" + rowOid +
                            "] : RowStatus = destroy but row does not exist");
                }
            } else if (!isCreationEnabled()) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "beginRowAction",
                            "Can't destroy row[" + rowOid + "] : " +
                            "RowStatus = destroy but creation is disabled");
                }
                checkRowStatusFail(req,SnmpStatusException.snmpRspNoAccess);
            }
            checkRemoveTableRow(req,rowOid,depth);
            break;
        case EnumRowStatus.active:
        case EnumRowStatus.notInService:
            if (isnew) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "beginRowAction", "Can't switch state of row[" +
                            rowOid + "] : specified RowStatus = active | " +
                            "notInService but row does not exist");
                }
                checkRowStatusFail(req,
                        SnmpStatusException.snmpRspInconsistentValue);
            }
            checkRowStatusChange(req,rowOid,depth,action);
            break;
        case EnumRowStatus.notReady:
        default:
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                        SnmpMibTable.class.getName(),
                        "beginRowAction", "Invalid RowStatus value for row[" +
                        rowOid + "] : specified RowStatus = " + action);
            }
            checkRowStatusFail(req,
                    SnmpStatusException.snmpRspInconsistentValue);
        }
    }

    /**
     * This method takes care of final RowStatus handling during the
     * set() phase of a SET request.
     *
     * In particular it will:
     *     <ul><li>either call <code>setRowStatus(<i>active</i>)</code>
     *         (<code> rowAction = <i>createAndGo</i> or <i>active</i>
     *         </code>),</li>
     *     <li>or call <code>setRowStatus(<i>notInService</i> or <i>
     *         notReady</i>)</code> depending on the result of <code>
     *         isRowReady()</code> (<code>rowAction = <i>createAndWait</i>
     *         </code>),</li>
     *     <li>or call <code>setRowStatus(<i>notInService</i>)</code>
     *         (<code> rowAction = <i>notInService</i></code>),
     *     <li>or call <code>removeTableRow()</code> (<code>
     *         rowAction = <i>destroy</i></code>),</li>
     *     <li>or generate a SnmpStatusException if the passed <code>
     *         rowAction</code> is not correct. This should be avoided
     *         since it would break SET request atomicity</li>
     *     </ul>
     * <p>
     * In principle, you should not need to redefine this method.
     * <p>
     * <code>endRowAction()</code> is called during the set() phase
     * of a SET request, after the actual set() on the varbind list
     * has been performed. The varbind containing the control variable
     * is updated with the value returned by setRowStatus() (if it is
     * not <code>null</code>).
     *
     * <p>
     * @param req    The sub-request that must be handled by this node.
     *
     * @param rowOid The <CODE>SnmpOid</CODE> identifying the table
     *               row involved in the operation.
     *
     * @param depth  The depth reached in the OID tree.
     *
     * @param rowAction The requested action as returned by <code>
     *        getRowAction()</code>: one of the RowStatus codes defined in
     *        {@link com.sun.jmx.snmp.EnumRowStatus}. These codes
     *        correspond to RowStatus codes as defined in RFC 2579,
     *        plus the <i>unspecified</i> value which is SNMP Runtime specific.
     *
     * @exception SnmpStatusException if the specified <code>rowAction</code>
     *            is not valid.
     *
     * @see com.sun.jmx.snmp.EnumRowStatus
     **/
    protected void endRowAction(SnmpMibSubRequest req, SnmpOid rowOid,
                               int depth, int rowAction)
        throws SnmpStatusException {
        final boolean     isnew  = req.isNewEntry();
        final SnmpOid     oid    = rowOid;
        final int         action = rowAction;
        final Object      data   = req.getUserData();
        SnmpValue         value  = null;

        switch (action) {
        case EnumRowStatus.unspecified:
            break;
        case EnumRowStatus.createAndGo:
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                        SnmpMibTable.class.getName(),
                        "endRowAction", "Setting RowStatus to 'active' " +
                        "for row[" + rowOid + "] : requested RowStatus = " +
                        "createAndGo");
            }
            value = setRowStatus(oid,EnumRowStatus.active,data);
            break;
        case EnumRowStatus.createAndWait:
            if (isRowReady(oid,data)) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "endRowAction",
                            "Setting RowStatus to 'notInService' for row[" +
                            rowOid + "] : requested RowStatus = createAndWait");
                }
                value = setRowStatus(oid,EnumRowStatus.notInService,data);
            } else {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "endRowAction", "Setting RowStatus to 'notReady' " +
                            "for row[" + rowOid + "] : requested RowStatus = " +
                            "createAndWait");
                }
                value = setRowStatus(oid,EnumRowStatus.notReady,data);
            }
            break;
        case EnumRowStatus.destroy:
            if (isnew) {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "endRowAction",
                            "Warning: requested RowStatus = destroy, " +
                            "but row[" + rowOid + "] does not exist");
                }
            } else {
                if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                            SnmpMibTable.class.getName(),
                            "endRowAction", "Destroying row[" + rowOid +
                            "] : requested RowStatus = destroy");
                }
            }
            removeTableRow(req,oid,depth);
            break;
        case EnumRowStatus.active:
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                        SnmpMibTable.class.getName(),
                        "endRowAction",
                        "Setting RowStatus to 'active' for row[" +
                        rowOid + "] : requested RowStatus = active");
            }
            value = setRowStatus(oid,EnumRowStatus.active,data);
            break;
        case EnumRowStatus.notInService:
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                        SnmpMibTable.class.getName(),
                        "endRowAction",
                        "Setting RowStatus to 'notInService' for row[" +
                        rowOid + "] : requested RowStatus = notInService");
            }
            value = setRowStatus(oid,EnumRowStatus.notInService,data);
            break;
        case EnumRowStatus.notReady:
        default:
            if (SNMP_ADAPTOR_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_ADAPTOR_LOGGER.logp(Level.FINEST,
                        SnmpMibTable.class.getName(),
                        "endRowAction", "Invalid RowStatus value for row[" +
                        rowOid + "] : specified RowStatus = " + action);
            }
            setRowStatusFail(req,
                          SnmpStatusException.snmpRspInconsistentValue);
        }
        if (value != null) {
            final SnmpVarBind vb = req.getRowStatusVarBind();
            if (vb != null) vb.value = value;
        }
    }

    // -------------------------------------------------------------------
    // PROTECTED METHODS - get next
    // -------------------------------------------------------------------

    /**
     * Return the next OID arc corresponding to a readable columnar
     * object in the underlying entry OBJECT-TYPE, possibly skipping over
     * those objects that must not or cannot be returned.
     * Calls {@link
     * #getNextVarEntryId(com.sun.jmx.snmp.SnmpOid,long,java.lang.Object)},
     * until
     * {@link #skipEntryVariable(com.sun.jmx.snmp.SnmpOid,long,
     * java.lang.Object,int)} returns false.
     *
     *
     * @param rowOid The OID index of the row involved in the operation.
     *
     * @param var Id of the variable we start from, looking for the next.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @param pduVersion Protocol version of the original request PDU.
     *
     * @return The next columnar object id which can be returned using
     *         the given PDU's protocol version.
     *
     * @exception SnmpStatusException If no id is found after the given id.
     *
     **/
    protected long getNextVarEntryId(SnmpOid rowOid,
                                     long var,
                                     Object userData,
                                     int pduVersion)
        throws SnmpStatusException {

        long varid=var;
        do {
            varid = getNextVarEntryId(rowOid,varid,userData);
        } while (skipEntryVariable(rowOid,varid,userData,pduVersion));

        return varid;
    }

    /**
     * Hook for subclasses.
     * The default implementation of this method is to always return
     * false. Subclasses should redefine this method so that it returns
     * true when:
     * <ul><li>the variable is a leaf that is not instantiated,</li>
     * <li>or the variable is a leaf whose type cannot be returned by that
     *     version of the protocol (e.g. an Counter64 with SNMPv1).</li>
     * </ul>
     *
     * @param rowOid The OID index of the row involved in the operation.
     *
     * @param var Id of the variable we start from, looking for the next.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @param pduVersion Protocol version of the original request PDU.
     *
     * @return true if the variable must be skipped by the get-next
     *         algorithm.
     */
    protected boolean skipEntryVariable(SnmpOid rowOid,
                                        long var,
                                        Object userData,
                                        int pduVersion) {
        return false;
    }

    /**
     * Get the <CODE>SnmpOid</CODE> index of the row that follows
     * the given <CODE>oid</CODE> in the table. The given <CODE>
     * oid</CODE> does not need to be a valid row OID index.
     *
     * <p>
     * @param oid The OID from which the search will begin.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @return The next <CODE>SnmpOid</CODE> index.
     *
     * @exception SnmpStatusException There is no index following the
     *     specified <CODE>oid</CODE> in the table.
     */
    protected SnmpOid getNextOid(SnmpOid oid, Object userData)
        throws SnmpStatusException {

        if (size == 0) {
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        }

        final SnmpOid resOid = oid;

        // Just a simple check to speed up retrieval of last element ...
        //
        // XX SnmpOid last= (SnmpOid) oids.lastElement();
        SnmpOid last= tableoids[tablecount-1];
        if (last.equals(resOid)) {
            // Last element of the table ...
            //
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        }

        // First find the oid. This will allow to speed up retrieval process
        // during smart discovery of table (using the getNext) as the
        // management station will use the valid index returned during a
        // previous getNext ...
        //

        // Returns the position following the position at which resOid
        // is found, or the position at which resOid should be inserted.
        //
        final int newPos = getInsertionPoint(resOid,false);

        // If the position returned is not out of bound, we will find
        // the next element in the array.
        //
        if (newPos > -1 && newPos < size) {
            try {
                // XX last = (SnmpOid) oids.elementAt(newPos);
                last = tableoids[newPos];
            } catch(ArrayIndexOutOfBoundsException e) {
                throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
            }
        } else {
            // We are dealing with the last element of the table ..
            //
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        }


        return last;
    }

    /**
     * Return the first entry OID registered in the table.
     *
     * <p>
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @return The <CODE>SnmpOid</CODE> of the first entry in the table.
     *
     * @exception SnmpStatusException If the table is empty.
     */
    protected SnmpOid getNextOid(Object userData)
        throws SnmpStatusException {
        if (size == 0) {
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        }
        // XX return (SnmpOid) oids.firstElement();
        return tableoids[0];
    }

    // -------------------------------------------------------------------
    // Abstract Protected Methods
    // -------------------------------------------------------------------

    /**
     * This method is used internally and is implemented by the
     * <CODE>SnmpMibTable</CODE> subclasses generated by <CODE>mibgen</CODE>.
     *
     * <p> Return the next OID arc corresponding to a readable columnar
     *     object in the underlying entry OBJECT-TYPE.</p>
     *
     * <p>
     * @param rowOid The OID index of the row involved in the operation.
     *
     * @param var Id of the variable we start from, looking for the next.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @return The next columnar object id.
     *
     * @exception SnmpStatusException If no id is found after the given id.
     *
     **/
    abstract protected long getNextVarEntryId(SnmpOid rowOid, long var,
                                              Object userData)
        throws SnmpStatusException;

    /**
     * This method is used internally and is implemented by the
     * <CODE>SnmpMibTable</CODE> subclasses generated by <CODE>mibgen</CODE>.
     *
     * <p>
     * @param rowOid The OID index of the row involved in the operation.
     *
     * @param var The var we want to validate.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @exception SnmpStatusException If this id is not valid.
     *
     */
    abstract protected void validateVarEntryId(SnmpOid rowOid, long var,
                                               Object userData)
        throws SnmpStatusException;

    /**
     *
     * This method is used internally and is implemented by the
     * <CODE>SnmpMibTable</CODE> subclasses generated by <CODE>mibgen</CODE>.
     *
     * <p>
     * @param rowOid The OID index of the row involved in the operation.
     *
     * @param var The OID arc.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @exception SnmpStatusException If this id is not valid.
     *
     */
    abstract protected boolean isReadableEntryId(SnmpOid rowOid, long var,
                                                 Object userData)
        throws SnmpStatusException;

    /**
     * This method is used internally and is implemented by the
     * <CODE>SnmpMibTable</CODE> subclasses generated by <CODE>mibgen</CODE>.
     */
    abstract protected void get(SnmpMibSubRequest req,
                                SnmpOid rowOid, int depth)
        throws SnmpStatusException;

    /**
     * This method is used internally and is implemented by the
     * <CODE>SnmpMibTable</CODE> subclasses generated by <CODE>mibgen</CODE>.
     */
    abstract protected void check(SnmpMibSubRequest req,
                                  SnmpOid rowOid, int depth)
        throws SnmpStatusException;

    /**
     * This method is used internally and is implemented by the
     * <CODE>SnmpMibTable</CODE> subclasses generated by <CODE>mibgen</CODE>.
     */
    abstract protected void set(SnmpMibSubRequest req,
                                SnmpOid rowOid, int depth)
        throws SnmpStatusException;

    // ----------------------------------------------------------------------
    // PACKAGE METHODS
    // ----------------------------------------------------------------------

    /**
     * Get the <CODE>SnmpOid</CODE> index of the row that follows the
     * index extracted from the specified OID array.
     * Builds the SnmpOid corresponding to the row OID and calls
     * <code>getNextOid(oid,userData)</code>;
     *
     * <p>
     * @param oid The OID array.
     *
     * @param pos The position in the OID array at which the index starts.
     *
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @return The next <CODE>SnmpOid</CODE>.
     *
     * @exception SnmpStatusException There is no index following the
     *     specified one in the table.
     */
    SnmpOid getNextOid(long[] oid, int pos, Object userData)
        throws SnmpStatusException {

        // Construct the sub-oid starting at pos.
        // This sub-oid correspond to the oid part just after the entry
        // variable oid.
        //
        final SnmpOid resOid = new SnmpEntryOid(oid,pos);

        return getNextOid(resOid,userData);
    }

    // ---------------------------------------------------------------------
    //
    // Register an exception when checking the RowStatus variable
    //
    // ---------------------------------------------------------------------

    static void checkRowStatusFail(SnmpMibSubRequest req, int errorStatus)
        throws SnmpStatusException {

        final SnmpVarBind statusvb  = req.getRowStatusVarBind();
        final SnmpStatusException x = new SnmpStatusException(errorStatus);
        req.registerCheckException(statusvb,x);
    }

    // ---------------------------------------------------------------------
    //
    // Register an exception when checking the RowStatus variable
    //
    // ---------------------------------------------------------------------

    static void setRowStatusFail(SnmpMibSubRequest req, int errorStatus)
        throws SnmpStatusException {

        final SnmpVarBind statusvb  = req.getRowStatusVarBind();
        final SnmpStatusException x = new SnmpStatusException(errorStatus);
        req.registerSetException(statusvb,x);
    }

    // ---------------------------------------------------------------------
    //
    // Implements the method defined in SnmpMibNode.
    //
    // ---------------------------------------------------------------------
    @Override
    final synchronized void findHandlingNode(SnmpVarBind varbind,
                                             long[] oid, int depth,
                                             SnmpRequestTree handlers)
        throws SnmpStatusException {

        final int  length = oid.length;

        if (handlers == null)
            throw new SnmpStatusException(SnmpStatusException.snmpRspGenErr);

        if (depth >= length)
            throw new SnmpStatusException(SnmpStatusException.noAccess);

        if (oid[depth] != nodeId)
            throw new SnmpStatusException(SnmpStatusException.noAccess);

        if (depth+2 >= length)
            throw new SnmpStatusException(SnmpStatusException.noAccess);

        // Checks that the oid is valid
        // validateOid(oid,depth);

        // Gets the part of the OID that identifies the entry
        final SnmpOid entryoid = new SnmpEntryOid(oid, depth+2);

        // Finds the entry: false means that the entry does not exists
        final Object data = handlers.getUserData();
        final boolean hasEntry = contains(entryoid, data);

        // Fails if the entry is not found and the table does not
        // not support creation.
        // We know that the entry does not exists if (isentry == false).
        if (!hasEntry) {
            if (!handlers.isCreationAllowed()) {
                // we're not doing a set
                throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
            } else if (!isCreationEnabled())
                // we're doing a set but creation is disabled.
                throw new
                    SnmpStatusException(SnmpStatusException.snmpRspNoAccess);
        }

        final long   var  = oid[depth+1];

        // Validate the entry id
        if (hasEntry) {
            // The entry already exists - validate the id
            validateVarEntryId(entryoid,var,data);
        }

        // Registers this node for the identified entry.
        //
        if (handlers.isSetRequest() && isRowStatus(entryoid,var,data))

            // We only try to identify the RowStatus for SET operations
            //
            handlers.add(this,depth,entryoid,varbind,(!hasEntry),varbind);

        else
            handlers.add(this,depth,entryoid,varbind,(!hasEntry));
    }


    // ---------------------------------------------------------------------
    //
    // Implements the method defined in SnmpMibNode. The algorithm is very
    // largely inspired from the original getNext() method.
    //
    // ---------------------------------------------------------------------
    @Override
    final synchronized long[] findNextHandlingNode(SnmpVarBind varbind,
                                                   long[] oid,
                                                   int pos,
                                                   int depth,
                                                   SnmpRequestTree handlers,
                                                   AcmChecker checker)
        throws SnmpStatusException {

            int length = oid.length;

            if (handlers == null) {
                // This should be considered as a genErr, but we do not want to
                // abort the whole request, so we're going to throw
                // a noSuchObject...
                //
                throw new SnmpStatusException(SnmpStatusException.noSuchObject);
            }

            final Object data = handlers.getUserData();
            final int pduVersion = handlers.getRequestPduVersion();

            long var= -1;

            // If the querried oid contains less arcs than the OID of the
            // xxxEntry object, we must return the first leaf under the
            // first columnar object: the best way to do that is to reset
            // the queried oid:
            //   oid[0] = nodeId (arc of the xxxEntry object)
            //   pos    = 0 (points to the arc of the xxxEntry object)
            // then we just have to proceed...
            //
            if (pos >= length) {
                // this will have the side effect to set
                //    oid[pos] = nodeId
                // and
                //    (pos+1) = length
                // so we won't fall into the "else if" cases below -
                // so using "else if" rather than "if ..." is guaranteed
                // to be safe.
                //
                oid = new long[1];
                oid[0] = nodeId;
                pos = 0;
                length = 1;
            } else if (oid[pos] > nodeId) {
                // oid[pos] is expected to be the id of the xxxEntry ...
                // The id requested is greater than the id of the xxxEntry,
                // so we won't find the next element in this table... (any
                // element in this table will have a smaller OID)
                //
                throw new SnmpStatusException(SnmpStatusException.noSuchObject);
            } else if (oid[pos] < nodeId) {
                // we must return the first leaf under the first columnar
                // object, so we are back to our first case where pos was
                // out of bounds... => reset the oid to contain only the
                // arc of the xxxEntry object.
                //
                oid = new long[1];
                oid[0] = nodeId;
                pos = 0;
                length = 0;
            } else if ((pos + 1) < length) {
                // The arc at the position "pos+1" is the id of the columnar
                // object (ie: the id of the variable in the table entry)
                //
                var = oid[pos+1];
            }

            // Now that we've got everything right we can begin.
            SnmpOid entryoid;

            if (pos == (length - 1)) {
                // pos points to the last arc in the oid, and this arc is
                // guaranteed to be the xxxEntry id (we have handled all
                // the other possibilities before)
                //
                // We must therefore return the first leaf below the first
                // columnar object in the table.
                //
                // Get the first index. If an exception is raised,
                // then it means that the table is empty. We thus do not
                // have to catch the exception - we let it propagate to
                // the caller.
                //
                entryoid = getNextOid(data);
                var = getNextVarEntryId(entryoid,var,data,pduVersion);
            } else if ( pos == (length-2)) {
                // In that case we have (pos+1) = (length-1), so pos
                // points to the arc of the querried variable (columnar object).
                // Since the requested oid stops there, it means we have
                // to return the first leaf under this columnar object.
                //
                // So we first get the first index:
                // Note: if this raises an exception, this means that the table
                // is empty, so we can let the exception propagate to the caller.
                //
                entryoid = getNextOid(data);

                // XXX revisit: not exactly perfect:
                //     a specific row could be empty.. But we don't know
                //     how to make the difference! => tradeoff holes
                //     in tables can't be properly supported (all rows
                //     must have the same holes)
                //
                if (skipEntryVariable(entryoid,var,data,pduVersion)) {
                    var = getNextVarEntryId(entryoid,var,data,pduVersion);
                }
            } else {

                // So now there remain one last case, namely: some part of the
                // index is provided by the oid...
                // We build a possibly incomplete and invalid index from
                // the OID.
                // The piece of index provided should begin at pos+2
                //   oid[pos]   = id of the xxxEntry object,
                //   oid[pos+1] = id of the columnar object,
                //   oid[pos+2] ... oid[length-1] = piece of index.
                //

                // We get the next index following the provided index.
                // If this raises an exception, then it means that we have
                // reached the last index in the table, and we must then
                // try with the next columnar object.
                //
                // Bug fix 4269251
                // The SnmpIndex is defined to contain a valid oid:
                // this is not an SNMP requirement for the getNext request.
                // So we no more use the SnmpIndex but directly the SnmpOid.
                //
                try {
                    entryoid = getNextOid(oid, pos + 2, data);

                    // If the variable must ne skipped, fall through...
                    //
                    // XXX revisit: not exactly perfect:
                    //     a specific row could be empty.. But we don't know
                    //     how to make the difference! => tradeoff holes
                    //     in tables can't be properly supported (all rows
                    //     must have the same holes)
                    //
                    if (skipEntryVariable(entryoid,var,data,pduVersion)) {
                        throw new SnmpStatusException(SnmpStatusException.noSuchObject);
                    }
                } catch(SnmpStatusException se) {
                    entryoid = getNextOid(data);
                    var = getNextVarEntryId(entryoid,var,data,pduVersion);
                }
            }

            return findNextAccessibleOid(entryoid,
                                         varbind,
                                         oid,
                                         depth,
                                         handlers,
                                         checker,
                                         data,
                                         var);
        }

    private long[] findNextAccessibleOid(SnmpOid entryoid,
                                         SnmpVarBind varbind,long[] oid,
                                         int depth, SnmpRequestTree handlers,
                                         AcmChecker checker, Object data,
                                         long var)
        throws SnmpStatusException {
        final int pduVersion = handlers.getRequestPduVersion();

        // Loop on each var (column)
        while(true) {
            // This should not happen. If it happens, (bug, or customized
            // methods returning garbage instead of raising an exception),
            // it probably means that there is nothing to return anyway.
            // So we throw the exception.
            // => will skip to next node in the MIB tree.
            //
            if (entryoid == null || var == -1 ) {
                throw new SnmpStatusException(SnmpStatusException.noSuchObject);
            }

            // So here we know both the row (entryoid) and the column (var)
            //

            try {
                // Raising an exception here will make the catch() clause
                // switch to the next variable. If `var' is not readable
                // for this specific entry, it is not readable for any
                // other entry => skip to next column.
                //
                if (!isReadableEntryId(entryoid,var,data)) {
                    throw new SnmpStatusException(SnmpStatusException.noSuchObject);
                }

                // Prepare the result and the ACM checker.
                //
                final long[] etable  = entryoid.longValue(false);
                final int    elength = etable.length;
                final long[] result  = new long[depth + 2 + elength];
                result[0] = -1 ; // Bug detector!

                // Copy the entryOid at the end of `result'
                //
                java.lang.System.arraycopy(etable, 0, result,
                                           depth+2, elength);

                // Set the node Id and var Id in result.
                //
                result[depth] = nodeId;
                result[depth+1] = var;

                // Append nodeId.varId.<rowOid> to ACM checker.
                //
                checker.add(depth,result,depth,elength+2);

                // No we're going to ACM check our OID.
                try {
                    checker.checkCurrentOid();

                    // No exception thrown by checker => this is all OK!
                    // we have it: register the handler and return the
                    // result.
                    //
                    handlers.add(this,depth,entryoid,varbind,false);
                    return result;
                } catch(SnmpStatusException e) {
                    // Skip to the next entry. If an exception is
                    // thrown, will be catch by enclosing catch
                    // and a skip is done to the next var.
                    //
                    entryoid = getNextOid(entryoid, data);
                } finally {
                    // Clean the checker.
                    //
                    checker.remove(depth,elength+2);
                }
            } catch(SnmpStatusException e) {
                // Catching an exception here means we have to skip to the
                // next column.
                //
                // Back to the first row.
                entryoid = getNextOid(data);

                // Find out the next column.
                //
                var = getNextVarEntryId(entryoid,var,data,pduVersion);

            }

            // This should not happen. If it happens, (bug, or customized
            // methods returning garbage instead of raising an exception),
            // it probably means that there is nothing to return anyway.
            // No need to continue, we throw an exception.
            // => will skip to next node in the MIB tree.
            //
            if (entryoid == null || var == -1 ) {
                throw new SnmpStatusException(SnmpStatusException.noSuchObject);
            }
        }
    }


    /**
     * Validate the specified OID.
     *
     * <p>
     * @param oid The OID array.
     *
     * @param pos The position in the array.
     *
     * @exception SnmpStatusException If the validation fails.
     */
    final void validateOid(long[] oid, int pos) throws SnmpStatusException {
        final int length= oid.length;

        // Control the length of the oid
        //
        if (pos +2 >= length) {
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        }

        // Check that the entry identifier is specified
        //
        if (oid[pos] != nodeId) {
            throw new SnmpStatusException(SnmpStatusException.noSuchObject);
        }
    }

    // ----------------------------------------------------------------------
    // PRIVATE METHODS
    // ----------------------------------------------------------------------

    /**
     * Enable this <CODE>SnmpMibTable</CODE> to send a notification.
     *
     * <p>
     * @param notification The notification to send.
     */
    private synchronized void sendNotification(Notification notification) {

        // loop on listener
        //
        for(java.util.Enumeration<NotificationListener> k = handbackTable.keys();
            k.hasMoreElements(); ) {

            NotificationListener listener = k.nextElement();

            // Get the associated handback list and the associated filter list
            //
            java.util.Vector<?> handbackList = handbackTable.get(listener) ;
            java.util.Vector<NotificationFilter> filterList =
                filterTable.get(listener) ;

            // loop on handback
            //
            java.util.Enumeration<NotificationFilter> f = filterList.elements();
            for(java.util.Enumeration<?> h = handbackList.elements();
                h.hasMoreElements(); ) {

                Object handback = h.nextElement();
                NotificationFilter filter = f.nextElement();

                if ((filter == null) ||
                     (filter.isNotificationEnabled(notification))) {

                    listener.handleNotification(notification,handback) ;
                }
            }
        }
    }

    /**
     * This method is used by the SnmpMibTable to create and send a table
     * entry notification to all the listeners registered for this kind of
     * notification.
     *
     * <p>
     * @param type The notification type.
     *
     * @param timeStamp The notification emission date.
     *
     * @param entry The entry object.
     */
    private void sendNotification(String type, long timeStamp,
                                  Object entry, ObjectName name) {

        synchronized(this) {
            sequenceNumber = sequenceNumber + 1;
        }

        SnmpTableEntryNotification notif =
            new SnmpTableEntryNotification(type, this, sequenceNumber,
                                           timeStamp, entry, name);

        this.sendNotification(notif) ;
    }

    /**
     * Return true if the entry identified by the given OID index
     * is contained in this table.
     * <p>
     * <b>Do not call this method directly</b>.
     * <p>
     * This method is provided has a hook for subclasses.
     * It is called when a get/set request is received in order to
     * determine whether the specified entry is contained in the table.
     * You may want to override this method if you need to perform e.g.
     * lazy evaluation of tables (you need to update the table when a
     * request is received) or if your table is virtual.
     * <p>
     * Note that this method is called by the Runtime from within a
     * synchronized block.
     *
     * @param oid The index part of the OID we're looking for.
     * @param userData A contextual object containing user-data.
     *        This object is allocated through the <code>
     *        {@link com.sun.jmx.snmp.agent.SnmpUserDataFactory}</code>
     *        for each incoming SNMP request.
     *
     * @return <code>true</code> if the entry is found, <code>false</code>
     *         otherwise.
     *
     * @since 1.5
     **/
    protected boolean contains(SnmpOid oid, Object userData) {
        return (findObject(oid) > -1);
    }

    /**
     * Look for the given oid in the OID table (tableoids) and returns
     * its position.
     *
     * <p>
     * @param oid The OID we're looking for.
     *
     * @return The position of the OID in the table. -1 if the given
     *         OID was not found.
     *
     **/
    private int findObject(SnmpOid oid) {
        int low= 0;
        int max= size - 1;
        SnmpOid pos;
        int comp;
        int curr= low + (max-low)/2;
        //System.out.println("Try to retrieve: " + oid.toString());
        while (low <= max) {

            // XX pos = (SnmpOid) oids.elementAt(curr);
            pos = tableoids[curr];

            //System.out.println("Compare with" + pos.toString());
            // never know ...we might find something ...
            //
            comp = oid.compareTo(pos);
            if (comp == 0)
                return curr;

            if (oid.equals(pos) == true) {
                return curr;
            }
            if (comp > 0) {
                low = curr + 1;
            } else {
                max = curr - 1;
            }
            curr = low + (max-low)/2;
        }
        return -1;
    }

    /**
     * Search the position at which the given oid should be inserted
     * in the OID table (tableoids).
     *
     * <p>
     * @param oid The OID we would like to insert.
     *
     * @param fail Tells whether a SnmpStatusException must be generated
     *             if the given OID is already present in the table.
     *
     * @return The position at which the OID should be inserted in
     *         the table. When the OID is found, it returns the next
     *         position. Note that it is not valid to insert twice the
     *         same OID. This feature is only an optimization to improve
     *         the getNextOid() behaviour.
     *
     * @exception SnmpStatusException if the OID is already present in the
     *            table and <code>fail</code> is <code>true</code>.
     *
     **/
    private int getInsertionPoint(SnmpOid oid, boolean fail)
        throws SnmpStatusException {

        final int failStatus = SnmpStatusException.snmpRspNotWritable;
        int low= 0;
        int max= size - 1;
        SnmpOid pos;
        int comp;
        int curr= low + (max-low)/2;
        while (low <= max) {

            // XX pos= (SnmpOid) oids.elementAt(curr);
            pos= tableoids[curr];

            // never know ...we might find something ...
            //
            comp= oid.compareTo(pos);

            if (comp == 0) {
                if (fail)
                    throw new SnmpStatusException(failStatus,curr);
                else
                    return curr+1;
            }

            if (comp>0) {
                low= curr +1;
            } else {
                max= curr -1;
            }
            curr= low + (max-low)/2;
        }
        return curr;
    }

    /**
     * Remove the OID located at the given position.
     *
     * <p>
     * @param pos The position at which the OID to be removed is located.
     *
     **/
    private void removeOid(int pos) {
        if (pos >= tablecount) return;
        if (pos < 0) return;
        final int l1 = --tablecount-pos;
        tableoids[pos] = null;
        if (l1 > 0)
            java.lang.System.arraycopy(tableoids,pos+1,tableoids,pos,l1);
        tableoids[tablecount] = null;
    }

    /**
     * Insert an OID at the given position.
     *
     * <p>
     * @param oid The OID to be inserted in the table
     * @param pos The position at which the OID to be added is located.
     *
     **/
    private void insertOid(int pos, SnmpOid oid) {
        if (pos >= tablesize || tablecount == tablesize) {
                // Vector must be enlarged

                // Save old vector
                final SnmpOid[] olde = tableoids;

                // Allocate larger vectors
                tablesize += Delta;
                tableoids = new SnmpOid[tablesize];

                // Check pos validity
                if (pos > tablecount) pos = tablecount;
                if (pos < 0) pos = 0;

                final int l1 = pos;
                final int l2 = tablecount - pos;

                // Copy original vector up to `pos'
                if (l1 > 0)
                    java.lang.System.arraycopy(olde,0,tableoids,0,l1);

                // Copy original vector from `pos' to end, leaving
                // an empty room at `pos' in the new vector.
                if (l2 > 0)
                    java.lang.System.arraycopy(olde,l1,tableoids,
                                               l1+1,l2);

            } else if (pos < tablecount) {
                // Vector is large enough to accommodate one additional
                // entry.
                //
                // Shift vector, making an empty room at `pos'

                java.lang.System.arraycopy(tableoids,pos,tableoids,
                                           pos+1,tablecount-pos);
            }

            // Fill the gap at `pos'
            tableoids[pos]  = oid;
            tablecount++;
    }


    // ----------------------------------------------------------------------
    // PROTECTED VARIABLES
    // ----------------------------------------------------------------------

    /**
     * The id of the contained entry object.
     * @serial
     */
    protected int nodeId=1;

    /**
     * The MIB to which the metadata is linked.
     * @serial
     */
    protected SnmpMib theMib;

    /**
     * <CODE>true</CODE> if remote creation of entries via SET operations
     * is enabled.
     * [default value is <CODE>false</CODE>]
     * @serial
     */
    protected boolean creationEnabled = false;

    /**
     * The entry factory
     */
    protected SnmpTableEntryFactory factory = null;

    // ----------------------------------------------------------------------
    // PRIVATE VARIABLES
    // ----------------------------------------------------------------------

    /**
     * The number of elements in the table.
     * @serial
     */
    private int size=0;

    /**
     * The list of indexes.
     * @serial
     */
    //    private Vector indexes= new Vector();

    /**
     * The list of OIDs.
     * @serial
     */
    // private Vector oids= new Vector();
    private final static int Delta = 16;
    private int     tablecount     = 0;
    private int     tablesize      = Delta;
    private SnmpOid tableoids[]    = new SnmpOid[tablesize];

    /**
     * The list of entries.
     * @serial
     */
    private final Vector<Object> entries= new Vector<>();

    /**
     * The list of object names.
     * @serial
     */
    private final Vector<ObjectName> entrynames= new Vector<>();

    /**
     * Callback handlers
     */
    // final Vector callbacks = new Vector();

    /**
     * Listener hashtable containing the hand-back objects.
     */
    private Hashtable<NotificationListener, Vector<Object>> handbackTable =
            new Hashtable<>();

    /**
     * Listener hashtable containing the filter objects.
     */
    private Hashtable<NotificationListener, Vector<NotificationFilter>>
            filterTable = new Hashtable<>();

    // PACKAGE VARIABLES
    //------------------
    /**
     * SNMP table sequence number.
     * The default value is set to 0.
     */
    transient long sequenceNumber = 0;
}
