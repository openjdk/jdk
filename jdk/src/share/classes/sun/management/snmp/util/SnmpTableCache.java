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
 * This abstract class implements a weak cache that holds table data.
 * <p>The table data is stored in an instance of
 * {@link SnmpCachedData}, which is kept in a {@link WeakReference}.
 * If the WeakReference is null or empty, the cached data is recomputed.</p>
 *
 * <p><b>NOTE: This class is not synchronized, subclasses must implement
 *          the appropriate synchronization when needed.</b></p>
 **/
public abstract class SnmpTableCache implements Serializable {

    /**
     * Interval of time in ms during which the cached table data
     * is considered valid.
     **/
    protected long validity;

    /**
     * A weak refernce holding cached table data.
     **/
    protected transient WeakReference<SnmpCachedData> datas;

    /**
     * true if the given cached table data is obsolete.
     **/
    protected boolean isObsolete(SnmpCachedData cached) {
        if (cached   == null) return true;
        if (validity < 0)     return false;
        return ((System.currentTimeMillis() - cached.lastUpdated) > validity);
    }

    /**
     * Returns the cached table data.
     * Returns null if the cached data is obsolete, or if there is no
     * cached data, or if the cached data was garbage collected.
     * @return a still valid cached data or null.
     **/
    protected SnmpCachedData getCachedDatas() {
        if (datas == null) return null;
        final SnmpCachedData cached = datas.get();
        if ((cached == null) || isObsolete(cached)) return null;
        return cached;
    }

    /**
     * Returns the cached table data, if it is still valid,
     * or recompute it if it is obsolete.
     * <p>
     * When cache data is recomputed, store it in the weak reference,
     * unless {@link #validity} is 0: then the data will not be stored
     * at all.<br>
     * This method calls {@link #isObsolete(SnmpCachedData)} to determine
     * whether the cached data is obsolete, and {
     * {@link #updateCachedDatas(Object)} to recompute it.
     * </p>
     * @param context A context object.
     * @return the valid cached data, or the recomputed table data.
     **/
    protected synchronized SnmpCachedData getTableDatas(Object context) {
        final SnmpCachedData cached   = getCachedDatas();
        if (cached != null) return cached;
        final SnmpCachedData computedDatas = updateCachedDatas(context);
        if (validity != 0) datas = new WeakReference<SnmpCachedData>(computedDatas);
        return computedDatas;
    }

    /**
     * Recompute cached data.
     * @param context A context object, as passed to
     *        {@link #getTableDatas(Object)}
     **/
    protected abstract SnmpCachedData updateCachedDatas(Object context);

    /**
     * Return a table handler that holds the table data.
     * This method should return the cached table data if it is still
     * valid, recompute it and cache the new value if it's not.
     **/
    public abstract SnmpTableHandler getTableHandler();

}
