/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.Vector;

import javax.management.ObjectName;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;

import static com.sun.jmx.defaults.JmxProperties.SNMP_ADAPTOR_LOGGER;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpDefinitions;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpEngine;
import com.sun.jmx.snmp.SnmpUnknownModelException;
import com.sun.jmx.snmp.internal.SnmpAccessControlModel;
import com.sun.jmx.snmp.internal.SnmpEngineImpl;

/**
 * This list is used in order to construct the OID during the getnext.
 * The constructed oid is checked by the checker AcmChecker.
 */
final class LongList {

    public static int DEFAULT_CAPACITY = 10;

    public static int DEFAULT_INCREMENT = 10;


    private final int DELTA;
    private int size;

    /**
     * The list content. Any access to this variable must be protected
     * by a synchronized block on the LongList object.
     * Only read-only action should be performed on this object.
     **/
    public  long[] list;

    LongList() {
        this(DEFAULT_CAPACITY,DEFAULT_INCREMENT);
    }

    LongList(int initialCapacity) {
        this(initialCapacity,DEFAULT_INCREMENT);
    }

    LongList(int initialCapacity, int delta) {
        size = 0;
        DELTA = delta;
        list = allocate(initialCapacity);
    }

    /**
     * Same behaviour than size() in {@link java.util.List}.
     **/
    public final int size() { return size;}

    /**
     * Same behaviour than add(long o) in {@link java.util.List}.
     * Any access to this method should be protected in a synchronized
     * block on the LongList object.
     **/
    public final boolean add(final long o) {
        if (size >= list.length)
            resize();
        list[size++]=o;
        return true;
    }

    /**
     * Same behaviour than add(int index, long o) in
     * {@link java.util.List}.
     * Any access to this method should be protected in a synchronized
     * block on the LongList object.
     **/
    public final void add(final int index, final long o) {
        if (index >  size) throw new IndexOutOfBoundsException();
        if (index >= list.length) resize();
        if (index == size) {
            list[size++]=o;
            return;
        }

        java.lang.System.arraycopy(list,index,list,index+1,size-index);
        list[index]=o;
        size++;
    }

    /**
     * Adds <var>count</var> elements to the list.
     * @param at index at which the elements must be inserted. The
     *        first element will be inserted at this index.
     * @param src  An array containing the elements we want to insert.
     * @param from Index of the first element from <var>src</var> that
     *        must be inserted.
     * @param count number of elements to insert.
     * Any access to this method should be protected in a synchronized
     * block on the LongList object.
     **/
    public final void add(final int at,final long[] src, final int from,
                          final int count) {
        if (count <= 0) return;
        if (at > size) throw new IndexOutOfBoundsException();
        ensure(size+count);
        if (at < size) {
            java.lang.System.arraycopy(list,at,list,at+count,size-at);
        }
        java.lang.System.arraycopy(src,from,list,at,count);
        size+=count;
    }

    /**
     * Any access to this method should be protected in a synchronized
     * block on the LongList object.
     **/
    public final long remove(final int from, final int count) {
        if (count < 1 || from < 0) return -1;
        if (from+count > size) return -1;

        final long o = list[from];
        final int oldsize = size;
        size = size - count;

        if (from == size) return o;

        java.lang.System.arraycopy(list,from+count,list,from,
                                   size-from);
        return o;
    }

    /**
     * Same behaviour than remove(int index) in {@link java.util.List}.
     * Any access to this method should be protected in a synchronized
     * block on the LongList object.
     **/
    public final long remove(final int index) {
        if (index >= size) return -1;
        final long o = list[index];
        list[index]=0;
        if (index == --size) return o;

        java.lang.System.arraycopy(list,index+1,list,index,
                                   size-index);
        return o;
    }

    /**
     * Same behaviour than the toArray(long[] a) method in
     * {@link java.util.List}.
     * Any access to this method should be protected in a synchronized
     * block on the LongList object.
     **/
    public final long[] toArray(long[] a) {
        java.lang.System.arraycopy(list,0,a,0,size);
        return a;
    }

    /**
     * Same behaviour than the toArray() method in
     * {@link java.util.List}.
     * Any access to this method should be protected in a synchronized
     * block on the LongList object.
     **/
    public final long[] toArray() {
        return toArray(new long[size]);
    }

    /**
     * Resize the list. Increase its capacity by DELTA elements.
     * Any call to this method must be protected by a synchronized
     * block on this LongList.
     **/
    private final void resize() {
        final long[] newlist = allocate(list.length + DELTA);
        java.lang.System.arraycopy(list,0,newlist,0,size);
        list = newlist;
    }

    /**
     * Resize the list. Insure that the new length will be at
     * least equal to <var>length</var>.
     * @param length new minimal length requested.
     * Any call to this method must be protected by a synchronized
     * block on this LongList.
     **/
    private final void ensure(int length) {
        if (list.length < length) {
            final int min = list.length+DELTA;
            length=(length<min)?min:length;
            final long[] newlist = allocate(length);
            java.lang.System.arraycopy(list,0,newlist,0,size);
            list = newlist;
        }
    }

    /**
     * Allocate a new array of object of specified length.
     **/
    private final long[] allocate(final int length) {
        return new long[length];
    }

}
