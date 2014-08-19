/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
package sun.management.snmp.util;

import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.mbeanserver.Util;

import java.io.Serializable;

import java.util.Comparator;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.Iterator;

import java.lang.ref.WeakReference;


/**
 * This abstract class implements a weak cache that holds table data, for
 * a table whose data is obtained from a list  where a name can be obtained
 * for each item in the list.
 * <p>This object maintains a map between an entry name and its associated
 * SnmpOid index, so that a given entry is always associated to the same
 * index.</p>
 * <p><b>NOTE: This class is not synchronized, subclasses must implement
 *          the appropriate synchronization whwn needed.</b></p>
 **/
@SuppressWarnings("serial") // JDK implementation class
public abstract class SnmpNamedListTableCache extends SnmpListTableCache {

    /**
     * This map associate an entry name with the SnmpOid index that's
     * been allocated for it.
     **/
    protected TreeMap<String, SnmpOid> names = new TreeMap<>();

    /**
     * The last allocate index.
     **/
    protected long last = 0;

    /**
     * true if the index has wrapped.
     **/
    boolean   wrapped = false;

    /**
     * Returns the key to use as name for the given <var>item</var>.
     * <br>This method is called by {@link #getIndex(Object,List,int,Object)}.
     * The given <var>item</var> is expected to be always associated with
     * the same name.
     * @param context The context passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rawDatas Raw table datas passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rank Rank of the given <var>item</var> in the
     *        <var>rawDatas</var> list iterator.
     * @param item The raw data object for which a key name must be determined.
     **/
    protected abstract String getKey(Object context, List<?> rawDatas,
                                     int rank, Object item);

    /**
     * Find a new index for the entry corresponding to the
     * given <var>item</var>.
     * <br>This method is called by {@link #getIndex(Object,List,int,Object)}
     * when a new index needs to be allocated for an <var>item</var>. The
     * index returned must not be already in used.
     * @param context The context passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rawDatas Raw table datas passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rank Rank of the given <var>item</var> in the
     *        <var>rawDatas</var> list iterator.
     * @param item The raw data object for which an index must be determined.
     **/
    protected SnmpOid makeIndex(Object context, List<?> rawDatas,
                                int rank, Object item) {

        // check we are in the limits of an unsigned32.
        if (++last > 0x00000000FFFFFFFFL) {
            // we just wrapped.
            log.debug("makeIndex", "Index wrapping...");
            last = 0;
            wrapped=true;
        }

        // If we never wrapped, we can safely return last as new index.
        if (!wrapped) return new SnmpOid(last);

        // We wrapped. We must look for an unused index.
        for (int i=1;i < 0x00000000FFFFFFFFL;i++) {
            if (++last >  0x00000000FFFFFFFFL) last = 1;
            final SnmpOid testOid = new SnmpOid(last);

            // Was this index already in use?
            if (names == null) return testOid;
            if (names.containsValue(testOid)) continue;

            // Have we just used it in a previous iteration?
            if (context == null) return testOid;
            if (((Map)context).containsValue(testOid)) continue;

            // Ok, not in use.
            return testOid;
        }
        // all indexes are in use! we're stuck.
        // // throw new IndexOutOfBoundsException("No index available.");
        // better to return null and log an error.
        return null;
    }

    /**
     * Call {@link #getKey(Object,List,int,Object)} in order to get
     * the item name. Then check whether an index was already allocated
     * for the entry by that name. If yes return it. Otherwise, call
     * {@link #makeIndex(Object,List,int,Object)} to compute a new
     * index for that entry.
     * Finally store the association between
     * the name and index in the context TreeMap.
     * @param context The context passed to
     *        {@link #updateCachedDatas(Object,List)}.
     *        It is expected to
     *        be an instance of  {@link TreeMap}.
     * @param rawDatas Raw table datas passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rank Rank of the given <var>item</var> in the
     *        <var>rawDatas</var> list iterator.
     * @param item The raw data object for which an index must be determined.
     **/
    protected SnmpOid getIndex(Object context, List<?> rawDatas,
                               int rank, Object item) {
        final String key   = getKey(context,rawDatas,rank,item);
        final Object index = (names==null||key==null)?null:names.get(key);
        final SnmpOid result =
            ((index != null)?((SnmpOid)index):makeIndex(context,rawDatas,
                                                      rank,item));
        if ((context != null) && (key != null) && (result != null)) {
            Map<Object, Object> map = Util.cast(context);
            map.put(key,result);
        }
        log.debug("getIndex","key="+key+", index="+result);
        return result;
    }

    /**
     * Allocate a new {@link TreeMap} to serve as context, then
     * call {@link SnmpListTableCache#updateCachedDatas(Object,List)}, and
     * finally replace the {@link #names} TreeMap by the new allocated
     * TreeMap.
     * @param rawDatas The table datas from which the cached data will be
     *        computed.
     **/
    protected SnmpCachedData updateCachedDatas(Object context, List<?> rawDatas) {
        TreeMap<String,SnmpOid> ctxt = new TreeMap<>();
        final SnmpCachedData result =
            super.updateCachedDatas(context,rawDatas);
        names = ctxt;
        return result;
    }


    /**
     * Load a list of raw data from which to build the cached data.
     * This method is called when nothing is found in the request
     * contextual cache.
     * @param userData The request contextual cache allocated by
     *        the {@link JvmContextFactory}.
     *
     **/
    protected abstract List<?>  loadRawDatas(Map<Object,Object> userData);

    /**
     *The name under which the raw data is to be found/put in
     *        the request contextual cache.
     **/
    protected abstract String getRawDatasKey();

    /**
     * Get a list of raw data from which to build the cached data.
     * Obtains a list of raw data by first looking it up in the
     * request contextual cache <var>userData</var> under the given
     * <var>key</var>. If nothing is found in the cache, calls
     * {@link #loadRawDatas(Map)} to obtain a new rawData list,
     * and cache the result in <var>userData</var> under <var>key</var>.
     * @param userData The request contextual cache allocated by
     *        the {@link JvmContextFactory}.
     * @param key The name under which the raw data is to be found/put in
     *        the request contextual cache.
     *
     **/
    protected List<?> getRawDatas(Map<Object, Object> userData, String key) {
        List<?> rawDatas = null;

        // Look for memory manager list in request contextual cache.
        if (userData != null)
            rawDatas =  (List<?>)userData.get(key);

        if (rawDatas == null) {
            // No list in contextual cache, get it from API
            rawDatas = loadRawDatas(userData);


            // Put list in cache...
            if (rawDatas != null && userData != null)
                userData.put(key, rawDatas);
        }

        return rawDatas;
    }

    /**
     * Update cahed datas.
     * Obtains a {@link List} of raw datas by calling
     * {@link #getRawDatas(Map,String) getRawDatas((Map)context,getRawDatasKey())}.<br>
     * Then allocate a new {@link TreeMap} to serve as temporary map between
     * names and indexes, and call {@link #updateCachedDatas(Object,List)}
     * with that temporary map as context.<br>
     * Finally replaces the {@link #names} TreeMap by the temporary
     * TreeMap.
     * @param context The request contextual cache allocated by the
     *        {@link JvmContextFactory}.
     **/
    protected SnmpCachedData updateCachedDatas(Object context) {

        final Map<Object, Object> userData =
            (context instanceof Map)?Util.<Map<Object, Object>>cast(context):null;

        // Look for memory manager list in request contextual cache.
        final List<?> rawDatas = getRawDatas(userData,getRawDatasKey());

        log.debug("updateCachedDatas","rawDatas.size()=" +
              ((rawDatas==null)?"<no data>":""+rawDatas.size()));

        TreeMap<String,SnmpOid> ctxt = new TreeMap<>();
        final SnmpCachedData result =
            super.updateCachedDatas(ctxt,rawDatas);
        names = ctxt;
        return result;
    }

    static final MibLogger log = new MibLogger(SnmpNamedListTableCache.class);
}
