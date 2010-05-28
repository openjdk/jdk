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
package sun.management.snmp.util;

import com.sun.jmx.snmp.SnmpOid;

import java.io.Serializable;

import java.util.Comparator;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.List;
import java.util.Iterator;

import java.lang.ref.WeakReference;

/**
 * This abstract class implements a weak cache for a table whose data
 * is obtained from a {@link  List}.
 *
 * <p><b>NOTE: This class is not synchronized, subclasses must implement
 *          the appropriate synchronization whwn needed.</b></p>
 **/
public abstract class SnmpListTableCache extends SnmpTableCache {


    /**
     * The index of the entry corresponding to the given <var>item</var>.
     * <br>This method is called by {@link #updateCachedDatas(Object,List)}.
     * The given <var>item</var> is expected to be always associated with
     * the same index.
     * @param context The context passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rawDatas Raw table datas passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rank Rank of the given <var>item</var> in the
     *        <var>rawDatas</var> list iterator.
     * @param item The raw data object for which an index must be determined.
     **/
    protected abstract SnmpOid getIndex(Object context, List rawDatas,
                                        int rank, Object item);

    /**
     * The data for the entry corresponding to the given <var>item</var>.
     * <br>This method is called by {@link #updateCachedDatas(Object,List)}.
     * @param context The context passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rawDatas Raw table datas passed to
     *        {@link #updateCachedDatas(Object,List)}.
     * @param rank Rank of the given <var>item</var> in the
     *        <var>rawDatas</var> list iterator.
     * @param item The raw data object from which the entry data must be
     *        extracted.
     * @return By default <var>item</var> is returned.
     **/
    protected Object getData(Object context, List rawDatas,
                             int rank, Object item) {
        return item;
    }

    /**
     * Recompute cached data.
     * @param context A context object, valid during the duration of
     *        of the call to this method, and that will be passed to
     *        {@link #getIndex} and {@link #getData}. <br>
     *        This method is intended to be called by
     *        {@link #updateCachedDatas(Object)}. It is assumed that
     *        the context is be allocated by  before this method is called,
     *        and released just after this method has returned.<br>
     *        This class does not use the context object: it is a simple
     *        hook for subclassed.
     * @param rawDatas The table datas from which the cached data will be
     *        computed.
     * @return the computed cached data.
     **/
    protected SnmpCachedData updateCachedDatas(Object context, List rawDatas) {
        final int size = ((rawDatas == null)?0:rawDatas.size());
        if (size == 0) return  null;

        final long time = System.currentTimeMillis();
        final Iterator it  = rawDatas.iterator();
        final TreeMap<SnmpOid, Object> map =
                new TreeMap<SnmpOid, Object>(SnmpCachedData.oidComparator);
        for (int rank=0; it.hasNext() ; rank++) {
            final Object  item  = it.next();
            final SnmpOid index = getIndex(context, rawDatas, rank, item);
            final Object  data  = getData(context, rawDatas, rank, item);
            if (index == null) continue;
            map.put(index,data);
        }

        return new SnmpCachedData(time,map);
    }

}
