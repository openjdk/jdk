/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
package sun.management.snmp.jvminstr;


// java imports
//
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// jmx imports
//
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.agent.SnmpStandardObjectServer;

import java.lang.management.MemoryManagerMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

import sun.management.snmp.jvmmib.JvmMemGCTableMeta;
import sun.management.snmp.util.SnmpCachedData;
import sun.management.snmp.util.SnmpTableCache;
import sun.management.snmp.util.SnmpTableHandler;
import sun.management.snmp.util.MibLogger;
import sun.management.snmp.util.JvmContextFactory;

/**
 * The class is used for implementing the "JvmMemGCTable" table.
 */
public class JvmMemGCTableMetaImpl extends  JvmMemGCTableMeta {

    static final long serialVersionUID = 8250461197108867607L;

    /**
     * This class acts as a filter over the SnmpTableHandler
     * used for the JvmMemoryManagerTable. It filters out
     * (skip) all MemoryManagerMXBean that are not instances of
     * GarbageCollectorMXBean so that only Garbage Collectors are
     * seen. This is a better solution than relying on
     * ManagementFactory.getGarbageCollectorMXBeans() because it makes it
     * possible to guarantee the consistency betwen the MemoryManager table
     * and the GCTable since both will be sharing the same cache.
     **/
    protected static class GCTableFilter {

        /**
         * Returns the index that immediately follows the given
         * <var>index</var>. The returned index is strictly greater
         * than the given <var>index</var>, and is contained in the table.
         * <br>If the given <var>index</var> is null, returns the first
         * index in the table.
         * <br>If there are no index after the given <var>index</var>,
         * returns null.
         * This method is an optimization for the case where the
         * SnmpTableHandler is in fact an instance of SnmpCachedData.
         **/
        public SnmpOid getNext(SnmpCachedData datas, SnmpOid index) {

            final boolean dbg = log.isDebugOn();

            // We're going to loop until we find an instance of
            // GarbageCollectorMXBean. First we attempt to find
            // the next element whose OID follows the given index.
            // If `index' is null, the insertion point is -1
            // (the next is 0 = -insertion - 1)
            //
            final int insertion = (index==null)?-1:datas.find(index);
            if (dbg) log.debug("GCTableFilter","oid="+index+
                               " at insertion="+insertion);

            int next;
            if (insertion > -1) next = insertion+1;
            else next = -insertion -1;

            // Now `next' points to the element that imediately
            // follows the given `index'. We're going to loop
            // through the table, starting at `next' (included),
            // and return the first element which is an instance
            // of GarbageCollectorMXBean.
            //
            for (;next<datas.indexes.length;next++) {
                if (dbg) log.debug("GCTableFilter","next="+next);
                final Object value = datas.datas[next];
                if (dbg) log.debug("GCTableFilter","value["+next+"]=" +
                      ((MemoryManagerMXBean)value).getName());
                if (value instanceof GarbageCollectorMXBean) {
                    // That's the next: return it.
                    if (dbg) log.debug("GCTableFilter",
                          ((MemoryManagerMXBean)value).getName() +
                          " is a  GarbageCollectorMXBean.");
                    return datas.indexes[next];
                }
                if (dbg) log.debug("GCTableFilter",
                      ((MemoryManagerMXBean)value).getName() +
                      " is not a  GarbageCollectorMXBean: " +
                      value.getClass().getName());
                // skip to next index...
            }
            return null;
        }

        /**
         * Returns the index that immediately follows the given
         * <var>index</var>. The returned index is strictly greater
         * than the given <var>index</var>, and is contained in the table.
         * <br>If the given <var>index</var> is null, returns the first
         * index in the table.
         * <br>If there are no index after the given <var>index</var>,
         * returns null.
         **/
        public SnmpOid getNext(SnmpTableHandler handler, SnmpOid index) {

            // try to call the optimized method
            if (handler instanceof SnmpCachedData)
                return getNext((SnmpCachedData)handler, index);

            // too bad - revert to non-optimized generic algorithm
            SnmpOid next = index;
            do {
                next = handler.getNext(next);
                final Object value = handler.getData(next);
                if (value instanceof GarbageCollectorMXBean)
                    // That's the next! return it
                    return next;
                // skip to next index...
            } while (next != null);
            return null;
        }

        /**
         * Returns the data associated with the given index.
         * If the given index is not found, null is returned.
         * Note that returning null does not necessarily means that
         * the index was not found.
         **/
        public Object  getData(SnmpTableHandler handler, SnmpOid index) {
            final Object value = handler.getData(index);
            if (value instanceof GarbageCollectorMXBean) return value;
            // Behaves as if there was nothing at this index...
            //
            return null;
        }

        /**
         * Returns true if the given <var>index</var> is present.
         **/
        public boolean contains(SnmpTableHandler handler, SnmpOid index) {
            if (handler.getData(index) instanceof GarbageCollectorMXBean)
                return true;
            // Behaves as if there was nothing at this index...
            //
            return false;
        }
    }


    private transient JvmMemManagerTableMetaImpl managers = null;
    private static GCTableFilter filter = new GCTableFilter();


    /**
     * Constructor for the table. Initialize metadata for "JvmMemGCTableMeta".
     */
    public JvmMemGCTableMetaImpl(SnmpMib myMib,
                                 SnmpStandardObjectServer objserv) {
        super(myMib,objserv);
    }

    // Returns a pointer to the JvmMemManager meta node - we're going
    // to reuse its SnmpTableHandler by filtering out all that is
    // not a GarbageCollectorMXBean.
    private final JvmMemManagerTableMetaImpl getManagers(SnmpMib mib) {
        if (managers == null) {
            managers = (JvmMemManagerTableMetaImpl)
                mib.getRegisteredTableMeta("JvmMemManagerTable");
        }
        return managers;
    }

    /**
     * Returns the JvmMemManagerTable SnmpTableHandler
     **/
    protected SnmpTableHandler getHandler(Object userData) {
        JvmMemManagerTableMetaImpl managerTable= getManagers(theMib);
        return managerTable.getHandler(userData);
    }

    // See com.sun.jmx.snmp.agent.SnmpMibTable
    protected SnmpOid getNextOid(Object userData)
        throws SnmpStatusException {
        // null means get the first OID.
        return getNextOid(null,userData);
    }

    // See com.sun.jmx.snmp.agent.SnmpMibTable
    protected SnmpOid getNextOid(SnmpOid oid, Object userData)
        throws SnmpStatusException {
        final boolean dbg = log.isDebugOn();
        try {
            if (dbg) log.debug("getNextOid", "previous=" + oid);

            // Get the data handler.
            //
            SnmpTableHandler handler = getHandler(userData);
            if (handler == null) {
                // This should never happen.
                // If we get here it's a bug.
                //
                if (dbg) log.debug("getNextOid", "handler is null!");
                throw new
                    SnmpStatusException(SnmpStatusException.noSuchInstance);
            }


            // Get the next oid, using the GC filter.
            //
            final SnmpOid next = filter.getNext(handler,oid);
            if (dbg) log.debug("getNextOid", "next=" + next);

            // if next is null: we reached the end of the table.
            //
            if (next == null)
                throw new
                    SnmpStatusException(SnmpStatusException.noSuchInstance);

            return next;
        } catch (RuntimeException x) {
            // debug. This should never happen.
            //
            if (dbg) log.debug("getNextOid",x);
            throw x;
        }
    }


    // See com.sun.jmx.snmp.agent.SnmpMibTable
    protected boolean contains(SnmpOid oid, Object userData) {
        // Get the handler.
        //
        SnmpTableHandler handler = getHandler(userData);

        // handler should never be null.
        //
        if (handler == null)
            return false;
        return filter.contains(handler,oid);
    }

    // See com.sun.jmx.snmp.agent.SnmpMibTable
    public Object getEntry(SnmpOid oid)
        throws SnmpStatusException {

        if (oid == null)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        // Get the request contextual cache (userData).
        //
        final Map<Object, Object> m = JvmContextFactory.getUserData();

        // First look in the request contextual cache: maybe we've already
        // created this entry...
        //

        // We know in the case of this table that the index is an integer,
        // it is thus the first OID arc of the index OID.
        //
        final long   index    = oid.getOidArc(0);

        // We're going to use this name to store/retrieve the entry in
        // the request contextual cache.
        //
        // Revisit: Probably better programming to put all these strings
        //          in some interface.
        //
        final String entryTag = ((m==null)?null:("JvmMemGCTable.entry." +
                                                 index));

        // If the entry is in the cache, simply return it.
        //
        if (m != null) {
            final Object entry = m.get(entryTag);
            if (entry != null) return entry;
        }

        // Entry was not in request cache. Make a new one.
        //
        // Get the data hanler.
        //
        SnmpTableHandler handler = getHandler(m);

        // handler should never be null.
        //
        if (handler == null)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        // Use the filter to retrieve only GarabageCollectorMBean data.
        //
        final Object data = filter.getData(handler,oid);

        // data may be null if the OID we were given is not valid.
        // (e.g. it identifies a MemoryManager which is not a
        // GarbageCollector)
        //
        if (data == null)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        // Make a new entryy (transient object that will be kept only
        // for the duration of the request.
        //
        final Object entry =
            new JvmMemGCEntryImpl((GarbageCollectorMXBean)data,(int)index);

        // Put the entry in the request cache in case we need it later
        // in the processing of the request. Note that we could have
        // optimized this by making JvmMemGCEntryImpl extend
        // JvmMemManagerEntryImpl, and then make sure that
        // JvmMemManagerTableMetaImpl creates an instance of JvmMemGCEntryImpl
        // instead of JvmMemManagerEntryImpl when the associated data is
        // an instance of GarbageCollectorMXBean. This would have made it
        // possible to share the transient entry object.
        // As it is, we may have two transient objects that points to
        // the same underlying MemoryManagerMXBean (which is definitely
        // not a problem - but is only a small dysatisfaction)
        //
        if (m != null && entry != null) {
            m.put(entryTag,entry);
        }

        return entry;
    }

    static final MibLogger log = new MibLogger(JvmMemGCTableMetaImpl.class);
}
