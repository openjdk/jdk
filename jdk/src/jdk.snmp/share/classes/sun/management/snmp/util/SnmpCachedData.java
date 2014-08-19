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
 * This class is used to cache table data.
 **/
public class SnmpCachedData implements SnmpTableHandler {

    /**
     * Compares two SnmpOid.
     **/
    public static final Comparator<SnmpOid> oidComparator =
        new Comparator<SnmpOid>() {
            public int compare(SnmpOid o1, SnmpOid o2) {
                return o1.compareTo(o2);
            }
            public boolean equals(Object o1, Object o2) {
                if (o1 == o2) return true;
                else return o1.equals(o2);
            }
        };

    /**
     * Constructs a new instance of SnmpCachedData. Instances are
     * immutable.
     * @param lastUpdated Time stamp as returned by
     *        {@link System#currentTimeMillis System.currentTimeMillis()}
     * @param indexes The table entry indexes, sorted in ascending order.
     * @param datas   The table datas, sorted according to the
     *                order in <code>indexes</code>: <code>datas[i]</code>
     *                is the data that corresponds to
     *                <code>indexes[i]</code>
     **/
    public SnmpCachedData(long lastUpdated, SnmpOid indexes[],
                          Object  datas[]) {
        this.lastUpdated = lastUpdated;
        this.indexes     = indexes;
        this.datas       = datas;
    }

    /**
     * Constructs a new instance of SnmpCachedData. Instances are
     * immutable.
     * @param lastUpdated Time stamp as returned by
     *        {@link System#currentTimeMillis System.currentTimeMillis()}
     * @param indexMap The table indexed table data, sorted in ascending
     *                 order by {@link #oidComparator}. The keys must be
     *                 instances of {@link SnmpOid}.
     **/
    public SnmpCachedData(long lastUpdated, TreeMap<SnmpOid, Object> indexMap) {
        this(lastUpdated, indexMap, true);
    }
    /**
     * Constructs a new instance of SnmpCachedData. Instances are
     * immutable.
     * @param lastUpdated Time stamp as returned by
     *        {@link System#currentTimeMillis System.currentTimeMillis()}
     * @param indexMap The table indexed table data, sorted in ascending
     *                 order by {@link #oidComparator}. The keys must be
     *                 instances of {@link SnmpOid}.
     **/
    public SnmpCachedData(long lastUpdated, TreeMap<SnmpOid, Object> indexMap,
                          boolean b) {

        final int size = indexMap.size();
        this.lastUpdated = lastUpdated;
        this.indexes     = new SnmpOid[size];
        this.datas       = new Object[size];

        if(b) {
            indexMap.keySet().toArray(this.indexes);
            indexMap.values().toArray(this.datas);
        } else
            indexMap.values().toArray(this.datas);
    }

    /**
     * Time stamp as returned by
     * {@link System#currentTimeMillis System.currentTimeMillis()}
     **/
    public final long    lastUpdated;

    /**
     * The table entry indexes, sorted in ascending order.
     **/
    public final SnmpOid indexes[];

    /**
     * The table datas, sorted according to the
     * order in <code>indexes</code>: <code>datas[i]</code>
     * is the data that corresponds to <code>indexes[i]</code>
     **/
    public final Object  datas[];

    /**
     * The position of the given <var>index</var>, as returned by
     * <code>java.util.Arrays.binarySearch()</code>
     **/
    public final int find(SnmpOid index) {
        return Arrays.binarySearch(indexes,index,oidComparator);
    }

    // SnmpTableHandler.getData()
    public  Object getData(SnmpOid index) {
        final int pos = find(index);
        if ((pos < 0)||(pos >= datas.length)) return null;
        return datas[pos];
    }

    // SnmpTableHandler.getNext()
    public  SnmpOid getNext(SnmpOid index) {
        if (index == null) {
            if (indexes.length>0) return indexes[0];
            else return null;
        }
        final int pos = find(index);
        if (pos > -1) {
            if (pos < (indexes.length -1) ) return indexes[pos+1];
            else return null;
        }
        final int insertion = -pos -1;
        if ((insertion > -1) && (insertion < indexes.length))
            return indexes[insertion];
        else return null;
    }

    // SnmpTableHandler.contains()
    public  boolean contains(SnmpOid index) {
        final int pos = find(index);
        return ((pos > -1)&&(pos < indexes.length));
    }

}
