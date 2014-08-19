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
import com.sun.jmx.snmp.SnmpStatusException;

import java.io.Serializable;

import java.util.Comparator;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.List;
import java.util.Iterator;

import java.lang.ref.WeakReference;

/**
 * This class is used to cache LoadedClass table data.
 * WARNING : MUST IMPLEMENT THE SnmpTableHandler directly. Some changes in daniel classes.
 **/
public final class SnmpLoadedClassData extends SnmpCachedData {

    /**
     * Constructs a new instance of SnmpLoadedClassData. Instances are
     * immutable.
     * @param lastUpdated Time stamp as returned by
     *        {@link System#currentTimeMillis System.currentTimeMillis()}
     * @param indexMap The table indexed table data, sorted in ascending
     *                 order by {@link #oidComparator}. The keys must be
     *                 instances of {@link SnmpOid}.
     **/
    public SnmpLoadedClassData(long lastUpdated, TreeMap<SnmpOid, Object> indexMap) {
        super(lastUpdated, indexMap, false);
    }


    // SnmpTableHandler.getData()
    public final Object getData(SnmpOid index) {
        int pos = 0;

        try {
            pos = (int) index.getOidArc(0);
        }catch(SnmpStatusException e) {
            return null;
        }

        if (pos >= datas.length) return null;
        return datas[pos];
    }

    // SnmpTableHandler.getNext()
    public final SnmpOid getNext(SnmpOid index) {
        int pos = 0;
        if (index == null) {
            if( (datas!= null) && (datas.length >= 1) )
                return new SnmpOid(0);
        }
        try {
            pos = (int) index.getOidArc(0);
        }catch(SnmpStatusException e) {
            return null;
        }

        if(pos < (datas.length - 1))
            return new SnmpOid(pos+1);
        else
            return null;
    }

    // SnmpTableHandler.contains()
    public final boolean contains(SnmpOid index) {
        int pos = 0;

        try {
            pos = (int) index.getOidArc(0);
        }catch(SnmpStatusException e) {
            return false;
        }

        return (pos < datas.length);
    }

}
