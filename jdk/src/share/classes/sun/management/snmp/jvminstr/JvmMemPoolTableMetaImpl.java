/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.jmx.mbeanserver.Util;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


// jmx imports
//
import javax.management.MBeanServer;
import javax.management.ObjectName;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.agent.SnmpMibSubRequest;
import com.sun.jmx.snmp.agent.SnmpStandardObjectServer;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ManagementFactory;

import sun.management.snmp.jvmmib.JvmMemPoolTableMeta;
import sun.management.snmp.util.SnmpTableCache;
import sun.management.snmp.util.SnmpNamedListTableCache;
import sun.management.snmp.util.SnmpTableHandler;
import sun.management.snmp.util.MibLogger;
import sun.management.snmp.util.JvmContextFactory;

/**
 * The class is used for implementing the "JvmMemPoolTable" group.
 */
public class JvmMemPoolTableMetaImpl extends JvmMemPoolTableMeta {

    /**
     * A concrete implementation of {@link SnmpNamedListTableCache}, for the
     * jvmMemPoolTable.
     **/
    private static class JvmMemPoolTableCache extends SnmpNamedListTableCache {
        /**
         * Create a weak cache for the jvmMemPoolTable.
         * @param validity validity of the cached data, in ms.
         **/
        JvmMemPoolTableCache(long validity) {
            this.validity = validity;
        }

        /**
         * Use the MemoryPoolMXBean name as key.
         * @param context A {@link TreeMap} as allocated by the parent
         *        {@link SnmpNamedListTableCache} class.
         * @param rawDatas List of {@link MemoryPoolMXBean}, as
         *        returned by
         * <code>ManagementFactory.getMemoryPoolMXBeans()</code>
         * @param rank The <var>rank</var> of <var>item</var> in the list.
         * @param item The <var>rank</var><super>th</super>
         *        <code>MemoryPoolMXBean</code> in the list.
         * @return  <code>((MemoryPoolMXBean)item).getName()</code>
         **/
        protected String getKey(Object context, List rawDatas,
                                int rank, Object item) {
            if (item == null) return null;
            final String name = ((MemoryPoolMXBean)item).getName();
            log.debug("getKey", "key=" + name);
            return name;
        }

        /**
         * Call <code>getTableDatas(JvmContextFactory.getUserData())</code>.
         **/
        public SnmpTableHandler getTableHandler() {
            final Map userData = JvmContextFactory.getUserData();
            return getTableDatas(userData);
        }

        /**
         * Return the key used to cache the raw data of this table.
         **/
        protected String getRawDatasKey() {
            return "JvmMemManagerTable.getMemoryPools";
        }

        /**
         * Call ManagementFactory.getMemoryPoolMXBeans() to
         * load the raw data of this table.
         **/
        protected List   loadRawDatas(Map userData) {
            return ManagementFactory.getMemoryPoolMXBeans();
        }
    }

    // The weak cache for this table.
    protected SnmpTableCache cache;

    /**
     * Constructor for the table.
     * Initialize metadata for "JvmMemPoolTableMeta".
     */
    public JvmMemPoolTableMetaImpl(SnmpMib myMib,
                                   SnmpStandardObjectServer objserv) {
        super(myMib,objserv);
        this.cache = new
            JvmMemPoolTableCache(((JVM_MANAGEMENT_MIB_IMPL)myMib).
                                    validity()*30);
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

            // Get the next oid
            //
            final SnmpOid next = handler.getNext(oid);
            if (dbg) log.debug("getNextOid", "next=" + next);

            // if next is null: we reached the end of the table.
            //
            if (next == null)
                throw new
                    SnmpStatusException(SnmpStatusException.noSuchInstance);

            return next;
        } catch (SnmpStatusException x) {
            if (dbg) log.debug("getNextOid", "End of MIB View: " + x);
            throw x;
        } catch (RuntimeException r) {
            if (dbg) log.debug("getNextOid", "Unexpected exception: " + r);
            if (dbg) log.debug("getNextOid",r);
            throw r;
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

        return handler.contains(oid);
    }

    // See com.sun.jmx.snmp.agent.SnmpMibTable
    public Object getEntry(SnmpOid oid)
        throws SnmpStatusException {

        if (oid == null)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        // Get the request contextual cache (userData).
        //
        final Map<Object, Object> m = Util.cast(JvmContextFactory.getUserData());

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
        final String entryTag = ((m==null)?null:("JvmMemPoolTable.entry." +
                                                 index));

        // If the entry is in the cache, simply return it.
        //
        if (m != null) {
            final Object entry = m.get(entryTag);
            if (entry != null) return entry;
        }

        // The entry was not in the cache, make a new one.
        //
        // Get the data hanler.
        //
        SnmpTableHandler handler = getHandler(m);

        // handler should never be null.
        //
        if (handler == null)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        // Get the data associated with our entry.
        //
        final Object data = handler.getData(oid);

        // data may be null if the OID we were given is not valid.
        //
        if (data == null)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        // make the new entry (transient object that will be kept only
        // for the duration of the request.
        //
        if (log.isDebugOn())
            log.debug("getEntry","data is a: " + data.getClass().getName());
        final Object entry =
            new JvmMemPoolEntryImpl((MemoryPoolMXBean)data,(int)index);

        // Put the entry in the cache in case we need it later while processing
        // the request.
        //
        if (m != null && entry != null) {
            m.put(entryTag,entry);
        }

        return entry;
    }

    /**
     * Get the SnmpTableHandler that holds the jvmMemPoolTable data.
     * First look it up in the request contextual cache, and if it is
     * not found, obtain it from the weak cache.
     * <br>The request contextual cache will be released at the end of the
     * current requests, and is used only to process this request.
     * <br>The weak cache is shared by all requests, and is only
     * recomputed when it is found to be obsolete.
     * <br>Note that the data put in the request contextual cache is
     *     never considered to be obsolete, in order to preserve data
     *     coherency.
     **/
    protected SnmpTableHandler getHandler(Object userData) {
        final Map<Object, Object> m;
        if (userData instanceof Map) m = Util.cast(userData);
        else m = null;

        // Look in the contextual cache.
        if (m != null) {
            final SnmpTableHandler handler =
                (SnmpTableHandler)m.get("JvmMemPoolTable.handler");
            if (handler != null) return handler;
        }

        // No handler in contextual cache, make a new one.
        final SnmpTableHandler handler = cache.getTableHandler();

        if (m != null && handler != null )
            m.put("JvmMemPoolTable.handler",handler);

        return handler;
    }

    static final MibLogger log = new MibLogger(JvmMemPoolTableMetaImpl.class);
}
