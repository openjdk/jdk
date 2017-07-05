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
import com.sun.jmx.mbeanserver.Util;
import java.io.Serializable;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Collections;

// jmx imports
//
import javax.management.MBeanServer;
import javax.management.ObjectName;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.agent.SnmpStandardObjectServer;
import com.sun.jmx.snmp.agent.SnmpMibTable;

import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;

import sun.management.snmp.jvmmib.JvmMemMgrPoolRelTableMeta;
import sun.management.snmp.util.SnmpTableCache;
import sun.management.snmp.util.SnmpCachedData;
import sun.management.snmp.util.SnmpTableHandler;
import sun.management.snmp.util.MibLogger;
import sun.management.snmp.util.JvmContextFactory;

/**
 * The class is used for implementing the "JvmMemMgrPoolRelTable" group.
 */
public class JvmMemMgrPoolRelTableMetaImpl extends JvmMemMgrPoolRelTableMeta
    implements Serializable {

    static final long serialVersionUID = 1896509775012355443L;

    /**
     * A concrete implementation of {@link SnmpTableCache}, for the
     * jvmMemMgrPoolRelTable.
     **/

    private static class JvmMemMgrPoolRelTableCache
        extends SnmpTableCache {

        static final long serialVersionUID = 6059937161990659184L;
        final private JvmMemMgrPoolRelTableMetaImpl meta;

        /**
         * Create a weak cache for the jvmMemMgrPoolRelTable.
         * @param validity validity of the cached data, in ms.
         **/
        JvmMemMgrPoolRelTableCache(JvmMemMgrPoolRelTableMetaImpl meta,
                                   long validity) {
            this.validity = validity;
            this.meta     = meta;
        }

        /**
         * Call <code>getTableDatas(JvmContextFactory.getUserData())</code>.
         **/
        public SnmpTableHandler getTableHandler() {
            final Map<Object,Object> userData = JvmContextFactory.getUserData();
            return getTableDatas(userData);
        }

        /**
         * Builds a map pool-name => pool-index from the SnmpTableHandler
         * of the JvmMemPoolTable.
         **/
        private static Map<String, SnmpOid> buildPoolIndexMap(SnmpTableHandler handler) {
            // optimization...
            if (handler instanceof SnmpCachedData)
                return buildPoolIndexMap((SnmpCachedData)handler);

            // not optimizable... too bad.
            final Map<String, SnmpOid> m = new HashMap<>();
            SnmpOid index=null;
            while ((index = handler.getNext(index))!=null) {
                final MemoryPoolMXBean mpm =
                    (MemoryPoolMXBean)handler.getData(index);
                if (mpm == null) continue;
                final String name = mpm.getName();
                if (name == null) continue;
                m.put(name,index);
            }
            return m;
        }

        /**
         * Builds a map pool-name => pool-index from the SnmpTableHandler
         * of the JvmMemPoolTable.
         * Optimized algorithm.
         **/
        private static Map<String, SnmpOid> buildPoolIndexMap(SnmpCachedData cached) {
            if (cached == null) return Collections.emptyMap();
            final SnmpOid[] indexes = cached.indexes;
            final Object[]  datas   = cached.datas;
            final int len = indexes.length;
            final Map<String, SnmpOid> m = new HashMap<>(len);
            for (int i=0; i<len; i++) {
                final SnmpOid index = indexes[i];
                if (index == null) continue;
                final MemoryPoolMXBean mpm =
                    (MemoryPoolMXBean)datas[i];
                if (mpm == null) continue;
                final String name = mpm.getName();
                if (name == null) continue;
                m.put(name,index);
            }
            return m;
        }

        /**
         * Return a table handler that holds the jvmMemManagerTable table data.
         * This method return the cached table data if it is still
         * valid, recompute it and cache the new value if it's not.
         * If it needs to recompute the cached data, it first
         * try to obtain the list of memory managers from the request
         * contextual cache, and if it is not found, it calls
         * <code>ManagementFactory.getMemoryMBean().getMemoryManagers()</code>
         * and caches the value.
         * This ensures that
         * <code>ManagementFactory.getMemoryMBean().getMemoryManagers()</code>
         * is not called more than once per request, thus ensuring a
         * consistent view of the table.
         **/
        protected SnmpCachedData updateCachedDatas(Object userData) {
            // Get the MemoryManager     table
            final SnmpTableHandler mmHandler =
                meta.getManagerHandler(userData);

            // Get the MemoryPool        table
            final SnmpTableHandler mpHandler =
                meta.getPoolHandler(userData);

            // Time stamp for the cache
            final long time = System.currentTimeMillis();

            //     Build a Map poolname -> index
            final Map<String,SnmpOid> poolIndexMap = buildPoolIndexMap(mpHandler);

            // For each memory manager, get the list of memory pools
            // For each memory pool, find its index in the memory pool table
            // Create a row in the relation table.
            final TreeMap<SnmpOid, Object> table =
                    new TreeMap<>(SnmpCachedData.oidComparator);
            updateTreeMap(table,userData,mmHandler,mpHandler,poolIndexMap);

            return new SnmpCachedData(time,table);
        }


        /**
         * Get the list of memory pool associated with the
         * given MemoryManagerMXBean.
         **/
        protected String[] getMemoryPools(Object userData,
                                      MemoryManagerMXBean mmm, long mmarc) {
            final String listTag =
                "JvmMemManager." + mmarc + ".getMemoryPools";

            String[] result=null;
            if (userData instanceof Map) {
                result = (String[])((Map)userData).get(listTag);
                if (result != null) return result;
            }

            if (mmm!=null) {
                result = mmm.getMemoryPoolNames();
            }
            if ((result!=null)&&(userData instanceof Map)) {
                Map<Object, Object> map = Util.cast(userData);
                map.put(listTag,result);
            }

            return result;
        }

        protected void updateTreeMap(TreeMap<SnmpOid, Object> table, Object userData,
                                     MemoryManagerMXBean mmm,
                                     SnmpOid mmIndex,
                                     Map<String, SnmpOid> poolIndexMap) {

            // The MemoryManager index is an int, so it's the first
            // and only subidentifier.
            final long mmarc;
            try {
                mmarc = mmIndex.getOidArc(0);
            } catch (SnmpStatusException x) {
                log.debug("updateTreeMap",
                          "Bad MemoryManager OID index: "+mmIndex);
                log.debug("updateTreeMap",x);
                return;
            }


            // Cache this in userData + get it from cache?
            final String[] mpList = getMemoryPools(userData,mmm,mmarc);
            if (mpList == null || mpList.length < 1) return;

            final String mmmName = mmm.getName();
            for (int i = 0; i < mpList.length; i++) {
                final String mpmName = mpList[i];
                if (mpmName == null) continue;
                final SnmpOid mpIndex = poolIndexMap.get(mpmName);
                if (mpIndex == null) continue;

                // The MemoryPool index is an int, so it's the first
                // and only subidentifier.
                final long mparc;
                try {
                    mparc  = mpIndex.getOidArc(0);
                } catch (SnmpStatusException x) {
                    log.debug("updateTreeMap","Bad MemoryPool OID index: " +
                          mpIndex);
                    log.debug("updateTreeMap",x);
                    continue;
                }
                // The MemoryMgrPoolRel table indexed is composed
                // of the MemoryManager index, to which the MemoryPool
                // index is appended.
                final long[] arcs = { mmarc, mparc };

                final SnmpOid index = new SnmpOid(arcs);

                table.put(index, new JvmMemMgrPoolRelEntryImpl(mmmName,
                                                               mpmName,
                                                               (int)mmarc,
                                                               (int)mparc));
            }
        }

        protected void updateTreeMap(TreeMap<SnmpOid, Object> table, Object userData,
                                     SnmpTableHandler mmHandler,
                                     SnmpTableHandler mpHandler,
                                     Map<String, SnmpOid> poolIndexMap) {
            if (mmHandler instanceof SnmpCachedData) {
                updateTreeMap(table,userData,(SnmpCachedData)mmHandler,
                              mpHandler,poolIndexMap);
                return;
            }

            SnmpOid mmIndex=null;
            while ((mmIndex = mmHandler.getNext(mmIndex))!=null) {
                final MemoryManagerMXBean mmm =
                    (MemoryManagerMXBean)mmHandler.getData(mmIndex);
                if (mmm == null) continue;
                updateTreeMap(table,userData,mmm,mmIndex,poolIndexMap);
            }
        }

        protected void updateTreeMap(TreeMap<SnmpOid, Object> table, Object userData,
                                     SnmpCachedData mmHandler,
                                     SnmpTableHandler mpHandler,
                                     Map<String, SnmpOid> poolIndexMap) {

            final SnmpOid[] indexes = mmHandler.indexes;
            final Object[]  datas   = mmHandler.datas;
            final int size = indexes.length;
            for (int i=size-1; i>-1; i--) {
                final MemoryManagerMXBean mmm =
                    (MemoryManagerMXBean)datas[i];
                if (mmm == null) continue;
                updateTreeMap(table,userData,mmm,indexes[i],poolIndexMap);
            }
        }
    }

    // The weak cache for this table.
    protected SnmpTableCache cache;

    private transient JvmMemManagerTableMetaImpl managers = null;
    private transient JvmMemPoolTableMetaImpl    pools    = null;

    /**
     * Constructor for the table. Initialize metadata for
     * "JvmMemMgrPoolRelTableMeta".
     * The reference on the MBean server is updated so the entries
     * created through an SNMP SET will be AUTOMATICALLY REGISTERED
     * in Java DMK.
     */
    public JvmMemMgrPoolRelTableMetaImpl(SnmpMib myMib,
                                      SnmpStandardObjectServer objserv) {
        super(myMib,objserv);
        this.cache = new
            JvmMemMgrPoolRelTableCache(this,((JVM_MANAGEMENT_MIB_IMPL)myMib).
                                       validity());
    }

    // Returns a pointer to the JvmMemManager meta node - we're going
    // to reuse its SnmpTableHandler in order to implement the
    // relation table.
    private final JvmMemManagerTableMetaImpl getManagers(SnmpMib mib) {
        if (managers == null) {
            managers = (JvmMemManagerTableMetaImpl)
                mib.getRegisteredTableMeta("JvmMemManagerTable");
        }
        return managers;
    }

    // Returns a pointer to the JvmMemPool meta node - we're going
    // to reuse its SnmpTableHandler in order to implement the
    // relation table.
    private final JvmMemPoolTableMetaImpl getPools(SnmpMib mib) {
        if (pools == null) {
            pools = (JvmMemPoolTableMetaImpl)
                mib.getRegisteredTableMeta("JvmMemPoolTable");
        }
        return pools;
    }

    /**
     * Returns the JvmMemManagerTable SnmpTableHandler
     **/
    protected SnmpTableHandler getManagerHandler(Object userData) {
        final JvmMemManagerTableMetaImpl managerTable = getManagers(theMib);
        return managerTable.getHandler(userData);
    }

    /**
     * Returns the JvmMemPoolTable SnmpTableHandler
     **/
    protected SnmpTableHandler getPoolHandler(Object userData) {
        final JvmMemPoolTableMetaImpl poolTable = getPools(theMib);
        return poolTable.getHandler(userData);
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
        if (dbg) log.debug("getNextOid", "previous=" + oid);


        // Get the data handler.
        //
        SnmpTableHandler handler = getHandler(userData);
        if (handler == null) {
            // This should never happen.
            // If we get here it's a bug.
            //
            if (dbg) log.debug("getNextOid", "handler is null!");
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        }

        // Get the next oid
        //
        final SnmpOid next = handler.getNext(oid);
        if (dbg) log.debug("getNextOid", "next=" + next);

        // if next is null: we reached the end of the table.
        //
        if (next == null)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        return next;
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

        if (oid == null || oid.getLength() < 2)
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        // Get the request contextual cache (userData).
        //
        final Map<Object, Object> m = JvmContextFactory.getUserData();

        // We know in the case of this table that the index is composed
        // of two integers,
        //  o The MemoryManager is the first  OID arc of the index OID.
        //  o The MemoryPool    is the second OID arc of the index OID.
        //
        final long   mgrIndex     = oid.getOidArc(0);
        final long   poolIndex    = oid.getOidArc(1);

        // We're going to use this name to store/retrieve the entry in
        // the request contextual cache.
        //
        // Revisit: Probably better programming to put all these strings
        //          in some interface.
        //
        final String entryTag = ((m==null)?null:
                                 ("JvmMemMgrPoolRelTable.entry." +
                                  mgrIndex + "." + poolIndex));

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
        if (!(data instanceof JvmMemMgrPoolRelEntryImpl))
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);

        // make the new entry (transient object that will be kept only
        // for the duration of the request.
        //
        final Object entry = (JvmMemMgrPoolRelEntryImpl)data;
        // XXXXX Revisit
        // new JvmMemMgrPoolRelEntryImpl((MemoryManagerMXBean)data,
        //                                (int)mgrIndex,(int)poolIndex);

        // Put the entry in the cache in case we need it later while processing
        // the request.
        //
        if (m != null && entry != null) {
            m.put(entryTag,entry);
        }

        return entry;
    }

    /**
     * Get the SnmpTableHandler that holds the jvmMemManagerTable data.
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
        if (userData instanceof Map) m=Util.cast(userData);
        else m=null;

        // Look in the contextual cache.
        if (m != null) {
            final SnmpTableHandler handler =
                (SnmpTableHandler)m.get("JvmMemMgrPoolRelTable.handler");
            if (handler != null) return handler;
        }

        // No handler in contextual cache, make a new one.
        final SnmpTableHandler handler = cache.getTableHandler();

        if (m != null && handler != null )
            m.put("JvmMemMgrPoolRelTable.handler",handler);

        return handler;
    }

    static final MibLogger log =
        new MibLogger(JvmMemMgrPoolRelTableMetaImpl.class);
}
