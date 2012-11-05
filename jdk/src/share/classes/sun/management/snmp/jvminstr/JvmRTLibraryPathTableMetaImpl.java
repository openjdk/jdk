/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;

// jmx imports
//
import javax.management.MBeanServer;
import javax.management.ObjectName;
import com.sun.jmx.snmp.SnmpCounter;
import com.sun.jmx.snmp.SnmpCounter64;
import com.sun.jmx.snmp.SnmpGauge;
import com.sun.jmx.snmp.SnmpInt;
import com.sun.jmx.snmp.SnmpUnsignedInt;
import com.sun.jmx.snmp.SnmpIpAddress;
import com.sun.jmx.snmp.SnmpTimeticks;
import com.sun.jmx.snmp.SnmpOpaque;
import com.sun.jmx.snmp.SnmpString;
import com.sun.jmx.snmp.SnmpStringFixed;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpNull;
import com.sun.jmx.snmp.SnmpValue;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpStatusException;

// jdmk imports
//
import com.sun.jmx.snmp.agent.SnmpIndex;
import com.sun.jmx.snmp.agent.SnmpMib;
import com.sun.jmx.snmp.agent.SnmpMibTable;
import com.sun.jmx.snmp.agent.SnmpMibSubRequest;
import com.sun.jmx.snmp.agent.SnmpStandardObjectServer;

import sun.management.snmp.jvmmib.JvmRTLibraryPathTableMeta;
import sun.management.snmp.util.SnmpCachedData;
import sun.management.snmp.util.SnmpTableCache;
import sun.management.snmp.util.SnmpTableHandler;
import sun.management.snmp.util.MibLogger;
import sun.management.snmp.util.JvmContextFactory;

/**
 * The class is used for implementing the "JvmRTLibraryPathTable".
  */
public class JvmRTLibraryPathTableMetaImpl extends JvmRTLibraryPathTableMeta {

    static final long serialVersionUID = 6713252710712502068L;
    private SnmpTableCache cache;

     /**
     * A concrete implementation of {@link SnmpTableCache}, for the
     * JvmRTLibraryPathTable.
     **/
    private static class JvmRTLibraryPathTableCache extends SnmpTableCache {
        static final long serialVersionUID = 2035304445719393195L;
        private JvmRTLibraryPathTableMetaImpl meta;

        JvmRTLibraryPathTableCache(JvmRTLibraryPathTableMetaImpl meta,
                                 long validity) {
            this.meta = meta;
            this.validity = validity;
        }

        /**
         * Call <code>getTableDatas(JvmContextFactory.getUserData())</code>.
         **/
        public SnmpTableHandler getTableHandler() {
            final Map<Object,Object> userData = JvmContextFactory.getUserData();
            return getTableDatas(userData);
        }


        /**
         * Return a table handler containing the Thread indexes.
         * Indexes are computed from the ThreadId.
         **/
        protected SnmpCachedData updateCachedDatas(Object userData) {


            // We are getting all the input args
            final String[] path =
                JvmRuntimeImpl.getLibraryPath(userData);

            // Time stamp for the cache
            final long time = System.currentTimeMillis();
            final int len = path.length;

            SnmpOid indexes[] = new SnmpOid[len];

            for(int i = 0; i < len; i++) {
                indexes[i] = new SnmpOid(i + 1);
            }

            return new SnmpCachedData(time, indexes, path);
        }
    }

    /**
     * Constructor for the table. Initialize metadata for
     * "JvmRTLibraryPathTableMeta".
     * The reference on the MBean server is updated so the entries
     * created through an SNMP SET will be AUTOMATICALLY REGISTERED
     * in Java DMK.
     */
    public JvmRTLibraryPathTableMetaImpl(SnmpMib myMib,
                                       SnmpStandardObjectServer objserv) {
        super(myMib, objserv);
        cache = new JvmRTLibraryPathTableCache(this, -1);
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
        if (dbg) log.debug("*** **** **** **** getNextOid", "next=" + next);

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
        final boolean dbg = log.isDebugOn();
        if (dbg) log.debug("getEntry", "oid [" + oid + "]");
        if (oid == null || oid.getLength() != 1) {
            if (dbg) log.debug("getEntry", "Invalid oid [" + oid + "]");
            throw new SnmpStatusException(SnmpStatusException.noSuchInstance);
        }

        // Get the request contextual cache (userData).
        //
        final Map<Object, Object> m = JvmContextFactory.getUserData();

        // We're going to use this name to store/retrieve the entry in
        // the request contextual cache.
        //
        // Revisit: Probably better programming to put all these strings
        //          in some interface.
        //
        final String entryTag = ((m==null)?null:
                                 ("JvmRTLibraryPathTable.entry." +
                                  oid.toString()));

        // If the entry is in the cache, simply return it.
        //
        if (m != null) {
            final Object entry = m.get(entryTag);
            if (entry != null) {
                if (dbg)
                    log.debug("getEntry", "Entry is already in the cache");
                return entry;
            } else if (dbg) log.debug("getEntry", "Entry is not in the cache");

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
        if (dbg) log.debug("getEntry","data is a: "+
                           data.getClass().getName());
        final Object entry =
            new JvmRTLibraryPathEntryImpl((String) data,(int)oid.getOidArc(0));

        // Put the entry in the cache in case we need it later while processing
        // the request.
        //
        if (m != null && entry != null) {
            m.put(entryTag,entry);
        }

        return entry;
    }

    /**
     * Get the SnmpTableHandler that holds the jvmThreadInstanceTable data.
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
                (SnmpTableHandler)m.get("JvmRTLibraryPathTable.handler");
            if (handler != null) return handler;
        }

        // No handler in contextual cache, make a new one.
        final SnmpTableHandler handler = cache.getTableHandler();

        if (m != null && handler != null )
            m.put("JvmRTLibraryPathTable.handler",handler);

        return handler;
    }

    static final MibLogger log =
        new MibLogger(JvmRTLibraryPathTableMetaImpl.class);
}
